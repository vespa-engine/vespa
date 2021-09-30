// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text.interpretation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An annotation is a description of a an area of text, with a given class. For example, an annotation for the
 *
 * @author Arne Bergene Fossaa
 */
public class Annotations {

    private final Span span;

    protected Map<String,Object> annotations;

    /**
     * Adds an annotation to the the the set of annotations.
     */
    public void put(String key,Object o) {
        if (annotations == null) {
            annotations = new HashMap<>();
        }
        annotations.put(key,o);
    }

    public Map<String,Object> getMap() {
        if (annotations == null) {
            return Collections.emptyMap();
        } else {
            return annotations;
        }
    }

    public Annotations(Span span) {
        this.span = span;
    }

    public Object get(String key) {
        return getMap().get(key);
    }

    /**
     * The span that this annotation is for.
     */
    public Span getSpan() {
        return span;
    }

    /**
     * The text this annotation is for.
     */
    public String getSubString() {
        return span.getText();
    }


    /**
     * Helper function to get a Double annotation.
     * <p>
     * This function first checks if the Object in a map is a <code>Number</code>, and then calls doubleValue() on it
     * If it is not, then  Double.parseDouble() is called on the string representation of the object. If the string
     * is not parseable as a double, a NumberFormatException is thrown.
     */
    public Double getDouble(String key)  {
        Object o = getMap().get(key);
        if(o instanceof Number) {
            return ((Number)o).doubleValue();
        } else if(o == null) {
            return null;
        } else {
            return Double.parseDouble(o.toString());
        }
    }

    /**
     * Helper function to get a String from the Annotation. This function will simply call <code>toString()</code> on the
     * object saved in the Annotation or return null if the object is null;
     */
    public String getString(String key) {
        Object o = getMap().get(key);
        if(o == null) {
            return null;
        } else {
            return o.toString();
        }
    }

    /**
    * Helper function to get a Double annotation.
     * <p>
     * This function first checks if the Object in a map is a <code>Number</code>, and intValue() is called on it.
     * If it is not, then Double.parseDouble() is called on the string representation of the object. If the string
     * is not parseable as a double, a NumberFormatException is thrown.
     */
    public Integer getInteger(String key) {
        Object o = getMap().get(key);
        if(o == null) {
            return null;
        } else if(o instanceof Number) {
            return ((Number)o).intValue();
        } else {
            return Integer.parseInt(o.toString());
        }
    }

    /**
     * Helper function to get a Boolean annotation.
     */
    public Boolean getBoolean(String key) {
        Object o = getMap().get(key);
        if ( ! (o instanceof Boolean)) {
            return null;
        } else {
            return (Boolean)o;
        }
    }

}
