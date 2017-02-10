// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "inserter.h"
#include "slime.h"

namespace vespalib {
namespace slime {

Cursor &SlimeInserter::insertNix()                const { return slime.setNix(); }
Cursor &SlimeInserter::insertBool(bool value)     const { return slime.setBool(value); }
Cursor &SlimeInserter::insertLong(int64_t value)  const { return slime.setLong(value); }
Cursor &SlimeInserter::insertDouble(double value) const { return slime.setDouble(value); }
Cursor &SlimeInserter::insertString(Memory value) const { return slime.setString(value); }
Cursor &SlimeInserter::insertData(Memory value)   const { return slime.setData(value); }
Cursor &SlimeInserter::insertArray()              const { return slime.setArray(); }
Cursor &SlimeInserter::insertObject()             const { return slime.setObject(); }
Symbol  SlimeInserter::insert(Memory symbol_name) const { return slime.insert(symbol_name); }

Cursor &ArrayInserter::insertNix()                const { return cursor.addNix(); }
Cursor &ArrayInserter::insertBool(bool value)     const { return cursor.addBool(value); }
Cursor &ArrayInserter::insertLong(int64_t value)  const { return cursor.addLong(value); }
Cursor &ArrayInserter::insertDouble(double value) const { return cursor.addDouble(value); }
Cursor &ArrayInserter::insertString(Memory value) const { return cursor.addString(value); }
Cursor &ArrayInserter::insertData(Memory value)   const { return cursor.addData(value); }
Cursor &ArrayInserter::insertArray()              const { return cursor.addArray(); }
Cursor &ArrayInserter::insertObject()             const { return cursor.addObject(); }
Symbol  ArrayInserter::insert(Memory symbol_name) const { return cursor.insert(symbol_name); }

Cursor &ObjectSymbolInserter::insertNix()                const { return cursor.setNix(symbol); }
Cursor &ObjectSymbolInserter::insertBool(bool value)     const { return cursor.setBool(symbol, value); }
Cursor &ObjectSymbolInserter::insertLong(int64_t value)  const { return cursor.setLong(symbol, value); }
Cursor &ObjectSymbolInserter::insertDouble(double value) const { return cursor.setDouble(symbol, value); }
Cursor &ObjectSymbolInserter::insertString(Memory value) const { return cursor.setString(symbol, value); }
Cursor &ObjectSymbolInserter::insertData(Memory value)   const { return cursor.setData(symbol, value); }
Cursor &ObjectSymbolInserter::insertArray()              const { return cursor.setArray(symbol); }
Cursor &ObjectSymbolInserter::insertObject()             const { return cursor.setObject(symbol); }
Symbol  ObjectSymbolInserter::insert(Memory symbol_name) const { return cursor.insert(symbol_name); }

Cursor &ObjectInserter::insertNix()                const { return cursor.setNix(name); }
Cursor &ObjectInserter::insertBool(bool value)     const { return cursor.setBool(name, value); }
Cursor &ObjectInserter::insertLong(int64_t value)  const { return cursor.setLong(name, value); }
Cursor &ObjectInserter::insertDouble(double value) const { return cursor.setDouble(name, value); }
Cursor &ObjectInserter::insertString(Memory value) const { return cursor.setString(name, value); }
Cursor &ObjectInserter::insertData(Memory value)   const { return cursor.setData(name, value); }
Cursor &ObjectInserter::insertArray()              const { return cursor.setArray(name); }
Cursor &ObjectInserter::insertObject()             const { return cursor.setObject(name); }
Symbol  ObjectInserter::insert(Memory symbol_name) const { return cursor.insert(symbol_name); }

} // namespace slime
} // namespace vespalib
