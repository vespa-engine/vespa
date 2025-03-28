package ai.vespa.schemals.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;

public abstract class Node implements Iterable<Node> {

    public enum LanguageType {
        SCHEMA,
        INDEXING,
        RANK_EXPRESSION,
        YQLPlus,
        GROUPING,
        CUSTOM
    }

    // This array has to be in order, without overlapping elements
    private List<Node> children = new ArrayList<>();
    private Node parent;

    protected LanguageType language; // Language specifies which parser the node comes from
    protected Range range;
    protected boolean isDirty;
    protected Symbol symbol;

    protected Node(LanguageType language, Range range, boolean isDirty) {
        this.language = language;
        this.range = range;
        this.isDirty = isDirty;
    }

    public LanguageType getLanguageType() { return language; }

    public Range getRange() {
        return range;
    }

    public boolean getIsDirty() {
        return isDirty;
    }

    public int size() {
        return children.size();
    }

    public Node get(int i) {
        return children.get(i);
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public void addChild(Node child) {
        child.setParent(this);
        this.children.add(child);
    }

    public void addChildren(List<? extends Node> children) {
        for (Node child : children) {
            addChild(child);
        }
    }

    public void clearChildren() {
        for (Node child : children) {
            child.setParent(null);
        }

        children.clear();
    }

    public Node removeChild(int i) {
        Node child = children.remove(i);
        child.setParent(null);

        return child;
    }

    public Node getParent(int levels) {
        if (levels == 0) {
            return this;
        }

        if (parent == null) {
            return null;
        }

        return parent.getParent(levels - 1);
    }

    public Node getParent() {
        return getParent(1);
    }

    public void insertChildAfter(int index, Node child) {
        this.children.add(index+1, child);
        child.setParent(this);
    }

    /**
     * Returns the previous SchemaNode of the sibilings of the node.
     * If there is no previous node, it moves upwards to the parent and tries to get the previous node.
     *
     * @return the previous SchemaNode, or null if there is no previous node
     */
    public Node getPrevious() {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1)return null; // invalid setup

        if (parentIndex == 0)return parent;
        return parent.get(parentIndex - 1);
    }

    /**
     * Returns the next SchemaNode of the sibilings of the node.
     * If there is no next node, it returns the next node of the parent node.
     *
     * @return the next SchemaNode or null if there is no next node
     */
    public Node getNext() {
        if (parent == null) return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1) return null;
        
        if (parentIndex == parent.size() - 1) return parent.getNext();

        return parent.get(parentIndex + 1);
    }

    /**
     * Returns the sibling node at the specified relative index.
     * A sibling node is a node that shares the same parent node.
     *
     * @param relativeIndex the relative index of the sibling node
     * @return the sibling node at the specified relative index, or null if the sibling node does not exist
     */
    public Node getSibling(int relativeIndex) {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1) return null; // invalid setup

        int siblingIndex = parentIndex + relativeIndex;
        if (siblingIndex < 0 || siblingIndex >= parent.size()) return null;
        
        return parent.get(siblingIndex);
    }

    /**
     * Returns the previous sibling of this schema node.
     * A sibling node is a node that shares the same parent node. 
     *
     * @return the previous sibling of this schema node, or null if there is no previous sibling
     */
    public Node getPreviousSibling() {
        return getSibling(-1);
    }

    /**
     * Returns the next sibling of this schema node.
     * A sibling node is a node that shares the same parent node.
     *
     * @return the next sibling of this schema node, or null if there is no previous sibling
     */
    public Node getNextSibling() {
        return getSibling(1);
    }

    public int indexOf(Node child) {
        return this.children.indexOf(child);
    }

    public boolean isLeaf() {
        return children.size() == 0;
    }

    public boolean hasSymbol() {
        return this.symbol != null;
    }

    public Symbol getSymbol() {
        if (!hasSymbol()) throw new IllegalArgumentException("get Symbol called on node without a symbol!");
        return this.symbol;
    }

    public void removeSymbol() {
        this.symbol = null;
    }

    public Symbol setSymbol(SymbolType type, String fileURI, Symbol scope, String shortIdentifier) {
        if (this.hasSymbol()) {
            throw new IllegalArgumentException("Cannot set symbol for node: " + this.toString() + ". Already has symbol.");
        }
        this.symbol = new Symbol(this, type, fileURI, scope, shortIdentifier);

        return symbol;
    }

    public Symbol setSymbol(SymbolType type, String fileURI) {
        if (this.hasSymbol()) {
            throw new IllegalArgumentException("Cannot set symbol for node: " + this.toString() + ". Already has symbol.");
        }
        this.symbol = new Symbol(this, type, fileURI);

        return symbol;
    }

    public Symbol setSymbol(SymbolType type, String fileURI, Symbol scope) {
        if (this.hasSymbol()) {
            throw new IllegalArgumentException("Cannot set symbol for node: " + this.toString() + ". Already has symbol.");
        }
        this.symbol = new Symbol(this, type, fileURI, scope);

        return symbol;
    }

    public Symbol setSymbol(SymbolType type, String fileURI, Optional<Symbol> scope) {
        if (scope.isPresent()) {
            setSymbol(type, fileURI, scope.get());
        } else {
            setSymbol(type, fileURI);
        }

        return symbol;
    }

    public abstract int getBeginOffset();
    public abstract int getEndOffset();

    public abstract String getText();
    public abstract Class<?> getASTClass();

    public boolean isASTInstance(Class<?> cls) {
        return getASTClass() == cls;
    }

    public boolean isSchemaNode() { return false; }
    public SchemaNode getSchemaNode() {
        throw new UnsupportedOperationException("Cannot get a SchemaNode from a non SchemaNode.");
    }

    public boolean isYQLNode() { return false; }
    public YQLNode getYQLNode() {
        throw new UnsupportedOperationException("Cannot get a YQLNode from a non YQLNode.");
    }

    @Override
	public Iterator<Node> iterator() {
        return new Iterator<Node>() {
            int currentIndex = 0;

			@Override
			public boolean hasNext() {
                return currentIndex < children.size();
			}

			@Override
			public Node next() {
                return children.get(currentIndex++);
			}
        };
	}
}

