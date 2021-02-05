package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.IgnoreFailureCallback;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.NullCall;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.JavaUtils.NoDeleteArrayList;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jetbrains.annotations.NotNull;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Response;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public class EmbeddingEngine {
    private static EmbeddingEngine instance;
    private final List<Embedder<?>> embedders = new NoDeleteArrayList<>();

    // a cache for titles and durations to prevent extra api calls if not necessary
    // maps a URL to a title and duration string; if durations are disabled, the second argument is an empty string
    public static LruCache<String, EmbedResult> videoTitleDurCache = new LruCache<>(500);

    private static final LinkExtractor LINK_EXTRACTOR =
            LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

    private EmbeddingEngine() {
        // Media embedders
        embedders.add(new YoutubeEmbedder());
        embedders.add(new StreamableEmbedder());
        embedders.add(new VocarooEmbedder());
        embedders.add(new ClypEmbedder());
        embedders.add(new SoundcloudEmbedder());
        embedders.add(new BandcampEmbedder());
        embedders.add(new VimeoEmbedder());
        embedders.add(new PixivEmbedder());

        // Special embedders
        embedders.add(new QuickLatexEmbedder());
    }

    public static EmbeddingEngine getInstance() {
        if (instance == null) {
            instance = new EmbeddingEngine();
        }
        return instance;
    }

    public List<Embedder<?>> getEmbedders() {
        return Collections.unmodifiableList(embedders);
    }

    /**
     * To add a media link parser:<br>
     * 1) Implement the Embedder class.<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- The helper method addStandardCalls should be used for most stuff, it takes in all the appropriate stuff you'll need to implement functionality<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- For more complicated stuff, you can extend the necessary items yourself quite easily.<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- Look at already implemented embedders for hints! Streamable's is probably the easiest to understand for a baseline.
     * 2) Add it to the EmbeddingEngine constructor.
     * 3) Done! Everything else is taken care of for you.<br>
     * <br>
     *
     * @param theme              The theme to style the links with
     * @param post               The post where the links will be found and replaced
     * @param invalidateFunction The entire view to be refreshed after embedding
     * @return A list of enqueued calls that will embed the given post
     */

    public List<Call> embed(
            final Theme theme, @NonNull final Post post, @NonNull final InvalidateFunction invalidateFunction
    ) {
        if (post.embedComplete.get()) return Collections.emptyList();
        post.embedComplete.set(true); // prevent duplicate calls
        SpannableStringBuilder modifiableCopy = new SpannableStringBuilder(post.comment);

        // These count as embedding, so we do them here
        List<PostLinkable> generatedLinkables = new ArrayList<>(generateAutoLinks(theme, modifiableCopy));
        List<PostImage> generatedImages = new NoDeleteArrayList<>(generatePostImages(generatedLinkables));

        // Generate all the calls if embedding is on
        List<Pair<Call, Callback>> generatedCallPairs = new NoDeleteArrayList<>();
        if (ChanSettings.enableEmbedding.get()) {
            for (Embedder<?> e : embedders) {
                if (!e.shouldEmbed(modifiableCopy, post.board)) continue;
                generatedCallPairs.addAll(e.generateCallPairs(theme,
                        modifiableCopy,
                        generatedLinkables,
                        generatedImages
                ));
            }
        }

        if (generatedCallPairs.isEmpty()) { // nothing to embed
            post.setComment(modifiableCopy);
            post.linkables.addAll(generatedLinkables);
            post.addImages(generatedImages);
            invalidateFunction.invalidateView(theme, post);
            return Collections.emptyList();
        }

        // Set up and enqueue all the generated calls
        int callCount = generatedCallPairs.size();
        List<Call> calls = new ArrayList<>();
        AtomicInteger processed = new AtomicInteger();
        for (Pair<Call, Callback> c : generatedCallPairs) {
            // enqueue all at the same time, wrapped callback to check when everything's complete
            c.first.enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    BackgroundUtils.runOnBackgroundThread(() -> {
                        c.second.onFailure(call, e);
                        post.embedComplete.set(false); // we've failed embedding and need a redo
                        checkInvalidate();
                    });
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    BackgroundUtils.runOnBackgroundThread(() -> {
                        try {
                            c.second.onResponse(call, response);
                        } catch (IOException e) {
                            c.second.onFailure(call, e);
                        }
                        checkInvalidate();
                    });
                }

                private void checkInvalidate() {
                    if (callCount != processed.incrementAndGet()) return; // still completing calls
                    onEmbeddingComplete(theme,
                            post,
                            modifiableCopy,
                            generatedLinkables,
                            generatedImages,
                            invalidateFunction
                    );
                }
            });
            calls.add(c.first);
        }

        return calls;
    }

    private void onEmbeddingComplete(
            Theme theme,
            Post post,
            SpannableStringBuilder modifiableCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages,
            InvalidateFunction invalidateFunction
    ) {
        // clear out existing links, they're going to be overridden
        List<PostLinkable> toClear = new ArrayList<>();
        for (PostLinkable p : post.linkables) {
            if (p.type == PostLinkable.Type.LINK) {
                for (Embedder<?> e : embedders) {
                    if (TextUtils.equals(p.key, p.value.toString()) && e.shouldEmbed(p.value.toString(),
                            Board.getDummyBoard()
                    )) {
                        toClear.add(p);
                        break;
                    }
                }
            }
        }
        post.linkables.removeAll(toClear);

        // clear out any overlapping embed postlinkables from the generated set
        // split up auto/embed links
        List<PostLinkable> autolinks = new ArrayList<>();
        List<PostLinkable> embedlinks = new ArrayList<>();
        for (PostLinkable linkable : generatedLinkables) {
            if (linkable.key.equals(linkable.value)) {
                autolinks.add(linkable);
            } else {
                embedlinks.add(linkable);
            }
        }

        // remove autolinks if an embed link exists
        for (PostLinkable autolink : autolinks) {
            for (PostLinkable embed : embedlinks) {
                if (autolink.value.equals(embed.value)) {
                    generatedLinkables.remove(autolink);
                }
            }
        }

        post.setComment(modifiableCopy);
        post.linkables.addAll(generatedLinkables);
        post.addImages(generatedImages);
        BackgroundUtils.runOnMainThread(() -> invalidateFunction.invalidateView(theme, post));
    }

    //region Embedding Helper Functions
    private static List<PostLinkable> generateAutoLinks(Theme theme, SpannableStringBuilder comment) {
        List<PostLinkable> generated = new ArrayList<>();
        Iterable<LinkSpan> links = LINK_EXTRACTOR.extractLinks(comment);
        for (LinkSpan link : links) {
            String linkText = TextUtils.substring(comment, link.getBeginIndex(), link.getEndIndex());
            String scheme = linkText.substring(0, linkText.indexOf(':'));
            if (!"http".equals(scheme) && !"https".equals(scheme)) continue; // only autolink URLs, not any random URI
            PostLinkable pl = new PostLinkable(theme, linkText, linkText, PostLinkable.Type.LINK);
            //priority is 0 by default which is maximum above all else; higher priority is like higher layers, i.e. 2 is above 1, 3 is above 2, etc.
            //we use 500 here for to go below post linkables, but above everything else basically
            comment.setSpan(pl,
                    link.getBeginIndex(),
                    link.getEndIndex(),
                    ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY) | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            );
            generated.add(pl);
        }
        return generated;
    }

    //region Image Inlining
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://.*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac)",
            Pattern.CASE_INSENSITIVE
    );
    private static final String[] noThumbLinkSuffixes = {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac"};

    private static List<PostImage> generatePostImages(List<PostLinkable> linkables) {
        if (!ChanSettings.parsePostImageLinks.get()) return Collections.emptyList();
        List<PostImage> generated = new ArrayList<>();
        for (PostLinkable linkable : linkables) {
            Matcher matcher = IMAGE_URL_PATTERN.matcher((String) linkable.value);
            if (matcher.matches()) {
                boolean noThumbnail = StringUtils.endsWithAny(linkable.value.toString(), noThumbLinkSuffixes);
                String spoilerThumbnail = BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png";

                HttpUrl imageUrl = HttpUrl.parse((String) linkable.value);
                if (imageUrl == null
                        || ((String) linkable.value).contains("saucenao")) { // ignore saucenao links, not actual images
                    continue;
                }

                PostImage inlinedImage = new PostImage.Builder().serverFilename(matcher.group(1))
                        //spoiler thumb for some linked items, the image itself for the rest; probably not a great idea
                        .thumbnailUrl(HttpUrl.parse(noThumbnail ? spoilerThumbnail : (String) linkable.value))
                        .spoilerThumbnailUrl(HttpUrl.parse(spoilerThumbnail))
                        .imageUrl(imageUrl)
                        .filename(matcher.group(1))
                        .extension(matcher.group(2))
                        .spoiler(true)
                        .isInlined()
                        .size(-1)
                        .build();

                generated.add(inlinedImage);

                NetUtils.makeHeadersRequest(imageUrl, new ResponseResult<Headers>() {
                    @Override
                    public void onFailure(Exception e) {}

                    @Override
                    public void onSuccess(Headers result) {
                        String size = result.get("Content-Length");
                        inlinedImage.size = size == null ? 0 : Long.parseLong(size);
                    }
                });
            }
        }
        return generated;
    }
    //endregion

    public static <T> List<Pair<Call, Callback>> addStandardEmbedCalls(
            Embedder<T> embedder,
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages
    ) {
        List<Pair<Call, Callback>> calls = new ArrayList<>();
        Set<Pair<String, HttpUrl>> toReplace = generateReplacements(embedder, commentCopy);

        for (Pair<String, HttpUrl> urlPair : toReplace) {
            EmbedResult result = videoTitleDurCache.get(urlPair.first);
            if (result != null) {
                // we've previously cached this embed and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(getStandardCachedCallPair(theme,
                        commentCopy,
                        generatedLinkables,
                        generatedImages,
                        result,
                        urlPair.first,
                        embedder.getIconBitmap()
                ));
            } else {
                // we haven't cached this embed, or we need additional information
                calls.add(NetUtils.makeCall(urlPair.second, embedder, embedder, getStandardResponseResult(theme,
                        commentCopy,
                        generatedLinkables,
                        generatedImages,
                        urlPair.first,
                        embedder.getIconBitmap()
                ), 2500, false));
            }
        }
        return calls;
    }

    private static <T> Set<Pair<String, HttpUrl>> generateReplacements(
            Embedder<T> embedder, SpannableStringBuilder comment
    ) {
        Set<Pair<String, HttpUrl>> result = new HashSet<>();
        Matcher linkMatcher = embedder.getEmbedReplacePattern().matcher(comment);
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);
            if (URL == null) continue;
            result.add(new Pair<>(URL, embedder.generateRequestURL(linkMatcher)));
        }
        return result;
    }

    private static ResponseResult<EmbedResult> getStandardResponseResult(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages,
            String URL,
            Bitmap iconBitmap
    ) {
        return new ResponseResult<EmbedResult>() {
            @Override
            public void onFailure(Exception e) {} // don't do anything, let the autolinker take care of it

            @Override
            public void onSuccess(EmbedResult result) {
                //got a result, replace with the result and also cache the result
                videoTitleDurCache.put(URL, result);
                performStandardEmbedding(theme,
                        commentCopy,
                        generatedLinkables,
                        generatedImages,
                        result,
                        URL,
                        iconBitmap
                );
            }
        };
    }

    private static Pair<Call, Callback> getStandardCachedCallPair(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages,
            EmbedResult result,
            String URL,
            Bitmap iconBitmap
    ) {
        return new Pair<>(new NullCall(HttpUrl.get(URL)), new IgnoreFailureCallback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                performStandardEmbedding(theme,
                        commentCopy,
                        generatedLinkables,
                        generatedImages,
                        result,
                        URL,
                        iconBitmap
                );
            }
        });
    }

    /**
     * Performs a "standard" embed of an icon followed by a title and optional duration.<br>
     * Additionally, this method adds in any embed post image results to the given post.<br>
     * <br>
     * Generally don't use this directly, use it through the addStandardEmbedCalls method
     */
    public static void performStandardEmbedding(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages,
            @NonNull EmbedResult parseResult,
            @NonNull String URL,
            final Bitmap icon
    ) {
        int index = 0;
        while (true) { // this will always break eventually
            synchronized (commentCopy) {
                // search from the last known replacement location
                index = TextUtils.indexOf(commentCopy, URL, index);
                if (index < 0) break;

                // Generate a fresh replacement string (in case of repeats)
                SpannableStringBuilder replacement = new SpannableStringBuilder(
                        "  " + parseResult.title + (!TextUtils.isEmpty(parseResult.duration) ? " "
                                + parseResult.duration : ""));

                // Set the icon span for the linkable
                ImageSpan siteIcon = new ImageSpan(getAppContext(), icon);
                int height = sp(ChanSettings.fontSize.get());
                int width = (int) (height / (icon.getHeight() / (float) icon.getWidth()));
                siteIcon.getDrawable().setBounds(0, 0, width, height);
                replacement.setSpan(siteIcon,
                        0,
                        1,
                        ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
                                | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                );

                // Set the linkable to be the entire length, including the icon
                PostLinkable pl = new PostLinkable(theme, replacement, URL, PostLinkable.Type.LINK);
                replacement.setSpan(pl,
                        0,
                        replacement.length(),
                        ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
                                | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                );

                // replace the proper section of the comment with the link
                commentCopy.replace(index, index + URL.length(), replacement);
                generatedLinkables.add(pl);

                // if linking is enabled, add in any processed inlines
                if (ChanSettings.parsePostImageLinks.get() && parseResult.extraImage != null) {
                    generatedImages.add(parseResult.extraImage);
                }
                // update the index to the next location
                index = index + replacement.length();
            }
        }
    }

    public static class EmbedResult {
        /**
         * The title for this embed result. While this can't be null, also don't make this an empty string either.
         */
        public String title;

        /**
         * An optional duration for this embed. If null and duration parsing is enabled, then another request will be sent to get a duration.
         * If you don't want/have any duration data, set this to an empty string.
         */
        public String duration;

        /**
         * An optional post image that is generated by an embedder, if image hotlinking is enabled. Makes some embeds more easily viewable in-app quickly.
         */
        public PostImage extraImage;

        @SuppressWarnings("unused")
        private EmbedResult() {} // for gson, don't use otherwise

        public EmbedResult(@NonNull String title, @Nullable String duration, @Nullable PostImage extraImage) {
            this.title = title;
            this.duration = duration;
            this.extraImage = extraImage;
        }
    }

    public interface InvalidateFunction {
        void invalidateView(Theme theme, Post post);
    }
    //endregion
}
