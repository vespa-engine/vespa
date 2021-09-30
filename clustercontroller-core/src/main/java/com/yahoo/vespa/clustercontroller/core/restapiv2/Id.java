// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Class representations of resources in State Rest API.
 *
 * Note that the toString() implementation will put out a slash separated list of the tokens,
 * and thus be compatible with the link format.
 */

package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.NodeType;

public class Id {

    public static class Cluster extends Id {
        private final String id;

        public Cluster(String id) { this.id = id; }

        public final String getClusterId() { return id; }
        public String toString() { return id; }
    }

    public static class Service extends Cluster {
        private final NodeType id;

        public Service(Cluster c, NodeType service) {
            super(c.getClusterId());
            id = service;
        }

        public final NodeType getService() { return id; }
        public String toString() { return super.toString() + "/" + id; }
    }

    public static class Node extends Service {
        private final int id;

        public Node(Service service, int nodeIndex) {
            super(service, service.id);
            this.id = nodeIndex;
        }

        /**
         * Looks bad with name overlap here, but everywhere else Node will have to be
         * referred to as Id.Node, so users won't get conflicts.
         */
        public final com.yahoo.vdslib.state.Node getNode() {
            return new com.yahoo.vdslib.state.Node(getService(), id);
        }

        public String toString() { return super.toString() + "/" + id; }
    }

    public static class Partition extends Node {
        private final int id;

        public Partition(Node n, int partition) {
            super(n, n.id);
            this.id = partition;
        }

        public String toString() { return super.toString() + "/" + id; }
    }

}
