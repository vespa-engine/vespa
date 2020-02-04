// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.codegen.CNode;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.codegen.LeafCNode;
import com.yahoo.slime.*;

/**
 * Applies default values of a given config definition to a slime payload.
 * TODO: Support giving correct type of default values
 *
 * @author Ulf Lilleengen
 */
public class DefaultValueApplier {

    public Slime applyDefaults(Slime slime, InnerCNode def) {
        applyDefaultsRecursive(slime.get(), def);
        return slime;
    }

    private void applyDefaultsRecursive(Cursor cursor, InnerCNode def) {
        if (def.isArray) {
            applyDefaultsToArray(cursor, def);
        } else if (def.isMap) {
            applyDefaultsToMap(cursor, def);
        } else {
            applyDefaultsToObject(cursor, def);
        }
    }

    private void applyDefaultsToMap(final Cursor cursor, final InnerCNode def) {
        cursor.traverse((ObjectTraverser) (name, inspector) -> applyDefaultsToObject(cursor.field(name), def));
    }

    private void applyDefaultsToArray(final Cursor cursor, final InnerCNode def) {
        cursor.traverse((ArrayTraverser) (idx, inspector) -> applyDefaultsToObject(cursor.entry(idx), def));
    }

    private void applyDefaultsToObject(Cursor cursor, InnerCNode def) {
        for (CNode child : def.getChildren()) {
            Cursor childCursor = cursor.field(child.getName());
            if (isLeafNode(child) && canApplyDefault(childCursor, child)) {
                applyDefaultToLeaf(cursor, child);
            } else if (isInnerNode(child)) {
                if (!childCursor.valid()) {
                    if (child.isArray) {
                        childCursor = cursor.setArray(child.getName());
                    } else {
                        childCursor = cursor.setObject(child.getName());
                    }
                }
                applyDefaultsRecursive(childCursor, (InnerCNode) child);
            }
        }
    }

    private boolean isInnerNode(CNode child) {
        return child instanceof InnerCNode;
    }

    private boolean isLeafNode(CNode child) {
        return child instanceof LeafCNode;
    }

    private void applyDefaultToLeaf(Cursor cursor, CNode child) {
        cursor.setString(child.getName(), ((LeafCNode) child).getDefaultValue().getValue());
    }

    private boolean canApplyDefault(Cursor cursor, CNode child) {
        return !cursor.valid() && ((LeafCNode) child).getDefaultValue() != null;
    }
}
