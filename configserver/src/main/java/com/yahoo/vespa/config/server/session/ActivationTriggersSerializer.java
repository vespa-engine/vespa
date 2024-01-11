package com.yahoo.vespa.config.server.session;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.config.server.session.ActivationTriggers.NodeRestart;
import com.yahoo.vespa.config.server.session.ActivationTriggers.Reindexing;

import java.util.List;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author jonmv
 */
public class ActivationTriggersSerializer {

    static final String NODE_RESTARTS = "nodeRestarts";
    static final String REINDEXINGS = "reindexings";
    static final String CLUSTER_NAME = "clusterName";
    static final String DOCUMENT_TYPE = "documentType";

    public static byte[] toJson(ActivationTriggers triggers) {
        Slime root = new Slime();
        toSlime(triggers, root.setObject());
        return uncheck(() -> SlimeUtils.toJsonBytes(root));
    }

    public static ActivationTriggers fromJson(byte[] json) {
        return fromSlime(SlimeUtils.jsonToSlime(json).get());
    }

    public static void toSlime(ActivationTriggers triggers, Cursor object) {
        Cursor nodeRestarts = object.setArray(NODE_RESTARTS);
        for (NodeRestart nodeRestart : triggers.nodeRestarts())
            nodeRestarts.addString(nodeRestart.hostname());

        Cursor reindexings = object.setArray(REINDEXINGS);
        for (Reindexing reindexing : triggers.reindexings()) {
            Cursor entry = reindexings.addObject();
            entry.setString(CLUSTER_NAME, reindexing.clusterId());
            entry.setString(DOCUMENT_TYPE, reindexing.documentType());
        }
    }

    public static ActivationTriggers fromSlime(Cursor object) {
        if ( ! object.valid())
            return ActivationTriggers.empty();

        List<NodeRestart> nodeRestarts = SlimeUtils.entriesStream(object.field(NODE_RESTARTS))
                                                   .map(entry -> new NodeRestart(entry.asString()))
                                                   .toList();
        List<Reindexing> reindexings = SlimeUtils.entriesStream(object.field(REINDEXINGS))
                                                   .map(entry -> new Reindexing(entry.field(CLUSTER_NAME).asString(),
                                                                                entry.field(DOCUMENT_TYPE).asString()))
                                                   .toList();
        return new ActivationTriggers(nodeRestarts, reindexings);
    }

}
