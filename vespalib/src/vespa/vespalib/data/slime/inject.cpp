// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "inject.h"
#include "cursor.h"
#include "array_traverser.h"
#include "object_traverser.h"
#include <cstdlib>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.data.slime.inject");

namespace vespalib {
namespace slime {

namespace {

struct NestedInjector : ArrayTraverser, ObjectTraverser {
    Cursor &cursor;
    const Inspector *guard;
    NestedInjector(Cursor &c, const Inspector *g) : cursor(c), guard(g) {}
    void entry(size_t, const Inspector &inspector) override;
    void field(const Memory &symbol_name, const Inspector &inspector) override;
};

void injectNix(const Inserter &inserter) { inserter.insertNix(); }
void injectBool(const Inserter &inserter, bool value) { inserter.insertBool(value); }
void injectLong(const Inserter &inserter, int64_t value) { inserter.insertLong(value); }
void injectDouble(const Inserter &inserter, double value) { inserter.insertDouble(value); }
void injectString(const Inserter &inserter, const Memory &memory) { inserter.insertString(memory); }
void injectData(const Inserter &inserter, const Memory &memory) { inserter.insertData(memory); }
void injectArray(const Inserter &inserter, const Inspector &inspector, const Inspector *guard) {
    Cursor &cursor = inserter.insertArray();
    NestedInjector injector(cursor, (guard != 0) ? guard : &cursor);
    ArrayTraverser &array_traverser = injector;
    inspector.traverse(array_traverser);
}
void injectObject(const Inserter &inserter, const Inspector &inspector, const Inspector *guard) {
    Cursor &cursor = inserter.insertObject();
    NestedInjector injector(cursor, (guard != 0) ? guard : &cursor);
    ObjectTraverser &object_traverser = injector;
    inspector.traverse(object_traverser);
}
void injectValue(const Inserter &inserter, const Inspector &inspector, const Inspector *guard) {
    switch (inspector.type().getId()) {
    case NIX::ID:    return injectNix(inserter);
    case BOOL::ID:   return injectBool(inserter, inspector.asBool());
    case LONG::ID:   return injectLong(inserter, inspector.asLong());
    case DOUBLE::ID: return injectDouble(inserter, inspector.asDouble());
    case STRING::ID: return injectString(inserter, inspector.asString());
    case DATA::ID:   return injectData(inserter, inspector.asData());
    case ARRAY::ID:  return injectArray(inserter, inspector, guard);
    case OBJECT::ID: return injectObject(inserter, inspector, guard);
    }
    LOG_ABORT("should not be reached"); // should not be reached
}

void
NestedInjector::entry(size_t, const Inspector &inspector) {
    if (&inspector == guard) {
        return;
    }
    ArrayInserter inserter(cursor);
    injectValue(inserter, inspector, guard);
}

void
NestedInjector::field(const Memory &symbol_name, const Inspector &inspector) {
    if (&inspector == guard) {
        return;
    }
    ObjectInserter inserter(cursor, symbol_name);
    injectValue(inserter, inspector, guard);
}

} // namespace vespalib::slime::<unnamed>

void inject(const Inspector &inspector, const Inserter &inserter) {
    if (inspector.valid()) {
        injectValue(inserter, inspector, 0);
    }
}

} // namespace vespalib::slime
} // namespace vespalib
