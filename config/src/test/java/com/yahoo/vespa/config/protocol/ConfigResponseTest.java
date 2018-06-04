// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.text.StringUtilities;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.LZ4PayloadCompressor;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class ConfigResponseTest {

    @Test
    public void require_that_slime_response_is_initialized() throws IOException {
        ConfigPayload configPayload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        DefParser dParser = new DefParser(SimpletypesConfig.getDefName(), new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n")));
        InnerCNode targetDef = dParser.getTree();
        ConfigResponse response = SlimeConfigResponse.fromConfigPayload(configPayload, targetDef, 3, false, "mymd5");
        List<String> payload = response.getLegacyPayload();
        assertNotNull(payload);
        assertThat(payload.size(), is(6));
        assertThat(payload.get(0), is("boolval false"));
        assertThat(response.getGeneration(), is(3L));
        assertThat(response.getConfigMd5(), is("mymd5"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.serialize(baos, CompressionType.UNCOMPRESSED);
        assertThat(baos.toString(), is("{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}"));
    }

    @Test
    public void require_that_slime_response_decompresses_on_serialize() throws IOException {
        ConfigPayload configPayload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        DefParser dParser = new DefParser(SimpletypesConfig.getDefName(), new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n")));
        InnerCNode targetDef = dParser.getTree();
        Utf8Array data = configPayload.toUtf8Array(true);
        Utf8Array bytes = new Utf8Array(new LZ4PayloadCompressor().compress(data.getBytes()));
        ConfigResponse response = new SlimeConfigResponse(bytes, targetDef, 3, false, "mymd5", CompressionInfo.create(CompressionType.LZ4, data.getByteLength()));
        List<String> payload = response.getLegacyPayload();
        assertNotNull(payload);
        assertThat(payload.size(), is(6));
        assertThat(payload.get(0), is("boolval false"));
        assertThat(response.getGeneration(), is(3L));
        assertThat(response.getConfigMd5(), is("mymd5"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.serialize(baos, CompressionType.UNCOMPRESSED);
        assertThat(baos.toString(), is("{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}"));
    }

}
