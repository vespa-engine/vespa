package com.yahoo.vespa.config.server.session;

import com.yahoo.vespa.config.server.session.ActivationTriggers.NodeRestart;
import com.yahoo.vespa.config.server.session.ActivationTriggers.Reindexing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author jonmv
 */
class ActivationTriggersSerializerTest {

    @Test
    void testSerialization() {
        ActivationTriggers triggers = new ActivationTriggers(List.of(new NodeRestart("node1"),
                                                                     new NodeRestart("node2")),
                                                             List.of(new Reindexing("cluster1", "type1"),
                                                                     new Reindexing("cluster1", "type2"),
                                                                     new Reindexing("cluster2", "type1")));
        assertEquals(triggers, ActivationTriggersSerializer.fromJson(ActivationTriggersSerializer.toJson(triggers)));
    }

}
