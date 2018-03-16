// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.DiskState;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InternalFailure;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class Response {

    public static class UnitStateImpl implements UnitState {
        private final String id;
        private final String reason;

        public UnitStateImpl(State s) throws StateRestApiException {
            this.id = parseId(s);
            this.reason = "";
        }
        public UnitStateImpl(NodeState ns) throws StateRestApiException {
            this.id = parseId(ns.getState());
            this.reason = ns.getDescription();
        }
        public UnitStateImpl(DiskState ds) throws StateRestApiException {
            this.id = parseId(ds.getState());
            this.reason = ds.getDescription();
        }

        public String parseId(State id) throws StateRestApiException {
            switch (id) {
                case UP: return "up";
                case DOWN: return "down";
                case INITIALIZING: return "initializing";
                case MAINTENANCE: return "maintenance";
                case RETIRED: return "retired";
                case STOPPING: return "stopping";
            }
            throw new InternalFailure("Unknown state '" + id + "' found.");
        }

        @Override
        public String getId() { return id; }
        @Override
        public String getReason() { return reason; }
    }
    public static class Link implements SubUnitList {
        private final Map<String, String> links = new LinkedHashMap<>();
        private final Map<String, UnitResponse> units = new LinkedHashMap<>();

        public Link addLink(String unit, String link) {
            links.put(unit, link);
            return this;
        }

        public Link addUnit(String unit, UnitResponse r) {
            units.put(unit, r);
            return this;
        }

        @Override
        public Map<String, String> getSubUnitLinks() { return links; }
        @Override
        public Map<String, UnitResponse> getSubUnits() { return units; }
    }

    public static abstract class EmptyResponse<T extends UnitResponse>
            implements UnitResponse, UnitMetrics, UnitAttributes, CurrentUnitState, DistributionStates
    {
        protected final Map<String, String> attributes = new LinkedHashMap<>();
        protected final Map<String, SubUnitList> subUnits = new LinkedHashMap<>();
        protected final Map<String, Number> metrics = new LinkedHashMap<>();
        protected final Map<String, UnitState> stateMap = new LinkedHashMap<>();
        protected DistributionState publishedState = null;

        @Override
        public UnitAttributes getAttributes() { return attributes.isEmpty() ? null : this; }
        @Override
        public CurrentUnitState getCurrentState() { return stateMap.isEmpty() ? null : this; }
        @Override
        public Map<String, SubUnitList> getSubUnits() { return subUnits.isEmpty() ? null : subUnits; }
        @Override
        public UnitMetrics getMetrics() { return metrics.isEmpty() ? null : this; }
        @Override
        public DistributionStates getDistributionStates() {
            return (publishedState == null) ? null : this;
        }

        @Override
        public Map<String, Number> getMetricMap() { return metrics; }
        @Override
        public Map<String, UnitState> getStatePerType() { return stateMap; }
        @Override
        public Map<String, String> getAttributeValues() { return attributes; }
        @Override
        public DistributionState getPublishedState() {
            return publishedState;
        }

        public EmptyResponse<T> addLink(String type, String unit, String link) {
            Link list = (Link) subUnits.get(type);
            if (list == null) {
                list = new Link();
                subUnits.put(type, list);
            }
            list.addLink(unit, link);
            return this;
        }
        public EmptyResponse<T> addEntry(String type, String unit, T response) {
            Link list = (Link) subUnits.get(type);
            if (list == null) {
                list = new Link();
                subUnits.put(type, list);
            }
            list.addUnit(unit, response);
            return this;
        }
        public EmptyResponse<T> addMetric(String name, Number value) {
            metrics.put(name, value);
            return this;
        }
        public EmptyResponse<T> addState(String type, UnitStateImpl state) {
            stateMap.put(type, state);
            return this;
        }
        public EmptyResponse<T> addAttribute(String name, String value) {
            attributes.put(name, value);
            return this;
        }
        public EmptyResponse<T> setPublishedState(DistributionState publishedState) {
            this.publishedState = publishedState;
            return this;
        }
    }

    public static class ClusterListResponse extends EmptyResponse<ClusterResponse> {}
    public static class ClusterResponse extends EmptyResponse<ServiceResponse> {}
    public static class ServiceResponse extends EmptyResponse<NodeResponse> {}
    public static class NodeResponse extends EmptyResponse<PartitionResponse> {}
    public static class PartitionResponse extends EmptyResponse<UnitResponse> {}

}
