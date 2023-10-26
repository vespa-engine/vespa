// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "inserter.h"
#include "slime.h"

namespace vespalib::slime {

using ExtMemUP = ExternalMemory::UP;

Cursor &SlimeInserter::insertNix()                const { return slime.setNix(); }
Cursor &SlimeInserter::insertBool(bool value)     const { return slime.setBool(value); }
Cursor &SlimeInserter::insertLong(int64_t value)  const { return slime.setLong(value); }
Cursor &SlimeInserter::insertDouble(double value) const { return slime.setDouble(value); }
Cursor &SlimeInserter::insertString(Memory value) const { return slime.setString(value); }
Cursor &SlimeInserter::insertData(Memory value)   const { return slime.setData(value); }
Cursor &SlimeInserter::insertData(ExtMemUP value) const { return slime.setData(std::move(value)); }
Cursor &SlimeInserter::insertArray(size_t resv)   const { return slime.setArray(resv); }
Cursor &SlimeInserter::insertObject()             const { return slime.setObject(); }

Cursor &ArrayInserter::insertNix()                const { return cursor.addNix(); }
Cursor &ArrayInserter::insertBool(bool value)     const { return cursor.addBool(value); }
Cursor &ArrayInserter::insertLong(int64_t value)  const { return cursor.addLong(value); }
Cursor &ArrayInserter::insertDouble(double value) const { return cursor.addDouble(value); }
Cursor &ArrayInserter::insertString(Memory value) const { return cursor.addString(value); }
Cursor &ArrayInserter::insertData(Memory value)   const { return cursor.addData(value); }
Cursor &ArrayInserter::insertData(ExtMemUP value) const { return cursor.addData(std::move(value)); }
Cursor &ArrayInserter::insertArray(size_t resv)   const { return cursor.addArray(resv); }
Cursor &ArrayInserter::insertObject()             const { return cursor.addObject(); }

Cursor &ObjectSymbolInserter::insertNix()                const { return cursor.setNix(symbol); }
Cursor &ObjectSymbolInserter::insertBool(bool value)     const { return cursor.setBool(symbol, value); }
Cursor &ObjectSymbolInserter::insertLong(int64_t value)  const { return cursor.setLong(symbol, value); }
Cursor &ObjectSymbolInserter::insertDouble(double value) const { return cursor.setDouble(symbol, value); }
Cursor &ObjectSymbolInserter::insertString(Memory value) const { return cursor.setString(symbol, value); }
Cursor &ObjectSymbolInserter::insertData(Memory value)   const { return cursor.setData(symbol, value); }
Cursor &ObjectSymbolInserter::insertData(ExtMemUP value) const { return cursor.setData(symbol, std::move(value)); }
Cursor &ObjectSymbolInserter::insertArray(size_t resv)   const { return cursor.setArray(symbol, resv); }
Cursor &ObjectSymbolInserter::insertObject()             const { return cursor.setObject(symbol); }

Cursor &ObjectInserter::insertNix()                const { return cursor.setNix(name); }
Cursor &ObjectInserter::insertBool(bool value)     const { return cursor.setBool(name, value); }
Cursor &ObjectInserter::insertLong(int64_t value)  const { return cursor.setLong(name, value); }
Cursor &ObjectInserter::insertDouble(double value) const { return cursor.setDouble(name, value); }
Cursor &ObjectInserter::insertString(Memory value) const { return cursor.setString(name, value); }
Cursor &ObjectInserter::insertData(Memory value)   const { return cursor.setData(name, value); }
Cursor &ObjectInserter::insertData(ExtMemUP value) const { return cursor.setData(name, std::move(value)); }
Cursor &ObjectInserter::insertArray(size_t resv)   const { return cursor.setArray(name, resv); }
Cursor &ObjectInserter::insertObject()             const { return cursor.setObject(name); }

} // namespace vespalib::slime
