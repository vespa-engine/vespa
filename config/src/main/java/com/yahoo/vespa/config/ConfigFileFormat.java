// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.codegen.CNode;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.codegen.LeafCNode;
import com.yahoo.slime.*;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.*;
import java.util.Stack;

/**
 * @author Ulf Lilleengen
 */
public class ConfigFileFormat implements SlimeFormat, ObjectTraverser {

    private final InnerCNode root;
    private DataOutputStream out = null;
    private Stack<Node> nodeStack;

    public ConfigFileFormat(InnerCNode root) {
        this.root = root;
        this.nodeStack = new Stack<>();
    }

    private void printPrefix() throws IOException {
        for (Node node : nodeStack) {
            CNode cnode = node.node;
            if (cnode != root) {
                encodeString(cnode.getName());
                if (cnode.isArray) {
                    encodeString("[" + node.arrayIndex + "]");
                    if (!(cnode instanceof LeafCNode)) {
                        encodeString(".");
                    }
                } else if (cnode.isMap) {
                    encodeString("{\"" + node.mapKey + "\"}");
                    if (!(cnode instanceof LeafCNode)) {
                        encodeString(".");
                    }
                } else if (cnode instanceof LeafCNode) {
                    encodeString("");
                } else {
                    encodeString(".");
                }
            }
        }
        encodeString(" ");
    }

    private void encode(Inspector inspector, CNode node) throws IOException {
        switch (inspector.type()) {
            case BOOL:
                encodeValue(String.valueOf(inspector.asBool()), (LeafCNode) node);
                return;
            case LONG:
                encodeValue(String.valueOf(inspector.asLong()), (LeafCNode) node);
                return;
            case DOUBLE:
                encodeValue(String.valueOf(inspector.asDouble()), (LeafCNode) node);
                return;
            case STRING:
                encodeValue(inspector.asString(), (LeafCNode) node);
                return;
            case ARRAY:
                encodeArray(inspector, node);
                return;
            case OBJECT:
                if (node.isMap) {
                    encodeMap(inspector, node);
                } else {
                    encodeObject(inspector, node);
                }
                return;
            case NIX:
            case DATA:
                throw new IllegalArgumentException("Illegal config format supplied. Unknown type for field '" + node.getName() + "'");
        }
        throw new RuntimeException("Should not be reached");
    }

    private void encodeMap(Inspector inspector, final CNode node) {
        inspector.traverse(new ObjectTraverser() {
            @Override
            public void field(String name, Inspector inspector) {
                try {
                    nodeStack.push(new Node(node, -1, name));
                    if (inspector.type().equals(Type.OBJECT)) {
                        encodeObject(inspector, node);
                    } else {
                        encode(inspector, node);
                    }
                    nodeStack.pop();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void encodeArray(Inspector inspector, final CNode node) {
        inspector.traverse(new ArrayTraverser() {
            @Override
            public void entry(int idx, Inspector inspector) {
                try {
                    nodeStack.push(new Node(node, idx, ""));
                    encode(inspector, node);
                    nodeStack.pop();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }

    private void encodeObject(Inspector inspector, CNode node) {
        if (!node.isArray && !node.isMap) {
            nodeStack.push(new Node(node));
            inspector.traverse(this);
            nodeStack.pop();
        } else {
            inspector.traverse(this);
        }
    }

    private void encodeValue(String value, LeafCNode node) throws IOException {
        printPrefix();
        try {
            if (node instanceof LeafCNode.StringLeaf) {
                encodeStringQuoted(value);
            } else if (node instanceof LeafCNode.IntegerLeaf) {
                //Integer.parseInt(value);
                encodeString(value);
            } else if (node instanceof LeafCNode.LongLeaf) {
                //Long.parseLong(value);
                encodeString(value);
            } else if (node instanceof LeafCNode.DoubleLeaf) {
                //Double.parseDouble(value);
                encodeString(value);
            } else if (node instanceof LeafCNode.BooleanLeaf) {
                encodeString(String.valueOf(Boolean.parseBoolean(value)));
            } else if (node instanceof LeafCNode.EnumLeaf) {
                // LeafCNode.EnumLeaf enumNode = (LeafCNode.EnumLeaf) node;
                // TODO: Reenable this when we can return illegal config id.
                // checkLegalEnumValue(enumNode, value);
                encodeString(value);
            } else {
                encodeStringQuoted(value);
            }
            encodeString("\n");
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize field '" + node.getFullName() + "': ", e);
        }
    }

    private void checkLegalEnumValue(LeafCNode.EnumLeaf enumNode, String value) {
        boolean found = false;
        for (String legalVal : enumNode.getLegalValues()) {
            if (legalVal.equals(value)) {
                found = true;
            }
        }
        if (!found)
            throw new IllegalArgumentException("Illegal enum value '" + value + "'");
    }

    private void encodeStringQuoted(String s) throws IOException {
        encodeString("\"" + escapeString(s) + "\"");
    }

    private String escapeString(String s) {
        return ConfigUtils.escapeConfigFormatValue(s);
    }

    private void encodeString(String s) throws IOException {
        out.write(Utf8.toBytes(s));
    }

    @Override
    public void encode(OutputStream os, Slime slime) throws IOException {
        encode(os, slime.get());
    }

    private void encode(OutputStream os, Inspector inspector) throws IOException {
        this.out = new DataOutputStream(os);
        this.nodeStack = new Stack<>();
        nodeStack.push(new Node(root));
        encode(inspector, root);
    }

    @Override
    public void field(String fieldName,  Inspector inspector) {
        try {
            Node parent = nodeStack.peek();
            CNode child = parent.node.getChild(fieldName);
            if (child == null) {
                return; // Skip this field to optimistic
            }
            if (!child.isArray && !child.isMap && child instanceof LeafCNode) {
                nodeStack.push(new Node(child));
                encode(inspector, child);
                nodeStack.pop();
            } else {
                encode(inspector, child);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class Node {
        final int arrayIndex;
        final String mapKey;
        final CNode node;
        Node(CNode node, int arrayIndex, String mapKey) {
            this.node = node;
            this.arrayIndex = arrayIndex;
            this.mapKey = mapKey;
        }

        Node(CNode node) {
            this(node, -1, "");
        }
    }
}
