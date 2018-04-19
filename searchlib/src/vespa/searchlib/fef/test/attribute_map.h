// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_context.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <memory>
#include <map>

#pragma once

namespace search::fef::test {

/**
 * Simple mapping from attribute name to IAttributeVector which can be used for tests
 * that do do not want the complexity of instantiating a full AttributeManager, or
 * for tests that need to work with IAttributeVectors rather than AttributeVectors.
 *
 * Allows for creating AttributeContext instances which transparently access the
 * attribute map for their lookups.
 */
class AttributeMap {
    std::map<vespalib::string, std::shared_ptr<attribute::IAttributeVector>> _attributes;
    std::map<vespalib::string, std::unique_ptr<attribute::AttributeReadGuard>> _guards;
public:
    using IAttributeVector = attribute::IAttributeVector;

    void add(std::shared_ptr<attribute::IAttributeVector> attr) {
        _attributes.emplace(attr->getName(), std::move(attr));
    }

    void add(std::unique_ptr<attribute::AttributeReadGuard> guard) {
        _guards.emplace(guard->attribute()->getName(), std::move(guard));
    }

    const IAttributeVector * getAttribute(const vespalib::string & name) const {
        auto attrItr = _attributes.find(name);
        if (attrItr != _attributes.end()) {
            return attrItr->second.get();
        }
        auto guardItr = _guards.find(name);
        if (guardItr != _guards.end()) {
            return guardItr->second->attribute();
        }
        return nullptr;
    }

    void getAttributeList(std::vector<const IAttributeVector *> & list) const {
        for (const auto& attr : _attributes) {
            list.emplace_back(attr.second.get());
        }
        for (const auto &guard : _guards) {
            list.emplace_back(guard.second->attribute());
        }
    }

    attribute::IAttributeContext::UP createContext() const {
        return std::make_unique<MockAttributeContext>(*this);
    }
};

}
