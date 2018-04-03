// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace vespalib {

struct TraceVisitor;

/**
 * This class contains the actual trace information of a {@link Trace} object.
 * A trace node can be encoded to, and decoded from a string representation to
 * allow transport across the network. Each node contains a list of children, a
 * strictness flag and an optional note. The child list is what forms the trace
 * tree, the strictness flag dictates whether or not the ordering of the
 * children is important, and the note is the actual traced data.
 *
 * The most important feature to notice is the {@link #normalize()} method that
 * will compact, sort and 'rootify' the trace tree so that trees become
 * well-formed (and can be compared for equality).
 */
class TraceNode {
private:
    TraceNode             *_parent;
    bool                   _strict;
    bool                   _hasNote;
    string                 _note;
    std::vector<TraceNode> _children;
    int64_t                _timestamp;

public:
    /**
     * Create an empty trace tree.
     */
    TraceNode();

    /**
     * Create a leaf node with the given note and timestamp.
     * @param note The note for this node.
     * @param timestamp The timestamp to give to node.
     */
    explicit TraceNode(const string &note, int64_t timestamp);

    /**
     * Create a leaf node with no note and a time stamp.
     * @param timestamp The timestamp to give to node.
     */
    explicit TraceNode(int64_t timestamp);

    TraceNode & operator =(const TraceNode &);
    TraceNode(TraceNode &&) noexcept;
    TraceNode & operator =(TraceNode &&) noexcept = default;
    ~TraceNode();

    /**
     * Create a trace tree which is a copy of another.
     *
     * @param rhs The tree to copy.
     */
    TraceNode(const TraceNode &rhs);

    /**
     * Swap the internals of this tree with another.
     *
     * @param other The tree to swap internals with.
     * @return This, to allow chaining.
     */
    TraceNode &swap(TraceNode &t);

    /**
     * Remove all trace information from this tree.
     *
     * @return This, to allow chaining.
     */
    TraceNode &clear();

    /**
     * Sort non-strict children recursively down the tree.
     *
     * @return This, to allow chaining.
     */
    TraceNode &sort();

    /**
     * Compact this tree. This will reduce the height of this tree as much as
     * possible without removing information stored in it.
     *
     * @return This, to allow chaining.
     */
    TraceNode &compact();

    /**
     * Normalize this tree. This will transform all equivalent trees into the
     * same form. Note that this will also perform an implicit compaction of
     * the tree.
     *
     * @return This, to allow chaining.
     */
    TraceNode &normalize();

    /**
     * Check whether or not this is a root node.
     *
     * @return True if this has no parent.
     */
    bool isRoot() const { return _parent == NULL; }

    /**
     * Check whether or not this is a leaf node.
     *
     * @return True if this has no children.
     */
    bool isLeaf() const { return _children.empty(); }

    /**
     * Check whether or not this node is empty, i.e. it has no note and no
     * children.
     *
     * @return True if this node is empty.
     */
    bool isEmpty() const { return !_hasNote && _children.empty(); }

    /**
     * Check whether or not the children of this node are strictly ordered.
     *
     * @return True if this node is strict.
     */
    bool isStrict() const { return _strict; }

    /**
     * Sets whether or not the children of this node are strictly ordered.
     *
     * @param strict True to order children strictly.
     * @return This, to allow chaining.
     */
    TraceNode &setStrict(bool strict) { _strict = strict; return *this; }

    /**
     * Returns whether or not a note is assigned to this node.
     *
     * @return True if a note is assigned.
     */
    bool hasNote() const { return _hasNote; }

    /**
     * Returns the note assigned to this node.
     *
     * @return The note.
     */
    const string & getNote() const { return _note; }

    /**
     * Returns the timestamp assigned to this node.
     *
     * @return The timestamp.
     */
    int64_t getTimestamp() const { return _timestamp; }


    /**
     * Returns the number of child nodes of this.
     *
     * @return The number of children.
     */
    uint32_t getNumChildren() const { return _children.size(); }

    /**
     * Returns the child trace node at the given index.
     *
     * @param i The index of the child to return.
     * @return The child at the given index.
     */
    const TraceNode &getChild(uint32_t i) const {  return _children[i]; }

    /**
     * Convenience method to add a child node containing a note to this.
     * A timestamp of 0 will be assigned to the new node.
     *
     * @param note The note to assign to the child.
     * @return This, to allow chaining.
     */
    TraceNode &addChild(const string &note);

    /**
     * Convenience method to add a child node containing a note to this and a timestamp.
     *
     * @param note The note to assign to the child.
     * @param timestamp The timestamp to give this child.
     * @return This, to allow chaining.
     */
    TraceNode &addChild(const string &note, int64_t timestamp);

    /**
     * Adds a child node to this.
     *
     * @param child The child to add.
     * @return This, to allow chaining.
     */
    TraceNode &addChild(TraceNode child);

    /**
     * Adds a list of child nodes to this.
     *
     * @param children The children to add.
     * @return This, to allow chaining.
     */
    TraceNode &addChildren(std::vector<TraceNode> children);

    /**
     * Generate a non-parseable, human-readable string representation of
     * this trace node.
     *
     * @return generated string
     * @param limit soft cap for maximum size of generated string
     **/
    string toString(size_t limit = -1) const;

    /**
     * Writes a non-parseable, human-readable string representation of
     * this trace node to the given string.
     *
     * @return false if string was capped, true otherwise
     * @param dst output string
     * @param indent number of spaces to be used for indent
     * @param limit soft cap for maximum size of generated string
     **/
    bool writeString(string &dst, size_t indent, size_t limit) const;

    /**
     * Returns a parseable (using {@link #decode(String)}) string
     * representation of this trace node.
     *
     * @return A string representation of this tree.
     */
    string encode() const;

    /**
     * Build a trace tree from the given string representation (possibly
     * encoded using {@link #encode()}).
     *
     * @param str The string to parse.
     * @return The corresponding trace tree, or an empty node if parsing failed.
     */
    static TraceNode decode(const string &str);

    /**
     * Visits this TraceNode and all of its descendants in depth-first, prefix order.
     *
     * @param visitor The visitor to accept.
     * @return The visitor parameter.
     */
    TraceVisitor & accept(TraceVisitor & visitor) const;

};

} // namespace vespalib

