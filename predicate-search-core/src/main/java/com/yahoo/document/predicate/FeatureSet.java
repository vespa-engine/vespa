// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Simon Thoresen Hult
 */
public class FeatureSet extends PredicateValue {

    private Set<String> values;
    private String key;

    public FeatureSet(String key, String... values) {
        this(key, Arrays.asList(values));
    }

    public FeatureSet(String key, Collection<String> values) {
        Objects.requireNonNull(key, "key");
        if (values == null) {
            throw new NullPointerException("values");
        }
        this.key = key;
        this.values = new TreeSet<>(values);
    }

    public FeatureSet setKey(String key) {
        Objects.requireNonNull(key, "key");
        this.key = key;
        return this;
    }

    public String getKey() {
        return key;
    }

    public FeatureSet addValue(String value) {
        Objects.requireNonNull(value, "value");
        values.add(value);
        return this;
    }

    public FeatureSet addValues(Collection<String> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }
        this.values.addAll(values);
        return this;
    }

    public FeatureSet setValues(Collection<String> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }
        this.values.clear();
        this.values.addAll(values);
        return this;
    }

    public Set<String> getValues() {
        return values;
    }

    @Override
    public FeatureSet clone() throws CloneNotSupportedException {
        FeatureSet obj = (FeatureSet)super.clone();
        obj.values = new TreeSet<>(values);
        return obj;
    }

    @Override
    public int hashCode() {
        return (key.hashCode() + values.hashCode()) * 31;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof FeatureSet)) {
            return false;
        }
        FeatureSet rhs = (FeatureSet)obj;
        if (!key.equals(rhs.key)) {
            return false;
        }
        if (!values.equals(rhs.values)) {
            return false;
        }
        return true;
    }

    @Override
    protected void appendTo(StringBuilder out) {
        appendInAsTo("in", out);
    }

    protected void appendNegatedTo(StringBuilder out) {
        appendInAsTo("not in", out);
    }

    private void appendInAsTo(String in, StringBuilder out) {
        appendQuotedTo(key, out);
        out.append(' ').append(in).append(" [");
        for (Iterator<String> it = values.iterator(); it.hasNext(); ) {
            appendQuotedTo(it.next(), out);
            if (it.hasNext()) {
                out.append(", ");
            }
        }
        out.append("]");
    }

}
