package ai.vespa.schemals.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.lsp4j.Range;

public abstract class Node<NodeType extends Node> implements Iterable<NodeType> {

    public enum LanguageType {
        SCHEMA,
        INDEXING,
        RANK_EXPRESSION,
        YQLPlus,
        VESPA_GROUPING,
        CUSTOM
    }

    // This array has to be in order, without overlapping elements
    private List<NodeType> children = new ArrayList<>();
    private NodeType parent;

    protected LanguageType language; // Language specifies which parser the node comes from
    protected Range range;

    protected Node(LanguageType language, Range range) {
        this.language = language;
        this.range = range;
    }

    public LanguageType getLanguageType() { return language; }

    public Range getRange() {
        return range;
    }

    public int size() {
        return children.size();
    }

    public NodeType get(int i) {
        return children.get(i);
    }

    public void setParent(NodeType parent) {
        this.parent = parent;
    }

    public void addChild(NodeType child) {
        child.setParent(this);
        this.children.add(child);
    }

    public void addChildren(List<NodeType> children) {
        for (NodeType child : children) {
            addChild(child);
        }
    }

    public void clearChildren() {
        for (NodeType child : children) {
            child.setParent(null);
        }

        children.clear();
    }

    public NodeType getParent(int levels) {
        if (levels == 0) {
            return (NodeType) this;
        }

        if (parent == null) {
            return null;
        }

        return (NodeType) parent.getParent(levels - 1);
    }

    public NodeType getParent() {
        return getParent(1);
    }

    public void insertChildAfter(int index, NodeType child) {
        this.children.add(index+1, child);
        child.setParent(this);
    }

    /**
     * Returns the previous SchemaNode of the sibilings of the node.
     * If there is no previous node, it moves upwards to the parent and tries to get the previous node.
     *
     * @return the previous SchemaNode, or null if there is no previous node
     */
    public NodeType getPrevious() {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1)return null; // invalid setup

        if (parentIndex == 0)return parent;
        return (NodeType) parent.get(parentIndex - 1);
    }

    /**
     * Returns the next SchemaNode of the sibilings of the node.
     * If there is no next node, it returns the next node of the parent node.
     *
     * @return the next SchemaNode or null if there is no next node
     */
    public NodeType getNext() {
        if (parent == null) return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1) return null;
        
        if (parentIndex == parent.size() - 1) return (NodeType) parent.getNext();

        return (NodeType) parent.get(parentIndex + 1);
    }

    /**
     * Returns the sibling node at the specified relative index.
     * A sibling node is a node that shares the same parent node.
     *
     * @param relativeIndex the relative index of the sibling node
     * @return the sibling node at the specified relative index, or null if the sibling node does not exist
     */
    public NodeType getSibling(int relativeIndex) {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1) return null; // invalid setup

        int siblingIndex = parentIndex + relativeIndex;
        if (siblingIndex < 0 || siblingIndex >= parent.size()) return null;
        
        return (NodeType) parent.get(siblingIndex);
    }

    /**
     * Returns the previous sibling of this schema node.
     * A sibling node is a node that shares the same parent node. 
     *
     * @return the previous sibling of this schema node, or null if there is no previous sibling
     */
    public NodeType getPreviousSibling() {
        return getSibling(-1);
    }

    /**
     * Returns the next sibling of this schema node.
     * A sibling node is a node that shares the same parent node.
     *
     * @return the next sibling of this schema node, or null if there is no previous sibling
     */
    public NodeType getNextSibling() {
        return getSibling(1);
    }

    public int indexOf(NodeType child) {
        return this.children.indexOf(child);
    }

    public boolean isLeaf() {
        return children.size() == 0;
    }

    @Override
	public Iterator<NodeType> iterator() {
        return new Iterator<NodeType>() {
            int currentIndex = 0;

			@Override
			public boolean hasNext() {
                return currentIndex < children.size();
			}

			@Override
			public NodeType next() {
                return children.get(currentIndex++);
			}
        };
	}
}

