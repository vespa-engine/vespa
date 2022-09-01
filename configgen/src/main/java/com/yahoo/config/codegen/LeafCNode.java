// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

/**
 * @author gjoranv
 */
public abstract class LeafCNode extends CNode {

    private boolean isInitialized = false;
    private DefaultValue defaultValue = null;
    private boolean restart = false;

    /** Constructor for the leaf nodes */
    protected LeafCNode(InnerCNode parent, String name) {
        super(parent, name);
    }

    public static LeafCNode newInstance(DefLine.Type type, InnerCNode parent, String name) {
        try {
            switch (type.name) {
                case "int": return new IntegerLeaf(parent, name);
                case "long": return new LongLeaf(parent, name);
                case "double": return new DoubleLeaf(parent, name);
                case "bool": return new BooleanLeaf(parent, name);
                case "string": return new StringLeaf(parent, name);
                case "reference": return new ReferenceLeaf(parent, name);
                case "file": return new FileLeaf(parent, name);
                case "path": return new PathLeaf(parent, name);
                case "enum": return new EnumLeaf(parent, name, type.enumArray);
                case "url" : return new UrlLeaf(parent, name);
                case "model" : return new ModelLeaf(parent, name);
                default: return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LeafCNode newInstance(DefLine.Type type, InnerCNode parent, String name, String defVal) {
        LeafCNode ret = newInstance(type, parent, name);
        if (defVal!=null) {
            DefaultValue def = new DefaultValue(defVal, type);
            ret.setDefaultValue(def);
        }
        return ret;
    }

    public abstract String getType();

    @Override
    public CNode[] getChildren() {
        return new CNode[0];
    }

    @Override
    public CNode getChild(String name) {
        return null;
    }

    public DefaultValue getDefaultValue() {
        return defaultValue;
    }

    public LeafCNode setDefaultValue(DefaultValue defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * @param defaultValue the value to check.
     * @throws IllegalArgumentException if the value is illegal according to the node type.
     */
    public void checkDefaultValue(DefaultValue defaultValue) throws IllegalArgumentException {
    }

    @Override
    protected void setLeaf(String name, DefLine defLine, String comment) throws IllegalArgumentException {
        DefLine.Type type = defLine.getType();
        // TODO: why the !is... conditions?
        if (!isMap && !isArray && isInitialized) {
            throw new IllegalArgumentException(name + " is already defined");
        }
        isInitialized = true;
        checkMyName(name);
        if (!type.name.equalsIgnoreCase(getType())) {
            throw new IllegalArgumentException("Type " + type.name + " does not match " + getType());
        }
        setValue(defLine.getDefault());
        setComment(comment);
        restart |= defLine.getRestart();
    }

    @Override
    public boolean needRestart() {
        return restart;
    }

    public final void setValue(DefaultValue defaultValue) throws IllegalArgumentException {
        try {
            checkDefaultValue(defaultValue);
            setDefaultValue(defaultValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid default value", e);
        }
    }

    /**
     * Superclass for leaf nodes that should not generate class.
     */
    public static abstract class NoClassLeafCNode extends LeafCNode {
        protected NoClassLeafCNode(InnerCNode parent, String name) {
            super(parent, name);
        }
    }

    /**
     * Superclass for no-class leaf nodes that cannot have a default.
     */
    public static abstract class NoClassNoDefaultLeafCNode extends LeafCNode {
        protected NoClassNoDefaultLeafCNode(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public LeafCNode setDefaultValue(DefaultValue defaultValue) {
            if (defaultValue != null)
                throw new IllegalArgumentException("Parameters of type '" + getType() + "' cannot have a default value.");
            return this;
        }
    }

    public static class IntegerLeaf extends NoClassLeafCNode {
        protected IntegerLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "int";
        }
    }

    public static class LongLeaf extends NoClassLeafCNode {
        protected LongLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "long";
        }
    }

    public static class DoubleLeaf extends NoClassLeafCNode {
        protected DoubleLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "double";
        }
    }

    public static class BooleanLeaf extends NoClassLeafCNode {
        protected BooleanLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "bool";
        }
    }

    public static class StringLeaf extends NoClassLeafCNode {
        protected StringLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "string";
        }
    }

    public static class ReferenceLeaf extends StringLeaf {
        ReferenceLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "reference";
        }
    }

    public static class FileLeaf extends NoClassNoDefaultLeafCNode {
        FileLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "file";
        }
    }

    public static class PathLeaf extends NoClassNoDefaultLeafCNode {
        PathLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "path";
        }
    }

    public static class UrlLeaf extends NoClassLeafCNode {
        UrlLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "url";
        }
    }

    public static class ModelLeaf extends NoClassLeafCNode {
        ModelLeaf(InnerCNode parent, String name) {
            super(parent, name);
        }

        @Override
        public String getType() {
            return "model";
        }
    }

    public static class EnumLeaf extends LeafCNode {

        private final String[] legalValues;

        protected EnumLeaf(InnerCNode parent, String name, String[] valArray) {
            super(parent, name);
            this.legalValues = valArray;
        }

        @Override
        public String getType() {
            return "enum";
        }

        /** Returns this enum's legal values. */
        public String[] getLegalValues() {
            return legalValues;
        }

        @Override
        public void checkDefaultValue(DefaultValue defaultValue) throws IllegalArgumentException {
            if ((defaultValue != null) && (defaultValue.getValue() != null))  {
                String defaultString = null;
                String value = defaultValue.getValue();
                for (String val : legalValues) {
                    if (value.equals(val)) {
                        defaultString = val;
                    }
                }
                if (defaultString == null)
                    throw new IllegalArgumentException("Could not initialize enum with: " + value);
            }
        }
    }

}
