package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.UnitStateRequest;

import java.util.List;

public class StateRequests {

    public static class Get extends StateRequests implements UnitStateRequest {
        private final List<String> path;
        private final int recursive;

        public Get(String req, int recursive) {
            path = req.isEmpty() ? List.of() : List.of(req.split("/"));
            this.recursive = recursive;
        }

        @Override
        public int getRecursiveLevels() {
            return recursive;
        }

        @Override
        public List<String> getUnitPath() {
            return path;
        }

    }

}
