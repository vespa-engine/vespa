// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationOverrides.ValidationException;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeReindexAction;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 * @author bjorncs
 */
public class IndexingModeChangeValidatorTest {

    @Test
    void testChangingIndexModeFromIndexedToStreamingWhenDisallowedButInDev() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices("index"), Environment.dev, "<validation-overrides />").getFirst();
        List<ConfigChangeAction> actions = tester.deploy(oldModel, getServices("streaming"), Environment.dev, "<calidation-overrides />").getSecond();
        assertReindexingChange("Document type 'music' in cluster 'default-content' changed indexing mode from 'indexed' to 'streaming'", actions);
    }

    @Test
    void testChangingIndexModeFromIndexedToStreamingWhenDisallowed() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices("index"), Environment.prod, "<validation-overrides />").getFirst();
        try {
            tester.deploy(oldModel, getServices("streaming"), Environment.prod, "<calidation-overrides />").getSecond();
            fail("Should throw on disallowed config change action");
        }
        catch (ValidationException e) {
            assertEquals("indexing-mode-change: " +
                    "Document type 'music' in cluster 'default-content' changed indexing mode from 'indexed' to 'streaming'. " +
                    "To allow this add <allow until='yyyy-mm-dd'>indexing-mode-change</allow> to validation-overrides.xml, see https://docs.vespa.ai/en/reference/validation-overrides.html",
                    e.getMessage());
        }
    }

    @Test
    void testChangingIndexModeFromIndexedToStreaming() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices("index"), Environment.prod, validationOverrides).getFirst();
        List<ConfigChangeAction> changeActions =
                tester.deploy(oldModel, getServices("streaming"), Environment.prod, validationOverrides).getSecond();

        assertReindexingChange( // allowed=true due to validation override
                "Document type 'music' in cluster 'default-content' changed indexing mode from 'indexed' to 'streaming'",
                changeActions);
    }

    @Test
    void testChangingIndexModeFromStoreOnlyToIndexed() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices("index"), Environment.prod, validationOverrides).getFirst();
        List<ConfigChangeAction> changeActions =
                tester.deploy(oldModel, getServices("store-only"), Environment.prod, validationOverrides).getSecond();

        assertReindexingChange( // allowed=true due to validation override
                "Document type 'music' in cluster 'default-content' changed indexing mode from 'indexed' to 'store-only'",
                changeActions);
    }

    private void assertReindexingChange(String message, List<ConfigChangeAction> changeActions) {
        List<ConfigChangeAction> reindexingActions = changeActions.stream()
                                                              .filter(a -> a instanceof ConfigChangeReindexAction)
                                                              .toList();
        assertEquals(1, reindexingActions.size());
        assertTrue(reindexingActions.get(0) instanceof ConfigChangeReindexAction);
        assertEquals("indexing-mode-change", ((ConfigChangeReindexAction)reindexingActions.get(0)).name());
        assertEquals(message, reindexingActions.get(0).getMessage());
    }

    private static String getServices(String indexingMode) {
        return "<services version='1.0'>" +
               "  <container id='default' version='1.0'>" +
               "    <nodes count='1'/>" +
               "  </container>" +
               "  <content id='default-content' version='1.0'>" +
               "    <redundancy>1</redundancy>" +
               "    <documents>" +
               "      <document type='music' mode='" + indexingMode + "'/>" +
               "    </documents>" +
               "    <nodes count='1'/>" +
               "   </content>" +
               "</services>";
    }

    private static final String validationOverrides =
            """
            <validation-overrides>
                <allow until='2000-01-14' comment='test override'>indexing-mode-change</allow>
            </validation-overrides>
            """;

}
