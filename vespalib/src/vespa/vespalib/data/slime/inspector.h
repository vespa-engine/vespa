// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "type.h"
#include "symbol.h"
#include <vespa/vespalib/data/memory.h>
#include <functional>
#include <variant>

namespace vespalib::slime {

struct ArrayTraverser;
struct ObjectSymbolTraverser;
struct ObjectTraverser;

struct Inspector {
    virtual bool valid() const = 0;
    virtual Type type() const = 0;
    virtual size_t children() const = 0;
    virtual size_t entries() const = 0;
    virtual size_t fields() const = 0;

    virtual bool asBool() const = 0;
    virtual int64_t asLong() const = 0;
    virtual double asDouble() const = 0;
    virtual Memory asString() const = 0;
    virtual Memory asData() const = 0;

    virtual void traverse(ArrayTraverser &at) const = 0;
    virtual void traverse(ObjectSymbolTraverser &ot) const = 0;
    virtual void traverse(ObjectTraverser &ot) const = 0;

    virtual vespalib::string toString() const = 0;

    virtual Inspector &operator[](size_t idx) const = 0;
    virtual Inspector &operator[](Symbol sym) const = 0;
    virtual Inspector &operator[](Memory name) const = 0;

    virtual ~Inspector() {}
};

bool operator==(const Inspector &a, const Inspector &b);
std::ostream &operator<<(std::ostream &os, const Inspector &inspector);

// check if two inspectors are equal
// has support for allowing specific mismatches
bool are_equal(const Inspector &a, const Inspector &b,
               std::function<bool(const std::vector<std::variant<size_t,std::string_view>> &path,
                                  const Inspector &sub_a, const Inspector &sub_b)> allow_mismatch);

} // namespace vespalib::slime
