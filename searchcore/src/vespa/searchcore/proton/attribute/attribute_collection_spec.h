// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_spec.h"
#include <vespa/searchlib/common/serialnum.h>
#include <memory>
#include <vector>

namespace proton {

/**
 * A specification of which attribute vectors an attribute manager should instantiate and manage.
 */
class AttributeCollectionSpec
{
public:
    typedef std::unique_ptr<AttributeCollectionSpec> UP;

    typedef std::vector<AttributeSpec> AttributeList;

private:
    typedef search::SerialNum SerialNum;

    AttributeList _attributes;
    uint32_t      _docIdLimit;
    SerialNum     _currentSerialNum;

public:
    AttributeCollectionSpec(const AttributeList &attributes,
                            uint32_t docIdLimit,
                            SerialNum currentSerialNum);
    ~AttributeCollectionSpec();
    const AttributeList &getAttributes() const {
        return _attributes;
    }
    uint32_t getDocIdLimit() const {
        return _docIdLimit;
    }
    SerialNum getCurrentSerialNum() const {
        return _currentSerialNum;
    }
    bool hasAttribute(const vespalib::string &name) const {
        for (const auto &attr : _attributes) {
            if (attr.getName() == name) {
                return true;
            }
        }
        return false;
    }
};

} // namespace proton

