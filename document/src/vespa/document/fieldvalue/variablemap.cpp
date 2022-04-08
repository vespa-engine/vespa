// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "variablemap.h"
#include "fieldvalue.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace document::fieldvalue {

IndexValue::IndexValue() : index(-1), key() {}
IndexValue::IndexValue(int index_) : index(index_), key() {}

bool
IndexValue::operator==(const IndexValue& other) const {
    if (key) {
        if (other.key && *key == *other.key) {
            return true;
        }
        return false;
    }

    return index == other.index;
}

IndexValue::IndexValue(const FieldValue& key_)
    : index(-1),
      key(key_.clone())
{ }

IndexValue::IndexValue(IndexValue && rhs) noexcept = default;
IndexValue & IndexValue::operator = (IndexValue && rhs) noexcept = default;
IndexValue::IndexValue(const IndexValue & rhs) :
    index(rhs.index),
    key(rhs.key ? rhs.key->clone() : nullptr)
{}
IndexValue & IndexValue::operator = (const IndexValue & rhs) {
    if (this != & rhs) {
        IndexValue tmp(rhs);
        std::swap(index, tmp.index);
        std::swap(key, tmp.key);
    }
    return *this;
}

IndexValue::~IndexValue() = default;

vespalib::string
IndexValue::toString() const {
    if (key) {
        return key->toString();
    } else {
        return vespalib::make_string("%d", index);
    }
}

VariableMap::VariableMap(VariableMap && rhs) noexcept = default;
VariableMap & VariableMap::operator = (VariableMap && rhs) noexcept = default;
VariableMap::VariableMap() = default;
VariableMap::~VariableMap() = default;

vespalib::string
VariableMap::toString() const {
    vespalib::asciistream out;
    out << "[ ";
    for (const auto & entry : *this) {
        out << entry.first << "=" << entry.second.toString() << " ";
    }
    out << "]";
    return out.str();
}

}

