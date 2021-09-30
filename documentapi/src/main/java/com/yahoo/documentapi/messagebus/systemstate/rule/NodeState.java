// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.systemstate.rule;

import java.util.logging.Level;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public class NodeState {

    /** A location string that expresses the use of the PARENT node. */
    public static final String NODE_PARENT = "..";

    /** A location string that expresses the use of THIS node. */
    public static final String NODE_CURRENT = ".";

    private static final Logger log = Logger.getLogger(NodeState.class.getName());
    private final Map<String, NodeState> children = new LinkedHashMap<String, NodeState>();
    private final Map<String, String> state = new LinkedHashMap<String, String>();
    private NodeState parent = null;
    private String id = null;

    /**
     * Creates a node state that no internal content.
     */
    public NodeState() {
        // empty
    }

    /**
     * Creates a node state based on a list of argument objects. These arguments are iterated and added to this node's
     * internal state map.
     *
     * @param args The arguments to use as state.
     */
    public NodeState(List<Argument> args) {
        for (Argument arg : args) {
            setState(arg.getName(), arg.getValue());
        }
    }

    /**
     * Adds a child to this node at the given location. The key can be a location string, in which case the necessary
     * intermediate node states are created.
     *
     * @param key   The location at which to add the child.
     * @param child The child node to add.
     * @return This, to allow chaining.
     */
    public NodeState addChild(String key, NodeState child) {
        getChild(key, true).copy(child);
        return this;
    }

    /**
     * Returns the child at the given location relative to this.
     *
     * @param key The location of the child to return.
     * @return The child object, null if not found.
     */
    public NodeState getChild(String key) {
        return getChild(key, false);
    }

    /**
     * Returns the child at the given location relative to this. This method can be forced to return a child node even
     * if it does not exist, by adding all intermediate nodes and the target node itself.
     *
     * @param key   The location of the child to return.
     * @param force Whether or not to force a return value by creating missing nodes.
     * @return The child object, null if not found.
     */
    public NodeState getChild(String key, boolean force) {
        if (key == null || key.length() == 0) {
            return this;
        }
        String arr[] = key.split("/", 2);
        while (arr.length == 2 && arr[0].equals(NODE_CURRENT)) {
            arr = arr[1].split("/", 2);
        }
        if (arr[0].equals(NODE_CURRENT)) {
            return this;
        }
        if (arr[0].equals(NODE_PARENT)) {
            if (parent == null) {
                log.log(Level.SEVERE, "Location string '" + key + "' requests a parent above the top-most node, " +
                                        "returning self to avoid crash.");
            }
            return parent.getChild(arr[1], force);
        }
        if (!children.containsKey(arr[0])) {
            if (!force) {
                return null;
            }
            children.put(arr[0], new NodeState());
            children.get(arr[0]).setParent(this, arr[0]);
        }
        if (arr.length == 2) {
            return children.get(arr[0]).getChild(arr[1], force);
        }
        return children.get(arr[0]);
    }

    /**
     * Returns the map of child nodes for iteration.
     *
     * @return The internal child map.
     */
    public Map<String, NodeState> getChildren() {
        return children;
    }

    /**
     * Removes the named child node from this node, and attempts to compact the system state from this node upwards by
     * removing empty nodes.
     *
     * @param key The child to remove.
     * @return The result of invoking {@link #compact} after the remove.
     */
    public NodeState removeChild(String key) {
        if (key == null || key.length() == 0) {
            return this;
        }
        int pos = key.lastIndexOf("/");
        if (pos > -1) {
            NodeState parent = getChild(key.substring(0, pos), false);
            if (parent != null) {
                return parent.removeChild(key.substring(pos + 1));
            }
        }
        else {
            children.remove(key);
        }
        return compact();
    }

    /**
     * Retrieves some arbitrary state information for a given key. The key can be a location string, in which case the
     * necessary intermediate nodes are traversed. If the key is not found, this method returns null.
     *
     * @param key The name of the state information to return.
     * @return The value of the state key.
     */
    public String getState(String key) {
        if (key == null || key.length() == 0) {
            return null;
        }
        int pos = key.lastIndexOf("/");
        if (pos > -1) {
            NodeState parent = getChild(key.substring(0, pos), false);
            return parent != null ? parent.getState(key.substring(pos + 1)) : null;
        }
        return state.get(key);
    }

    /**
     * Sets some arbitrary state data in this node. The key can be a location string, in which case the necessary
     * intermediate nodes are traversed and even created if missing.
     *
     * @param key   The key to set.
     * @param value The value to assign to the key.
     * @return This, to allow chaining.
     */
    public NodeState setState(String key, String value) {
        if (key == null || key.length() == 0) {
            return this;
        }
        int pos = key.lastIndexOf("/");
        if (pos > -1) {
            getChild(key.substring(0, pos), true).setState(key.substring(pos + 1), value);
        }
        else {
            if (value == null || value.length() == 0) {
                return removeState(key);
            }
            else {
                state.put(key, value);
            }
        }
        return this;
    }

    /**
     * Removes the named (key, value) state pair from this node, and attempts to compact the system state from this node
     * upwards by removing empty nodes.
     *
     * @param key The state variable to clear.
     * @return The result of invoking {@link #compact} after the remove.
     */
    public NodeState removeState(String key) {
        if (key == null || key.length() == 0) {
            return this;
        }
        int pos = key.lastIndexOf("/");
        if (pos > -1) {
            NodeState parent = getChild(key.substring(0, pos), false);
            if (parent != null) {
                return parent.removeState(key.substring(pos + 1));
            }
        }
        else {
            state.remove(key);
        }
        return compact();
    }

    /**
     * Compacts the system state tree from this node upwards. This will delete itself if it has a parent, but no
     * internal state and no children.
     *
     * @return This or the first non-null ancestor, to allow chaining.
     */
    private NodeState compact() {
        if (state.isEmpty() && children.isEmpty()) {
            if (parent != null) {
                return parent.removeChild(id);
            }
        }
        return this;
    }

    /**
     * Copies the state content of another node state object into this.
     *
     * @param node The node state to copy into this.
     * @return This, to allow chaining.
     */
    public NodeState copy(NodeState node) {
        for (String key : node.state.keySet()) {
            state.put(key, node.state.get(key));
        }
        for (String key : node.children.keySet()) {
            getChild(key, true).copy(node.children.get(key));
        }
        return this;
    }

    /**
     * Clears both the internal state and child list, then compacts the tree from this node upwards.
     *
     * @return The result of invoking {@link #compact} after the remove.
     */
    public NodeState clear() {
        state.clear();
        children.clear();
        return compact();
    }

    /**
     * Sets the parent of this node.
     *
     * @param parent The parent node.
     * @param id     The identifier of this node as seen in the parent.
     * @return This, to allow chaining.
     */
    public NodeState setParent(NodeState parent, String id) {
        this.parent = parent;
        this.id = id;
        return this;
    }

    /**
     * Returns a string representation of this node state.
     *
     * @param prefix The prefix to use for this string.
     * @return A string representation of this.
     * @throws UnsupportedEncodingException Thrown if the host system does not support UTF-8 encoding.
     */
    private String toString(String prefix) throws UnsupportedEncodingException {
        StringBuffer buf = new StringBuffer();
        if (!state.isEmpty()) {
            buf.append(prefix.length() == 0 ? "." : prefix).append("?");
            String[] arr = state.keySet().toArray(new String[state.keySet().size()]);
            for (int i = 0; i < arr.length; ++i) {
                buf.append(arr[i]).append("=").append(URLEncoder.encode(state.get(arr[i]), "UTF-8"));
                if (i < arr.length - 1) {
                    buf.append("&");
                }
            }
            buf.append(" ");
        }
        if (prefix.length() > 0) {
            prefix += "/";
        }
        String[] keys = children.keySet().toArray(new String[children.keySet().size()]);
        Arrays.sort(keys);
        for (String loc : keys) {
            buf.append(children.get(loc).toString(prefix + URLEncoder.encode(loc, "UTF-8")));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        try {
            return toString("").trim();
        }
        catch (UnsupportedEncodingException e) {
            return e.toString();
        }
    }
}
