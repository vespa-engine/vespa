// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.util.JSONObjectWrapper;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class JsonHttpResult extends HttpResult {

    private JSONObject json;
    private boolean failedParsing = false;


    public JsonHttpResult() {
        addHeader("Content-Type", "application/json");
    }

    public JsonHttpResult(HttpResult other) {
        super(other);

        if (other.getContent() == null) {
            setParsedJson(new JSONObject());
            return;
        }
        try{
            if (other.getContent() instanceof JSONObject) {
                setParsedJson((JSONObject) other.getContent());
            } else {
                setParsedJson(new JSONObject(other.getContent().toString()));
            }
        } catch (JSONException e) {
            failedParsing = true;
            setParsedJson(createErrorJson(e.getMessage(), other));
        }
    }

    private JSONObject createErrorJson(String error, HttpResult other) {
        return new JSONObjectWrapper()
                .put("error", "Invalid JSON in output: " + error)
                .put("output", other.getContent().toString());
    }

    public JsonHttpResult setJson(JSONObject o) {
        setContent(o);
        json = o;
        return this;
    }

    private void setParsedJson(JSONObject o) {
        json = o;
    }

    public JSONObject getJson() {
        return json;
    }

    @Override
    public void printContent(StringBuilder sb) {
        if (failedParsing) {
            super.printContent(sb);
            return;
        }
        if (json != null) {
            sb.append("JSON: ");
            try{
                sb.append(json.toString(2));
            } catch (JSONException e) {
                sb.append(json.toString());
            }
        } else {
            super.printContent(sb);
        }
    }

}
