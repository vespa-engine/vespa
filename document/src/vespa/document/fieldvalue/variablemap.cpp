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
    if (key.get() != NULL) {
        if (other.key.get() != NULL && *key == *other.key) {
            return true;
        }
        return false;
    }

    return index == other.index;
}

IndexValue::IndexValue(const FieldValue& key_)
        : index(-1),
          key(FieldValue::CP(key_.clone()))
{ }

IndexValue::IndexValue(const IndexValue & rhs) = default;
IndexValue & IndexValue::operator = (const IndexValue & rhs) = default;

IndexValue::~IndexValue() { }

vespalib::string
IndexValue::toString() const {
    if (key.get() != NULL) {
        return key->toString();
    } else {
        return vespalib::make_string("%d", index);
    }
}

VariableMap::VariableMap() {}
VariableMap::~VariableMap() {}
VariableMap::VariableMap(const VariableMap & rhs) = default;
VariableMap & VariableMap::operator = (const VariableMap & rhs) = default;

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

