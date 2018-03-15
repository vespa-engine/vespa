// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.server;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.*;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.Map;

public class JsonWriter {

    private String pathPrefix = "/";

    public JsonWriter() {
    }

    public void setDefaultPathPrefix(String defaultPathPrefix) {
        if (defaultPathPrefix.isEmpty() || defaultPathPrefix.charAt(0) != '/') {
            throw new IllegalArgumentException("Path prefix must start with a slash");
        }
        this.pathPrefix = defaultPathPrefix;
    }

    public JSONObject createJson(UnitResponse data) throws Exception {
        JSONObject json = new JSONObject();
        fillInJson(data, json);
        return json;
    }

    public void fillInJson(UnitResponse data, JSONObject json) throws Exception {
        UnitAttributes attributes = data.getAttributes();
        if (attributes != null) {
            fillInJson(attributes, json);
        }
        CurrentUnitState stateData = data.getCurrentState();
        if (stateData != null) {
            fillInJson(stateData, json);
        }
        UnitMetrics metrics = data.getMetrics();
        if (metrics != null) {
            fillInJson(metrics, json);
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

    public void fillInJson(CurrentUnitState stateData, JSONObject json) throws Exception {
        JSONObject stateJson = new JSONObject();
        json.put("state", stateJson);
        Map<String, UnitState> state = stateData.getStatePerType();
        for (Map.Entry<String, UnitState> e : state.entrySet()) {
            String stateType = e.getKey();
            UnitState unitState = e.getValue();
            JSONObject stateTypeJson = new JSONObject()
                    .put("state", unitState.getId())
                    .put("reason", unitState.getReason());
            stateJson.put(stateType, stateTypeJson);
        }
    }

    public void fillInJson(UnitMetrics metrics, JSONObject json) throws Exception {
        JSONObject metricsJson = new JSONObject();
        for (Map.Entry<String, Number> e : metrics.getMetricMap().entrySet()) {
            metricsJson.put(e.getKey(), e.getValue());
        }
        json.put("metrics", metricsJson);
    }
    public void fillInJson(UnitAttributes attributes, JSONObject json) throws Exception {
        JSONObject attributesJson = new JSONObject();
        for (Map.Entry<String, String> e : attributes.getAttributeValues().entrySet()) {
            attributesJson.put(e.getKey(), e.getValue());
        }
        json.put("attributes", attributesJson);
    }

    public void fillInJson(Map<String, SubUnitList> subUnitMap, JSONObject json) throws Exception {
        for(Map.Entry<String, SubUnitList> e : subUnitMap.entrySet()) {
            String subUnitType = e.getKey();
            JSONObject typeJson = new JSONObject();
            for (Map.Entry<String, String> f : e.getValue().getSubUnitLinks().entrySet()) {
                JSONObject linkJson = new JSONObject();
                linkJson.put("link", pathPrefix + "/" + f.getValue());
                typeJson.put(f.getKey(), linkJson);
            }
            for (Map.Entry<String, UnitResponse> f : e.getValue().getSubUnits().entrySet()) {
                JSONObject subJson = new JSONObject();
                fillInJson(f.getValue(), subJson);
                typeJson.put(f.getKey(), subJson);
            }
            json.put(subUnitType, typeJson);
        }
    }

    private static void fillInJson(DistributionStates states, JSONObject json) throws Exception {
        JSONObject statesJson = new JSONObject();
        statesJson.put("published", distributionStateToJson(states.getPublishedState()));
        json.put("distribution-states", statesJson);
    }

    private static JSONObject distributionStateToJson(DistributionState state) throws Exception {
        JSONObject result = new JSONObject();
        result.put("baseline", state.getBaselineState());
        JSONArray bucketSpacesJson = new JSONArray();
        result.put("bucket-spaces", bucketSpacesJson);
        for (Map.Entry<String, String> entry : state.getBucketSpaceStates().entrySet()) {
            JSONObject bucketSpaceJson = new JSONObject();
            bucketSpaceJson.put("name", entry.getKey());
            bucketSpaceJson.put("state", entry.getValue());
            bucketSpacesJson.put(bucketSpaceJson);
        }
        return result;
    }

    public JSONObject createErrorJson(String description) {
        JSONObject o = new JSONObject();
        try{
            o.put("message", description);
        } catch (JSONException e) {
            // Can't really do anything if we get an error trying to report an error.
        }
        return o;
    }

    public JSONObject createJson(SetResponse setResponse) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("wasModified", setResponse.getWasModified());
        jsonObject.put("reason", setResponse.getReason());
        return jsonObject;
    }

}
