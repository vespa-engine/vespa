// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A string which contains one or more elements of the form %{name},
 * where these occurrences are to be replaced by a query profile lookup on name.
 * <p>
 * This objects does the analysis on creation and provides a (reasonably) fast method of
 * performing the actual substitution (at lookup time).
 * <p>
 * This is a value object. Lookups in this are thread safe.
 *
 * @author bratseth
 */
public class SubstituteString {

    private final List<Component> components;
    private final String stringValue;

    /**
     * Returns a new SubstituteString if the given string contains substitutions, null otherwise.
     */
    public static SubstituteString create(String value) {
        int lastEnd=0;
        int start=value.indexOf("%{");
        if (start<0) return null; // Shortcut
        List<Component> components=new ArrayList<>();
        while (start>=0) {
            int end=value.indexOf("}",start+2);
            if (end<0)
                throw new IllegalArgumentException("Unterminated value substitution '" + value.substring(start) + "'");
            String propertyName=value.substring(start+2,end);
            if (propertyName.indexOf("%{")>=0)
                throw new IllegalArgumentException("Unterminated value substitution '" + value.substring(start) + "'");
            components.add(new StringComponent(value.substring(lastEnd,start)));
            components.add(new PropertyComponent(propertyName));
            lastEnd=end+1;
            start=value.indexOf("%{",lastEnd);
        }
        components.add(new StringComponent(value.substring(lastEnd,value.length())));
        return new SubstituteString(components, value);
    }

    private SubstituteString(List<Component> components, String stringValue) {
        this.components = components;
        this.stringValue = stringValue;
    }

    /**
     * Perform the substitution in this, by looking up in the given query profile,
     * and returns the resulting string
     */
    public String substitute(Map<String, String> context, Properties substitution) {
        StringBuilder b = new StringBuilder();
        for (Component component : components)
            b.append(component.getValue(context,substitution));
        return b.toString();
    }

    @Override
    public int hashCode() {
        return stringValue.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof SubstituteString)) return false;
        return this.stringValue.equals(((SubstituteString)other).stringValue);
    }

    /** Returns this string in original (unsubstituted) form */
    @Override
    public String toString() {
        return stringValue;
    }

    private abstract static class Component {

        protected abstract String getValue(Map<String,String> context,Properties substitution);

    }

    private final static class StringComponent extends Component {

        private final String value;

        public StringComponent(String value) {
            this.value=value;
        }

        @Override
        public String getValue(Map<String,String> context,Properties substitution) {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }

    }

    private final static class PropertyComponent extends Component {

        private final String propertyName;

        public PropertyComponent(String propertyName) {
            this.propertyName=propertyName;
        }

        @Override
        public String getValue(Map<String,String> context,Properties substitution) {
            Object value=substitution.get(propertyName,context,substitution);
            if (value==null) return "";
            return String.valueOf(value);
        }

        @Override
        public String toString() {
            return "%{" + propertyName + "}";
        }

    }

}
