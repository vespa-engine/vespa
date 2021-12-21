// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static com.yahoo.vespa.config.server.configchange.Utils.*;

/**
 * @author geirst
 * @since 5.44
 */
public class RestartActionsFormatterTest {

    @Test
    public void formatting_of_single_action() {
        RestartActions actions = new ConfigChangeActionsBuilder().
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                build().getRestartActions();
        assertThat(new RestartActionsFormatter(actions).format(),
                equalTo("In cluster 'foo' of type 'search':\n" +
                        "    Restart services of type 'searchnode' because:\n" +
                        "        1) change\n"));
    }

    @Test
    public void formatting_of_multiple_actions() {
        RestartActions actions = new ConfigChangeActionsBuilder().
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG_2, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG, CLUSTER_2, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                build().getRestartActions();
        assertThat(new RestartActionsFormatter(actions).format(),
                equalTo("In cluster 'bar' of type 'search':\n" +
                        "    Restart services of type 'searchnode' because:\n" +
                        "        1) change\n" +
                        "In cluster 'foo' of type 'search':\n" +
                        "    Restart services of type 'searchnode' because:\n" +
                        "        1) change\n" +
                        "        2) other change\n"));
    }

}
