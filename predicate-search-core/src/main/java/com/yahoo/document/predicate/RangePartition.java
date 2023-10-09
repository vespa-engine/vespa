// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

/**
 * @author Magnar Nedland
 */
public class RangePartition extends PredicateValue {

    private String label;

    public RangePartition(String label) {
        this.label = label;
    }

    public RangePartition(String key, long fromInclusive, long toInclusive, boolean isNeg) {
        this(makeLabel(key, fromInclusive, toInclusive, isNeg));
    }

    private static String makeLabel(String key, long fromInclusive, long toInclusive, boolean isNeg) {
        if (isNeg) {
            // special case for toInclusive==long_min: It will print its own hyphen.
            return key + "=" + (toInclusive==0x8000000000000000L? "" : "-") + toInclusive + "-" + fromInclusive;
        } else {
            // special case for toInclusive==long_min: It will print its own hyphen.
            return key + "=" + fromInclusive + (toInclusive==0x8000000000000000L? "" : "-") + toInclusive;
        }
    }

    public String getLabel() {
        return label;
    }

    @Override
    public RangePartition clone() throws CloneNotSupportedException {
        return (RangePartition)super.clone();
    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof RangePartition)) {
            return false;
        }
        return label.equals(((RangePartition)obj).getLabel());
    }

    @Override
    protected void appendTo(StringBuilder out) {
        int i = label.lastIndexOf('=');
        appendQuotedTo(label.substring(0, i), out);
        if (out.charAt(out.length() - 1) == '\'') {
            out.deleteCharAt(out.length() - 1);
            out.append(label.substring(i));
            out.append('\'');
        } else {
            out.append(label.substring(i));
        }
    }

}
