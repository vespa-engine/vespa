// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import ai.vespa.util.http.hc4.retry.DelayedConnectionLevelRetryHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.integration.ControllerIdentityProvider;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireErrorResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * A client for /flags/v1 rest api on configserver and controller.
 *
 * @author bjorncs
 */
class FlagsClient {

    private static final String FLAGS_V1_PATH = "/flags/v1";

    private static final ObjectMapper mapper = new ObjectMapper();

    private final CloseableHttpClient client;

    FlagsClient(ControllerIdentityProvider identityProvider, Set<FlagsTarget> targets) {
        this.client = createClient(identityProvider, targets);
    }

    List<FlagData> listFlagData(FlagsTarget target) throws FlagsException, UncheckedIOException {
        HttpGet request = new HttpGet(createUri(target, "/data", List.of(new BasicNameValuePair("recursive", "true"))));
        return executeRequest(request, response -> {
            verifySuccess(response, null);
            return FlagData.deserializeList(EntityUtils.toByteArray(response.getEntity()));
        });
    }

    List<FlagId> listDefinedFlags(FlagsTarget target) {
        HttpGet request = new HttpGet(createUri(target, "/defined", List.of()));
        return executeRequest(request, response -> {
            verifySuccess(response, null);
            JsonNode json = mapper.readTree(response.getEntity().getContent());
            List<FlagId> flagIds = new ArrayList<>();
            json.fieldNames().forEachRemaining(fieldName -> flagIds.add(new FlagId(fieldName)));
            return flagIds;
        });
    }

    void putFlagData(FlagsTarget target, FlagData flagData) throws FlagsException, UncheckedIOException {
        HttpPut request = new HttpPut(createUri(target, "/data/" + flagData.id().toString(), List.of()));
        request.setEntity(jsonContent(flagData.serializeToJson()));
        executeRequest(request, response -> {
            verifySuccess(response, flagData.id());
            return null;
        });
    }

    void deleteFlagData(FlagsTarget target, FlagId flagId) throws FlagsException, UncheckedIOException {
        HttpDelete request = new HttpDelete(createUri(target, "/data/" + flagId.toString(), List.of(new BasicNameValuePair("force", "true"))));
        executeRequest(request, response -> {
            verifySuccess(response, flagId);
            return null;
        });
    }

    private static CloseableHttpClient createClient(ControllerIdentityProvider identityProvider, Set<FlagsTarget> targets) {
        DelayedConnectionLevelRetryHandler retryHandler = DelayedConnectionLevelRetryHandler.Builder
                .withExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(20), 5)
                .build();
        SSLConnectionSocketFactory connectionSocketFactory = new SSLConnectionSocketFactory(
                identityProvider.getConfigServerSslSocketFactory(), new FlagTargetsHostnameVerifier(targets));

        return HttpClientBuilder.create()
                .setUserAgent("controller-flags-v1-client")
                .setSSLSocketFactory(connectionSocketFactory)
                .setDefaultRequestConfig(RequestConfig.custom()
                                                 .setConnectTimeout((int) Duration.ofSeconds(10).toMillis())
                                                 .setConnectionRequestTimeout((int) Duration.ofSeconds(10).toMillis())
                                                 .setSocketTimeout((int) Duration.ofSeconds(20).toMillis())
                                                 .build())
                .setMaxConnPerRoute(2)
                .setMaxConnTotal(100)
                .setRetryHandler(retryHandler)
                .build();
    }

    private <T> T executeRequest(HttpUriRequest request, ResponseHandler<T> handler) {
        try {
            return client.execute(request, handler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static URI createUri(FlagsTarget target, String subPath, List<NameValuePair> queryParams) {
        try {
            return new URIBuilder(target.endpoint())
                    .setPath(FLAGS_V1_PATH + subPath)
                    .setParameters(queryParams)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e); // should never happen
        }
    }

    private static void verifySuccess(HttpResponse response, FlagId flagId) throws IOException {
        if (!success(response)) {
            throw createFlagsException(response, flagId);
        }
    }

    private static FlagsException createFlagsException(HttpResponse response, FlagId flagId) throws IOException {
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        int statusCode = response.getStatusLine().getStatusCode();
        if (ContentType.get(entity).getMimeType().equals(ContentType.APPLICATION_JSON.getMimeType())) {
            WireErrorResponse error = mapper.readValue(content, WireErrorResponse.class);
            return new FlagsException(statusCode, flagId, error.errorCode, error.message);
        } else {
            return new FlagsException(statusCode, flagId, null, content);
        }
    }

    private static boolean success(HttpResponse response) {
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
    }

    private static StringEntity jsonContent(String json) {
        return new StringEntity(json, ContentType.APPLICATION_JSON);
    }

    private static class FlagTargetsHostnameVerifier implements HostnameVerifier {

        private final AthenzIdentityVerifier athenzVerifier;

        FlagTargetsHostnameVerifier(Set<FlagsTarget> targets) {
            this.athenzVerifier = createAthenzIdentityVerifier(targets);
        }

        private static AthenzIdentityVerifier createAthenzIdentityVerifier(Set<FlagsTarget> targets) {
            Set<AthenzIdentity> identities = targets.stream()
                    .flatMap(target -> target.athenzHttpsIdentity().stream())
                    .collect(toSet());
            return new AthenzIdentityVerifier(identities);
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return "localhost".equals(hostname) /* for controllers */ || athenzVerifier.verify(hostname, session);
        }
    }

    static class FlagsException extends RuntimeException {

        private FlagsException(int statusCode, FlagId flagId, String errorCode, String errorMessage) {
            super(createErrorMessage(statusCode, flagId, errorCode, errorMessage));
        }

        private static String createErrorMessage(int statusCode, FlagId flagId, String errorCode, String errorMessage) {
            StringBuilder builder = new StringBuilder().append("Received ").append(statusCode);
            if (errorCode != null) {
                builder.append('/').append(errorCode);
            }
            if (flagId != null) {
                builder.append(" for flag '").append(flagId).append("'");
            }
            return builder.append(": ").append(errorMessage).toString();
        }
    }
}
