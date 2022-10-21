// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.CurrentUnitState;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.DistributionState;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.DistributionStates;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.SetResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.SubUnitList;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitAttributes;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitMetrics;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;

import java.util.Map;

public class JsonWriter {

    private static final ObjectMapper mapper = new ObjectMapper();

    private String pathPrefix = "/";

    public JsonWriter() { }

    public void setDefaultPathPrefix(String defaultPathPrefix) {
        if (defaultPathPrefix.isEmpty() || defaultPathPrefix.charAt(0) != '/') {
            throw new IllegalArgumentException("Path prefix must start with a slash");
        }
        this.pathPrefix = defaultPathPrefix;
    }

    public JsonNode createJson(UnitResponse data) {
        ObjectNode json = new ObjectNode(mapper.getNodeFactory());
        fillInJson(data, json);
        return json;
    }

    public void fillInJson(UnitResponse data, ObjectNode json) {
        UnitAttributes attributes = data.getAttributes();
        if (attributes != null) {
            json.putPOJO("attributes", attributes.getAttributeValues());
        }
        CurrentUnitState stateData = data.getCurrentState();
        if (stateData != null) {
            fillInJson(stateData, json);
        }
        UnitMetrics metrics = data.getMetrics();
        if (metrics != null) {
            json.putPOJO("metrics", metrics.getMetricMap());
        }
        Map<String, SubUnitList> subUnits = data.getSubUnits();
        if (subUnits != null) {
            fillInJson(subUnits, json);
        }
        DistributionStates distributionStates = data.getDistributionStates();
        if (distributionStates != null) {
            fillInJson(distributionStates, json);
        }
    }

    public void fillInJson(CurrentUnitState stateData, ObjectNode json) {
        ObjectNode stateJson = json.putObject("state");
        Map<String, UnitState> state = stateData.getStatePerType();
        state.forEach((stateType, unitState) -> stateJson.putObject(stateType)
                                                         .put("state", unitState.getId())
                                                         .put("reason", unitState.getReason()));
    }

    public void fillInJson(Map<String, SubUnitList> subUnitMap, ObjectNode json) {
        subUnitMap.forEach((subUnitType, units) -> {
            ObjectNode typeJson = json.putObject(subUnitType);
            units.getSubUnitLinks().forEach((key, value) -> typeJson.putObject(key).put("link", pathPrefix + "/" + value));
            units.getSubUnits().forEach((key, value) -> fillInJson(value, typeJson.putObject(key)));
        });
    }

    private static void fillInJson(DistributionStates states, ObjectNode json) {
        fillDistributionState(states.getPublishedState(),
                              json.putObject("distribution-states")
                                  .putObject("published"));
    }

    private static void fillDistributionState(DistributionState state, ObjectNode result) {
        result.put("baseline", state.getBaselineState());
        ArrayNode bucketSpacesJson = result.putArray("bucket-spaces");
        state.getBucketSpaceStates().forEach((key, value) -> {
            ObjectNode bucketSpaceJson = bucketSpacesJson.addObject();
            bucketSpaceJson.put("name", key);
            bucketSpaceJson.put("state", value);
        });
    }

    public JsonNode createErrorJson(String description) {
        return new ObjectNode(mapper.getNodeFactory()).put("message", description);
    }

    public JsonNode createJson(SetResponse setResponse) {
        return new ObjectNode(mapper.getNodeFactory()).put("wasModified", setResponse.getWasModified())
                                                      .put("reason", setResponse.getReason());
    }

}
