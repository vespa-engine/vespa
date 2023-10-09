// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

import java.util.ListIterator;

/**
 * Base class of nodes in a Span tree.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public abstract class SpanNode implements Comparable<SpanNode>, SpanNodeParent {

    private boolean valid = true;
    /**
     * This scratch id is used to avoid using IdentityHashMaps as they are very costly.
     */
    private int scratchId = -1;
    private SpanNodeParent parent;

    protected SpanNode() {
    }

    /**
     * Returns whether this node is valid or not.&nbsp;When a child node from a SpanList, the child
     * is marked as invalid, and the reference to it is removed from the parent SpanList. However,
     * Annotations in the global list kept in SpanTree may still have references to the removed SpanNode.
     * Removing these references is costly, and is only done when calling {@link com.yahoo.document.annotation.SpanTree#cleanup()}.
     *
     * @return true if this node is valid, false otherwise.
     */
    public boolean isValid() {
        return valid;
    }

    void setInvalid() {
        valid = false;
    }

    public void setScratchId(int id) {
        scratchId = id;
    }

    public int getScratchId() {
        return scratchId;
    }
    /**
     * Returns the parent node of this SpanNode, if any.
     *
     * @return the parent node, or null if this is not yet added to a parent SpanList
     */
    public SpanNodeParent getParent() {
        return parent;
    }

    void setParent(SpanNodeParent parent) {
        this.parent = parent;
    }

    /**
     * Returns the SpanTree that this node belongs to, if any.
     *
     * @return the SpanTree that this node belongs to, or null if it is not yet added to a SpanTree.
     */
    @Override
    public SpanTree getSpanTree() {
        if (parent == null) {
            return null;
        }
        return parent.getSpanTree();
    }

    /** Returns the SpanTree this belongs to and throws a nice NullPointerException if none */
    private SpanTree getNonNullSpanTree() {
        SpanTree spanTree=getSpanTree();
        if (spanTree==null)
            throw new NullPointerException(this + " is not attached to a SpanTree through its parent yet");
        return spanTree;
    }


    /**
     * Convenience method for adding an annotation to this span, same as
     * <code>getSpanTree().{@link SpanTree#annotate(SpanNode,Annotation) spanTree.annotate(this,annotation)}</code>
     *
     * @param annotation the annotation to add
     * @return this for chaining
     * @throws NullPointerException if this span is not attached to a tree
     */
    public SpanNode annotate(Annotation annotation) {
        getNonNullSpanTree().annotate(this, annotation);
        return this;
    }

    /**
     * Convenience method for adding an annotation to this span, same as
     * <code>getSpanTree().{@link SpanTree#annotate(SpanNode,AnnotationType,FieldValue) spanTree.annotate(this,type,value)}</code>
     *
     * @param type the type of the annotation to add
     * @param value the value of the annotation to add
     * @return this for chaining
     * @throws NullPointerException if this span is not attached to a tree
     */
    public SpanNode annotate(AnnotationType type,FieldValue value) {
        getNonNullSpanTree().annotate(this,type,value);
        return this;
    }

    /**
     * Convenience method for adding an annotation to this span, same as
     * <code>getSpanTree().{@link SpanTree#annotate(SpanNode,AnnotationType,FieldValue) spanTree.annotate(this,type,new StringFieldValue(value))}</code>
     *
     * @param type the type of the annotation to add
     * @param value the string value of the annotation to add
     * @return this for chaining
     * @throws NullPointerException if this span is not attached to a tree
     */
    public SpanNode annotate(AnnotationType type,String value) {
        getNonNullSpanTree().annotate(this, type, new StringFieldValue(value));
        return this;
    }

    /**
     * Convenience method for adding an annotation to this span, same as
     * <code>getSpanTree().{@link SpanTree#annotate(SpanNode,AnnotationType,FieldValue) spanTree.annotate(this,type,new IntegerFieldValue(value))}</code>
     *
     * @param type the type of the annotation to add
     * @param value the integer value of the annotation to add
     * @return this for chaining
     * @throws NullPointerException if this span is not attached to a tree
     */
    public SpanNode annotate(AnnotationType type,Integer value) {
        getNonNullSpanTree().annotate(this, type, new IntegerFieldValue(value));
        return this;
    }

    /**
     * Convenience method for adding an annotation with no value to this span, same as
     * <code>getSpanTree().{@link SpanTree#annotate(SpanNode,AnnotationType) spanTree.annotate(this,type)}</code>
     *
     * @param type the type of the annotation to add
     * @return this for chaining
     * @throws NullPointerException if this span is not attached to a tree
     */
    public SpanNode annotate(AnnotationType type) {
        getNonNullSpanTree().annotate(this,type);
        return this;
    }

    /**
     * Returns the StringFieldValue that this node belongs to, if any.
     *
     * @return the StringFieldValue that this node belongs to, if any, otherwise null.
     */
    @Override
    public StringFieldValue getStringFieldValue() {
        if (parent == null) {
            return null;
        }
        return parent.getStringFieldValue();
    }

    /**
     * Returns true if this node is a leaf node in the tree.
     *
     * @return true if this node is a leaf node in the tree.
     */
    public abstract boolean isLeafNode();

    /**
     * Traverses all immediate children of this SpanNode.
     *
     * @return a ListIterator which traverses all immediate children of this SpanNode
     */
    public abstract ListIterator<SpanNode> childIterator();

    /**
     * Recursively traverses all possible children (not only leaf nodes) of this SpanNode, in a depth-first fashion.
     *
     * @return a ListIterator which recursively traverses all children and their children etc. of this SpanNode.
     */
    public abstract ListIterator<SpanNode> childIteratorRecursive();

    /**
     * Returns the character index where this SpanNode starts (inclusive).
     *
     * @return the character index where this SpanNode starts (inclusive).
     */
    public abstract int getFrom();

    /**
     * Returns the character index where this SpanNode ends (exclusive).
     *
     * @return the character index where this SpanNode ends (exclusive).
     */
    public abstract int getTo();

    /**
     * Returns the length of this span, i.e.&nbsp;getFrom() - getTo().
     *
     * @return the length of this span
     */
    public abstract int getLength();

    /**
     * Returns the text that is covered by this SpanNode.
     *
     * @param text the input text
     * @return the text that is covered by this SpanNode.
     */
    public abstract CharSequence getText(CharSequence text);

    /**
     * Checks if the text covered by this span overlaps with the text covered by another span.
     *
     * @param o the other SpanNode to check
     * @return true if spans are overlapping, false otherwise
     */
    public boolean overlaps(SpanNode o) {
        int from = getFrom();
        int otherFrom = o.getFrom();
        int to = getTo();
        int otherTo = o.getTo();

        //is other from within our range, or vice versa?
        if ((otherFrom >= from && otherFrom < to)
            || (from >= otherFrom && from < otherTo)) {
            return true;
        }

        //is other to within our range, or vice versa?
        if ((otherTo > from && otherTo <= to)
            || (to > otherFrom && to <= otherTo)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the text covered by another span is within the text covered by this span.
     *
     * @param o the other SpanNode to check.
     * @return true if the text covered by another span is within the text covered by this span, false otherwise.
     */
    public boolean contains(SpanNode o) {
        int from = getFrom();
        int otherFrom = o.getFrom();
        int to = getTo();
        int otherTo = o.getTo();

        if (otherFrom >= from && otherTo <= to) {
            //other span node is within our range:
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpanNode)) return false;

        SpanNode spanNode = (SpanNode) o;

        if (getFrom() != spanNode.getFrom()) return false;
        if (getTo() != spanNode.getTo()) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = getFrom();
        result = 31 * result + getTo();
        return result;
    }

    /**
     * Compares two SpanNodes.&nbsp;Note: this class has a natural ordering that <strong>might be</strong> inconsistent with equals.
     * <p>
     * First, getFrom() is compared, and -1 or 1 is return if our getFrom() is smaller or greater that o.getFrom(), respectively.
     * If and only if getFrom() is equal, getTo() is compared, and  -1 or 1 is return if our getTo() is smaller or greater that o.getTo(), respectively.
     * In all other cases, the two SpanNodes are equal both for getFrom() and getTo(), and 0 is returned.
     * <p>
     * Note that if a subclass has overridden equals(), which is very likely, but has not overridden compareTo(), then that subclass
     * will have a natural ordering that is inconsistent with equals.
     *
     * @param o the SpanNode to compare to
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object
     */
    @Override
    public int compareTo(SpanNode o) {
        int from = getFrom();
        int otherFrom = o.getFrom();

        if (from < otherFrom) {
            return -1;
        }
        if (from > otherFrom) {
            return 1;
        }

        //so from is equal. Check to:
        int to = getTo();
        int otherTo = o.getTo();

        if (to < otherTo) {
            return -1;
        }
        if (to > otherTo) {
            return 1;
        }

        //both from and to are equal
        return 0;
    }
}
