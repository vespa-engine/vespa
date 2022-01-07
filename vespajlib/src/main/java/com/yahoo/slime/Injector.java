// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
     */
    public void inject(Inspector inspector, Inserter inserter) {
        if (inspector.valid()) {
            injectValue(inserter, inspector, null);
        }
    }

    private void injectValue(Inserter inserter, Inspector inspector, Inspector guard) {
        inspector.accept(new Visitor() {
            @Override public void visitInvalid()           { }
            @Override public void visitNix()               { inserter.insertNIX(); }
            @Override public void visitBool(boolean bit)   { inserter.insertBOOL(bit); }
            @Override public void visitLong(long l)        { inserter.insertLONG(l); }
            @Override public void visitDouble(double d)    { inserter.insertDOUBLE(d); }
            @Override public void visitString(String str)  { inserter.insertSTRING(str); }
            @Override public void visitString(byte[] utf8) { inserter.insertSTRING(utf8); }
            @Override public void visitData(byte[] data)   { inserter.insertDATA(data); }

            @Override
            public void visitArray(Inspector arr) {
                Cursor cursor = inserter.insertARRAY();
                ArrayTraverser arrayTraverser = new NestedInjector(cursor, guard != null ? guard : cursor);
                arr.traverse(arrayTraverser);
            }
            @Override
            public void visitObject(Inspector obj) {
                Cursor cursor = inserter.insertOBJECT();
                ObjectTraverser objectTraverser = new NestedInjector(cursor, guard != null ? guard : cursor);
                obj.traverse(objectTraverser);
            }
        });
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

            injectValue(new ArrayInserter(cursor), inspector, guard);
        }

        @Override
        public void field(String name, Inspector inspector) {
            if (inspector == guard) {
                return;
            }

            injectValue(new ObjectInserter(cursor, name), inspector, guard);
        }
    }

}
