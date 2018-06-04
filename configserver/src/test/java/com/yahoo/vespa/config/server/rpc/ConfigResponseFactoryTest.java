// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.config.SimpletypesConfig;
import com.yahoo.config.codegen.DefParser;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class ConfigResponseFactoryTest {

    private InnerCNode def;

    @Before
    public void setup() {
        DefParser dParser = new DefParser(SimpletypesConfig.getDefName(),
                                          new StringReader(StringUtilities.implode(SimpletypesConfig.CONFIG_DEF_SCHEMA, "\n")));
        def = dParser.getTree();
    }

    @Test
    public void testUncompressedFacory() {
        UncompressedConfigResponseFactory responseFactory = new UncompressedConfigResponseFactory();
        ConfigResponse response = responseFactory.createResponse(ConfigPayload.empty(), def, 3, false);
        assertEquals(CompressionType.UNCOMPRESSED, response.getCompressionInfo().getCompressionType());
        assertEquals(3L,response.getGeneration());
        assertEquals(2, response.getPayload().getByteLength());
    }

    @Test
    public void testLZ4CompressedFacory() {
        LZ4ConfigResponseFactory responseFactory = new LZ4ConfigResponseFactory();
        ConfigResponse response = responseFactory.createResponse(ConfigPayload.empty(), def, 3, false);
        assertEquals(CompressionType.LZ4, response.getCompressionInfo().getCompressionType());
        assertEquals(3L, response.getGeneration());
        assertEquals(3, response.getPayload().getByteLength());
    }

}
