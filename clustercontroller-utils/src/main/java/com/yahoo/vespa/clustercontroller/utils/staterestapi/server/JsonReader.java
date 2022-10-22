// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class JsonReader {

    private static final ObjectMapper mapper = new ObjectMapper();

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
        JsonNode json = mapper.readTree(request.getPostContent().toString());

        final boolean probe = json.has("probe") && json.get("probe").booleanValue();

        final SetUnitStateRequest.Condition condition;
        if (json.has("condition")) {
            condition = SetUnitStateRequest.Condition.fromString(json.get("condition").textValue());
        } else {
            condition = SetUnitStateRequest.Condition.FORCE;
        }

        final SetUnitStateRequest.ResponseWait responseWait = json.has("response-wait")
                ? SetUnitStateRequest.ResponseWait.fromString(json.get("response-wait").textValue())
                : SetUnitStateRequest.ResponseWait.WAIT_UNTIL_CLUSTER_ACKED;

        Map<String, UnitState> stateMap = new HashMap<>();
        if (!json.has("state")) {
            throw new InvalidContentException("Set state requests must contain a state object");
        }
        JsonNode o = json.get("state");
        if ( ! (o instanceof ObjectNode state)) {
            throw new InvalidContentException("value of state is not a json object");
        }

        for (Iterator<Map.Entry<String, JsonNode>> fields = state.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String type = entry.getKey();
            if ( ! (entry.getValue() instanceof ObjectNode userState)) {
                throw new InvalidContentException("value of state->" + type + " is not a json object");
            }
            String code = "up";
            if (userState.has("state")) {
                o = userState.get("state");
                if ( ! o.isTextual()) {
                    throw new InvalidContentException("value of state->" + type + "->state is not a string");
                }
                code = o.textValue();
            }
            String reason = "";
            if (userState.has("reason")) {
                o = userState.get("reason");
                if ( ! o.isTextual()) {
                    throw new InvalidContentException("value of state->" + type + "->reason is not a string");
                }
                reason = o.textValue();
            }
            stateMap.put(type, new UnitStateImpl(code, reason));
        }

        return new SetRequestData(probe, stateMap, condition, responseWait);
    }

}
