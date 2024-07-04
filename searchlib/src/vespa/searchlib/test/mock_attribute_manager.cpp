// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_manager.h"
#include <memory>

namespace search::attribute::test {

AttributeVector::SP
MockAttributeManager::findAttribute(std::string_view name) const {
    auto itr = _attributes.find(vespalib::string(name));
    if (itr != _attributes.end()) {
        return itr->second;
    }
    return {};
}


MockAttributeManager::MockAttributeManager() = default;
MockAttributeManager::~MockAttributeManager() = default;

AttributeGuard::UP
MockAttributeManager::getAttribute(std::string_view name) const {
    AttributeVector::SP attr = findAttribute(name);
    return std::make_unique<AttributeGuard>(attr);
}

void
MockAttributeManager::asyncForAttribute(std::string_view , std::unique_ptr<IAttributeFunctor>) const {
    throw std::runtime_error("search::MockAttributeManager::asyncForAttribute not implemented.");
}

std::unique_ptr<AttributeReadGuard>
MockAttributeManager::getAttributeReadGuard(std::string_view name, bool stableEnumGuard) const {
    AttributeVector::SP attr = findAttribute(name);
    if (attr) {
        return attr->makeReadGuard(stableEnumGuard);
    } else {
        return {};
    }
}

void
MockAttributeManager::getAttributeList(std::vector<AttributeGuard> &list) const {
    list.reserve(_attributes.size());
    for (const auto &attr : _attributes) {
        list.emplace_back(attr.second);
    }
}

IAttributeContext::UP
MockAttributeManager::createContext() const {
    return std::make_unique<AttributeContext>(*this);
}

void
MockAttributeManager::addAttribute(std::string_view name, const AttributeVector::SP &attr) {
    attr->addReservedDoc();
    _attributes[vespalib::string(name)] = attr;
}
void
MockAttributeManager::addAttribute(const AttributeVector::SP &attr) {
    addAttribute(attr->getName(), attr);
}

std::shared_ptr<attribute::ReadableAttributeVector>
MockAttributeManager::readable_attribute_vector(std::string_view name) const {
    return findAttribute(name);
}

}
