// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_context.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <memory>
#include <map>

#pragma once

namespace search {
namespace fef {
namespace test {

class AttributeMap {
    std::map<vespalib::string, std::shared_ptr<attribute::IAttributeVector>> _attributes;
public:
    using IAttributeVector = attribute::IAttributeVector;

    void add(std::shared_ptr<attribute::IAttributeVector> attr) {
        _attributes.emplace(attr->getName(), std::move(attr));
    }

    const IAttributeVector * getAttribute(const vespalib::string & name) const {
        auto iter = _attributes.find(name);
        return (iter != _attributes.end() ? iter->second.get() : nullptr);
    }

    void getAttributeList(std::vector<const IAttributeVector *> & list) const {
        for (const auto& attr : _attributes) {
            list.emplace_back(attr.second.get());
        }
    }

    attribute::IAttributeContext::UP createContext() const {
        return std::make_unique<MockAttributeContext>(*this);
    }
};

} // test
} // fef
} // search
