// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.config.ConfigKey;

import org.junit.Test;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class HttpConfigRequestTest {
    @Test
    public void require_that_request_can_be_created() {
        final ConfigKey<?> configKey = new ConfigKey<>("foo", "myid", "bar");

        HttpConfigRequest request = HttpConfigRequest.createFromRequestV1(HttpRequest.createTestRequest("http://example.yahoo.com:8080/config/v1/" +
                configKey.getNamespace() + "." + configKey.getName() + "/" + configKey.getConfigId(), GET));
        assertEquals(configKey, request.getConfigKey());
        assertTrue(request.getDefContent().isEmpty());
    }

    @Test
    public void require_namespace_can_have_dots() {
        final ConfigKey<?> configKey = new ConfigKey<>("foo", "myid", "bar.baz");
        HttpConfigRequest request = HttpConfigRequest.createFromRequestV1(HttpRequest.createTestRequest("http://example.yahoo.com:8080/config/v1/" +
                configKey.getNamespace() + "." + configKey.getName() + "/" + configKey.getConfigId(), GET));
        assertEquals(request.getConfigKey().getNamespace(), "bar.baz");
    }

    @Test
    public void require_that_request_can_be_created_with_advanced_uri() {
        HttpConfigRequest.createFromRequestV1(HttpRequest.createTestRequest(
                "http://example.yahoo.com:19071/config/v1/vespa.config.cloud.sentinel/host-01.example.yahoo.com", GET));
    }
}
