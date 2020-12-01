// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.SimpletypesConfig;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.SlimeConfigResponse;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class HttpConfigResponseTest {
    @Test
    public void require_that_response_is_created_from_config() throws IOException {
        final long generation = 1L;
        ConfigPayload payload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        ConfigResponse configResponse = SlimeConfigResponse.fromConfigPayload(payload, generation, false, "mymd5");
        HttpConfigResponse response = HttpConfigResponse.createFromConfig(configResponse);
        assertThat(SessionHandlerTest.getRenderedString(response), is("{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}"));
    }
}
