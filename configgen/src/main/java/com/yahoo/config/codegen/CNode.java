// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.util.StringTokenizer;

/**
 * Abstract superclass for all nodes representing a config definition.
 *
 * @author gjoranv
 */
public abstract class CNode {

    // TODO: replace by "type" enum
    public final boolean isArray;
    public final boolean isMap;
    final String name;
    final InnerCNode parent;

    // TODO: remove! Only set for the root node, and root.getName() returns the same thing!
    String defName = null;

    String defNamespace = null;
    String defPackage = null;
    String defMd5 = "MISSING MD5";
    String comment = "";


    protected CNode(InnerCNode parent, String name) {
        this.parent = parent;
        int bracketIdx = name.indexOf('[');
        int curlyIdx = name.indexOf('{');
        if (bracketIdx != -1) {
            this.name = name.substring(0, bracketIdx);
            isArray = true;
            isMap = false;
        } else if (curlyIdx != -1) {
            this.name = name.substring(0, curlyIdx);
            isMap = true;
            isArray = false;
        } else {
            this.name = name;
            isMap = false;
            isArray = false;
        }
    }

    /**
     * Returns the simple name of this node.
     * @return the simple name of this node
     */
    public String getName() {
        return name;
    }

    public InnerCNode getParent() {
        return parent;
    }

    public abstract CNode[] getChildren();

    public abstract CNode getChild(String name);

    public String getMd5() {
        return defMd5;
    }

    void setMd5(String md5) {
        defMd5 = md5;
    }

    public String getNamespace() {
        if (defNamespace != null) return defNamespace;
        if (defPackage != null) return defPackage;
        return null;
    }

    void setNamespace(String namespace) {
        defNamespace = namespace;
    }

    public String getPackage() {
        return defPackage;
    }

    void setPackage(String defPackage) { this.defPackage = defPackage; }

    public String getComment() {
        return comment;
    }

    void setComment(String comment) {
        this.comment = comment;
    }

    protected abstract void setLeaf(String name, DefLine defLine, String comment)
            throws IllegalArgumentException;

    public abstract boolean needRestart();

    protected void checkMyName(String myName) throws IllegalArgumentException {
        if (isArray) {
            int n1 = myName.indexOf('[');
            int n2 = myName.indexOf(']');
            if (n1 == -1 || n2 < n1)
                throw new IllegalArgumentException("Invalid array syntax: " + myName);
            myName = myName.substring(0, n1);
        } else if (isMap) {
            int n1 = myName.indexOf('{');
            int n2 = myName.indexOf('}');
            if (n1 == -1 || n2 < n1)
                throw new IllegalArgumentException("Invalid map syntax: " + myName);
            myName = myName.substring(0, n1);
        } else if (myName.contains("[]")) {
            throw new IllegalArgumentException("Parameter with name '" + getName() + "' has already been declared as a non-array type.");
        } else if (myName.contains("{}")) {
            throw new IllegalArgumentException("Parameter with name '" + getName() + "' has already been declared as a non-map type.");
        }
        if (!myName.equals(getName()))
            throw new IllegalArgumentException(myName + " does not match " + getName() + ".");
    }

    /**
     * @return the full name as a config path of this node.
     */
    public String getFullName() {
        StringBuilder buf = new StringBuilder();
        if (parent != null)
            buf.append(parent.getFullName());
        if (buf.length() > 0)
            buf.append('.');
        StringBuilder theName = new StringBuilder(this.getName());

        if (isArray) theName.append("[]");
        else if (isMap) theName.append("{}");

        return buf.append(theName).toString();
    }

    /**
     * @param prefix  The prefix to use, usually an indent (spaces) followed by either '*' or "//"
     * @return a comment block where each line is prefixed, but the caller must close it if using '*'.
     */
    public String getCommentBlock(String prefix) {
        prefix = prefix + " ";
        StringBuilder ret = new StringBuilder();
        if (getComment().length() > 0) {
            StringTokenizer st = new StringTokenizer(getComment(), "\n");
            while (st.hasMoreTokens()) {
                ret.append(prefix).append(st.nextToken()).append("\n");
            }
        }
        return ret.toString();
    }

    @Override
    public String toString() {
        return "CNode{" +
                "namespace='" + defNamespace + '\'' +
                ", package='" + defPackage + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

}
