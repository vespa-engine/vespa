// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An immutable address to a tensor cell.
 * This is sparse: Only dimensions which have a different label than "undefined" are
 * explicitly included.
 * <p>
 * Tensor addresses are ordered by increasing size primarily, and by the natural order of the elements in sorted
 * order secondarily.
 *
 * @author bratseth
 */
@Beta
public final class TensorAddress implements Comparable<TensorAddress> {

    public static final TensorAddress empty = new TensorAddress.Builder().build();

    private final ImmutableList<Element> elements;

    /** Note that the elements list MUST be sorted before calling this */
    private TensorAddress(List<Element> elements) {
        this.elements = ImmutableList.copyOf(elements);
    }

    public static TensorAddress fromSorted(List<Element> elements) {
        return new TensorAddress(elements);
    }

    /**
     * Creates a tensor address from an unsorted list of elements.
     * This call assigns ownership of the elements list to this class.
     */
    public static TensorAddress fromUnsorted(List<Element> elements) {
        Collections.sort(elements);
        return new TensorAddress(elements);
    }

    /** Creates a tenor address from a string on the form {dimension1:label1,dimension2:label2,...} */
    public static TensorAddress from(String address) {
        address = address.trim();
        if ( ! (address.startsWith("{") && address.endsWith("}")))
            throw new IllegalArgumentException("Expecting a tensor address to be enclosed in {}, got '" + address + "'");

        String addressBody = address.substring(1, address.length() - 1).trim();
        if (addressBody.isEmpty()) return TensorAddress.empty;

        List<Element> elements = new ArrayList<>();
        for (String elementString : addressBody.split(",")) {
            String[] pair = elementString.split(":");
            if (pair.length != 2)
                throw new IllegalArgumentException("Expecting argument elements to be on the form dimension:label, " +
                                                   "got '" + elementString + "'");
            elements.add(new Element(pair[0].trim(), pair[1].trim()));
        }
        Collections.sort(elements);
        return TensorAddress.fromSorted(elements);
    }

    /** Creates an empty address with a set of dimensions */
    public static TensorAddress emptyWithDimensions(Set<String> dimensions) {
        List<Element> elements = new ArrayList<>(dimensions.size());
        for (String dimension : dimensions)
            elements.add(new Element(dimension, Element.undefinedLabel));
        return TensorAddress.fromUnsorted(elements);
    }

    /** Returns an immutable list of the elements of this address in sorted order */
    public List<Element> elements() { return elements; }

    /** Returns true if this address has a value (other than implicit "undefined") for the given dimension */
    public boolean hasDimension(String dimension) {
        for (TensorAddress.Element element : elements)
            if (element.dimension().equals(dimension))
                return true;
        return false;
    }

    /** Returns a possibly immutable set of the dimensions of this */
    public Set<String> dimensions() {
        Set<String> dimensions = new HashSet<>();
        for (Element e : elements)
            dimensions.add(e.dimension());
        return dimensions;
    }

    @Override
    public int compareTo(TensorAddress other) {
        int sizeComparison = Integer.compare(this.elements.size(), other.elements.size());
        if (sizeComparison != 0) return sizeComparison;

        for (int i = 0; i < elements.size(); i++) {
            int elementComparison = this.elements.get(i).compareTo(other.elements.get(i));
            if (elementComparison != 0) return elementComparison;
        }

        return 0;
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof TensorAddress)) return false;
        return ((TensorAddress)other).elements.equals(this.elements);
    }

    /** Returns this on the form {dimension1:label1,dimension2:label2,... */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("{");
        for (TensorAddress.Element element : elements) {
            //if (element.label() == Element.undefinedLabel) continue;
            b.append(element.toString());
            b.append(",");
        }
        if (b.length() > 1)
            b.setLength(b.length() - 1);
        b.append("}");
        return b.toString();
    }

    /** A tensor address element. Elements have the lexical order of the dimensions as natural order. */
    public static class Element implements Comparable<Element> {

        static final String undefinedLabel = "-";

        private final String dimension;
        private final String label;
        private final int hashCode;

        public Element(String dimension, String label) {
            this.dimension = dimension;
            if (label.equals(undefinedLabel))
                this.label = undefinedLabel;
            else
                this.label = label;
            this.hashCode = dimension.hashCode() + label.hashCode();
        }

        public String dimension() { return dimension; }

        public String label() { return label; }

        @Override
        public int compareTo(Element other) {
            return this.dimension.compareTo(other.dimension);
        }

        @Override
        public int hashCode() { return hashCode; }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Element)) return false;
            Element other = (Element)o;
            if ( ! other.dimension.equals(this.dimension)) return false;
            if ( ! other.label.equals(this.label)) return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append(dimension).append(":").append(label);
            return b.toString();
        }

    }

    /** Supports building of a tensor address */
    public static class Builder {

        private final List<Element> elements = new ArrayList<>();

        /**
         * Adds a label in a dimension to this.
         *
         * @return this for convenience
         */
        public Builder add(String dimension, String label) {
            elements.add(new Element(dimension, label));
            return this;
        }

        public TensorAddress build() {
            Collections.sort(elements); // Consistent order to get a consistent hash
            return TensorAddress.fromSorted(elements);
        }

    }

}
