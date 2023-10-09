// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import java.util.logging.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class contains the actual trace information of a {@link Trace} object. A trace node can be encoded to, and
 * decoded from a string representation to allow transport across the network. Each node contains a list of children, a
 * strictness flag and an optional note. The child list is what forms the trace tree, the strictness flag dictates
 * whether or not the ordering of the children is important, and the note is the actual traced data.
 *
 * The most important feature to notice is the {@link #normalize()} method that will compact, sort and 'rootify' the
 * trace tree so that trees become well-formed (and can be compared for equality).
 *
 * @author Simon Thoresen Hult
 */
public class TraceNode implements Comparable<TraceNode> {

    private static final Logger log = Logger.getLogger(TraceNode.class.getName());
    private TraceNode parent = null;
    private boolean strict = true;
    private String note = null;
    private List<TraceNode> children = new ArrayList<>();

    /**
     * Create an empty trace tree.
     */
    public TraceNode() {
        // empty
    }

    /**
     * Create a leaf node with the given note.
     *
     * @param note The note to assign to this.
     */
    private TraceNode(String note) {
        this.note = note;
    }

    /**
     * Create a trace tree which is a copy of another.
     *
     * @param rhs The tree to copy.
     */
    private TraceNode(TraceNode rhs) {
        strict = rhs.strict;
        note = rhs.note;
        addChildren(rhs.children);
    }

    /**
     * Swap the internals of this tree with another.
     *
     * @param other The tree to swap internals with.
     * @return This, to allow chaining.
     */
    public TraceNode swap(TraceNode other) {
        TraceNode parent = this.parent;
        this.parent = other.parent;
        other.parent = parent;

        boolean strict = this.strict;
        this.strict = other.strict;
        other.strict = strict;

        String note = this.note;
        this.note = other.note;
        other.note = note;

        List<TraceNode> children = this.children;
        this.children = other.children;
        for (TraceNode child : this.children) {
            child.parent = this;
        }
        other.children = children;
        for (TraceNode child : other.children) {
            child.parent = other;
        }

        return this;
    }

    /**
     * Remove all trace information from this tree.
     *
     * @return This, to allow chaining.
     */
    public TraceNode clear() {
        parent = null;
        strict = true;
        note = null;
        children.clear();
        return this;
    }

    /**
     * Sort non-strict children recursively down the tree.
     *
     * @return This, to allow chaining.
     */
    public TraceNode sort() {
        if (!isLeaf()) {
            for (TraceNode child : children) {
                child.sort();
            }
            if (!isStrict()) {
                Collections.sort(children);
            }
        }
        return this;
    }

    @Override
    public int compareTo(TraceNode rhs) {
        if (isLeaf() || rhs.isLeaf()) {
            if (isLeaf() && rhs.isLeaf()) {
                return note.compareTo(rhs.getNote());
            } else {
                return isLeaf() ? -1 : 1;
            }
        }
        if (children.size() != rhs.children.size()) {
            return children.size() < rhs.children.size() ? -1 : 1;
        }
        for (int i = 0; i < children.size(); ++i) {
            int cmp = children.get(i).compareTo(rhs.children.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return -1;
    }

    /**
     * Compact this tree. This will reduce the height of this tree as much as possible without removing information
     * stored in it.
     *
     * @return This, to allow chaining.
     */
    public TraceNode compact() {
        if (isLeaf()) {
            return this;
        }
        List<TraceNode> tmp = this.children;
        this.children = new ArrayList<>();
        for (TraceNode child : tmp) {
            child.compact();
            if (child.isEmpty()) {
                // ignore
            } else if (child.isLeaf()) {
                addChild(child);
            } else if (strict == child.strict) {
                addChildren(child.children);
            } else if (child.getNumChildren() == 1) {
                TraceNode grandChild = child.getChild(0);
                if (grandChild.isEmpty()) {
                    // ignore
                } else if (grandChild.isLeaf() || strict != grandChild.strict) {
                    addChild(grandChild);
                } else {
                    addChildren(grandChild.children);
                }
            } else {
                addChild(child);
            }
        }
        return this;
    }

    /**
     * Normalize this tree. This will transform all equivalent trees into the same form. Note that this will also
     * perform an implicit compaction of the tree.
     *
     * @return This, to allow chaining.
     */
    public TraceNode normalize() {
        compact();
        sort();
        if (note != null || !strict) {
            TraceNode child = new TraceNode();
            child.swap(this);
            addChild(child);
            strict = true;
        }
        return this;
    }

    /**
     * Check whether or not this is a root node.
     *
     * @return True if this has no parent.
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Check whether or not this is a leaf node.
     *
     * @return True if this has no children.
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Check whether or not this node is empty, i.e. it has no note and no children.
     *
     * @return True if this node is empty.
     */
    public boolean isEmpty() {
        return note == null && children.isEmpty();
    }

    /**
     * Check whether or not the children of this node are strictly ordered.
     *
     * @return True if this node is strict.
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Sets whether or not the children of this node are strictly ordered.
     *
     * @param strict True to order children strictly.
     * @return This, to allow chaining.
     */
    public TraceNode setStrict(boolean strict) {
        this.strict = strict;
        return this;
    }

    /**
     * Returns whether or not a note is assigned to this node.
     *
     * @return True if a note is assigned.
     */
    public boolean hasNote() {
        return note != null;
    }

    /**
     * Returns the note assigned to this node.
     *
     * @return The note.
     */
    public String getNote() {
        return note;
    }

    /**
     * Returns the number of child nodes of this.
     *
     * @return The number of children.
     */
    public int getNumChildren() {
        return children.size();
    }

    /**
     * Returns the child trace node at the given index.
     *
     * @param i The index of the child to return.
     * @return The child at the given index.
     */
    public TraceNode getChild(int i) {
        return children.get(i);
    }

    /**
     * Convenience method to add a child node containing a note to this.
     *
     * @param note The note to assign to the child.
     * @return This, to allow chaining.
     */
    public TraceNode addChild(String note) {
        return addChild(new TraceNode(note));
    }

    /**
     * Adds a child node to this.
     *
     * @param child The child to add.
     * @return This, to allow chaining.
     */
    public TraceNode addChild(TraceNode child) {
        if (note != null) {
            throw new IllegalStateException("Nodes with notes are leaf nodes, you can not add children to it.");
        }
        TraceNode node = new TraceNode(child);
        node.parent = this;
        children.add(node);
        return this;
    }

    /**
     * Adds a list of child nodes to this.
     *
     * @param children The children to add.
     * @return This, to allow chaining.
     */
    public TraceNode addChildren(List<TraceNode> children) {
        for (TraceNode child : children) {
            addChild(child);
        }
        return this;
    }

    // Overrides Object.
    @Override
    public String toString() {
        return toString(Integer.MAX_VALUE);
    }

    /**
     * Generates a non-parseable, human-readable string representation
     * of this trace node.
     *
     * @return generated string
     * @param limit soft limit for maximum string size
     **/
    public String toString(int limit) {
        StringBuilder out = new StringBuilder();
        if (!writeString(out, "", limit)) {
            out.append("...\n");
        }
        return out.toString();
    }

    /**
     * Writes a non-parseable, human-readable string representation of
     * this trace node to the given string builder using the given
     * indent string for every written line.
     *
     * @return false if written string was capped
     * @param ret    The string builder to write to.
     * @param indent The indent to use.
     * @param limit soft limit for maximum string size
     */
    private boolean writeString(StringBuilder ret, String indent, int limit) {
        if (ret.length() >= limit) {
            return false;
        }
        if (note != null) {
            ret.append(indent).append(note).append("\n");
        } else {
            String name = isStrict() ? "trace" : "fork";
            ret.append(indent).append("<").append(name).append(">\n");
            for (TraceNode child : children) {
                if (!child.writeString(ret, indent + "    ", limit)) {
                    return false;
                }
            }
            if (ret.length() >= limit) {
                return false;
            }
            ret.append(indent).append("</").append(name).append(">\n");
        }
        return true;
    }

    /**
     * Returns a parseable (using {@link #decode(String)}) string representation of this trace node.
     *
     * @return A string representation of this tree.
     */
    public String encode() {
        StringBuilder ret = new StringBuilder();
        encode(ret);
        return ret.toString();
    }

    /**
     * Writes a parseable string representation of this trace node to the given string builder.
     *
     * @param ret The string builder to write to.
     */
    private void encode(StringBuilder ret) {
        if (note != null) {
            ret.append("[");
            for (int i = 0, len = note.length(); i < len; ++i) {
                char c = note.charAt(i);
                if (c == '\\' || c == ']') {
                    ret.append('\\');
                }
                ret.append(note.charAt(i));
            }
            ret.append("]");
        } else {
            ret.append(strict ? "(" : "{");
            for (TraceNode child : children) {
                child.encode(ret);
            }
            ret.append(strict ? ")" : "}");
        }
    }

    /**
     * Build a trace tree from the given string representation (possibly encoded using {@link #encode()}).
     *
     * @param str The string to parse.
     * @return The corresponding trace tree, or an empty node if parsing failed.
     */
    public static TraceNode decode(String str) {
        if (str == null || str.isEmpty()) {
            return new TraceNode();
        }
        TraceNode proxy = new TraceNode();
        TraceNode node = proxy;
        StringBuilder note = null;
        boolean inEscape = false;
        for (int i = 0, len = str.length(); i < len; ++i) {
            char c = str.charAt(i);
            if (note != null) {
                if (inEscape) {
                    note.append(c);
                    inEscape = false;
                } else if (c == '\\') {
                    inEscape = true;
                } else if (c == ']') {
                    node.addChild(note.toString());
                    note = null;
                } else {
                    note.append(c);
                }
            } else {
                if (c == '[') {
                    note = new StringBuilder();
                } else if (c == '(' || c == '{') {
                    node.addChild(new TraceNode());
                    node = node.getChild(node.getNumChildren() - 1);
                    node.setStrict(c == '(');
                } else if (c == ')' || c == '}') {
                    if (node == null) {
                        log.log(Level.WARNING, "Unexpected closing brace in trace '" + str + "' at position " + i + ".");
                        return new TraceNode();
                    }
                    if (node.isStrict() != (c == ')')) {
                        log.log(Level.WARNING, "Mismatched closing brace in trace '" + str + "' at position " + i + ".");
                        return new TraceNode();
                    }
                    node = node.parent;
                }
            }
        }
        if (note != null) {
            log.log(Level.WARNING, "Unterminated note in trace '" + str + "'.");
            return new TraceNode();
        }
        if (node != proxy) {
            log.log(Level.WARNING, "Missing closing brace in trace '" + str + "'.");
            return new TraceNode();
        }
        if (proxy.getNumChildren() == 0) {
            log.log(Level.WARNING, "No nodes found in trace '" + str + "'.");
            return new TraceNode();
        }
        if (proxy.getNumChildren() != 1) {
            return proxy; // best-effort recovery from malformed input
        }
        TraceNode ret = proxy.children.remove(0);
        ret.parent = null;
        return ret;
    }
}
