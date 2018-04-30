// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.codegen.CNode;
import com.yahoo.config.codegen.LeafCNode;

import java.util.Arrays;

/**
 * Builds a ConfigDefinition from a tree of CNodes.
 *
 * @author hmusum
 */
public class ConfigDefinitionBuilder {

    /**
     * Creates a ConfigDefinition based on a tree generated from parsing a config
     * definition file.
     *
     * @param root the root node in a tree generated from parsing a config definition file.
     * @return a ConfigDefinition object
     */
    public static ConfigDefinition createConfigDefinition(CNode root) {
        ConfigDefinition def = new ConfigDefinition(root.getName(), root.getVersion(), root.getNamespace());

        for (CNode node : root.getChildren()) {
            addNode(def, node);
        }
        return def;
    }

    /**
     *
     * @param def a ConfigDefinition object
     * @param node the node to be added to the config definition
     */
    private static void addNode(ConfigDefinition def, CNode node) {
        String name = node.getName();
        if (node instanceof LeafCNode) {
            if (node.isArray) {
                //System.out.println("Adding array node " + name);
                String enumValues = null;
                String type = ((LeafCNode) node).getType();
                if ("enum".equals(type)) {
                    enumValues = convertToEnumValueCommaSeparated(((LeafCNode.EnumLeaf) node).getLegalValues());
                }
                def.arrayDef(name).setTypeSpec(
                        new ConfigDefinition.TypeSpec(name, ((LeafCNode) node).getType(), null, enumValues, null, null));

            } else if (node.isMap) {
		//System.out.println("Adding leaf map node " + name);
		def.leafMapDef(name).setTypeSpec(new ConfigDefinition.TypeSpec(name, ((LeafCNode) node).getType(), null, null, null, null));
            } else {
                //System.out.println("Adding basic node " + name);
                if (node instanceof LeafCNode.IntegerLeaf) {
                    addNode(def, (LeafCNode.IntegerLeaf) node);
                } else if (node instanceof LeafCNode.LongLeaf) {
                    addNode(def, (LeafCNode.LongLeaf) node);
                } else if (node instanceof LeafCNode.BooleanLeaf) {
                    addNode(def, (LeafCNode.BooleanLeaf) node);
                } else if (node instanceof LeafCNode.DoubleLeaf) {
                    addNode(def, (LeafCNode.DoubleLeaf) node);
                    // Need to come before StringLeaf, since it is a subclass of StringLeaf
                } else if (node instanceof LeafCNode.ReferenceLeaf) {
                    addNode(def, (LeafCNode.ReferenceLeaf) node);
                } else if (node instanceof LeafCNode.FileLeaf) {
                    addNode(def, (LeafCNode.FileLeaf) node);
                } else if (node instanceof LeafCNode.PathLeaf) {
                    addNode(def, (LeafCNode.PathLeaf) node);
                }else if (node instanceof LeafCNode.StringLeaf) {
                    addNode(def, (LeafCNode.StringLeaf) node);
                } else if (node instanceof LeafCNode.EnumLeaf) {
                    addNode(def, (LeafCNode.EnumLeaf) node);
                } else {
                    System.err.println("Unknown node type for node with name " + name);
                }
            }
        } else {
            ConfigDefinition newDef;
            if (node.isArray) {
                if (node.getChildren() != null && node.getChildren().length > 0) {
                    //System.out.println("\tAdding inner array node " + name);
                    newDef = def.innerArrayDef(name);
                    for (CNode childNode : node.getChildren()) {
                        //System.out.println("\tChild node " + childNode.getName());
                        addNode(newDef, childNode);
                    }
                }
            } else if (node.isMap) {
		//System.out.println("Adding struct map node " + name);
		newDef = def.structMapDef(name);
		if (node.getChildren() != null && node.getChildren().length > 0) {
                    for (CNode childNode : node.getChildren()) {
                        //System.out.println("\tChild node " + childNode.getName());
                        addNode(newDef, childNode);
                    }
                }

            } else {
                //System.out.println("Adding struct node " + name);
                newDef = def.structDef(name);
                if (node.getChildren() != null && node.getChildren().length > 0) {
                    for (CNode childNode : node.getChildren()) {
                        //System.out.println("\tChild node " + childNode.getName());
                        addNode(newDef, childNode);
                    }
                }
            }
        }
    }


    static void addNode(ConfigDefinition def, LeafCNode.IntegerLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addIntDef(leaf.getName(), Integer.valueOf(leaf.getDefaultValue().getValue()));
        } else {
            def.addIntDef(leaf.getName());
        }
    }

    static void addNode(ConfigDefinition def, LeafCNode.LongLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addLongDef(leaf.getName(), Long.valueOf(leaf.getDefaultValue().getValue()));
        } else {
            def.addLongDef(leaf.getName());
        }
    }

    static void addNode(ConfigDefinition def, LeafCNode.BooleanLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addBoolDef(leaf.getName(), Boolean.valueOf(leaf.getDefaultValue().getValue()));
        } else {
            def.addBoolDef(leaf.getName());
        }
    }

    static void addNode(ConfigDefinition def, LeafCNode.DoubleLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addDoubleDef(leaf.getName(), new Double(leaf.getDefaultValue().getValue()));
        } else {
            def.addDoubleDef(leaf.getName());
        }
    }

    static void addNode(ConfigDefinition def, LeafCNode.StringLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addStringDef(leaf.getName(), leaf.getDefaultValue().getValue());
        } else {
            def.addStringDef(leaf.getName());
        }
    }

    static void addNode(ConfigDefinition def, LeafCNode.ReferenceLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addReferenceDef(leaf.getName(), leaf.getDefaultValue().getValue());
        } else {
            def.addReferenceDef(leaf.getName(), null);
        }
    }

    static void addNode(ConfigDefinition def, LeafCNode.FileLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addFileDef(leaf.getName(), leaf.getDefaultValue().getValue());
        } else {
            def.addFileDef(leaf.getName(), null);
        }
    }

    static void addNode(ConfigDefinition def, LeafCNode.PathLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addPathDef(leaf.getName(), leaf.getDefaultValue().getValue());
        } else {
            def.addPathDef(leaf.getName(), null);
        }
    }

    static void addNode(ConfigDefinition def, LeafCNode.EnumLeaf leaf) {
        if (leaf.getDefaultValue() != null) {
            def.addEnumDef(leaf.getName(), Arrays.asList(leaf.getLegalValues()), leaf.getDefaultValue().getValue());
        } else {
            def.addEnumDef(leaf.getName(), Arrays.asList(leaf.getLegalValues()), null);
        }
    }

    static String convertToEnumValueCommaSeparated(String[] enumValues) {
        StringBuilder sb = new StringBuilder();
        for (String s : enumValues) {
            sb.append(s);
            sb.append(", ");
        }
        int length = sb.length();
        sb.delete(length - 2, length);
        return sb.toString();
    }
}
