// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace document {
    class FieldValue;
}

namespace document::fieldvalue {

class IndexValue {
public:
    IndexValue();
    IndexValue(int index_);
    IndexValue(const FieldValue& key_);
    IndexValue(IndexValue && rhs) = default;
    IndexValue & operator = (IndexValue && rhs) = default;
    IndexValue(const IndexValue & rhs);
    IndexValue & operator = (const IndexValue & rhs);

    ~IndexValue();

    vespalib::string toString() const;
    bool operator==(const IndexValue& other) const;

    int index; // For array
    vespalib::CloneablePtr<FieldValue> key; // For map/wset
};

using VariableMapT = std::map<vespalib::string, IndexValue>;

class VariableMap : public VariableMapT {
public:
    VariableMap();
    VariableMap(VariableMap && rhs) = default;
    VariableMap & operator = (VariableMap && rhs) = default;
    VariableMap(const VariableMap & rhs);
    VariableMap & operator = (const VariableMap & rhs);
    ~VariableMap();
    vespalib::string toString() const;
};

}
