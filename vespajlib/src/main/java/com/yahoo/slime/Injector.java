// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * @author hakonhall
 */
public class Injector {
    /**
     * Inject a slime sub-structure described by an Inspector into a slime
     * structure where the insertion point is described by an
     * Inserter. This will copy all the values represented by the
     * Inspector into the position described by the Inserter. Note that
     * this can be used to either copy data from one Slime structure to
     * another, or to copy data internally within a single slime
     * structure. If the Inspector contains the insertion point it will
     * only be expanded once to avoid infinite recursion.
     *
     * @param inspector what to inject
     * @param inserter where to inject
     **/
    public void inject(Inspector inspector, Inserter inserter) {
        if (inspector.valid()) {
            injectValue(inserter, inspector, null);
        }
    }

    private void injectValue(Inserter inserter, Inspector inspector, Inspector guard) {
        switch (inspector.type()) {
            case NIX:    inserter.insertNIX(); break;
            case BOOL:   inserter.insertBOOL(inspector.asBool()); break;
            case LONG:   inserter.insertLONG(inspector.asLong()); break;
            case DOUBLE: inserter.insertDOUBLE(inspector.asDouble()); break;
            case STRING: inserter.insertSTRING(inspector.asString()); break;
            case DATA:   inserter.insertDATA(inspector.asData()); break;
            case ARRAY:  injectArray(inserter, inspector, guard); break;
            case OBJECT: injectObject(inserter, inspector, guard); break;
            default:     throw new IllegalArgumentException("Unknown type " + inspector.type());
        }
    }

    private void injectArray(Inserter inserter, Inspector inspector, Inspector guard) {
        Cursor cursor = inserter.insertARRAY();
        ArrayTraverser arrayTraverser = new NestedInjector(cursor, guard != null ? guard : cursor);
        inspector.traverse(arrayTraverser);
    }

    private void injectObject(Inserter inserter, Inspector inspector, Inspector guard) {
        Cursor cursor = inserter.insertOBJECT();
        ObjectTraverser objectTraverser = new NestedInjector(cursor, guard != null ? guard : cursor);
        inspector.traverse(objectTraverser);
    }

    private class NestedInjector implements ArrayTraverser, ObjectTraverser {
        private final Cursor cursor;
        private final Inspector guard;

        public NestedInjector(Cursor cursor, Inspector guard) {
            this.cursor = cursor;
            this.guard = guard;
        }

        @Override
        public void entry(int idx, Inspector inspector) {
            if (inspector == guard) {
                return;
            }

            var inserter = new ArrayInserter();
            inserter.adjust(cursor);
            injectValue(inserter, inspector, guard);
        }

        @Override
        public void field(String name, Inspector inspector) {
            if (inspector == guard) {
                return;
            }

            var inserter = new ObjectInserter();
            inserter.adjust(cursor, name);
            injectValue(inserter, inspector, guard);
        }
    }
}
