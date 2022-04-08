// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <memory>

namespace document {
    class FieldValue;
}

namespace document::fieldvalue {

class IndexValue {
public:
    IndexValue();
    IndexValue(int index_);
    IndexValue(const FieldValue& key_);
    IndexValue(IndexValue && rhs) noexcept;
    IndexValue & operator = (IndexValue && rhs) noexcept;
    IndexValue(const IndexValue & rhs);
    IndexValue & operator = (const IndexValue & rhs);

    ~IndexValue();

    vespalib::string toString() const;
    bool operator==(const IndexValue& other) const;

    int index; // For array
    std::unique_ptr<FieldValue> key; // For map/wset
};

using VariableMapT = std::map<vespalib::string, IndexValue>;

class VariableMap : public VariableMapT {
public:
    VariableMap();
    VariableMap(VariableMap && rhs) noexcept;
    VariableMap & operator = (VariableMap && rhs) noexcept;
    VariableMap(const VariableMap & rhs) = delete;
    VariableMap & operator = (const VariableMap & rhs) = delete;
    ~VariableMap();
    vespalib::string toString() const;
};

}
