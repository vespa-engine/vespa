// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.Schema;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndexingScriptTestCase {

    private static final String TEST = "test";

    private static IlscriptsConfig ilscriptsConfig(Schema schema, boolean isStreaming) {
        IndexingScript script = new IndexingScript(schema, isStreaming);
        IlscriptsConfig.Builder cfgBuilder = new IlscriptsConfig.Builder();
        script.getConfig(cfgBuilder);
        return cfgBuilder.build();
    }

    private void verifyIndexingScript(boolean isStreaming) {
        VespaModel model = IndexInfoTestCase.createModel(TEST,
                """
                        field f type string { indexing: index }
                        field fa type array<string> { indexing: index }
                        """);
        Schema schema = model.getSearchClusters().get(0).schemas().get(TEST).fullSchema();
        IlscriptsConfig cfg = ilscriptsConfig(schema, isStreaming);
        assertEquals(1, cfg.ilscript().size());
        assertEquals(2, cfg.ilscript(0).content().size());
        String exp_f = isStreaming ? "" : "tokenize normalize stem:\"BEST\" | ";
        String exp_fa = isStreaming ? "" : "for_each { tokenize normalize stem:\"BEST\" } | ";
        assertEquals("clear_state | guard { input f | " + exp_f + "index f; }", cfg.ilscript(0).content(0));
        assertEquals("clear_state | guard { input fa | " + exp_fa + "index fa; }", cfg.ilscript(0).content(1));
    }

    @Test
    void testThatTokenizeIsIgnoredFromStreaming() {
        verifyIndexingScript(false);
        verifyIndexingScript(true);
    }

    private void verifyZcurveScript(boolean isStreaming) {
        VespaModel model = IndexInfoTestCase.createModel(TEST,
                """
                        field f type position { indexing: attribute }
                        """);
        Schema schema = model.getSearchClusters().get(0).schemas().get(TEST).fullSchema();
        IlscriptsConfig cfg = ilscriptsConfig(schema, isStreaming);
        assertEquals(1, cfg.ilscript().size());
        assertEquals(1, cfg.ilscript(0).content().size());
        String exp_f = isStreaming ? "attribute f" : "zcurve | attribute f_zcurve";
        assertEquals("clear_state | guard { input f | " + exp_f + "; }", cfg.ilscript(0).content(0));
    }

    @Test
    void testThatZcurveIsRewrittenFromStreaming() {
        verifyZcurveScript(false);
        verifyZcurveScript(true);
    }
}
