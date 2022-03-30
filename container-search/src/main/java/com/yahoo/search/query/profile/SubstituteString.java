// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.Properties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;

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
    private final boolean hasRelative;

    /**
     * Returns a new SubstituteString if the given string contains substitutions, null otherwise.
     */
    public static SubstituteString create(String value) {
        int lastEnd = 0;
        int start = value.indexOf("%{");
        if (start < 0) return null; // Shortcut
        List<Component> components = new ArrayList<>();
        while (start >= 0) {
            int end = value.indexOf("}", start + 2);
            if (end < 0)
                throw new IllegalArgumentException("Unterminated value substitution '" + value.substring(start) + "'");
            String propertyName = value.substring(start + 2, end);
            if (propertyName.contains("%{"))
                throw new IllegalArgumentException("Unterminated value substitution '" + value.substring(start) + "'");
            components.add(new StringComponent(value.substring(lastEnd, start)));
            if (propertyName.startsWith("."))
                components.add(new RelativePropertyComponent(propertyName.substring(1)));
            else
                components.add(new PropertyComponent(propertyName));
            lastEnd = end + 1;
            start = value.indexOf("%{", lastEnd);
        }
        components.add(new StringComponent(value.substring(lastEnd)));
        return new SubstituteString(components, value);
    }

    public SubstituteString(List<Component> components, String stringValue) {
        this.components = components;
        this.stringValue = stringValue;
        this.hasRelative = components.stream().anyMatch(component -> component instanceof RelativePropertyComponent);
    }

    /** Returns whether this has at least one relative component */
    public boolean hasRelative() { return hasRelative; }

    /**
     * Perform the substitution in this, by looking up in the given properties,
     * and returns the resulting string
     *
     * @param context the content which is used to resolve profile variants when looking up substitution values
     * @param substitution the properties in which values to be substituted are looked up
     */
    public Object substitute(Map<String, String> context, Properties substitution) {
        StringBuilder b = new StringBuilder();
        for (Component component : components)
            b.append(component.getValue(context, substitution));
        return b.toString();
    }

    public List<Component> components() { return components; }

    public String stringValue() { return stringValue; }

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

    public abstract static class Component {

        protected abstract String getValue(Map<String, String> context, Properties substitution);

    }

    public final static class StringComponent extends Component {

        private final String value;

        public StringComponent(String value) {
            this.value = value;
        }

        @Override
        public String getValue(Map<String, String> context, Properties substitution) {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }

    }

    public final static class PropertyComponent extends Component {

        private final String propertyName;

        public PropertyComponent(String propertyName) {
            this.propertyName = propertyName;
        }

        @Override
        public String getValue(Map<String, String> context, Properties substitution) {
            Object value = substitution.get(propertyName, context, substitution);
            if (value == null) return "";
            return String.valueOf(value);
        }

        @Override
        public String toString() {
            return "%{" + propertyName + "}";
        }

    }

    /**
     * A component where the value should be looked up in the profile containing the substitution field
     * rather than globally
     */
    public final static class RelativePropertyComponent extends Component {

        private final String fieldName;

        public RelativePropertyComponent(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        public String getValue(Map<String, String> context, Properties substitution) {
            throw new IllegalStateException("Should be resolved during compilation");
        }

        public String fieldName() { return fieldName; }

        @Override
        public String toString() {
            return "%{" + fieldName + "}";
        }

    }

}
