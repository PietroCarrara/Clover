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
package com.github.adamantcheese.chan.core.site.common;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;

import org.jsoup.Jsoup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.Response;

public abstract class CommonReplyHttpCall
        extends HttpCall {
    private static final Pattern THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->");

    public final ReplyResponse replyResponse;

    public CommonReplyHttpCall(Loadable loadable) {
        super(loadable.site);
        replyResponse = new ReplyResponse(loadable);
    }

    @Override
    public void setup(
            Request.Builder requestBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        MultipartBody.Builder formBuilder = new MultipartBody.Builder();
        formBuilder.setType(MultipartBody.FORM);

        addParameters(formBuilder, progressListener);

        HttpUrl replyUrl = getSite().endpoints().reply(replyResponse.originatingLoadable);
        requestBuilder.url(replyUrl).addHeader("Referer", replyUrl.toString()).post(formBuilder.build());
    }

    @Override
    public void process(Response response, String result) {
        /*
        FOR A REGULAR REPLY
        <!-- thread:3255892,no:3259817 -->
                    ^^^^^^^	   ^^^^^^^
                    thread#    post#

		FOR A NEW THREAD
        <!-- thread:0,no:204393073 -->
                    ^    ^^^^^^^^^
              catalog    thread#

        First parameter is always parsed as threadNo, second always as postNo
        */
        if (result.contains("errmsg")) {
            replyResponse.errorMessage = Jsoup.parse(result).select("#errmsg").first().html();
        } else {
            Matcher threadNoMatcher = THREAD_NO_PATTERN.matcher(result);
            if (threadNoMatcher.find()) {
                try {
                    replyResponse.threadNo = Integer.parseInt(threadNoMatcher.group(1));
                    replyResponse.postNo = Integer.parseInt(threadNoMatcher.group(2));
                } catch (NumberFormatException ignored) {
                }

                if (replyResponse.threadNo >= 0
                        && replyResponse.postNo > 0) { //threadNo can be 0 iff this is a new thread
                    replyResponse.posted = true;
                }
            }
        }
    }

    public abstract void addParameters(
            MultipartBody.Builder builder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    );
}
