// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_spec.h"
#include <vector>

namespace proton {

/**
 * A specification of attribute vectors an attribute manager should
 * instantiate and manage.
 */
class AttributeSpecs
{
private:
    std::vector<AttributeSpec> _specs;
public:
    AttributeSpecs()
        : _specs()
    {
    }
    ~AttributeSpecs() { }
    const std::vector<AttributeSpec> &getSpecs() const { return _specs; }
    bool operator==(const AttributeSpecs &rhs) const { return _specs == rhs._specs; }
    template <typename ...Args>
    void emplace_back(Args && ... args) { _specs.emplace_back(std::forward<Args>(args)...); }
};

} // namespace proton

