// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "nix_value.h"
#include "resolved_symbol.h"
#include "empty_value_factory.h"
#include "basic_value_factory.h"
#include "external_data_value_factory.h"
#include <vespa/vespalib/data/simple_buffer.h>
#include "json_format.h"

namespace vespalib::slime {

bool
Value::valid() const {
    return (this != NixValue::invalid());
}

Type
Value::type() const {
    return NIX::instance;
}

size_t
Value::children() const {
    return 0;
}

size_t
Value::entries() const {
    return 0;
}

size_t
Value::fields() const {
    return 0;
}

// default NOPs for leaf values
Cursor &
Value::addLeaf(const ValueFactory &) { return *NixValue::invalid(); }
Cursor &
Value::setLeaf(Symbol, const ValueFactory &) { return *NixValue::invalid(); }
Cursor &
Value::setLeaf(Memory, const ValueFactory &) { return *NixValue::invalid(); }

// default values for accessors
bool
Value::asBool() const { return false; }
int64_t
Value::asLong() const { return 0; }
double
Value::asDouble() const { return 0.0; }
Memory
Value::asString() const { return Memory(); }
Memory
Value::asData() const { return Memory(); }

Cursor &
Value::operator[](size_t) const { return *NixValue::invalid(); }
Cursor &
Value::operator[](Symbol) const { return *NixValue::invalid(); }
Cursor &
Value::operator[](Memory) const { return *NixValue::invalid(); }

// default NOPs for traversal
void
Value::traverse(ArrayTraverser &) const {}
void
Value::traverse(ObjectSymbolTraverser &) const {}
void
Value::traverse(ObjectTraverser &) const {}

// generate string representation
vespalib::string
Value::toString() const
{
    SimpleBuffer buf;
    slime::JsonFormat::encode(*this, buf, false);
    return buf.get().make_string();
}

// 7 x add
Cursor &
Value::addNix() { return addLeaf(NixValueFactory()); }
Cursor &
Value::addBool(bool bit) { return addLeaf(BoolValueFactory(bit)); }
Cursor &
Value::addLong(int64_t l) { return addLeaf(LongValueFactory(l)); }
Cursor &
Value::addDouble(double d) { return addLeaf(DoubleValueFactory(d)); }
Cursor &
Value::addString(Memory str) { return addLeaf(StringValueFactory(str)); }
Cursor &
Value::addData(Memory data) { return addLeaf(DataValueFactory(data)); }
Cursor &
Value::addData(ExternalMemory::UP data) { return addLeaf(ExternalDataValueFactory(std::move(data))); }

// 7 x set (with numeric symbol id)
Cursor &
Value::setNix(Symbol sym) { return setLeaf(sym, NixValueFactory()); }
Cursor &
Value::setBool(Symbol sym, bool bit) { return setLeaf(sym, BoolValueFactory(bit)); }
Cursor &
Value::setLong(Symbol sym, int64_t l) { return setLeaf(sym, LongValueFactory(l)); }
Cursor &
Value::setDouble(Symbol sym, double d) { return setLeaf(sym, DoubleValueFactory(d)); }
Cursor &
Value::setString(Symbol sym, Memory str) { return setLeaf(sym, StringValueFactory(str)); }
Cursor &
Value::setData(Symbol sym, Memory data) { return setLeaf(sym, DataValueFactory(data)); }
Cursor &
Value::setData(Symbol sym, ExternalMemory::UP data) { return setLeaf(sym, ExternalDataValueFactory(std::move(data))); }

// 7 x set (with symbol name)
Cursor &
Value::setNix(Memory name) { return setLeaf(name, NixValueFactory()); }
Cursor &
Value::setBool(Memory name, bool bit) { return setLeaf(name, BoolValueFactory(bit)); }
Cursor &
Value::setLong(Memory name, int64_t l) { return setLeaf(name, LongValueFactory(l)); }
Cursor &
Value::setDouble(Memory name, double d) { return setLeaf(name, DoubleValueFactory(d)); }
Cursor &
Value::setString(Memory name, Memory str) { return setLeaf(name, StringValueFactory(str)); }
Cursor &
Value::setData(Memory name, Memory data) { return setLeaf(name, DataValueFactory(data)); }
Cursor &
Value::setData(Memory name, ExternalMemory::UP data) { return setLeaf(name, ExternalDataValueFactory(std::move(data))); }

// nop defaults for array/objects
Cursor &
Value::addArray(size_t) { return *NixValue::invalid(); }
Cursor &
Value::addObject() { return *NixValue::invalid(); }

Cursor &
Value::setArray(Symbol, size_t) { return *NixValue::invalid(); }
Cursor &
Value::setObject(Symbol) { return *NixValue::invalid(); }
Cursor &
Value::setArray(Memory, size_t) { return *NixValue::invalid(); }
Cursor &
Value::setObject(Memory) { return *NixValue::invalid(); }

Symbol
Value::resolve(Memory) { return Symbol(); }

} // namespace vespalib::slime
