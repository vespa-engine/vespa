// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_context.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::attribute::test {

const IAttributeVector *
MockAttributeContext::get(std::string_view name) const {
    if (_vectors.find(name) == _vectors.end()) {
        return nullptr;
    }
    return _vectors.find(name)->second.get();
}
const IAttributeVector *
MockAttributeContext::getAttribute(std::string_view name) const {
    return get(name);
}
const IAttributeVector *
MockAttributeContext::getAttributeStableEnum(std::string_view name) const {
    return get(name);
}
void
MockAttributeContext::getAttributeList(std::vector<const IAttributeVector *> & list) const {
    for (const auto& elem : _vectors) {
        list.push_back(elem.second.get());
    }
}

MockAttributeContext::MockAttributeContext() = default;
MockAttributeContext::~MockAttributeContext() = default;

void
MockAttributeContext::add(std::shared_ptr<const IAttributeVector> attr) {
    _vectors[attr->getName()] = std::move(attr);
}

void
MockAttributeContext::asyncForAttribute(std::string_view, std::unique_ptr<IAttributeFunctor>) const {
    throw std::runtime_error("MockAttributeContext::asyncForAttribute is not implemented and should not be reached");
}

}
