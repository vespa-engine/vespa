// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static com.yahoo.vespa.config.server.configchange.Utils.*;

/**
 * @author geirst
 * @since 5.44
 */
public class RefeedActionsFormatterTest {

    @Test
    public void formatting_of_single_action() {
        RefeedActions actions = new ConfigChangeActionsBuilder().
                refeed(CHANGE_ID, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME).
                build().getRefeedActions();
        assertEquals("field-type-change: Consider removing data and re-feed document type 'music' in cluster 'foo' because:\n" +
                     "    1) change\n",
                     new RefeedActionsFormatter(actions).format());
    }

    @Test
    public void formatting_of_multiple_actions() {
        RefeedActions actions = new ConfigChangeActionsBuilder().
                refeed(CHANGE_ID, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME).
                refeed(CHANGE_ID, CHANGE_MSG_2, DOC_TYPE, CLUSTER, SERVICE_NAME).
                refeed(CHANGE_ID_2, CHANGE_MSG_2, DOC_TYPE, CLUSTER, SERVICE_NAME).
                refeed(CHANGE_ID_2, CHANGE_MSG_2, DOC_TYPE, CLUSTER, SERVICE_NAME).
                refeed(CHANGE_ID, CHANGE_MSG_2, DOC_TYPE_2, CLUSTER, SERVICE_NAME).
                build().getRefeedActions();
        assertEquals("field-type-change: Consider removing data and re-feed document type 'book' in cluster 'foo' because:\n" +
                     "    1) other change\n" +
                     "field-type-change: Consider removing data and re-feed document type 'music' in cluster 'foo' because:\n" +
                     "    1) change\n" +
                     "    2) other change\n" +
                     "indexing-change: Consider removing data and re-feed document type 'music' in cluster 'foo' because:\n" +
                     "    1) other change\n",
                new RefeedActionsFormatter(actions).format());
    }

}
