// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Represents an inner node in the configuration tree.
 *
 * @author gjoranv
 */
public class InnerCNode extends CNode {

    /**
     * The children of this Node. Mapped using their short name as
     * string. This variable is null only if Node is a leaf Node.
     */
    private final Map<String, CNode> children = new LinkedHashMap<>();
    private boolean restart = false;

    /**
     * Constructor for the root node.
     */
    public InnerCNode(String name) {
        super(null, name.split("\\.def")[0]);
        defName = this.name;
    }

    /**
     * Constructor for inner nodes.
     */
    private InnerCNode(InnerCNode parent, String name) {
        super(parent, name);
    }

    @Override
    public CNode[] getChildren() {
        CNode[] ret = new CNode[children.size()];
        children.values().toArray(ret);
        return ret;
    }

    /**
     * Access to children for testing
     * @return modifiable children map
     */
    public Map<String, CNode> children() {
        return children;
    }

    @Override
    public CNode getChild(String name) {
        return children.get(name);
    }

    /**
     * Returns and eventually creates the given subnode.
     */
    private CNode createOrGetChild(DefLine.Type type, String name) throws IllegalArgumentException {
        String key = name;
        int split = name.indexOf('.');
        CNode newChild;
        if (split != -1) {
            key = name.substring(0, split).trim();
            newChild = new InnerCNode(this, key);
        } else {
            newChild = LeafCNode.newInstance(type, this, key);
            if (newChild == null)
                throw new IllegalArgumentException("Could not create " + type.name + " " + name);
        }
        return children.containsKey(newChild.getName())
                ? children.get(newChild.getName())
                : newChild;
    }

    /**
     * Adds a child to this node with the given type, name and value. Necessary children on the path
     * to the given leaf node will be added as well.
     *
     * @param name         the full name/path of the node to add.
     * @param defLine      the parsed .def-file line to add.
     * @param comment      comment extracted from the .def-file.
     */
    @Override
    protected void setLeaf(String name, DefLine defLine, String comment) throws IllegalArgumentException {
        if (name.indexOf('.') < 0) {
            throw new IllegalArgumentException("Parameter with name '" + name +
                    "' cannot be a leaf node as it has already been declared as an inner node.");
        }
        checkMyName(name.substring(0, name.indexOf('.')));
        String childName = name.substring(name.indexOf('.') + 1);

        CNode child = createOrGetChild(defLine.getType(), childName);
        restart |= defLine.getRestart();
        child.setLeaf(childName, defLine, comment);
        children.put(child.getName(), child);
    }

    @Override
    public boolean needRestart() {
        return restart;
    }

}
