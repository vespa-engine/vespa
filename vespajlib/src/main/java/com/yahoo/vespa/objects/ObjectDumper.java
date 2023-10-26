// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import com.yahoo.vespa.objects.ObjectVisitor;

import java.lang.reflect.Array;
import java.util.List;

/**
 * This is a concrete object visitor that will build up a structured human-readable string representation of an object.
 *
 * @author Simon Thoresen Hult
 */
public class ObjectDumper extends ObjectVisitor {

    // The current string being written to.
    private final StringBuilder str = new StringBuilder();

    // The number of spaces to indent each level.
    private final int indent;

    // The current indent level.
    private int currIndent = 0;

    /**
     * Create an object dumper with the default indent size.
     */
    public ObjectDumper() {
        this(4);
    }

    /**
     * Create an object dumper with the given indent size.
     *
     * @param indent indent size in number of spaces
     */
    public ObjectDumper(int indent) {
        this.indent = indent;
    }

    /**
     * Add a number of spaces equal to the current indent to the string we are building.
     */
    private void addIndent() {
        int n = currIndent;
        for (int i = 0; i < n; ++i) {
            str.append(' ');
        }
    }

    /**
     * Add a complete line of output. Appropriate indentation will be added before the given string and a newline will
     * be added after it.
     *
     * @param line the line we want to add
     */
    private void addLine(String line) {
        addIndent();
        str.append(line);
        str.append('\n');
    }

    /**
     * Open a subscope by increasing the current indent level
     */
    private void openScope() {
        currIndent += indent;
    }

    /**
     * Close a subscope by decreasing the current indent level
     */
    private void closeScope() {
        currIndent -= indent;
    }

    /**
     * Obtain the created object string representation. This object should be invoked after the complete object
     * structure has been visited.
     *
     * @return object string representation
     */
    @Override
    public String toString() {
        return str.toString();
    }

    // Inherit doc from ObjectVisitor.
    @Override
    public void openStruct(String name, String type) {
        if (name == null || name.isEmpty()) {
            addLine(type + " {");
        } else {
            addLine(name + ": " + type + " {");
        }
        openScope();
    }

    // Inherit doc from ObjectVisitor.
    @Override
    public void closeStruct() {
        closeScope();
        addLine("}");
    }

    // Inherit doc from ObjectVisitor.
    @Override
    public void visit(String name, Object obj) {
        if (obj == null) {
            addLine(name + ": <NULL>");
        } else if (obj instanceof Identifiable) {
            openStruct(name, obj.getClass().getSimpleName());
            ((Identifiable)obj).visitMembers(this);
            closeStruct();
        } else if (obj instanceof String) {
            addLine(name + ": '" + obj + "'");
        } else if (obj.getClass().isArray()) {
            openStruct(name, obj.getClass().getComponentType().getSimpleName() + "[]");
            for (int i = 0, len = Array.getLength(obj); i < len; ++i) {
                visit("[" + i + "]", Array.get(obj, i));
            }
            closeStruct();
        } else if (obj instanceof List) {
            openStruct(name, "List");
            List<?> lst = (List<?>) obj;
            for (int i = 0; i < lst.size(); ++i) {
                visit("[" + i + "]", lst.get(i));
            }
            closeStruct();
        } else {
            addLine(name + ": " + obj);
        }
    }

}
