// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>
#include <map>

namespace documentapi {

/**
 * A node state is a single node in an annotatet tree of such nodes. It contains a reference to its parent
 * node, a list of named child nodes, as well as a mapping of (key, value) pairs that constitute the annotated
 * state of this node. To create an instance of a node state tree, one can either use the {@link SystemState}
 * factory class, or one can programmatically construct each node and use the chaining capabilities of its
 * set-methods to compact the necessary code.
 */
class NodeState {
public:
    typedef std::unique_ptr<NodeState> UP;
    typedef std::shared_ptr<NodeState> SP;
    typedef std::map<string, string> StateMap;
    typedef std::map<string, NodeState::SP> ChildMap;

private:
    NodeState*  _parent;
    string _id;
    ChildMap    _children;
    StateMap    _state;

    /**
     * Compacts the system state tree from this node upwards. This will delete itself if it has a parent, but
     * no internal state and no children.
     *
     * @return This or the first non-null ancestor, to allow chaining.
     */
    NodeState &compact();

    /**
     * Returns a string representation of this node state.
     *
     * @param prefix The prefix to use for this string.
     * @return A string representation of this.
     */
    const string toString(const string &prefix) const;

public:
    NodeState(NodeState && rhs) = default;
    NodeState & operator = (NodeState && rhs) = default;
    NodeState & operator = (const NodeState & rhs) = default;
    /**
     * Creates a node state that no internal content.
     */
    NodeState();

    /**
     * Creates a node state as a copy of another.
     *
     * @param rhs The state to copy.
     */
    NodeState(const NodeState &rhs);

    /**
     * Creates a node state based on a list of argument objects. These arguments are iterated and added to
     * this node's internal state map.
     *
     * @param args The arguments to use as state.
     */
    NodeState(StateMap args);

    ~NodeState();

    /**
     * Adds a child to this node at the given location. The key can be a location string, in which case the
     * necessary intermediate node states are created.
     *
     * @param key   The location at which to add the child.
     * @param child The child node to add.
     * @return This, to allow chaining.
     */
    NodeState &addChild(const string &key, const NodeState &child);

    /**
     * Returns the child at the given location relative to this. This method can be forced to return a child
     * node even if it does not exist, by adding all intermediate nodes and the target node itself.
     *
     * @param key   The location of the child to return.
     * @param force Whether or not to force a return value by creating missing nodes.
     * @return The child object, null if not found.
     */
    NodeState *getChild(const string &key, bool force = false);

    /**
     * Returns the map of child nodes for iteration.
     *
     * @return The internal child map.
     */
    const ChildMap &getChildren() const;

    /**
     * Removes the named child node from this node, and attempts to compact the system state from this node
     * upwards by removing empty nodes.
     *
     * @param key The child to remove.
     * @return The result of invoking {@link #compact} after the remove.
     */
    NodeState &removeChild(const string &key);

    /**
     * Retrieves some arbitrary state information for a given key. The key can be a location string, in which
     * case the necessary intermediate nodes are traversed. If the key is not found, this method returns
     * null. This method can not be const because it uses the non-const method {@link #getChild} to resolve a
     * pathed key.
     *
     * @param key The name of the state information to return.
     * @return The value of the state key.
     */
    const string getState(const string &key);

    /**
     * Sets some arbitrary state data in this node. The key can be a location string, in which case the
     * necessary intermediate nodes are traversed and even created if missing.
     *
     * @param key   The key to set.
     * @param value The value to assign to the key.
     * @return This, to allow chaining.
     */
    NodeState &setState(const string &key, const string &value);

    /**
     * Removes the named (key, value) state pair from this node, and attempts to compact the system state from
     * this node upwards by removing empty nodes.
     *
     * @param key The state variable to clear.
     * @return The result of invoking {@link #compact} after the remove.
     */
    NodeState &removeState(const string &key);

    /**
     * Copies the state content of another node state object into this.
     *
     * @param node The node state to copy into this.
     * @return This, to allow chaining.
     */
    NodeState &copy(const NodeState &node);

    /**
     * Clears both the internal state and child list, then compacts the tree from this node upwards.
     *
     * @return The result of invoking {@link #compact} after the remove.
     */
    NodeState &clear();

    /**
     * Sets the parent of this node.
     *
     * @param parent The parent node.
     * @param id     The identifier of this node as seen in the parent.
     * @return This, to allow chaining.
     */
    NodeState &setParent(NodeState &parent, const string &id);

    /**
     * Returns a string representation of this node state.
     *
     * @return A string representation of this.
     */
    const string toString() const;
};

}

