// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeReindexAction;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class IndexingModeChangeValidatorTest {

    @Test
    public void testChangingIndexMode() {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices(AbstractSearchCluster.IndexingMode.REALTIME), Environment.prod, validationOverrides).getFirst();
        List<ConfigChangeAction> changeActions =
                tester.deploy(oldModel, getServices(AbstractSearchCluster.IndexingMode.STREAMING), Environment.prod, validationOverrides).getSecond();

        assertReindexingChange(true, // allowed=true due to validation override
                           "Document type 'music' in cluster 'default' changed indexing mode from 'indexed' to 'streaming'",
                           changeActions);
    }

    private void assertReindexingChange(boolean allowed, String message, List<ConfigChangeAction> changeActions) {
        List<ConfigChangeAction> reindexingActions = changeActions.stream()
                                                              .filter(a -> a instanceof ConfigChangeReindexAction)
                                                              .collect(Collectors.toList());
        assertEquals(1, reindexingActions.size());
        assertEquals(allowed, reindexingActions.get(0).allowed());
        assertTrue(reindexingActions.get(0) instanceof ConfigChangeReindexAction);
        assertEquals("indexing-mode-change", ((ConfigChangeReindexAction)reindexingActions.get(0)).name());
        assertEquals(message, reindexingActions.get(0).getMessage());
    }

    private static final String getServices(AbstractSearchCluster.IndexingMode indexingMode) {
        return "<services version='1.0'>" +
               "  <content id='default' version='1.0'>" +
               "    <redundancy>1</redundancy>" +
               "    <documents>" +
               "      <document type='music' mode='" +
               (indexingMode.equals(AbstractSearchCluster.IndexingMode.REALTIME) ? "index" : "streaming") + "'/>" +
               "    </documents>" +
               "    <nodes count='1'/>" +
               "   </content>" +
               "</services>";
    }

    private static final String validationOverrides =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-14' comment='test override'>indexing-mode-change</allow>\n" +
            "</validation-overrides>\n";

}
