// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigChangeRefeedAction;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class IndexingModeChangeValidatorTest {

    @Test
    public void testChangingIndexMode() throws IOException, SAXException {
        ValidationTester tester = new ValidationTester();

        VespaModel oldModel =
                tester.deploy(null, getServices(AbstractSearchCluster.IndexingMode.REALTIME), validationOverrides).getFirst();
        List<ConfigChangeAction> changeActions =
                tester.deploy(oldModel, getServices(AbstractSearchCluster.IndexingMode.STREAMING), validationOverrides).getSecond();

        assertRefeedChange(true, // allowed=true due to validation override
                           "Cluster 'default' changed indexing mode from 'indexed' to 'streaming'",
                           changeActions);
    }

    private void assertRefeedChange(boolean allowed, String message, List<ConfigChangeAction> changeActions) {
        List<ConfigChangeAction> refeedActions = changeActions.stream()
                                                              .filter(a -> a instanceof ConfigChangeRefeedAction)
                                                              .collect(Collectors.toList());
        assertEquals(1, refeedActions.size());
        assertEquals(allowed, refeedActions.get(0).allowed());
        assertTrue(refeedActions.get(0) instanceof ConfigChangeRefeedAction);
        assertEquals("indexing-mode-change", ((ConfigChangeRefeedAction)refeedActions.get(0)).name());
        assertEquals(message, refeedActions.get(0).getMessage());
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
