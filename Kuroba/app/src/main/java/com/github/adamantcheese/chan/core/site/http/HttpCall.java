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
package com.github.adamantcheese.chan.core.site.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Http calls are an abstraction over a normal OkHttp call.
 * <p>These HttpCalls are used for emulating &lt;form&gt; elements used for posting, reporting, deleting, etc.
 * <p>Implement {@link #setup(Request.Builder, ProgressRequestBody.ProgressRequestListener)} and {@link #process(Response, String)}.
 * {@code setup()} is called on the main thread, set up up the request builder here. {@code execute()} is
 * called on a worker thread after the response was executed, do something with the response here.
 */
@SuppressWarnings("unchecked")
public abstract class HttpCall
        implements Callback {
    private final Site site;

    @SuppressWarnings("rawtypes")
    private HttpCallback callback;
    private Exception exception;

    public abstract void setup(
            Request.Builder requestBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    );

    public abstract void process(Response response, String result);

    public HttpCall(Site site) {
        this.site = site;
    }

    public Site getSite() {
        return site;
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) {
        try (ResponseBody body = response.body()) {
            if (body != null) {
                process(response, body.string());
            } else {
                exception = new NetUtilsClasses.HttpCodeException(response.code());
            }
        } catch (Exception e) {
            exception = new IOException("Error processing response", e);
        }

        if (exception != null) {
            Logger.e(this, "onResponse", exception);
            BackgroundUtils.runOnMainThread(() -> callback.onHttpFail(HttpCall.this, exception));
        } else {
            BackgroundUtils.runOnMainThread(() -> callback.onHttpSuccess(HttpCall.this));
        }
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        Logger.e(this, "onFailure", e);
        BackgroundUtils.runOnMainThread(() -> callback.onHttpFail(HttpCall.this, e));
    }

    public void setCallback(HttpCallback<? extends HttpCall> callback) {
        this.callback = callback;
    }

    public interface HttpCallback<T extends HttpCall> {
        void onHttpSuccess(T httpCall);

        void onHttpFail(T httpCall, Exception e);
    }
}
