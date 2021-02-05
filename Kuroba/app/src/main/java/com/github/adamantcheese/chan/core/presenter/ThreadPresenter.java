/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.presenter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ResolveLink;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.SearchLink;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ThreadLink;
import com.github.adamantcheese.chan.features.embedding.Embedder;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.ui.layout.ThreadListLayout;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.PostUtils;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;

public class ThreadPresenter
        implements ChanThreadLoader.ChanLoaderCallback, PostAdapter.PostAdapterCallback,
                   PostCellInterface.PostCellCallback, ThreadStatusCell.Callback,
                   ThreadListLayout.ThreadListLayoutPresenterCallback, ArchivesLayout.Callback {
    //region Private Variables
    private static final int POST_OPTION_QUOTE = 0;
    private static final int POST_OPTION_QUOTE_TEXT = 1;
    private static final int POST_OPTION_INFO = 2;
    private static final int POST_OPTION_LINKS = 3;
    private static final int POST_OPTION_COPY = 4;
    private static final int POST_OPTION_REPORT = 5;
    private static final int POST_OPTION_HIGHLIGHT_ID = 6;
    private static final int POST_OPTION_DELETE = 7;
    private static final int POST_OPTION_SAVE = 8;
    private static final int POST_OPTION_UNSAVE = 9;
    private static final int POST_OPTION_PIN = 10;
    private static final int POST_OPTION_SHARE = 11;
    private static final int POST_OPTION_HIGHLIGHT_TRIPCODE = 12;
    private static final int POST_OPTION_HIDE = 13;
    private static final int POST_OPTION_OPEN_BROWSER = 14;
    private static final int POST_OPTION_FILTER = 15;
    private static final int POST_OPTION_FILTER_TRIPCODE = 16;
    private static final int POST_OPTION_FILTER_IMAGE_HASH = 17;
    private static final int POST_OPTION_FILTER_SUBJECT = 18;
    private static final int POST_OPTION_FILTER_COMMENT = 19;
    private static final int POST_OPTION_FILTER_NAME = 20;
    private static final int POST_OPTION_FILTER_ID = 21;
    private static final int POST_OPTION_FILTER_FILENAME = 22;
    private static final int POST_OPTION_FILTER_COUNTRY_CODE = 23;
    private static final int POST_OPTION_EXTRA = 24;
    private static final int POST_OPTION_REMOVE = 25;
    private static final int POST_OPTION_COPY_POST_LINK = 26;
    private static final int POST_OPTION_COPY_CROSS_BOARD_LINK = 27;
    private static final int POST_OPTION_COPY_POST_TEXT = 28;
    private static final int POST_OPTION_COPY_IMG_URL = 29;
    private static final int POST_OPTION_COPY_POST_URL = 30;

    @Inject
    private WatchManager watchManager;

    @Inject
    private DatabaseLoadableManager databaseLoadableManager;

    @Inject
    private DatabaseSavedReplyManager databaseSavedReplyManager;

    @Inject
    private FileManager fileManager;

    @Inject
    private CacheHandler cacheHandler;

    @Inject
    private BoardManager boardManager;

    @Inject
    private FilterWatchManager filterWatchManager;

    private final ThreadPresenterCallback threadPresenterCallback;
    private Loadable loadable;
    private ChanThreadLoader chanLoader;
    private boolean searchOpen;
    private String searchQuery;
    private PostsFilter.Order order = PostsFilter.Order.BUMP;
    private final Context context;
    private List<FloatingMenuItem<Integer>> filterMenu;
    private List<FloatingMenuItem<Integer>> copyMenu;
    //endregion

    public ThreadPresenter(Context context, ThreadPresenterCallback callback) {
        this.context = context;
        threadPresenterCallback = callback;
        inject(this);
    }

    public void showNoContent() {
        threadPresenterCallback.showEmpty();
    }

    public synchronized void bindLoadable(Loadable loadable) {
        if (!loadable.equals(this.loadable)) {
            if (isBound()) {
                unbindLoadable();
            }

            this.loadable = loadable;

            loadable.lastLoadDate = GregorianCalendar.getInstance().getTime();
            DatabaseUtils.runTaskAsync(databaseLoadableManager.updateLoadable(loadable, false));

            chanLoader = ChanLoaderManager.obtain(loadable, this);
            threadPresenterCallback.showLoading();
        }
    }

    public synchronized void unbindLoadable() {
        if (isBound()) {
            chanLoader.clearTimer();
            ChanLoaderManager.release(chanLoader, this);
            chanLoader = null;
            loadable = null;

            threadPresenterCallback.showLoading();
        }
    }

    public void updateDatabaseLoadable() {
        DatabaseUtils.runTaskAsync(databaseLoadableManager.updateLoadable(loadable, false));
    }

    public boolean isBound() {
        return loadable != null && chanLoader != null;
    }

    public void requestInitialData() {
        if (isBound()) {
            if (chanLoader.getThread() == null) {
                requestData();
            } else {
                chanLoader.quickLoad();
            }
        }
    }

    public void requestData() {
        BackgroundUtils.ensureMainThread();

        if (isBound()) {
            threadPresenterCallback.showLoading();
            chanLoader.requestData();
        }
    }

    public void onForegroundChanged(boolean foreground) {
        if (isBound()) {
            if (foreground && isWatching()) {
                chanLoader.requestMoreData();
                if (chanLoader.getThread() != null) {
                    // Show loading indicator in the status cell
                    showPosts();
                }
            } else {
                chanLoader.clearTimer();
            }
        }
    }

    public synchronized boolean pin() {
        if (!isBound()) return false;
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null) {
            if (chanLoader.getThread() != null) {
                Post op = chanLoader.getThread().getOp();
                loadable.thumbnailUrl = op.image() == null ? null : op.image().getThumbnailUrl();
            }
            watchManager.createPin(loadable);
            return true;
        }

        if (pin.watching) {
            watchManager.deletePin(pin);
        } else {
            watchManager.createPin(pin);
        }

        return true;
    }

    public void onSearchVisibilityChanged(boolean visible) {
        searchOpen = visible;
        threadPresenterCallback.showSearch(visible);
        if (!visible) {
            searchQuery = null;
        }

        if (isBound() && chanLoader.getThread() != null && !visible) {
            showPosts();
        }
    }

    public void onSearchEntered(String entered) {
        searchQuery = entered;
        if (isBound() && chanLoader.getThread() != null) {
            showPosts();
            if (TextUtils.isEmpty(entered)) {
                threadPresenterCallback.setSearchStatus(null, true, false);
            } else {
                threadPresenterCallback.setSearchStatus(entered, false, false);
            }
        }
    }

    public void setOrder(PostsFilter.Order order) {
        if (this.order != order) {
            this.order = order;
            if (isBound() && chanLoader.getThread() != null) {
                scrollTo(0, false);
                showPosts();
            }
        }
    }

    public synchronized void showAlbum() {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        int[] pos = threadPresenterCallback.getCurrentPosition();
        int displayPosition = pos[0];

        List<PostImage> images = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < posts.size(); i++) {
            Post item = posts.get(i);
            for (PostImage image : item.images) {
                if (image.type != PostImage.Type.IFRAME) {
                    images.add(image);
                }
            }
            if (i == displayPosition) {
                index = images.size();
            }
        }

        threadPresenterCallback.showAlbum(images, index);
    }

    @Override
    public Loadable getLoadable() {
        return loadable;
    }

    /*
     * ChanThreadLoader callbacks
     */
    @Override
    public void onChanLoaderData(ChanThread result) {
        BackgroundUtils.ensureMainThread();

        if (isBound()) {
            if (isWatching()) {
                chanLoader.setTimer();
            } else {
                chanLoader.clearTimer();
            }
        } else {
            Logger.e(this, "onChanLoaderData when not bound!");
            return;
        }

        //allow for search refreshes inside the catalog
        if (result.getLoadable().isCatalogMode() && !TextUtils.isEmpty(searchQuery)) {
            onSearchEntered(searchQuery);
        } else {
            showPosts();
        }

        if (loadable.isThreadMode()) {
            List<Post> posts = result.getPosts();
            int postsCount = posts.size();

            // calculate how many new posts since last load
            int more = 0;
            for (int i = 0; i < postsCount; i++) {
                Post p = posts.get(i);
                if (p.no == loadable.lastLoaded) {
                    // end index minus last loaded index
                    more = (postsCount - 1) - i;
                    break;
                }
            }

            // update last loaded post
            loadable.lastLoaded = posts.get(postsCount - 1).no;

            // this loadable is fresh, for new post reasons set it to the last loaded
            if (loadable.lastViewed == -1) {
                loadable.lastViewed = loadable.lastLoaded;
            }

            if (more > 0 && loadable.no == result.getLoadable().no) {
                threadPresenterCallback.showNewPostsSnackbar(more);
            }
        }

        if (loadable.markedNo >= 0) {
            Post markedPost = PostUtils.findPostById(loadable.markedNo, chanLoader.getThread());
            if (markedPost != null) {
                highlightPostNo(markedPost.no);
                if (BackgroundUtils.isInForeground()) {
                    scrollToPost(markedPost, false);
                }
                if (StartActivity.loadedFromURL) {
                    BackgroundUtils.runOnMainThread(() -> scrollToPost(markedPost, false), 1000);
                    StartActivity.loadedFromURL = false;
                }
            }
            loadable.markedNo = -1;
        }

        updateDatabaseLoadable();
    }

    @Override
    public void onChanLoaderError(ChanThreadLoader.ChanLoaderException error) {
        Logger.d(this, "onChanLoaderError()");
        threadPresenterCallback.showError(error);
    }

    /*
     * PostAdapter callbacks
     */
    @Override
    public void onListScrolledToBottom() {
        if (!isBound()) return;
        if (chanLoader.getThread() != null && loadable.isThreadMode() && chanLoader.getThread().getPosts().size() > 0) {
            List<Post> posts = chanLoader.getThread().getPosts();
            loadable.lastViewed = posts.get(posts.size() - 1).no;
        }

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin != null) {
            watchManager.onBottomPostViewed(pin);
        }

        threadPresenterCallback.showNewPostsSnackbar(-1);

        // Update the last seen indicator
        showPosts();
    }

    public void onNewPostsViewClicked() {
        if (!isBound()) return;
        Post post = PostUtils.findPostById(loadable.lastViewed, chanLoader.getThread());
        int position = -1;
        if (post != null) {
            List<Post> posts = threadPresenterCallback.getDisplayingPosts();
            for (int i = 0; i < posts.size(); i++) {
                Post needle = posts.get(i);
                if (post.no == needle.no) {
                    position = i;
                    break;
                }
            }
        }
        // scroll to post after last viewed
        threadPresenterCallback.smoothScrollNewPosts(position + 1);
    }

    public void scrollTo(int displayPosition, boolean smooth) {
        threadPresenterCallback.scrollTo(displayPosition, smooth);
    }

    public void scrollToImage(PostImage postImage, boolean smooth) {
        if (!searchOpen) {
            int position = -1;
            List<Post> posts = threadPresenterCallback.getDisplayingPosts();

            out:
            for (int i = 0; i < posts.size(); i++) {
                Post post = posts.get(i);
                for (PostImage image : post.images) {
                    if (image == postImage) {
                        position = i;
                        break out;
                    }
                }
            }
            if (position >= 0) {
                scrollTo(position, smooth);
            }
        }
    }

    public void scrollToPost(Post needle, boolean smooth) {
        int position = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            if (post.no == needle.no) {
                position = i;
                break;
            }
        }
        if (position >= 0) {
            scrollTo(position, smooth);
        }
    }

    public void highlightPostNo(int postNo) {
        threadPresenterCallback.highlightPostNo(postNo);
    }

    public void selectPostImage(PostImage postImage) {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post post : posts) {
            for (PostImage image : post.images) {
                if (image == postImage) {
                    scrollToPost(post, false);
                    highlightPostNo(post.no);
                    return;
                }
            }
        }
    }

    public Post getPostFromPostImage(PostImage postImage) {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post post : posts) {
            for (PostImage image : post.images) {
                if (image == postImage) {
                    return post;
                }
            }
        }
        return null;
    }

    /*
     * PostView callbacks
     */
    @Override
    public void onPostClicked(Post post) {
        if (!isBound()) return;
        if (loadable.isCatalogMode()) {
            threadPresenterCallback.showThread(Loadable.forThread(post.board,
                    post.no,
                    PostHelper.getTitle(post, loadable)
            ));
        }
    }

    @Override
    public void onPostDoubleClicked(Post post) {
        if (isBound() && loadable.isThreadMode()) {
            if (searchOpen) {
                searchQuery = null;
                showPosts();
                threadPresenterCallback.setSearchStatus(null, false, true);
                threadPresenterCallback.showSearch(false);
                highlightPostNo(post.no);
                scrollToPost(post, false);
            } else {
                threadPresenterCallback.postClicked(post);
            }
        }
    }

    @Override
    public void onThumbnailClicked(PostImage postImage, ThumbnailView thumbnail) {
        if (!isBound()) return;
        List<PostImage> images = new ArrayList<>();
        int index = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post item : posts) {
            for (PostImage image : item.images) {
                if (!item.deleted.get() || cacheHandler.exists(image.imageUrl)) {
                    //deleted posts always have 404'd images, but let it through if the file exists in cache
                    images.add(image);
                    if (image.equals(postImage)) {
                        index = images.size() - 1;
                    }
                }
            }
        }

        if (!images.isEmpty()) {
            threadPresenterCallback.showImages(images, index, loadable, thumbnail);
        }
    }

    @Override
    public Object onPopulatePostOptions(
            Post post, List<FloatingMenuItem<Integer>> menu, List<FloatingMenuItem<Integer>> extraMenu
    ) {
        if (!isBound()) return null;

        boolean isSaved = databaseSavedReplyManager.isSaved(post.board, post.no);

        copyMenu = populateCopyMenuOptions(post);
        filterMenu = populateFilterMenuOptions(post);

        if (loadable.isCatalogMode() && watchManager.getPinByLoadable(Loadable.forThread(post.board,
                post.no,
                PostHelper.getTitle(post, loadable),
                false
        )) == null && !(loadable.site instanceof ExternalSiteArchive)) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_PIN, R.string.action_pin));
        }

        if (loadable.site.siteFeature(Site.SiteFeature.POSTING)) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_QUOTE, R.string.post_quote));
            menu.add(new FloatingMenuItem<>(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text));
        }

        if (loadable.site.siteFeature(Site.SiteFeature.POST_REPORT)) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_REPORT, R.string.post_report));
        }

        if (!(loadable.site instanceof ExternalSiteArchive)) {
            if ((loadable.isCatalogMode() || (loadable.isThreadMode() && !post.isOP))) {
                if (!post.filterStub) {
                    menu.add(new FloatingMenuItem<>(POST_OPTION_HIDE, R.string.post_hide));
                }
                menu.add(new FloatingMenuItem<>(POST_OPTION_REMOVE, R.string.post_remove));
            }
        }

        if (loadable.isThreadMode()) {
            if (!TextUtils.isEmpty(post.id)) {
                menu.add(new FloatingMenuItem<>(POST_OPTION_HIGHLIGHT_ID, R.string.post_highlight_id));
            }

            if (!TextUtils.isEmpty(post.tripcode)) {
                menu.add(new FloatingMenuItem<>(POST_OPTION_HIGHLIGHT_TRIPCODE, R.string.post_highlight_tripcode));
            }
        }

        menu.add(new FloatingMenuItem<>(POST_OPTION_COPY, R.string.post_copy_menu));

        if (loadable.site.siteFeature(Site.SiteFeature.POST_DELETE) && isSaved) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_DELETE, R.string.post_delete));
        }

        if (ChanSettings.accessibleInfo.get()) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_INFO, R.string.post_info));
        } else {
            extraMenu.add(new FloatingMenuItem<>(POST_OPTION_INFO, R.string.post_info));
        }

        menu.add(new FloatingMenuItem<>(POST_OPTION_EXTRA, R.string.post_more));

        if (post.linkables.size() > 0) {
            extraMenu.add(new FloatingMenuItem<>(POST_OPTION_LINKS, R.string.post_show_links));
        }
        extraMenu.add(new FloatingMenuItem<>(POST_OPTION_OPEN_BROWSER, R.string.action_open_browser));
        extraMenu.add(new FloatingMenuItem<>(POST_OPTION_SHARE, R.string.post_share));

        //if the filter menu only has a single option we place just that option in the root menu
        //in some cases a post will have nothing in it to filter (for example a post with no text and an image
        //that is removed by a filter), in such cases there is no filter menu option.
        if (filterMenu.size() > 1) {
            extraMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER, R.string.post_filter));
        } else if (filterMenu.size() == 1) {
            FloatingMenuItem<Integer> menuItem = filterMenu.remove(0);
            extraMenu.add(new FloatingMenuItem<>(menuItem.getId(), "Filter " + menuItem.getText().toLowerCase()));
        }

        if (!(loadable.site instanceof ExternalSiteArchive)) {
            extraMenu.add(new FloatingMenuItem<>(isSaved ? POST_OPTION_UNSAVE : POST_OPTION_SAVE,
                    isSaved ? R.string.unmark_as_my_post : R.string.mark_as_my_post
            ));
        }

        return POST_OPTION_EXTRA;
    }

    public void onPostOptionClicked(View anchor, Post post, Object id, boolean inPopup) {
        switch ((Integer) id) {
            case POST_OPTION_QUOTE:
                threadPresenterCallback.hidePostsPopup();
                threadPresenterCallback.quote(post, false);
                break;
            case POST_OPTION_QUOTE_TEXT:
                threadPresenterCallback.hidePostsPopup();
                threadPresenterCallback.quote(post, true);
                break;
            case POST_OPTION_INFO:
                showPostInfo(post);
                break;
            case POST_OPTION_LINKS:
                Set<String> added = new HashSet<>();
                List<CharSequence> keys = new ArrayList<>();
                for (PostLinkable linkable : post.linkables) {
                    //skip SPOILER linkables, they aren't useful to display
                    if (linkable.type == PostLinkable.Type.SPOILER) continue;
                    String key = linkable.key.toString();
                    String value = linkable.value.toString();
                    //need to trim off starting spaces for certain media links if embedded
                    String trimmedUrl = (key.charAt(0) == ' ' && key.charAt(1) == ' ') ? key.substring(2) : key;
                    boolean speciallyProcessed = false;
                    for (Embedder<?> e : EmbeddingEngine.getInstance().getEmbedders()) {
                        if (e.shouldEmbed(value, loadable.board)) {
                            if (added.contains(trimmedUrl)) continue;
                            keys.add(PostHelper.prependIcon(context, trimmedUrl, e.getIconBitmap(), sp(16)));
                            added.add(trimmedUrl);
                            speciallyProcessed = true;
                            break;
                        }
                    }
                    if (!speciallyProcessed) {
                        keys.add(key);
                    }
                }

                AlertDialog dialog = getDefaultAlertBuilder(context).create();

                ListView clickables = new ListView(context);
                clickables.setAdapter(new ArrayAdapter<>(context, R.layout.simple_list_item, keys));
                clickables.setOnItemClickListener((parent, view, position, id1) -> {
                    onPostLinkableClicked(post, new ArrayList<>(post.linkables).get(position));
                    dialog.dismiss();
                });
                clickables.setOnItemLongClickListener((parent, view, position, id1) -> {
                    setClipboardContent("Linkable URL", new ArrayList<>(post.linkables).get(position).value.toString());
                    showToast(context, R.string.linkable_copied_to_clipboard);
                    return true;
                });

                dialog.setView(clickables);
                dialog.setCanceledOnTouchOutside(true);
                dialog.show();
                break;
            case POST_OPTION_COPY:
                showSubMenuOptions(anchor, post, inPopup, copyMenu);
                break;
            case POST_OPTION_COPY_POST_LINK:
                setClipboardContent("Post link", String.format(Locale.ENGLISH, ">>%d", post.no));
                showToast(context, R.string.post_link_copied);
                break;
            case POST_OPTION_COPY_CROSS_BOARD_LINK:
                setClipboardContent("Cross-board link",
                        String.format(Locale.ENGLISH, ">>>/%s/%d", post.boardCode, post.no)
                );
                showToast(context, R.string.post_cross_board_link_copied);
                break;
            case POST_OPTION_COPY_POST_URL:
                setClipboardContent("Post URL", loadable.site.resolvable().desktopUrl(loadable, post.no));
                showToast(context, R.string.post_url_copied);
                break;
            case POST_OPTION_COPY_IMG_URL:
                setClipboardContent("Image URL", post.image().imageUrl.toString());
                showToast(context, R.string.image_url_copied_to_clipboard);
                break;
            case POST_OPTION_COPY_POST_TEXT:
                setClipboardContent("Post text", post.comment.toString());
                showToast(context, R.string.post_text_copied);
                break;
            case POST_OPTION_REPORT:
                if (inPopup) {
                    threadPresenterCallback.hidePostsPopup();
                }
                threadPresenterCallback.openReportView(post);
                break;
            case POST_OPTION_HIGHLIGHT_ID:
                threadPresenterCallback.highlightPostId(post.id);
                break;
            case POST_OPTION_HIGHLIGHT_TRIPCODE:
                threadPresenterCallback.highlightPostTripcode(post.tripcode);
                break;
            case POST_OPTION_FILTER:
                showSubMenuOptions(anchor, post, inPopup, filterMenu);
                break;
            case POST_OPTION_FILTER_SUBJECT:
                threadPresenterCallback.filterPostSubject(post.subject);
                break;
            case POST_OPTION_FILTER_COMMENT:
                threadPresenterCallback.filterPostComment(post.comment);
                break;
            case POST_OPTION_FILTER_NAME:
                threadPresenterCallback.filterPostName(post.name);
                break;
            case POST_OPTION_FILTER_FILENAME:
                threadPresenterCallback.filterPostFilename(post);
                break;
            case POST_OPTION_FILTER_COUNTRY_CODE:
                threadPresenterCallback.filterPostCountryCode(post);
                break;
            case POST_OPTION_FILTER_ID:
                threadPresenterCallback.filterPostID(post.id);
                break;
            case POST_OPTION_FILTER_TRIPCODE:
                threadPresenterCallback.filterPostTripcode(post.tripcode);
                break;
            case POST_OPTION_FILTER_IMAGE_HASH:
                threadPresenterCallback.filterPostImageHash(post);
                break;
            case POST_OPTION_DELETE:
                requestDeletePost(post);
                break;
            case POST_OPTION_SAVE:
                if (!isBound()) break;
                for (Post p : getMatchingIds(post)) {
                    SavedReply saved = SavedReply.fromBoardNoPassword(p.board, p.no, "");
                    DatabaseUtils.runTask(databaseSavedReplyManager.saveReply(saved));
                    Pin watchedPin = watchManager.getPinByLoadable(loadable);
                    if (watchedPin != null) {
                        synchronized (this) {
                            watchedPin.quoteLastCount += p.repliesFrom.size();
                        }
                    }
                }
                //force reload for reply highlighting
                requestData();
                break;
            case POST_OPTION_UNSAVE:
                if (!isBound()) break;
                for (Post p : getMatchingIds(post)) {
                    SavedReply saved = databaseSavedReplyManager.getSavedReply(p.board, p.no);
                    if (saved != null) {
                        //unsave
                        DatabaseUtils.runTask(databaseSavedReplyManager.unsaveReply(saved));
                        Pin watchedPin = watchManager.getPinByLoadable(loadable);
                        if (watchedPin != null) {
                            synchronized (this) {
                                watchedPin.quoteLastCount -= p.repliesFrom.size();
                            }
                        }
                    } else {
                        Logger.w(this, "SavedReply null, can't unsave");
                    }
                }
                //force reload for reply highlighting
                requestData();
                break;
            case POST_OPTION_PIN:
                Loadable threadPin = Loadable.forThread(post.board, post.no, PostHelper.getTitle(post, loadable));
                threadPin.thumbnailUrl = post.image() == null ? null : post.image().getThumbnailUrl();
                watchManager.createPin(threadPin);
                break;
            case POST_OPTION_OPEN_BROWSER:
                if (isBound()) {
                    openLink(loadable.site.resolvable().desktopUrl(loadable, post.no));
                }
                break;
            case POST_OPTION_SHARE:
                if (isBound()) {
                    shareLink(loadable.site.resolvable().desktopUrl(loadable, post.no));
                }
                break;
            case POST_OPTION_REMOVE:
            case POST_OPTION_HIDE:
                if (chanLoader == null || chanLoader.getThread() == null) {
                    break;
                }

                boolean hide = ((Integer) id) == POST_OPTION_HIDE;

                if (chanLoader.getThread().getLoadable().mode == Loadable.Mode.CATALOG) {
                    threadPresenterCallback.hideThread(post, post.no, hide);
                } else {
                    if (post.repliesFrom.isEmpty()) {
                        // no replies to this post so no point in showing the dialog
                        hideOrRemovePosts(hide, false, post, chanLoader.getThread().getOp().no);
                    } else {
                        // show a dialog to the user with options to hide/remove the whole chain of posts
                        threadPresenterCallback.showHideOrRemoveWholeChainDialog(hide,
                                post,
                                chanLoader.getThread().getOp().no
                        );
                    }
                }
                break;
        }
    }

    private Set<Post> getMatchingIds(Post post) {
        Set<Post> matching = new HashSet<>(1);
        if (TextUtils.isEmpty(post.id)) {
            //just this post
            matching.add(post);
        } else {
            //match all post IDs
            for (Post p : getChanThread().getPosts()) {
                if (!TextUtils.isEmpty(p.id) && p.id.equals(post.id)) {
                    matching.add(p);
                }
            }
        }
        return matching;
    }

    private void showSubMenuOptions(View anchor, Post post, Boolean inPopup, List<FloatingMenuItem<Integer>> options) {
        FloatingMenu<Integer> menu = new FloatingMenu<>(context, anchor, options);
        menu.setCallback(new FloatingMenu.ClickCallback<Integer>() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu<Integer> menu, FloatingMenuItem<Integer> item) {
                onPostOptionClicked(anchor, post, item.getId(), inPopup);
            }
        });
        menu.show();
    }

    @Override
    public void onPostLinkableClicked(Post post, PostLinkable linkable) {
        if (!isBound()) return;
        if (linkable.type == PostLinkable.Type.QUOTE) {
            Post linked = PostUtils.findPostById((int) linkable.value, chanLoader.getThread());
            if (linked != null) {
                threadPresenterCallback.showPostsPopup(post, Collections.singletonList(linked));
            }
        } else if (linkable.type == PostLinkable.Type.LINK) {
            threadPresenterCallback.openLink((String) linkable.value);
        } else if (linkable.type == PostLinkable.Type.THREAD) {
            ThreadLink link = (ThreadLink) linkable.value;

            Board board = loadable.site.board(link.board);
            if (board != null) {
                Loadable thread =
                        Loadable.forThread(board, link.threadId, "", !(board.site instanceof ExternalSiteArchive));
                thread.markedNo = link.postId;

                threadPresenterCallback.showThread(thread);
            }
        } else if (linkable.type == PostLinkable.Type.BOARD) {
            Board board = boardManager.getBoard(loadable.site, (String) linkable.value);
            if (board == null) {
                showToast(context, R.string.site_uses_dynamic_boards);
            } else {
                threadPresenterCallback.showBoard(Loadable.forCatalog(board));
            }
        } else if (linkable.type == PostLinkable.Type.SEARCH) {
            SearchLink search = (SearchLink) linkable.value;
            Board board = boardManager.getBoard(loadable.site, search.board);
            if (board == null) {
                showToast(context, R.string.site_uses_dynamic_boards);
            } else {
                threadPresenterCallback.showBoardAndSearch(Loadable.forCatalog(board), search.search);
            }
        } else if (linkable.type == PostLinkable.Type.ARCHIVE) {
            if (linkable.value instanceof ThreadLink) {
                ThreadLink opPostPair = (ThreadLink) linkable.value;
                Loadable constructed =
                        Loadable.forThread(Board.fromSiteNameCode(loadable.site, opPostPair.board, opPostPair.board),
                                opPostPair.threadId,
                                "",
                                false
                        );
                showArchives(constructed, opPostPair.postId);
            } else if (linkable.value instanceof ResolveLink) {
                ResolveLink toResolve = (ResolveLink) linkable.value;
                if (toResolve.board.site instanceof ExternalSiteArchive) {
                    showToast(context, "Calling archive API, just a moment!");
                    toResolve.resolve((threadLink) -> {
                        if (threadLink != null) {
                            Loadable constructed = Loadable.forThread(Board.fromSiteNameCode(toResolve.board.site,
                                    threadLink.board,
                                    threadLink.board
                                    ),
                                    threadLink.threadId,
                                    "",
                                    false
                            );
                            showArchives(constructed, threadLink.postId);
                        } else {
                            showToast(context, "Failed to resolve thread external post link!");
                        }
                    }, new ResolveLink.ResolveParser(toResolve));
                } else {
                    // for any dead links that aren't in an archive, assume that they're a link to a previous thread OP
                    Loadable constructed = Loadable.forThread(toResolve.board, toResolve.postId, "", false);
                    showArchives(constructed, toResolve.postId);
                }
            }
        }
    }

    @Override
    public void onPostNoClicked(Post post) {
        threadPresenterCallback.quote(post, false);
    }

    @Override
    public void onPostSelectionQuoted(Post post, CharSequence quoted) {
        threadPresenterCallback.quote(post, quoted);
    }

    @Override
    public void onShowPostReplies(Post post) {
        if (!isBound()) return;
        List<Post> posts = new ArrayList<>();
        for (int no : post.repliesFrom) {
            Post replyPost = PostUtils.findPostById(no, chanLoader.getThread());
            if (replyPost != null) {
                posts.add(replyPost);
            }
        }
        if (posts.size() > 0) {
            threadPresenterCallback.showPostsPopup(post, posts);
        }
    }

    /*
     * ThreadStatusCell callbacks
     */
    @Override
    public long getTimeUntilLoadMore() {
        return isBound() ? chanLoader.getTimeUntilLoadMore() : 0L;
    }

    @Override
    public boolean isWatching() {
        //@formatter:off
        return ChanSettings.autoRefreshThread.get()
            && BackgroundUtils.isInForeground()
            && isBound()
            && loadable.isThreadMode()
            && !(loadable.site instanceof ExternalSiteArchive)
            && chanLoader.getThread() != null
            && !chanLoader.getThread().isClosed()
            && !chanLoader.getThread().isArchived();
        //@formatter:on
    }

    @Nullable
    @Override
    public ChanThread getChanThread() {
        return isBound() ? chanLoader.getThread() : null;
    }

    @Override
    public void onListStatusClicked() {
        if (!isBound()) return;
        if (!chanLoader.getThread().isArchived()) {
            chanLoader.requestMoreData();
        } else {
            showArchives(loadable, loadable.no);
        }
    }

    public void showArchives(Loadable op, int postNo) {
        final ArchivesLayout dialogView =
                (ArchivesLayout) LayoutInflater.from(context).inflate(R.layout.layout_archives, null);
        boolean hasContents = dialogView.setLoadable(op);
        dialogView.setPostNo(postNo);
        dialogView.setCallback(this);

        if (loadable.site instanceof ExternalSiteArchive) {
            // skip the archive picker, re-use the same archive we're already in
            openArchive((ExternalSiteArchive) loadable.site, op, postNo);
        } else if (hasContents) {
            AlertDialog dialog = getDefaultAlertBuilder(context).setView(dialogView)
                    .setTitle(R.string.thread_view_external_archive)
                    .create();
            dialog.setCanceledOnTouchOutside(true);
            dialogView.attachToDialog(dialog);
            dialog.show();
        } else {
            showToast(context, "No archives for this board or site.");
        }
    }

    @Override
    public void showThread(Loadable loadable) {
        threadPresenterCallback.showThread(loadable);
    }

    @Override
    public void requestNewPostLoad() {
        if (isBound() && loadable.isThreadMode()) {
            chanLoader.requestMoreData();
            PageRepository.forceUpdateForBoard(chanLoader.getLoadable().board);
        }
    }

    @Override
    public void onUnhidePostClick(Post post) {
        threadPresenterCallback.unhideOrUnremovePost(post);
    }

    private void requestDeletePost(Post post) {
        SavedReply reply = databaseSavedReplyManager.getSavedReply(post.board, post.no);
        if (reply != null) {
            final View view = LayoutInflater.from(context).inflate(R.layout.dialog_post_delete, null);
            CheckBox checkBox = view.findViewById(R.id.image_only);
            getDefaultAlertBuilder(context).setTitle(R.string.delete_confirm)
                    .setView(view)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete,
                            (dialog, which) -> deletePostConfirmed(post, checkBox.isChecked())
                    )
                    .show();
        }
    }

    private void deletePostConfirmed(Post post, boolean onlyImageDelete) {
        threadPresenterCallback.showDeleting();

        SavedReply reply = databaseSavedReplyManager.getSavedReply(post.board, post.no);
        if (reply != null) {
            post.board.site.actions()
                    .delete(new DeleteRequest(post, reply, onlyImageDelete), new SiteActions.DeleteListener() {
                        @Override
                        public void onDeleteComplete(DeleteResponse deleteResponse) {
                            String message;
                            if (deleteResponse.deleted) {
                                message = getString(R.string.delete_success);
                            } else if (!TextUtils.isEmpty(deleteResponse.errorMessage)) {
                                message = deleteResponse.errorMessage;
                            } else {
                                message = getString(R.string.delete_error);
                            }
                            threadPresenterCallback.hideDeleting(message);
                        }

                        @Override
                        public void onDeleteError(Exception e) {
                            threadPresenterCallback.hideDeleting(getString(R.string.delete_error));
                        }
                    });
        }
    }

    private void showPostInfo(Post post) {
        StringBuilder text = new StringBuilder();
        if (post.isOP && !TextUtils.isEmpty(post.subject)) {
            text.append("Subject: ").append(post.subject).append("\n");
        }

        if (!TextUtils.isEmpty(post.name) && !TextUtils.equals(post.name, "Anonymous")) {
            text.append("Name: ").append(post.name).append("\n");
        }

        if (!TextUtils.isEmpty(post.tripcode)) {
            text.append("Tripcode: ").append(post.tripcode).append("\n");
        }

        if (!TextUtils.isEmpty(post.capcode)) {
            text.append("Capcode: ").append(post.capcode).append("\n");
        }

        if (!TextUtils.isEmpty(post.id) && isBound() && chanLoader.getThread() != null) {
            text.append("Id: ").append(post.id).append("\n");
            int count = 0;
            try {
                for (Post p : chanLoader.getThread().getPosts()) {
                    if (p.id.equals(post.id)) count++;
                }
            } catch (Exception ignored) {
            }
            text.append("Post count: ").append(count).append("\n");
        }

        if (post.httpIcons != null && !post.httpIcons.isEmpty()) {
            for (PostHttpIcon icon : post.httpIcons) {
                if (icon.url.toString().contains("troll")) {
                    text.append("Troll Country: ").append(icon.name).append("\n");
                } else if (icon.url.toString().contains("country")) {
                    text.append("Country: ").append(icon.name).append("\n");
                } else if (icon.url.toString().contains("minileaf")) {
                    text.append("4chan Pass Year: ").append(icon.name).append("\n");
                }
            }
        }

        text.append("Posted: ").append(PostHelper.getLocalDate(post));

        for (PostImage image : post.images) {
            text.append("\n\nFilename: ").append(image.filename).append(".").append(image.extension);
            if ("webm".equals(image.extension.toLowerCase()) && cacheHandler.exists(image.imageUrl)) {
                RawFile file = cacheHandler.getOrCreateCacheFile(image.imageUrl);
                try (InputStream stream = new FileInputStream(file.getFullPath())) {
                    byte[] bytes = new byte[1024];
                    stream.read(bytes);
                    for (int i = 0; i < bytes.length - 1; i++) {
                        if (((bytes[i] & 0xFF) << 8 | bytes[i + 1] & 0xFF) == 0x7ba9) {
                            text.append("\nMetadata title: ");
                            byte len = (byte) (bytes[i + 2] ^ 0x80);
                            for (byte j = 0; j < len; j++) {
                                text.append((char) bytes[i + 2 + j + 1]);
                            }
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (image.isInlined) {
                text.append("\nLinked file");
            } else {
                text.append("\nDimensions: ").append(image.imageWidth).append("x").append(image.imageHeight);
            }

            if (image.size > 0) {
                text.append("\nSize: ").append(getReadableFileSize(image.size));
            }

            if (image.spoiler() && !image.isInlined) { //all linked files are spoilered, don't say that
                text.append("\nSpoilered");
            }
        }

        AlertDialog dialog = getDefaultAlertBuilder(context).setMessage(text.toString())
                .setTitle(R.string.post_info)
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.show();
        View messageView = dialog.findViewById(android.R.id.message);
        if (messageView instanceof TextView) {
            ((TextView) messageView).setTextIsSelectable(true);
        }
    }

    private void showPosts() {
        if (chanLoader != null && chanLoader.getThread() != null) {
            threadPresenterCallback.showPosts(chanLoader.getThread(), new PostsFilter(order, searchQuery));
        }
    }

    public void hideOrRemovePosts(boolean hide, boolean wholeChain, Post post, int threadNo) {
        Set<Post> posts = new HashSet<>();

        if (isBound()) {
            if (wholeChain) {
                ChanThread thread = chanLoader.getThread();
                if (thread != null) {
                    posts.addAll(PostUtils.findPostWithReplies(post.no, thread.getPosts()));
                }
            } else {
                posts.add(PostUtils.findPostById(post.no, chanLoader.getThread()));
            }
        }

        threadPresenterCallback.hideOrRemovePosts(hide, wholeChain, posts, threadNo);
    }

    public void showRemovedPostsDialog() {
        if (!isBound() || chanLoader.getThread() == null || loadable.isCatalogMode()) {
            return;
        }

        threadPresenterCallback.viewRemovedPostsForTheThread(chanLoader.getThread().getPosts(), loadable.no);
    }

    public void onRestoreRemovedPostsClicked(List<Integer> selectedPosts) {
        if (!isBound()) return;

        threadPresenterCallback.onRestoreRemovedPostsClicked(loadable, selectedPosts);
    }

    @Override
    public void openArchive(ExternalSiteArchive externalSiteArchive, Loadable op, int postNo) {
        if (isBound()) {
            showThread(externalSiteArchive.getArchiveLoadable(op, postNo));
        }
    }

    public void markAllPostsAsSeen() {
        if (!isBound()) return;
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin != null) {
            watchManager.onBottomPostViewed(pin);
        }
    }

    private List<FloatingMenuItem<Integer>> populateFilterMenuOptions(Post post) {
        List<FloatingMenuItem<Integer>> filterMenu = new ArrayList<>();
        if (post.isOP && !TextUtils.isEmpty(post.subject)) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_SUBJECT, R.string.filter_subject));
        }
        if (!TextUtils.isEmpty(post.comment)) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_COMMENT, R.string.filter_comment));
        }
        if (!TextUtils.isEmpty(post.name) && !TextUtils.equals(post.name, "Anonymous")) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_NAME, R.string.filter_name));
        }
        if (!TextUtils.isEmpty(post.id)) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_ID, R.string.filter_id));
        }
        if (!TextUtils.isEmpty(post.tripcode)) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_TRIPCODE, R.string.filter_tripcode));
        }
        if (loadable.board.countryFlags) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_COUNTRY_CODE, R.string.filter_country_code));
        }
        if (!post.images.isEmpty()) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_FILENAME, R.string.filter_filename));
            if (loadable.site.siteFeature(Site.SiteFeature.IMAGE_FILE_HASH)) {
                filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_IMAGE_HASH, R.string.filter_image_hash));
            }
        }
        return filterMenu;
    }

    private List<FloatingMenuItem<Integer>> populateCopyMenuOptions(Post post) {
        List<FloatingMenuItem<Integer>> copyMenu = new ArrayList<>();
        if (!TextUtils.isEmpty(post.comment)) {
            copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_POST_TEXT, R.string.post_copy_text));
        }
        copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_POST_LINK, R.string.post_copy_link));
        copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_CROSS_BOARD_LINK, R.string.post_copy_cross_board_link));
        copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_POST_URL, R.string.post_copy_post_url));
        if (!post.images.isEmpty()) {
            copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_IMG_URL, R.string.post_copy_image_url));
        }
        copyMenu.add(new FloatingMenuItem<>(POST_OPTION_INFO, R.string.post_info));
        if (post.linkables.size() > 0) {
            copyMenu.add(new FloatingMenuItem<>(POST_OPTION_LINKS, R.string.post_copy_links));
        }
        return copyMenu;
    }

    public interface ThreadPresenterCallback {
        void showPosts(ChanThread thread, PostsFilter filter);

        void postClicked(Post post);

        void showError(ChanThreadLoader.ChanLoaderException error);

        void showLoading();

        void showEmpty();

        void showThread(Loadable threadLoadable);

        void showBoard(Loadable catalogLoadable);

        void showBoardAndSearch(Loadable catalogLoadable, String searchQuery);

        void openLink(String link);

        void openReportView(Post post);

        void showPostsPopup(Post forPost, List<Post> posts);

        void hidePostsPopup();

        List<Post> getDisplayingPosts();

        int[] getCurrentPosition();

        void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail);

        void showAlbum(List<PostImage> images, int index);

        void scrollTo(int displayPosition, boolean smooth);

        void smoothScrollNewPosts(int displayPosition);

        void highlightPostNo(int postNo);

        void highlightPostId(String id);

        void highlightPostTripcode(String tripcode);

        void filterPostSubject(String subject);

        void filterPostName(String name);

        void filterPostComment(CharSequence comment);

        void filterPostID(String ID);

        void filterPostCountryCode(Post post);

        void filterPostFilename(Post post);

        void filterPostTripcode(String tripcode);

        void filterPostImageHash(Post post);

        void showSearch(boolean show);

        void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard);

        void quote(Post post, boolean withText);

        void quote(Post post, CharSequence text);

        void showDeleting();

        void hideDeleting(String message);

        void hideThread(Post post, int threadNo, boolean hide);

        void showNewPostsSnackbar(int more);

        void showHideOrRemoveWholeChainDialog(boolean hide, Post post, int threadNo);

        void hideOrRemovePosts(boolean hide, boolean wholeChain, Set<Post> posts, int threadNo);

        void unhideOrUnremovePost(Post post);

        void viewRemovedPostsForTheThread(List<Post> threadPosts, int threadNo);

        void onRestoreRemovedPostsClicked(Loadable threadLoadable, List<Integer> selectedPosts);
    }
}
