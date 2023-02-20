// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_spec.h"
#include <vespa/searchlib/common/serialnum.h>
#include <optional>
#include <vector>

namespace proton {

/**
 * A specification of which attribute vectors an attribute manager should instantiate and manage.
 */
class AttributeCollectionSpec
{
public:
    using AttributeList = std::vector<AttributeSpec>;

private:
    using SerialNum = search::SerialNum;

    AttributeList _attributes;
    uint32_t      _docIdLimit;
    std::optional<SerialNum> _currentSerialNum;

public:
    AttributeCollectionSpec(AttributeList && attributes, uint32_t docIdLimit, std::optional<SerialNum> currentSerialNum);
    ~AttributeCollectionSpec();
    const AttributeList &getAttributes() const {
        return _attributes;
    }
    AttributeList stealAttributes() {
        return std::move(_attributes);
    }
    uint32_t getDocIdLimit() const {
        return _docIdLimit;
    }
    const std::optional<SerialNum>& getCurrentSerialNum() const noexcept {
        return _currentSerialNum;
    }
    bool hasAttribute(const vespalib::string &name) const;
};

} // namespace proton

