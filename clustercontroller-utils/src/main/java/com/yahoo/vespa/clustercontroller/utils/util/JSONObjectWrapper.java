// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.util;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * The Jettison json object class has an interface issue where it hides null pointer exceptions
 * as checked json exceptions. Consequently one has to create catch clauses that code cannot get
 * into. This class hides those exceptions.
 *
 * (Add functions to this wrapper for new functions needing to hide exceptions like this as they are
 * needed)
 */
public class JSONObjectWrapper extends JSONObject {

    @Override
    public JSONObjectWrapper put(String key, Object value) {
        try{
            super.put(key, value);
            return this;
        } catch (JSONException e) {
            throw new NullPointerException(e.getMessage());
        }
    }

}
