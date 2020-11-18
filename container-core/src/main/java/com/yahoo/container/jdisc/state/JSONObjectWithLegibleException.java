// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;

/**
 * A JSONObject that wraps the checked JSONException in a RuntimeException with a legible error message.
 *
 * @author gjoranv
 */
class JSONObjectWithLegibleException extends JSONObject {

    @Override
    public JSONObject put(String s, boolean b) {
        try {
            return super.put(s, b);
        } catch (JSONException e) {
            throw new RuntimeException(getErrorMessage(s, b, e), e);
        }
    }

    @Override
    public JSONObject put(String s, double v) {
        try {
            Double guardedVal = (((Double) v).isNaN() || ((Double) v).isInfinite()) ?
                    0.0 : v;
            return super.put(s, guardedVal);
        } catch (JSONException e) {
            throw new RuntimeException(getErrorMessage(s, v, e), e);
        }
    }

    @Override
    public JSONObject put(String s, int i) {
        try {
            return super.put(s, i);
        } catch (JSONException e) {
            throw new RuntimeException(getErrorMessage(s, i, e), e);
        }
    }

    @Override
    public JSONObject put(String s, long l) {
        try {
            return super.put(s, l);
        } catch (JSONException e) {
            throw new RuntimeException(getErrorMessage(s, l, e), e);
        }
    }

    @Override
    public JSONObject put(String s, Collection collection) {
        try {
            return super.put(s, collection);
        } catch (JSONException e) {
            throw new RuntimeException(getErrorMessage(s, collection, e), e);
        }
    }

    @Override
    public JSONObject put(String s, Map map) {
        try {
            return super.put(s, map);
        } catch (JSONException e) {
            throw new RuntimeException(getErrorMessage(s, map, e), e);
        }
    }

    @Override
    public JSONObject put(String s, Object o) {
        try {
            return super.put(s, o);
        } catch (JSONException e) {
            throw new RuntimeException(getErrorMessage(s, o, e), e);
        }
    }

    private String getErrorMessage(String key, Object value, JSONException e) {
        return "Trying to add invalid JSON object with key '" + key +
                "' and value '" + value + "' - " + e.getMessage();
    }

}
