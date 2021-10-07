// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_traverser.h"
#include "basic_value.h"
#include "basic_value_factory.h"
#include "binary_format.h"
#include "convenience.h"
#include "cursor.h"
#include "empty_value_factory.h"
#include "inject.h"
#include "inserter.h"
#include "inspector.h"
#include "json_format.h"
#include "named_symbol_inserter.h"
#include "named_symbol_lookup.h"
#include "nix_value.h"
#include "object_traverser.h"
#include "resolved_symbol.h"
#include "root_value.h"
#include "symbol.h"
#include "symbol_inserter.h"
#include "symbol_lookup.h"
#include "symbol_table.h"
#include "type.h"
#include "value.h"
#include "value_factory.h"
#include "external_data_value_factory.h"
#include <vespa/vespalib/data/input_reader.h>
#include <vespa/vespalib/data/output_writer.h>
#include <vespa/vespalib/data/output.h>

namespace vespalib {

/**
 * SLIME: 'Schema-Less Interface/Model/Exchange'. Slime is a way to
 * handle schema-less structured data to be used as part of interfaces
 * between components (RPC signatures), internal models
 * (config/parameters) and data exchange between components
 * (documents). The goal for Slime is to be flexible and lightweight
 * and at the same time limit the extra overhead in space and time
 * compared to schema-oriented approaches like protocol buffers and
 * avro. The data model is inspired by JSON and associative arrays
 * typically used in programming languages with dynamic typing.
 **/
class Slime
{
private:
    typedef slime::Symbol        Symbol;
    typedef slime::SymbolTable   SymbolTable;
    typedef slime::RootValue     RootValue;
    typedef slime::Cursor        Cursor;
    typedef slime::Inspector     Inspector;

    SymbolTable::UP              _names;
    Stash::UP                    _stash;
    RootValue                    _root;

public:
    typedef std::unique_ptr<Slime> UP;
    class Params {
    private:
        SymbolTable::UP  _symbols;
        size_t           _chunkSize;
    public:
        Params() : Params(std::make_unique<SymbolTable>()) { }
        explicit Params(SymbolTable::UP symbols) : _symbols(std::move(symbols)), _chunkSize(4096) { }
        Params & setChunkSize(size_t chunkSize) {
            _chunkSize = chunkSize;
            return *this;
        }
        size_t getChunkSize() const { return _chunkSize; }
        SymbolTable::UP detachSymbols() { return std::move(_symbols); }
    };
    /**
     * Construct an initially empty Slime object.
     **/
    explicit Slime(Params params = Params()) :
        _names(params.detachSymbols()),
        _stash(std::make_unique<Stash>(params.getChunkSize())),
        _root(_stash.get())
    { }

    ~Slime();

    Slime(Slime &&rhs) noexcept :
        _names(std::move(rhs._names)),
        _stash(std::move(rhs._stash)),
        _root(std::move(rhs._root))
    {
    }

    Slime(const Slime & rhs) = delete;
    Slime& operator = (const Slime & rhs) = delete;

    static SymbolTable::UP reclaimSymbols(Slime &&rhs) {
        rhs._stash.reset();
        rhs._root = RootValue(nullptr);
        return std::move(rhs._names);
    }

    Slime &operator=(Slime &&rhs) {
        _names = std::move(rhs._names);
        _stash = std::move(rhs._stash);
        _root = std::move(rhs._root);
        return *this;
    }

    size_t symbols() const {
        return _names->symbols();
    }

    Memory inspect(Symbol symbol) const {
        return _names->inspect(symbol);
    }

    Symbol insert(Memory name) {
        return _names->insert(name);
    }

    Symbol lookup(Memory name) const {
        return _names->lookup(name);
    }

    Cursor &get() { return _root.get(); }

    Inspector &get() const { return _root.get(); }

    template <typename ID>
    Inspector &operator[](ID id) const { return get()[id]; }

    template <typename ID>
    Cursor &operator[](ID id) { return get()[id]; }

    Cursor &setNix() {
        return _root.set(slime::NixValueFactory());
    }
    Cursor &setBool(bool bit) {
        return _root.set(slime::BoolValueFactory(bit));
    }
    Cursor &setLong(int64_t l) {
        return _root.set(slime::LongValueFactory(l));
    }
    Cursor &setDouble(double d) {
        return _root.set(slime::DoubleValueFactory(d));
    }
    Cursor &setString(const Memory& str) {
        return _root.set(slime::StringValueFactory(str));
    }
    Cursor &setData(const Memory& data) {
        return _root.set(slime::DataValueFactory(data));
    }
    Cursor &setData(slime::ExternalMemory::UP data) {
        return _root.set(slime::ExternalDataValueFactory(std::move(data)));
    }
    Cursor &setArray() {
        return setArray(0);
    }
    Cursor &setArray(size_t reserve) {
        return _root.set(slime::ArrayValueFactory(*_names, reserve));
    }
    Cursor &setObject() {
        return _root.set(slime::ObjectValueFactory(*_names));
    }

    Cursor &wrap(Symbol sym) {
        slime::ResolvedSymbol symbol(sym);
        return *_root.wrap(*_names, symbol);
    }

    Cursor &wrap(Memory name) {
        slime::NamedSymbolInserter symbol(*_names, name);
        return *_root.wrap(*_names, symbol);
    }

    vespalib::string toString() const { return get().toString(); }
};

bool operator == (const Slime & a, const Slime & b);
std::ostream & operator << (std::ostream & os, const Slime & slime);

} // namespace vespalib

