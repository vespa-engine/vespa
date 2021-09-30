// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import org.junit.Test;

import static com.yahoo.vespa.config.server.configchange.Utils.CHANGE_ID;
import static com.yahoo.vespa.config.server.configchange.Utils.CHANGE_ID_2;
import static com.yahoo.vespa.config.server.configchange.Utils.CHANGE_MSG;
import static com.yahoo.vespa.config.server.configchange.Utils.CHANGE_MSG_2;
import static com.yahoo.vespa.config.server.configchange.Utils.CLUSTER;
import static com.yahoo.vespa.config.server.configchange.Utils.DOC_TYPE;
import static com.yahoo.vespa.config.server.configchange.Utils.DOC_TYPE_2;
import static com.yahoo.vespa.config.server.configchange.Utils.SERVICE_NAME;
import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class ReindexActionsFormatterTest {

    @Test
    public void formatting_of_single_action() {
        ReindexActions actions = new ConfigChangeActionsBuilder().
                reindex(CHANGE_ID, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME).
                build().getReindexActions();
        assertEquals("field-type-change: Consider re-indexing document type 'music' in cluster 'foo' because:\n" +
                        "    1) change\n",
                new ReindexActionsFormatter(actions).format());
    }

    @Test
    public void formatting_of_multiple_actions() {
        ReindexActions actions = new ConfigChangeActionsBuilder().
                reindex(CHANGE_ID, CHANGE_MSG, DOC_TYPE, CLUSTER, SERVICE_NAME).
                reindex(CHANGE_ID, CHANGE_MSG_2, DOC_TYPE, CLUSTER, SERVICE_NAME).
                reindex(CHANGE_ID_2, CHANGE_MSG_2, DOC_TYPE, CLUSTER, SERVICE_NAME).
                reindex(CHANGE_ID_2, CHANGE_MSG_2, DOC_TYPE, CLUSTER, SERVICE_NAME).
                reindex(CHANGE_ID, CHANGE_MSG_2, DOC_TYPE_2, CLUSTER, SERVICE_NAME).
                build().getReindexActions();
        assertEquals("field-type-change: Consider re-indexing document type 'book' in cluster 'foo' because:\n" +
                        "    1) other change\n" +
                        "field-type-change: Consider re-indexing document type 'music' in cluster 'foo' because:\n" +
                        "    1) change\n" +
                        "    2) other change\n" +
                        "indexing-change: Consider re-indexing document type 'music' in cluster 'foo' because:\n" +
                        "    1) other change\n",
                new ReindexActionsFormatter(actions).format());
    }


}
