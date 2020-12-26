package com.github.adamantcheese.chan.core.site.sites.leftypol;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

public class LeftypolEndpoints extends VichanEndpoints {
    public LeftypolEndpoints(CommonSite commonSite, String rootUrl, String sysUrl) {
        super(commonSite, rootUrl, sysUrl);
    }

    @Override
    public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
        String[] videoExts = new String[]{"mp4", "webm"};

        return root.builder()
                .s(post.board.code)
                .s("thumb")
                .s(arg.get("tim") + (contains(videoExts, arg.get("ext")) ? ".jpg" : ".png"))
                .url();
    }

    private boolean contains(String[] haystack, String needle) {
        for (int i = 0; i < haystack.length; i++) {
            if (haystack[i].equals(needle)) {
                return true;
            }
        }

        return false;
    }
}
