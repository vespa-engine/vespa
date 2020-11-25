// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "inspector.h"
#include "external_memory.h"

namespace vespalib::slime {

struct Cursor : public Inspector {
    virtual Cursor &operator[](size_t idx) const override = 0;
    virtual Cursor &operator[](Symbol sym) const override = 0;
    virtual Cursor &operator[](Memory name) const override = 0;

    virtual Cursor &addNix() = 0;
    virtual Cursor &addBool(bool bit) = 0;
    virtual Cursor &addLong(int64_t l) = 0;
    virtual Cursor &addDouble(double d) = 0;
    virtual Cursor &addString(Memory str) = 0;
    virtual Cursor &addData(Memory data) = 0;
    virtual Cursor &addData(ExternalMemory::UP data) = 0;
    virtual Cursor &addArray() = 0;
    virtual Cursor &addObject() = 0;

    virtual Cursor &setNix(Symbol sym) = 0;
    virtual Cursor &setBool(Symbol sym, bool bit) = 0;
    virtual Cursor &setLong(Symbol sym, int64_t l) = 0;
    virtual Cursor &setDouble(Symbol sym, double d) = 0;
    virtual Cursor &setString(Symbol sym, Memory str) = 0;
    virtual Cursor &setData(Symbol sym, Memory data) = 0;
    virtual Cursor &setData(Symbol sym, ExternalMemory::UP data) = 0;
    virtual Cursor &setArray(Symbol sym) = 0;
    virtual Cursor &setObject(Symbol sym) = 0;

    virtual Cursor &setNix(Memory name) = 0;
    virtual Cursor &setBool(Memory name, bool bit) = 0;
    virtual Cursor &setLong(Memory name, int64_t l) = 0;
    virtual Cursor &setDouble(Memory name, double d) = 0;
    virtual Cursor &setString(Memory name, Memory str) = 0;
    virtual Cursor &setData(Memory name, Memory data) = 0;
    virtual Cursor &setData(Memory name, ExternalMemory::UP data) = 0;
    virtual Cursor &setArray(Memory name) = 0;
    virtual Cursor &setObject(Memory name) = 0;

    virtual Symbol resolve(Memory symbol_name) = 0;
};

}
