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
package com.github.adamantcheese.chan.ui.captcha.v2;

import android.content.Context;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.Chan.inject;

public class CaptchaNoJsPresenterV2 {
    private static final String acceptHeader =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3";
    private static final String acceptEncodingHeader = "deflate, br";
    private static final String acceptLanguageHeader = "en-US";
    private static final String recaptchaUrlBase = "https://www.google.com/recaptcha/api/fallback?k=";
    private static final String encoding = "UTF-8";
    private static final String mediaType = "application/x-www-form-urlencoded";
    private static final String recaptchaChallengeString = "reCAPTCHA challenge";
    private static final String verificationTokenString = "fbc-verification-token";
    private static final long CAPTCHA_REQUEST_THROTTLE_MS = 3000L;

    // this cookie is taken from dashchan
    private static final String defaultGoogleCookies =
            "NID=87=gkOAkg09AKnvJosKq82kgnDnHj8Om2pLskKhdna02msog8HkdHDlasDf";

    @Inject
    NetModule.OkHttpClientWithUtils okHttpClient;

    private final CaptchaNoJsHtmlParser parser;

    @Nullable
    private AuthenticationCallbacks callbacks;
    @Nullable
    private CaptchaInfo prevCaptchaInfo = null;

    private final AtomicBoolean verificationInProgress = new AtomicBoolean(false);
    private final AtomicBoolean captchaRequestInProgress = new AtomicBoolean(false);
    private String siteKey;
    private String baseUrl;
    private long lastTimeCaptchaRequest = 0L;

    public CaptchaNoJsPresenterV2(@Nullable AuthenticationCallbacks callbacks, Context context) {
        inject(this);
        this.callbacks = callbacks;
        this.parser = new CaptchaNoJsHtmlParser(context, okHttpClient);
    }

    public void init(String siteKey, String baseUrl) {
        this.siteKey = siteKey;
        this.baseUrl = baseUrl;
    }

    /**
     * Send challenge solution back to the recaptcha
     */
    public VerifyError verify(List<Integer> selectedIds)
            throws CaptchaNoJsV2Error {
        if (!verificationInProgress.compareAndSet(false, true)) {
            Logger.d(this, "Verify captcha request is already in progress");
            return VerifyError.ALREADY_IN_PROGRESS;
        }

        try {
            if (selectedIds.isEmpty()) {
                verificationInProgress.set(false);
                return VerifyError.NO_IMAGES_SELECTED;
            }

            if (prevCaptchaInfo == null) {
                throw new CaptchaNoJsV2Error("prevCaptchaInfo is null");
            }

            if (prevCaptchaInfo.cParameter == null) {
                throw new CaptchaNoJsV2Error("C parameter is null");
            }

            BackgroundUtils.runOnBackgroundThread(() -> {
                try {
                    String recaptchaUrl = recaptchaUrlBase + siteKey;
                    RequestBody body = createResponseBody(prevCaptchaInfo, selectedIds);

                    Logger.d(CaptchaNoJsPresenterV2.this, "Verify called");

                    Request request = new Request.Builder().url(recaptchaUrl)
                            .post(body)
                            .addHeader("Referer", recaptchaUrl)
                            .addHeader("Accept", acceptHeader)
                            .addHeader("Accept-Encoding", acceptEncodingHeader)
                            .addHeader("Accept-Language", acceptLanguageHeader)
                            .addHeader("Cookie", defaultGoogleCookies)
                            .build();

                    try (Response response = okHttpClient.getProxiedClient().newCall(request).execute()) {
                        prevCaptchaInfo = handleGetRecaptchaResponse(response);
                    } finally {
                        verificationInProgress.set(false);
                    }
                } catch (Throwable error) {
                    if (callbacks != null) {
                        try {
                            prevCaptchaInfo = null;
                            callbacks.onCaptchaInfoParseError(error);
                        } finally {
                            verificationInProgress.set(false);
                        }
                    }
                }
            });

            return VerifyError.OK;
        } catch (Throwable error) {
            verificationInProgress.set(false);
            throw error;
        }
    }

    /**
     * Requests captcha data, parses it and then passes it to the render function
     */
    public RequestCaptchaInfoError requestCaptchaInfo() {
        if (!captchaRequestInProgress.compareAndSet(false, true)) {
            Logger.d(this, "Request captcha request is already in progress");
            return RequestCaptchaInfoError.ALREADY_IN_PROGRESS;
        }

        try {
            // recaptcha may become very angry at you if your are fetching it too fast
            if (System.currentTimeMillis() - lastTimeCaptchaRequest < CAPTCHA_REQUEST_THROTTLE_MS) {
                captchaRequestInProgress.set(false);
                Logger.d(this, "Requesting captcha info too fast");
                return RequestCaptchaInfoError.HOLD_YOUR_HORSES;
            }

            lastTimeCaptchaRequest = System.currentTimeMillis();

            BackgroundUtils.runOnBackgroundThread(() -> {
                try {
                    try {
                        prevCaptchaInfo = getCaptchaInfo();
                    } catch (Throwable error) {
                        if (callbacks != null) {
                            callbacks.onCaptchaInfoParseError(error);
                        }

                        throw error;
                    }
                } catch (Throwable error) {
                    Logger.e(CaptchaNoJsPresenterV2.this, "Error while executing captcha requests", error);

                    prevCaptchaInfo = null;
                } finally {
                    captchaRequestInProgress.set(false);
                }
            });

            return RequestCaptchaInfoError.OK;
        } catch (Throwable error) {
            captchaRequestInProgress.set(false);

            if (callbacks != null) {
                callbacks.onCaptchaInfoParseError(error);
            }

            // return ok here too because we already handled this exception in the callback
            return RequestCaptchaInfoError.OK;
        }
    }

    @Nullable
    private CaptchaInfo getCaptchaInfo()
            throws IOException {
        BackgroundUtils.ensureBackgroundThread();

        String recaptchaUrl = recaptchaUrlBase + siteKey;

        Request request = new Request.Builder().url(recaptchaUrl)
                .addHeader("Referer", baseUrl)
                .addHeader("Accept", acceptHeader)
                .addHeader("Accept-Encoding", acceptEncodingHeader)
                .addHeader("Accept-Language", acceptLanguageHeader)
                .addHeader("Cookie", defaultGoogleCookies)
                .build();

        try (Response response = okHttpClient.getProxiedClient().newCall(request).execute()) {
            return handleGetRecaptchaResponse(response);
        }
    }

    private RequestBody createResponseBody(CaptchaInfo prevCaptchaInfo, List<Integer> selectedIds)
            throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();

        sb.append(URLEncoder.encode("c", encoding));
        sb.append("=");
        sb.append(URLEncoder.encode(prevCaptchaInfo.cParameter, encoding));
        sb.append("&");

        for (Integer selectedImageId : selectedIds) {
            sb.append(URLEncoder.encode("response", encoding));
            sb.append("=");
            sb.append(URLEncoder.encode(String.valueOf(selectedImageId), encoding));
            sb.append("&");
        }

        String resultBody;

        if (selectedIds.size() > 0) {
            resultBody = sb.deleteCharAt(sb.length() - 1).toString();
        } else {
            resultBody = sb.toString();
        }

        return MultipartBody.create(resultBody, MediaType.parse(mediaType));
    }

    @Nullable
    private CaptchaInfo handleGetRecaptchaResponse(Response response) {
        try {
            if (!response.isSuccessful()) {
                if (callbacks != null) {
                    callbacks.onCaptchaInfoParseError(new IOException(
                            "Bad status code for captcha request = " + response.code()));
                }

                return null;
            }

            ResponseBody body = response.body();
            if (body == null) {
                if (callbacks != null) {
                    callbacks.onCaptchaInfoParseError(new IOException("Captcha response body is empty (null)"));
                }

                return null;
            }

            String bodyString = body.string();
            if (!bodyString.contains(recaptchaChallengeString)) {
                throw new IllegalStateException("Response body does not contain \"reCAPTCHA challenge\" string");
            }

            if (bodyString.contains(verificationTokenString)) {
                // got the token
                String verificationToken = parser.parseVerificationToken(bodyString);
                Logger.d(this, "Got the verification token");

                if (callbacks != null) {
                    callbacks.onVerificationDone(verificationToken);
                }

                return null;
            } else {
                // got the challenge
                CaptchaInfo captchaInfo = parser.parseHtml(bodyString, siteKey);
                Logger.d(this, "Got new challenge");

                if (callbacks != null) {
                    callbacks.onCaptchaInfoParsed(captchaInfo);
                } else {
                    // Return null when callbacks are null to reset prevCaptchaInfo so that we won't
                    // get stuck without captchaInfo and disabled buttons forever
                    return null;
                }

                return captchaInfo;
            }
        } catch (Throwable e) {
            Logger.e(this, "Error while trying to parse captcha html data", e);

            if (callbacks != null) {
                callbacks.onCaptchaInfoParseError(e);
            }

            return null;
        }
    }

    public void onDestroy() {
        this.callbacks = null;
        this.prevCaptchaInfo = null;
        this.verificationInProgress.set(false);
        this.captchaRequestInProgress.set(false);
    }

    public enum VerifyError {
        OK,
        NO_IMAGES_SELECTED,
        ALREADY_IN_PROGRESS
    }

    public enum RequestCaptchaInfoError {
        OK,
        ALREADY_IN_PROGRESS,
        HOLD_YOUR_HORSES
    }

    public interface AuthenticationCallbacks {
        void onCaptchaInfoParsed(CaptchaInfo captchaInfo);

        void onCaptchaInfoParseError(Throwable error);

        void onVerificationDone(String verificationToken);
    }

    public static class CaptchaNoJsV2Error
            extends Exception {
        public CaptchaNoJsV2Error(String message) {
            super(message);
        }
    }
}
