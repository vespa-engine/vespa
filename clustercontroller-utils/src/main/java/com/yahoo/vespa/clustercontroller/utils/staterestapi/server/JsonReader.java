// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.server;

import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class JsonReader {
    private static class UnitStateImpl implements UnitState {
        private final String id;
        private final String reason;

        public UnitStateImpl(String id, String reason) {
            this.id = id;
            this.reason = reason;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getReason() {
            return reason;
        }
    }

    static class SetRequestData {
        final boolean probe;
        final Map<String, UnitState> stateMap;
        final SetUnitStateRequest.Condition condition;
        final SetUnitStateRequest.ResponseWait responseWait;

        public SetRequestData(boolean probe,
                              Map<String, UnitState> stateMap,
                              SetUnitStateRequest.Condition condition,
                              SetUnitStateRequest.ResponseWait responseWait) {
            this.probe = probe;
            this.stateMap = stateMap;
            this.condition = condition;
            this.responseWait = responseWait;
        }
    }

    public SetRequestData getStateRequestData(HttpRequest request) throws Exception {
        JSONObject json = new JSONObject(request.getPostContent().toString());

        final boolean probe = json.has("probe") && json.getBoolean("probe");

        final SetUnitStateRequest.Condition condition;
        if (json.has("condition")) {
            condition = SetUnitStateRequest.Condition.fromString(json.getString("condition"));
        } else {
            condition = SetUnitStateRequest.Condition.FORCE;
        }

        final SetUnitStateRequest.ResponseWait responseWait = json.has("response-wait")
                ? SetUnitStateRequest.ResponseWait.fromString(json.getString("response-wait"))
                : SetUnitStateRequest.ResponseWait.WAIT_UNTIL_CLUSTER_ACKED;

        Map<String, UnitState> stateMap = new HashMap<>();
        if (!json.has("state")) {
            throw new InvalidContentException("Set state requests must contain a state object");
        }
        Object o = json.get("state");
        if (!(o instanceof JSONObject)) {
            throw new InvalidContentException("value of state is not a json object");
        }

        JSONObject state = (JSONObject) o;

        JSONArray stateTypes = state.names();
        for (int i=0; i<stateTypes.length(); ++i) {
            o = stateTypes.get(i);
            String type = (String) o;
            o = state.get(type);
            if (!(o instanceof JSONObject)) {
                throw new InvalidContentException("value of state->" + type + " is not a json object");
            }
            JSONObject userState = (JSONObject) o;
            String code = "up";
            if (userState.has("state")) {
                o = userState.get("state");
                if (!(o instanceof String)) {
                    throw new InvalidContentException("value of state->" + type + "->state is not a string");
                }
                code = o.toString();
            }
            String reason = "";
            if (userState.has("reason")) {
                o = userState.get("reason");
                if (!(o instanceof String)) {
                    throw new InvalidContentException("value of state->" + type + "->reason is not a string");
                }
                reason = o.toString();
            }
            stateMap.put(type, new UnitStateImpl(code, reason));
        }

        return new SetRequestData(probe, stateMap, condition, responseWait);
    }
}
