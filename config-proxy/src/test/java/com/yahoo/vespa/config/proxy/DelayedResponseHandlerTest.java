// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class DelayedResponseHandlerTest {

    private final MockConfigSource source = new MockConfigSource();

    @TempDir
    public File temporaryFolder;

    @BeforeEach
    public void setup() {
        source.clear();
        source.put(ProxyServerTest.fooConfig.getKey(), ProxyServerTest.createConfigWithNextConfigGeneration(ProxyServerTest.fooConfig, 0));
    }

    @Test
    void basic() {
        ConfigTester tester = new ConfigTester();
        DelayedResponses delayedResponses = new DelayedResponses();
        MemoryCache memoryCache = new MemoryCache();
        memoryCache.update(ConfigTester.fooConfig);
        DelayedResponseHandler delayedResponseHandler = new DelayedResponseHandler(delayedResponses, memoryCache, new ResponseHandler());
        delayedResponses.add(new DelayedResponse(tester.createRequest(ProxyServerTest.fooConfig, 0)));
        delayedResponses.add(new DelayedResponse(tester.createRequest(ProxyServerTest.fooConfig, 1200000))); // should not be returned yet
        delayedResponses.add(new DelayedResponse(tester.createRequest(ProxyServerTest.errorConfig, 0)));  // will not give a config when resolving
        delayedResponseHandler.checkDelayedResponses();

        assertEquals(1, delayedResponseHandler.sentResponses());
    }

}
