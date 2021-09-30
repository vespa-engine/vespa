// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.serialization.SpanNodeReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * A node in a {@link SpanNode} tree that can have a <strong>multiple</strong> trees of child nodes, each with its own probability.
 * This class has quite a few convenience methods for accessing the <strong>first</strong> subtree.
 *
 * @author Einar M R Rosenvinge
 * @see com.yahoo.document.annotation.SpanList
 */
public class AlternateSpanList extends SpanList {

    public static final byte ID = 4;

    private final List<Children> childTrees = new LinkedList<>();
    private static final Comparator<Children> childComparator = new ProbabilityComparator();

    /** Create a new AlternateSpanList instance, having a single subtree with probability 1.0. */
    public AlternateSpanList() {
        super((List<SpanNode>) null);
        ensureAtLeastOneSubTree();
    }

    /**
     * Deep-copies another AlternateSpanList.
     *
     * @param otherSpanList the instance to deep-copy.
     */
    public AlternateSpanList(AlternateSpanList otherSpanList) {
        super((List<SpanNode>) null);
        for (Children otherSubtree : otherSpanList.childTrees) {
            //create our own subtree:
            Children children = new Children(this);
            //copy nodes:
            for (SpanNode otherNode : otherSubtree.children()) {
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
            //add this subtree to our subtrees:
            childTrees.add(children);
        }
    }

    public AlternateSpanList(SpanNodeReader reader) {
        this();
        reader.read(this);
    }

    private void ensureAtLeastOneSubTree() {
        if (childTrees.isEmpty()) {
            childTrees.add(new Children(getParent()));
        }
    }

    /**
     * Adds a child node to the <strong>first</strong> subtree of this AlternateSpanList. Note
     * that it might be a good idea to call {@link #sortSubTreesByProbability()} first.
     *
     * @param node the node to add.
     * @return this, for call chaining
     */
    @Override
    public AlternateSpanList add(SpanNode node) {
        return add(0, node);
    }

    /**
     * Sorts the subtrees under this AlternateSpanList by descending probability, such that the most probable
     * subtree becomes the first subtree, and so on.
     */
    public void sortSubTreesByProbability() {
        resetCachedFromAndTo();
        Collections.sort(childTrees, childComparator);
    }

    /**
     * Returns a modifiable {@link List} of child nodes of <strong>first</strong> subtree.
     *
     * @return a modifiable {@link List} of child nodes of <strong>first</strong> subtree
     */
    @Override
    protected List<SpanNode> children() {
        return children(0);
    }

    /**
     * Returns the number of subtrees under this node.
     *
     * @return the number of subtrees under this node.
     */
    public int getNumSubTrees() {
        return childTrees.size();
    }

    /** Clears all subtrees (the subtrees themselves are kept, but their contents are cleared and become invalidated). */
    @Override
    public void clearChildren() {
        for (Children c : childTrees) {
            c.clearChildren();
        }
    }

    /**
     * Clears a given subtree (the subtree itself is kept, but its contents are cleared and become invalidated).
     *
     * @param i the index of the subtree to clear
     */
    public void clearChildren(int i) {
        Children c = childTrees.get(i);
        if (c != null) {
            c.clearChildren();
        }
    }

    /**
     * Sorts children in <strong>all</strong> subtrees by occurrence in the text covered.
     *
     * @see SpanNode#compareTo(SpanNode)
     */
    @Override
    public void sortChildren() {
        for (Children children : childTrees) {
            Collections.sort(children.children());
        }
    }

    /**
     * Sorts children in subtree i by occurrence in the text covered.
     *
     * @param i the index of the subtree to sort
     * @see SpanNode#compareTo(SpanNode)
     */
    public void sortChildren(int i) {
        Children children = childTrees.get(i);
        Collections.sort(children.children());
    }

    /**
     * Recursively sorts all children in <strong>all</strong> subtrees by occurrence in the text covered.
     */
    public void sortChildrenRecursive() {
        for (Children children : childTrees) {
            for (SpanNode node : children.children()) {
                if (node instanceof SpanList) {
                    ((SpanList) node).sortChildrenRecursive();
                }
            }
            Collections.sort(children.children());
        }
    }

    /**
     * Recursively sorts all children in subtree i by occurrence in the text covered.
     *
     * @param i the index of the subtree to sort recursively
     */
    public void sortChildrenRecursive(int i) {
        Children children = childTrees.get(i);
        for (SpanNode node : children.children()) {
            if (node instanceof SpanList) {
                ((SpanList) node).sortChildrenRecursive();
            }
        }
        Collections.sort(children.children());
    }


    /**
     * Moves a child of this SpanList to another SpanList.
     *
     * @param i the index of the subtree to remove the node from
     * @param node the node to move
     * @param target the SpanList to add the node to
     * @throws IllegalArgumentException if the given node is not a child of this SpanList
     */
    public void move(int i, SpanNode node, SpanList target) {
        boolean removed = children(i).remove(node);
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
     * @param i the index of the subtree to remove the node from
     * @param nodeNum the index of the node to move
     * @param target the SpanList to add the node to
     * @throws IndexOutOfBoundsException if the given index is out of range
     */
    public void move(int i, int nodeNum, SpanList target) {
        SpanNode node = children(i).remove(nodeNum);
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
     * @param i the index of the subtree to remove the node from
     * @param node the node to move
     * @param target the SpanList to add the node to
     * @param targetSubTree the index of the subtree of the given AlternateSpanList to add the node to
     * @throws IllegalArgumentException if the given node is not a child of this SpanList
     * @throws IndexOutOfBoundsException if the given index is out of range, or if the target subtree index is out of range
     */
    public void move(int i, SpanNode node, AlternateSpanList target, int targetSubTree) {
        if (targetSubTree < 0 || targetSubTree >= target.getNumSubTrees()) {
            throw new IndexOutOfBoundsException(target + " has no subtree at index " + targetSubTree);
        }
        boolean removed = children(i).remove(node);
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
     * @param i the index of the subtree to remove the node from
     * @param nodeNum the index of the node to move
     * @param target the SpanList to add the node to
     * @param targetSubTree the index of the subtree of the given AlternateSpanList to add the node to
     * @throws IndexOutOfBoundsException if any of the given indeces are out of range, or the target subtree index is out of range
     */
    public void move(int i, int nodeNum, AlternateSpanList target, int targetSubTree) {
        if (targetSubTree < 0 || targetSubTree >= target.getNumSubTrees()) {
            throw new IndexOutOfBoundsException(target + " has no subtree at index " + targetSubTree);
        }
        SpanNode node = children(i).remove(nodeNum);
        if (node != null) {
            //we found the node
            node.setParent(null);
            resetCachedFromAndTo();
            target.add(targetSubTree, node);
        }
    }

    /**
     * Traverses all immediate children of all subtrees of this AlternateSpanList.
     * The ListIterator only supports iteration forwards, and the optional operations that are implemented are
     * remove() and set(). add() is not supported.
     *
     * @return a ListIterator which traverses all immediate children of this SpanNode
     * @see java.util.ListIterator
     */
    @Override
    public ListIterator<SpanNode> childIterator() {
        List<ListIterator<SpanNode>> childIterators = new ArrayList<ListIterator<SpanNode>>();
        for (Children ch : childTrees) {
            childIterators.add(ch.childIterator());
        }
        return new SerialIterator(childIterators);
    }

    /**
     * Recursively traverses all children (not only leaf nodes) of all subtrees of this AlternateSpanList, in a
     * depth-first fashion.
     * The ListIterator only supports iteration forwards, and the optional operations that are implemented are
     * remove() and set(). add() is not supported.
     *
     * @return a ListIterator which recursively traverses all children and their children etc. of all subtrees of this AlternateSpanList
     * @see java.util.ListIterator
     */
    @Override
    public ListIterator<SpanNode> childIteratorRecursive() {
        List<ListIterator<SpanNode>> childIterators = new ArrayList<ListIterator<SpanNode>>();
        for (Children ch : childTrees) {
            childIterators.add(ch.childIteratorRecursive());
        }
        return new SerialIterator(childIterators);
    }

    /**
     * Traverses all immediate children of the given subtree of this AlternateSpanList.
     * The ListIterator returned supports all optional operations
     * specified in the ListIterator interface.
     *
     * @param i the index of the subtree to iterate over
     * @return a ListIterator which traverses all immediate children of this SpanNode
     * @see java.util.ListIterator
     */
    public ListIterator<SpanNode> childIterator(int i) {
        return childTrees.get(i).childIterator();
    }

    /**
     * Recursively traverses all children (not only leaf nodes) of the given subtree of this AlternateSpanList, in a
     * depth-first fashion.
     * The ListIterator only supports iteration forwards, and the optional operations that are implemented are
     * remove() and set(). add() is not supported.
     *
     * @param i the index of the subtree to iterate over
     * @return a ListIterator which recursively traverses all children and their children etc. of the given subtree of this AlternateSpanList.
     * @see java.util.ListIterator
     */
    public ListIterator<SpanNode> childIteratorRecursive(int i) {
        return childTrees.get(i).childIteratorRecursive();
    }

    public int numChildren(int i) {
        return children(i).size();
    }

    /**
     * Returns a modifiable {@link List} of child nodes of the specified subtree.
     *
     * @param i the index of the subtree to search
     * @return a modifiable {@link List} of child nodes of the specified subtree
     */
    protected List<SpanNode> children(int i) {
        return childTrees.get(i).children();
    }

    @Override
    void setParent(SpanNodeParent parent) {
        super.setParent(parent);
        for (Children ch : childTrees) {
            ch.setParent(parent);
        }
    }

    /**
     * Adds a possible subtree of this AlternateSpanList, with the given probability. Note that the first subtree is
     * always available through the use of children(), so this method is only used for adding the second or higher
     * subtree.
     *
     * @param subtree     the subtree to add
     * @param probability the probability of this subtree
     * @return true if successful
     * @see #children()
     */
    public boolean addChildren(List<SpanNode> subtree, double probability) {
        Children childTree = new Children(getParent(), subtree, probability);
        resetCachedFromAndTo();
        return childTrees.add(childTree);

    }

    /**
     * Adds a possible subtree of this AlternateSpanList, with the given probability, at index i. Note that the first subtree is
     * always available through the use of children(), so this method is only used for adding the second or higher
     * subtree.
     *
     * @param i           the index of where to insert the subtree
     * @param subtree     the subtree to add
     * @param probability the probability of this subtree
     * @see #children()
     */
    public void addChildren(int i, List<SpanNode> subtree, double probability) {
        Children childTree = new Children(getParent(), subtree, probability);
        resetCachedFromAndTo();
        childTrees.add(i, childTree);
    }

    /**
     * Removes the subtree at index i (both the subtree itself and its contents, which become invalidated).
     * Note that if this AlternateSpanList has only one subtree and index 0 is given,
     * a new empty subtree is automatically added, since an AlternateSpanList always has at least one subtree.
     *
     * @param i the index of the subtree to remove
     * @return the subtree removed, if any (note: invalidated)
     */
    public List<SpanNode> removeChildren(int i) {
        Children retval = childTrees.remove(i);
        ensureAtLeastOneSubTree();
        resetCachedFromAndTo();
        if (retval != null) {
            retval.setInvalid();
            retval.setParent(null);
            for (SpanNode node : retval.children()) {
                node.setParent(null);
            }
            return retval.children();
        }
        return null;
    }

    /**
     * Removes all subtrees (both the subtrees themselves and their contents, which become invalidated).
     * Note that a new empty subtree is automatically added at index 0, since an AlternateSpanList always has at
     * least one subtree.
     */
    public void removeChildren() {
        for (Children ch : childTrees) {
            ch.setInvalid();
            ch.setParent(null);
            ch.clearChildren();
        }
        childTrees.clear();
        resetCachedFromAndTo();
        ensureAtLeastOneSubTree();
    }

    @Override
    void setInvalid() {
        //invalidate ourselves:
        super.setInvalid();
        //invalidate all child trees
        for (Children ch : childTrees) {
            ch.setInvalid();
        }
    }

    /**
     * Sets the subtree at index i.
     *
     * @param i           the index of where to set the subtree
     * @param subtree     the subtree to set
     * @param probability the probability to set
     * @return the overwritten subtree, if any
     */
    public List<SpanNode> setChildren(int i, List<SpanNode> subtree, double probability) {
        resetCachedFromAndTo();
        if (childTrees.size() == 1 && i == 0) {
            //replace the first subtree
            Children sub = new Children(getParent(), subtree, probability);
            Children retval = childTrees.set(i, sub);
            if (retval == null) {
                return null;
            } else {
                retval.setParent(null);
                for (SpanNode node : retval.children()) {
                    node.setParent(null);
                }
                return retval.children();
            }
        }
        List<SpanNode> retval = removeChildren(i);
        addChildren(i, subtree, probability);
        return retval;
    }

    /**
     * Returns the character index where this {@link SpanNode} starts (inclusive), i.e.&nbsp;the smallest {@link com.yahoo.document.annotation.SpanNode#getFrom()} of all children in subtree i.
     *
     * @param i the index of the subtree to use
     * @return the lowest getFrom() of all children in subtree i, or -1 if this SpanList has no children in subtree i.
     * @throws IndexOutOfBoundsException if this AlternateSpanList has no subtree i
     */
    public int getFrom(int i) {
        return childTrees.get(i).getFrom();
    }


    /**
     * Returns the character index where this {@link SpanNode} ends (exclusive), i.e.&nbsp;the greatest {@link com.yahoo.document.annotation.SpanNode#getTo()} of all children in subtree i.
     *
     * @param i the index of the subtree to use
     * @return the greatest getTo() of all children, or -1 if this SpanList has no children in subtree i.
     * @throws IndexOutOfBoundsException if this AlternateSpanList has no subtree i
     */
    public int getTo(int i) {
        return childTrees.get(i).getTo();
    }

    /**
     * Returns the length of this span according to subtree i, i.e.&nbsp;getFrom(i) - getTo(i).
     *
     * @param i the index of the subtree to use
     * @return the length of this span according to subtree i
     */
    public int getLength(int i) {
        return getTo(i) - getFrom(i);
    }

    /**
     * Returns the text covered by this span as given by subtree i, or null if subtree i is empty.
     *
     * @param i    the index of the subtree to use
     * @param text the text to get a substring from
     * @return the text covered by this span as given by subtree i, or null if subtree i is empty
     */
    public CharSequence getText(int i, CharSequence text) {
        if (children(i).isEmpty()) {
            return null;
        }
        StringBuilder str = new StringBuilder();
        List<SpanNode> ch = children(i);
        for (SpanNode node : ch) {
            CharSequence childText = node.getText(text);
            if (childText != null) {
                str.append(node.getText(text));
            }
        }
        return str;
    }

    /**
     * Returns the probability of the given subtree.
     *
     * @param i the subtree to return the probability of
     * @return the probability of the given subtree
     */
    public double getProbability(int i) {
        return childTrees.get(i).getProbability();
    }

    /**
     * Sets the probability of the given subtree.
     *
     * @param i           the subtree to set the probability of
     * @param probability the probability to set
     */
    public void setProbability(int i, double probability) {
        childTrees.get(i).setProbability(probability);
    }

    /** Normalizes all probabilities between 0.0 (inclusive) and 1.0 (exclusive). */
    public void normalizeProbabilities() {
        double sum = 0.0;
        for (Children c : childTrees) {
            sum += c.getProbability();
        }
        double coeff = 1.0 / sum;

        for (Children childTree : childTrees) {
            double newProb = childTree.getProbability() * coeff;
            childTree.setProbability(newProb);
        }
    }


    /**
     * Convenience method to add a span node to the child tree at index i. This is equivalent to calling
     * <code>
     * AlternateSpanList.children(i).add(node);
     * </code>
     *
     * @param i    index
     * @param node span node
     */
    public AlternateSpanList add(int i, SpanNode node) {
        checkValidity(node, children(i));
        node.setParent(this);
        children(i).add(node);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlternateSpanList)) return false;
        if (!super.equals(o)) return false;

        AlternateSpanList that = (AlternateSpanList) o;

        if (!childTrees.equals(that.childTrees)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + childTrees.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "AlternateSpanList, num subtrees=" + getNumSubTrees();
    }

    private static class ProbabilityComparator implements Comparator<Children> {
        @Override
        public int compare(Children o1, Children o2) {
            return Double.compare(o2.probability, o1.probability);  //note: opposite of natural ordering!
        }
    }

    private class Children extends SpanList {

        private double probability = 1.0;

        private Children(SpanNodeParent parent) {
            setParent(parent);
        }

        private Children(SpanNodeParent parent, List<SpanNode> children, double probability) {
            super(children);
            setParent(parent);
            if (children != null) {
                for (SpanNode node : children) {
                    node.setParent(AlternateSpanList.this);
                }
            }
            this.probability = probability;
        }

        public double getProbability() {
            return probability;
        }

        public void setProbability(double probability) {
            this.probability = probability;
        }

    }

}
