// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.document.DataType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporarySDField;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndexingScriptTestCase {

    private static IlscriptsConfig ilscriptsConfig(Schema schema, boolean isStreaming) {
        IndexingScript script = new IndexingScript(schema, isStreaming);
        IlscriptsConfig.Builder cfgBuilder = new IlscriptsConfig.Builder();
        script.getConfig(cfgBuilder);
        return cfgBuilder.build();
    }

    private void verifyIndexingScript(boolean isStreaming) {
        Schema schema = VsmFieldsTestCase.createSchema();
        SDField field = new TemporarySDField(schema.getDocument(), "f", DataType.STRING);
        field.parseIndexingScript("{ tokenize | index }");
        field.setMatching(new Matching(MatchType.TEXT));
        schema.getDocument().addField(field);
        IlscriptsConfig cfg = ilscriptsConfig(schema, isStreaming);
        assertEquals(1, cfg.ilscript().size());
        assertEquals(2, cfg.ilscript(0).content().size());
        String indexing = isStreaming ? "index" : "tokenize | index";
        assertEquals("clear_state | guard { " + indexing + "; }", cfg.ilscript(0).content(0));
    }
    @Test
    void testThatTokenizeIsIgnoredFromStreaming() {
        verifyIndexingScript(false);
        verifyIndexingScript(true);
    }
}
