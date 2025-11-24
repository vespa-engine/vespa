// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationOverrides.ValidationException;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeReindexAction;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 * @author bjorncs
 */
public class ChangeValidatorTest {

    private final Zone zone = Zone.defaultZone();
    private final Zone devZone = new Zone(SystemName.main, Environment.dev, RegionName.defaultName());

    @Test
    void testChangingIndexModeFromIndexedToStreamingAllowedInDev() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices("index"), devZone, "<validation-overrides />").getFirst();
        List<ConfigChangeAction> actions = tester.deploy(oldModel, getServices("streaming"), devZone, "<validation-overrides />").getSecond();
        assertReindexingChange("indexing-mode-change",
                               "Document type 'music' in cluster 'default-content' changed indexing mode from 'indexed' to 'streaming'",
                               actions,
                               true); // indexing mode change causes thread settings change
    }

    @Test
    void testChangingDistanceMetricAllowedInDev() {
        String from = """
                search music {
                  document music {
                    field vector type tensor(x[5]) {
                      indexing: attribute | index
                    }
                  }
                }
                """;
        String to = """
                search music {
                  document music {
                    field vector type tensor(x[5]) {
                      indexing: attribute | index
                      attribute {
                        distance-metric: angular
                      }
                    }
                  }
                }
                """;


        ValidationTester tester = new ValidationTester();

        tester.setSchema(from);
        VespaModel oldModel =
                tester.deploy(null, getServices("index"), devZone, "<validation-overrides />").getFirst();
        tester.setSchema(to);
        List<ConfigChangeAction> actions = tester.deploy(oldModel, getServices("index"), devZone, "<validation-overrides />").getSecond();
        assertReindexingChange("hnsw-settings-change",
                               "Document type 'music': Field 'vector' changed: change property 'distance-metric' from 'EUCLIDEAN' to 'ANGULAR'",
                               actions,
                               false);
    }

    @Test
    void testChangingIndexModeFromIndexedToStreamingWhenDisallowed() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices("index"), zone, "<validation-overrides />").getFirst();
        try {
            tester.deploy(oldModel, getServices("streaming"), zone, "<validation-overrides />").getSecond();
            fail("Should throw on disallowed config change action");
        }
        catch (ValidationException e) {
            assertEquals("Changing the index mode (streaming, indexed, store-only) of documents: " +
                    "Document type 'music' in cluster 'default-content' changed indexing mode from 'indexed' to 'streaming'. " +
                    "To allow this add <allow until='yyyy-mm-dd'>indexing-mode-change</allow> to validation-overrides.xml, see https://docs.vespa.ai/en/reference/validation-overrides.html",
                    e.getMessage());
        }
    }

    @Test
    void testChangingIndexModeFromIndexedToStreaming() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices("index"), zone, validationOverrides).getFirst();
        List<ConfigChangeAction> changeActions =
                tester.deploy(oldModel, getServices("streaming"), zone, validationOverrides).getSecond();

        assertReindexingChange("indexing-mode-change",
                               "Document type 'music' in cluster 'default-content' changed indexing mode from 'indexed' to 'streaming'",
                               changeActions,
                               true);  // indexing mode change causes thread settings change
    }

    @Test
    void testChangingIndexModeFromStoreOnlyToIndexed() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices("index"), zone, validationOverrides).getFirst();
        List<ConfigChangeAction> changeActions =
                tester.deploy(oldModel, getServices("store-only"), zone, validationOverrides).getSecond();

        assertReindexingChange("indexing-mode-change",
                               "Document type 'music' in cluster 'default-content' changed indexing mode from 'indexed' to 'store-only'",
                               changeActions,
                               false);
    }

    private void assertReindexingChange(String validationId, String message, List<ConfigChangeAction> changeActions,
                                        boolean ignoreRestartActions) {
        if (ignoreRestartActions)
            changeActions = changeActions.stream()
                                         .filter(action -> action instanceof ConfigChangeReindexAction)
                                         .toList();
        assertEquals(1, changeActions.size());
        if (changeActions.get(0) instanceof ConfigChangeReindexAction reindexingAction) // Could also be a restart action, which have no name
            assertEquals(validationId, reindexingAction.name());
        assertEquals(message, changeActions.get(0).getMessage());
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
