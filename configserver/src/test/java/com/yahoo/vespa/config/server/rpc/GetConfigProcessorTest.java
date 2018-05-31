// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;
import com.yahoo.vespa.config.ConfigKey;

import com.yahoo.vespa.config.protocol.CompressionInfo;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.tenant.MockTenantProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class GetConfigProcessorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testSentinelConfig() throws IOException {
        MockRpc rpc = new MockRpc(13337, false, temporaryFolder.newFolder());
        rpc.response = new MockConfigResponse("foo"); // should be a sentinel config, but it does not matter for this test

        // one tenant, which has host1 assigned
        boolean pretentToHaveLoadedApplications = true;
        TenantName testTenant = TenantName.from("test");
        rpc.onTenantCreate(testTenant, new MockTenantProvider(pretentToHaveLoadedApplications));
        rpc.hostsUpdated(testTenant, Collections.singleton("host1"));

        {   // a config is returned normally
            JRTServerConfigRequest req = createV3SentinelRequest("host1");
            GetConfigProcessor proc = new GetConfigProcessor(rpc, req, false);
            proc.run();
            assertTrue(rpc.tryResolveConfig);
            assertTrue(rpc.tryRespond);
            assertThat(rpc.errorCode, is(0));
        }

        rpc.resetChecks();
        // host1 is replaced by host2 for this tenant
        rpc.hostsUpdated(testTenant, Collections.singleton("host2"));

        {   // this causes us to get an empty config instead of normal config resolution
            JRTServerConfigRequest req = createV3SentinelRequest("host1");
            GetConfigProcessor proc = new GetConfigProcessor(rpc, req, false);
            proc.run();
            assertFalse(rpc.tryResolveConfig); // <-- no normal config resolution happening
            assertTrue(rpc.tryRespond);
            assertThat(rpc.errorCode, is(0));
        }
    }

    private static JRTServerConfigRequest createV3SentinelRequest(String fromHost) {
        final ConfigKey<?> configKey = new ConfigKey<>(SentinelConfig.CONFIG_DEF_NAME, "myid", SentinelConfig.CONFIG_DEF_NAMESPACE);
        return JRTServerConfigRequestV3.createFromRequest(JRTClientConfigRequestV3.
                createWithParams(configKey, DefContent.fromList(Arrays.asList(SentinelConfig.CONFIG_DEF_SCHEMA)),
                                 fromHost, "", 0, 100, Trace.createDummy(), CompressionType.UNCOMPRESSED,
                                 Optional.empty()).getRequest());
    }

    private class MockConfigResponse implements ConfigResponse {

        private final String line;
        public MockConfigResponse(String line) {
            this.line = line;
        }

        @Override
        public Utf8Array getPayload() {
            return new Utf8String("");
        }

        @Override
        public List<String> getLegacyPayload() {
            return Arrays.asList(line);
        }

        @Override
        public long getGeneration() {
            return 1;
        }

        @Override
        public String getConfigMd5() {
            return "mymd5";
        }

        @Override
        public void serialize(OutputStream os, CompressionType uncompressed) throws IOException {
            os.write(Utf8.toBytes(line));
        }

        @Override
        public CompressionInfo getCompressionInfo() {
            return CompressionInfo.uncompressed();
        }
    }

}
