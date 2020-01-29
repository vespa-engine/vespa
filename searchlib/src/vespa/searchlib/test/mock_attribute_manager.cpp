// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_manager.h"

namespace search::attribute::test {

AttributeVector::SP
MockAttributeManager::findAttribute(const vespalib::string &name) const {
    AttributeMap::const_iterator itr = _attributes.find(name);
    if (itr != _attributes.end()) {
        return itr->second;
    }
    return AttributeVector::SP();
}


MockAttributeManager::MockAttributeManager() = default;
MockAttributeManager::~MockAttributeManager() = default;

AttributeGuard::UP
MockAttributeManager::getAttribute(const vespalib::string &name) const {
    AttributeVector::SP attr = findAttribute(name);
    return AttributeGuard::UP(new AttributeGuard(attr));
}

void
MockAttributeManager::asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor>) const {
    throw std::runtime_error("search::MockAttributeManager::asyncForAttribute not implemented.");
}

std::unique_ptr<AttributeReadGuard>
MockAttributeManager::getAttributeReadGuard(const vespalib::string &name, bool stableEnumGuard) const {
    AttributeVector::SP attr = findAttribute(name);
    if (attr) {
        return attr->makeReadGuard(stableEnumGuard);
    } else {
        return std::unique_ptr<AttributeReadGuard>();
    }
}

void
MockAttributeManager::getAttributeList(std::vector<AttributeGuard> &list) const {
    list.reserve(_attributes.size());
    for (const auto &attr : _attributes) {
        list.push_back(AttributeGuard(attr.second));
    }
}

IAttributeContext::UP
MockAttributeManager::createContext() const {
    return IAttributeContext::UP(new AttributeContext(*this));
}

void
MockAttributeManager::addAttribute(const vespalib::string &name, const AttributeVector::SP &attr) {
    attr->addReservedDoc();
    _attributes[name] = attr;
}
void
MockAttributeManager::addAttribute(const AttributeVector::SP &attr) {
    addAttribute(attr->getName(), attr);
}

std::shared_ptr<attribute::ReadableAttributeVector>
MockAttributeManager::readable_attribute_vector(const string& name) const {
    return findAttribute(name);
}

}
