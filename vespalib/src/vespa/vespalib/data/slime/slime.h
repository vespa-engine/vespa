// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_traverser.h"
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

    std::unique_ptr<SymbolTable>   _names;
    std::unique_ptr<Stash>         _stash;
    RootValue                      _root;

public:
    typedef std::unique_ptr<Slime> UP;
    class Params {
    private:
        std::unique_ptr<SymbolTable>  _symbols;
        size_t                        _chunkSize;
    public:
        Params();
        explicit Params(std::unique_ptr<SymbolTable> symbols) noexcept;
        Params(Params &&) noexcept;
        ~Params();
        Params & setChunkSize(size_t chunkSize) {
            _chunkSize = chunkSize;
            return *this;
        }
        size_t getChunkSize() const { return _chunkSize; }
        std::unique_ptr<SymbolTable> detachSymbols();
    };
    /**
     * Construct an initially empty Slime object.
     **/
    explicit Slime(Params params = Params());

    ~Slime();

    Slime(Slime &&rhs) noexcept;
    Slime &operator=(Slime &&rhs) noexcept;

    Slime(const Slime & rhs) = delete;
    Slime& operator = (const Slime & rhs) = delete;

    static std::unique_ptr<SymbolTable> reclaimSymbols(Slime &&rhs);

    size_t symbols() const noexcept;

    Memory inspect(Symbol symbol) const;

    Symbol insert(Memory name);

    Symbol lookup(Memory name) const;

    Cursor &get() noexcept { return _root.get(); }

    Inspector &get() const noexcept { return _root.get(); }

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

bool operator == (const Slime & a, const Slime & b) noexcept;
std::ostream & operator << (std::ostream & os, const Slime & slime);

} // namespace vespalib

