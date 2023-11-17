// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.serialization.SpanNodeReader;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * A node in a Span tree that can have child nodes.
 *
 * @author Einar M R Rosenvinge
 */
public class SpanList extends SpanNode {

    public static final byte ID = 2;
    private final List<SpanNode> children;
    private int cachedFrom = Integer.MIN_VALUE; // triggers calculateFrom()
    private int cachedTo = Integer.MIN_VALUE;  // triggers calculateTo()

    /** Creates a new SpanList. */
    public SpanList() {
        this.children = new LinkedList<SpanNode>();
    }

    public SpanList(SpanNodeReader reader) {
        this();
        reader.read(this);
    }

    protected SpanList(List<SpanNode> children) {
        this.children = children;
    }

    /**
     * Deep-copies a SpanList.
     *
     * @param other the SpanList to copy.
     */
    public SpanList(SpanList other) {
        this.children = new LinkedList<>();
        for (SpanNode otherNode : other.children) {
            if (otherNode instanceof Span) {
                children.add(new Span((Span) otherNode));
            } else if (otherNode instanceof AlternateSpanList) {
                children.add(new AlternateSpanList((AlternateSpanList) otherNode));
            } else if (otherNode instanceof SpanList) {
                children.add(new SpanList((SpanList) otherNode));
            } else if (otherNode instanceof DummySpanNode) {
                children.add(otherNode);  //shouldn't really happen
            } else {
                throw new IllegalStateException("Cannot create copy of " + otherNode + " with class "
                                                + ((otherNode == null) ? "null" : otherNode.getClass()));
            }
        }
    }

    void checkValidity(SpanNode node, List<SpanNode> childrenToCheck) {
        if (!node.isValid()) {
            throw new IllegalStateException("Cannot reuse SpanNode instance " + node + ", is INVALID.");
        }
        if (node.getParent() != null) {
            if (node.getParent() != this) {
                throw new IllegalStateException(node + " is already a child of " + node.getParent() + ", cannot be added to " + this);
            } else if (node.getParent() == this && childrenToCheck.contains(node)) {
                throw new IllegalStateException(node + " is already a child of " + this + ", cannot be added twice to the same parent node.");
            }
        }
    }

    /**
     * Adds a child node to this SpanList.
     *
     * @param node the node to add.
     * @return this, for call chaining
     * @throws IllegalStateException if SpanNode.isValid() returns false.
     */
    public SpanList add(SpanNode node) {
        checkValidity(node, children());
        node.setParent(this);
        resetCachedFromAndTo();
        children().add(node);
        return this;
    }

    /** Create a span, add it to this list and return it */
    public Span span(int from, int length) {
        Span span = new Span(from, length);
        add(span);
        return span;
    }

    void setInvalid() {
        // invalidate ourselves:
        super.setInvalid();
        // invalidate all our children:
        for (SpanNode node : children()) {
            node.setInvalid();
        }
    }

    /**
     * Moves a child of this SpanList to another SpanList.
     *
     * @param node the node to move
     * @param target the SpanList to add the node to
     * @throws IllegalArgumentException if the given node is not a child of this SpanList
     */
    public void move(SpanNode node, SpanList target) {
        boolean removed = children().remove(node);
        if (removed) {
            //we found the node
            node.setParent(null);
            resetCachedFromAndTo();
            target.add(node);
        } else {
            throw new IllegalArgumentException("Node " + node + " is not a child of this SpanList, cannot move.");
        }
    }

    /**
     * Moves a child of this SpanList to another SpanList.
     *
     * @param nodeNum the index of the node to move
     * @param target the SpanList to add the node to
     * @throws IndexOutOfBoundsException if the given index is out of range
     */
    public void move(int nodeNum, SpanList target) {
        SpanNode node = children().remove(nodeNum);
        if (node != null) {
            //we found the node
            node.setParent(null);
            resetCachedFromAndTo();
            target.add(node);
        }
    }

    /**
     * Moves a child of this SpanList to another SpanList.
     *
     * @param node the node to move
     * @param target the SpanList to add the node to
     * @param targetSubTree the index of the subtree of the given AlternateSpanList to add the node to
     * @throws IllegalArgumentException if the given node is not a child of this SpanList
     * @throws IndexOutOfBoundsException if the target subtree index is out of range
     */
    public void move(SpanNode node, AlternateSpanList target, int targetSubTree) {
        if (targetSubTree < 0 || targetSubTree >= target.getNumSubTrees()) {
            throw new IndexOutOfBoundsException(target + " has no subtree at index " + targetSubTree);
        }
        boolean removed = children().remove(node);
        if (removed) {
            //we found the node
            node.setParent(null);
            resetCachedFromAndTo();
            target.add(targetSubTree, node);
        } else {
            throw new IllegalArgumentException("Node " + node + " is not a child of this SpanList, cannot move.");
        }
    }

    /**
     * Moves a child of this SpanList to another SpanList.
     *
     * @param nodeNum the index of the node to move
     * @param target the SpanList to add the node to
     * @param targetSubTree the index of the subtree of the given AlternateSpanList to add the node to
     * @throws IndexOutOfBoundsException if the given index is out of range, or the target subtree index is out of range
     */
    public void move(int nodeNum, AlternateSpanList target, int targetSubTree) {
        if (targetSubTree < 0 || targetSubTree >= target.getNumSubTrees()) {
            throw new IndexOutOfBoundsException(target + " has no subtree at index " + targetSubTree);
        }
        SpanNode node = children().remove(nodeNum);
        if (node != null) {
            //we found the node
            node.setParent(null);
            resetCachedFromAndTo();
            target.add(targetSubTree, node);
        }
    }


    /**
     * Removes and invalidates the given SpanNode from this.
     *
     * @param node the node to remove.
     * @return this, for chaining.
     */
    public SpanList remove(SpanNode node) {
        boolean removed = children().remove(node);
        if (removed) {
            node.setParent(null);
            resetCachedFromAndTo();
            node.setInvalid();
        }
        return this;
    }

    /**
     * Removes and invalidates the SpanNode at the given index from this.
     *
     * @param i the index of the node to remove.
     * @return this, for chaining.
     */
    public SpanList remove(int i) {
        SpanNode node = children().remove(i);
        if (node != null) {
            node.setParent(null);
            node.setInvalid();
        }
        return this;
    }

    /** Returns a modifiable list of the immediate children of this SpanList. */
    protected List<SpanNode> children() {
        return children;
    }

    /** Returns the number of children this SpanList holds. */
    public int numChildren() {
        return children().size();
    }

    /**
     * Traverses all immediate children of this SpanList. The ListIterator returned support all optional operations
     * specified in the ListIterator interface.
     *
     * @return a ListIterator which traverses all immediate children of this SpanNode
     * @see java.util.ListIterator
     */
    @Override
    public ListIterator<SpanNode> childIterator() {
        return new InvalidatingIterator(this, children().listIterator());
    }

    /**
     * Recursively traverses all children (not only leaf nodes) of this SpanList, in a depth-first fashion.
     * The ListIterator only supports iteration forwards, and the optional operations that are implemented are
     * remove() and set(). add() is not supported.
     *
     * @return a ListIterator which recursively traverses all children and their children etc. of this SpanList.
     * @see java.util.ListIterator
     */
    @Override
    public ListIterator<SpanNode> childIteratorRecursive() {
        return new InvalidatingIterator(this, new RecursiveNodeIterator(children().listIterator()));
    }

    /** Removes and invalidates all references to child nodes. */
    public void clearChildren() {
        for (SpanNode node : children()) {
            node.setInvalid();
            node.setParent(null);
        }
        children().clear();
        resetCachedFromAndTo();
    }

    /**
     * Sorts children by occurrence in the text covered.
     *
     * @see SpanNode#compareTo(SpanNode)
     */
    public void sortChildren() {
        Collections.sort(children());
    }

    /**
     * Recursively sorts all children by occurrence in the text covered.
     */
    public void sortChildrenRecursive() {
        for (SpanNode node : children()) {
            if (node instanceof SpanList) {
                ((SpanList) node).sortChildrenRecursive();
            }
            Collections.sort(children());
        }
    }

    /**
     * Always returns false, even if this node has no children.
     *
     * @return always false, even if this node has no children
     */
    @Override
    public boolean isLeafNode() {
        return false;
    }

    private void calculateFrom() {
        int smallestFrom = Integer.MAX_VALUE;
        for (SpanNode n : children()) {
            final int thisFrom = n.getFrom();
            if (thisFrom != -1) {
                smallestFrom = Math.min(thisFrom, smallestFrom);
            }
        }
        if (smallestFrom == Integer.MAX_VALUE) {
            //all children were empty SpanLists which returned -1
            smallestFrom = -1;
        }
        cachedFrom = smallestFrom;
    }

    /**
     * Returns the character index where this {@link SpanNode} starts (inclusive), i.e.&nbsp;the smallest {@link com.yahoo.document.annotation.SpanNode#getFrom()} of all children.
     *
     * @return the lowest getFrom() of all children, or -1 if this SpanList has no children.
     */
    @Override
    public int getFrom() {
        if (children().isEmpty()) {
            return -1;
        }
        if (cachedFrom == Integer.MIN_VALUE) {
            calculateFrom();
        }
        return cachedFrom;
    }

    private void calculateTo() {
        int greatestTo = Integer.MIN_VALUE;
        for (SpanNode n : children()) {
            greatestTo = Math.max(n.getTo(), greatestTo);
        }
        cachedTo = greatestTo;
    }

    /**
     * Returns the character index where this {@link SpanNode} ends (exclusive), i.e.&nbsp;the greatest {@link com.yahoo.document.annotation.SpanNode#getTo()} of all children.
     *
     * @return the greatest getTo() of all children, or -1 if this SpanList has no children.
     */
    @Override
    public int getTo() {
        if (children().isEmpty()) {
            return -1;
        }
        if (cachedTo == Integer.MIN_VALUE) {
            calculateTo();
        }
        return cachedTo;
    }

    void resetCachedFromAndTo() {
        cachedFrom = Integer.MIN_VALUE;
        cachedTo = Integer.MIN_VALUE;
        if (getParent() instanceof SpanList) {
            ((SpanList) getParent()).resetCachedFromAndTo();
        }
    }

    /**
     * Returns the length of this span, i.e.&nbsp;getFrom() - getTo().
     *
     * @return the length of this span
     */
    @Override
    public int getLength() {
        return getTo() - getFrom();
    }

    /**
     * Returns the text that is covered by this SpanNode.
     *
     * @param text the input text
     * @return the text that is covered by this SpanNode.
     */
    @Override
    public CharSequence getText(CharSequence text) {
        if (children().isEmpty()) {
            return "";
        }
        StringBuilder str = new StringBuilder();
        for (SpanNode node : children()) {
            CharSequence childText = node.getText(text);
            if (childText != null) {
                str.append(node.getText(text));
            }
        }
        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpanList spanList)) return false;
        if (!super.equals(o)) return false;

        if (children() != null ? !children().equals(spanList.children()) : spanList.children() != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (children() != null ? children().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SpanList with " + children().size() + " children";
    }

}
