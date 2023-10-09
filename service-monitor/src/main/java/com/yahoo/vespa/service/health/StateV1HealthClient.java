// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.logging.Level;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.function.Function;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * A thread-unsafe /state/v1/health endpoint client.
 *
 * @author hakonhall
 */
public class StateV1HealthClient implements AutoCloseable {

    private static final long MAX_CONTENT_LENGTH = 1L << 20; // 1 MB
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(StateV1HealthClient.class.getName());
    private final ApacheHttpClient httpClient;
    private final Function<HttpEntity, String> getContentFunction;

    StateV1HealthClient(URL url, Duration requestTimeout, Duration connectionKeepAlive) {
        this(new ApacheHttpClient(url, requestTimeout, connectionKeepAlive),
                entity -> uncheck(() -> EntityUtils.toString(entity)));
    }

    StateV1HealthClient(ApacheHttpClient apacheHttpClient, Function<HttpEntity, String> getContentFunction) {
        httpClient = apacheHttpClient;
        this.getContentFunction = getContentFunction;
    }

    HealthInfo get() throws Exception {
        return httpClient.get(this::handle);
    }

    private HealthInfo handle(CloseableHttpResponse httpResponse) throws IOException {
        int httpStatusCode = httpResponse.getStatusLine().getStatusCode();
        if (httpStatusCode < 200 || httpStatusCode >= 300) {
            return HealthInfo.fromBadHttpStatusCode(httpStatusCode);
        }

        HttpEntity bodyEntity = httpResponse.getEntity();
        long contentLength = bodyEntity.getContentLength();
        if (contentLength > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Content too long: " + contentLength + " bytes");
        }
        String body = getContentFunction.apply(bodyEntity);
        HealthResponse healthResponse = MAPPER.readValue(body, HealthResponse.class);

        if (healthResponse.status == null || healthResponse.status.code == null) {
            return HealthInfo.fromHealthStatusCode(HealthResponse.Status.DEFAULT_STATUS);
        } else {
            return HealthInfo.fromHealthStatusCode(healthResponse.status.code);
        }
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to close CloseableHttpClient", e);
        }
    }

}
