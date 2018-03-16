// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.MissingUnitException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OperationNotSupportedForUnitException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.UnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class DummyStateApi implements StateRestAPI {
    private final DummyBackend backend;
    private Exception induceException;

    public DummyStateApi(DummyBackend backend) {
        this.backend = backend;
    }

    public void induceException(StateRestApiException e) {
        induceException = e;
    }
    public void induceException(RuntimeException e) {
        induceException = e;
    }

    public class SubUnitListImpl implements SubUnitList {
        private Map<String, String> links = new LinkedHashMap<>();
        private Map<String, UnitResponse> values = new LinkedHashMap<>();

        @Override
        public Map<String, String> getSubUnitLinks() { return links; }
        @Override
        public Map<String, UnitResponse> getSubUnits() { return values; }

        public void addUnit(DummyBackend.Cluster cluster, int recursive) {
            if (recursive == 0) {
                links.put(cluster.id, cluster.id);
            } else {
                values.put(cluster.id, getClusterState(cluster, recursive - 1));
            }
        }
        public void addUnit(DummyBackend.Node node, int recursive) {
            if (recursive == 0) {
                String link = node.clusterId + '/' + node.id;
                links.put(node.id, link);
            } else {
                values.put(node.id, getNodeState(node));
            }
        }
    }

    private UnitResponse getClusterList(final int recursive) {
        return new UnitResponse() {
            @Override
            public UnitAttributes getAttributes() { return null; }
            @Override
            public CurrentUnitState getCurrentState() { return null; }
            @Override
            public UnitMetrics getMetrics() { return null; }
            @Override
            public Map<String, SubUnitList> getSubUnits() {
                Map<String, SubUnitList> result = new LinkedHashMap<>();
                SubUnitListImpl subUnits = new SubUnitListImpl();
                result.put("cluster", subUnits);
                for (Map.Entry<String, DummyBackend.Cluster> e : backend.getClusters().entrySet()) {
                    subUnits.addUnit(e.getValue(), recursive);
                }
                return result;
            }
            @Override
            public DistributionStates getDistributionStates() {
                return null;
            }
        };
    }
    private UnitResponse getClusterState(final DummyBackend.Cluster cluster, final int recursive) {
        return new UnitResponse() {
            @Override
            public UnitAttributes getAttributes() { return null; }
            @Override
            public CurrentUnitState getCurrentState() { return null; }
            @Override
            public UnitMetrics getMetrics() { return null; }
            @Override
            public Map<String, SubUnitList> getSubUnits() {
                Map<String, SubUnitList> result = new LinkedHashMap<>();
                SubUnitListImpl subUnits = new SubUnitListImpl();
                result.put("node", subUnits);
                for (Map.Entry<String, DummyBackend.Node> e : cluster.nodes.entrySet()) {
                    subUnits.addUnit(e.getValue(), recursive);
                }
                return result;
            }
            @Override
            public DistributionStates getDistributionStates() {
                return null;
            }
        };
    }
    private UnitResponse getNodeState(final DummyBackend.Node node) {
        return new UnitResponse() {
            @Override
            public UnitAttributes getAttributes() {
                return new UnitAttributes() {
                    @Override
                    public Map<String, String> getAttributeValues() {
                        Map<String, String> attrs = new LinkedHashMap<>();
                        attrs.put("group", node.group);
                        return attrs;
                    }
                };
            }
            @Override
            public Map<String, SubUnitList> getSubUnits() { return null; }
            @Override
            public CurrentUnitState getCurrentState() {
                return new CurrentUnitState() {
                    @Override
                    public Map<String, UnitState> getStatePerType() {
                        Map<String, UnitState> m = new LinkedHashMap<>();
                        m.put("current", new UnitState() {
                            @Override
                            public String getId() { return node.state; }
                            @Override
                            public String getReason() { return node.reason; }
                        });
                        return m;
                    }
                };
            }
            @Override
            public UnitMetrics getMetrics() {
                return new UnitMetrics() {
                    @Override
                    public Map<String, Number> getMetricMap() {
                        Map<String, Number> m = new LinkedHashMap<>();
                        m.put("doc-count", node.docCount);
                        return m;
                    }
                };
            }
            @Override
            public DistributionStates getDistributionStates() {
                return null;
            }
        };

    }

    @Override
    public UnitResponse getState(UnitStateRequest request) throws StateRestApiException {
        checkForInducedException();
        String[] path = request.getUnitPath();
        if (path.length == 0) {
            return getClusterList(request.getRecursiveLevels());
        }
        final DummyBackend.Cluster c = backend.getClusters().get(path[0]);
        if (c == null) throw new MissingUnitException(path, 0);
        if (path.length == 1) {
            return getClusterState(c, request.getRecursiveLevels());
        }
        final DummyBackend.Node n = c.nodes.get(path[1]);
        if (n == null) throw new MissingUnitException(path, 1);
        if (path.length == 2) {
            return getNodeState(n);
        }
        throw new MissingUnitException(path, 3);
    }

    @Override
    public SetResponse setUnitState(SetUnitStateRequest request) throws StateRestApiException {
        checkForInducedException();
        String[] path = request.getUnitPath();
        if (path.length != 2) {
            throw new OperationNotSupportedForUnitException(
                    path, "You can only set states on nodes");
        }
        DummyBackend.Node n = null;
        DummyBackend.Cluster c = backend.getClusters().get(path[0]);
        if (c != null) {
            n = c.nodes.get(path[1]);
        }
        if (n == null) throw new MissingUnitException(path, 2);
        Map<String, UnitState> newState = request.getNewState();
        if (newState.size() != 1 || !newState.containsKey("current")) {
            throw new InvalidContentException("Only state of type 'current' is allowed to be set.");
        }
        n.state = newState.get("current").getId();
        n.reason = newState.get("current").getReason();
        String reason = String.format("DummyStateAPI %s call", request.getResponseWait().getName());
        return new SetResponse(reason, true);
    }

    private void checkForInducedException() throws StateRestApiException {
        if (induceException == null) return;
        Exception e = induceException;
        induceException = null;
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        throw (StateRestApiException) e;
    }
}
