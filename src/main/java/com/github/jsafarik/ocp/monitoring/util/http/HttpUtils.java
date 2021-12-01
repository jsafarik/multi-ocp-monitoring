package com.github.jsafarik.ocp.monitoring.util.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import lombok.extern.jbosslog.JBossLog;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * Class with static methods to perform HTTP requests
 */
@JBossLog
public class HttpUtils {

    private static OkHttpClient client;

    private static OkHttpClient getClient() {
        if (client == null) {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[] {};
                    }
                }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                OkHttpClient.Builder builder = new OkHttpClient.Builder();
                builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier((hostname, session) -> true);
                client = builder
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IllegalStateException("Error while creating Http client", e);
            }
        }
        return client;
    }

    public static Response doRequest(String method, String url, Headers headers, String content) {
        RequestBody requestBody = null;

        switch (method) {
            case "GET":
                break;
            case "POST":
                requestBody = RequestBody.create(MediaType.get("text/plain"), content);
                break;
        }

        Request.Builder request = new Request.Builder().url(url).method(method, requestBody);

        if (headers != null) {
            request.headers(headers);
        }

        Response internalResponse = new Response();

        try (okhttp3.Response response = getClient().newCall(request.build()).execute()) {
            internalResponse.setCode(response.code());
            try (ResponseBody body = response.body()) {
                if (body != null) {
                    internalResponse.setBody(body.string());
                }
            }
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }

        return internalResponse;
    }
}
