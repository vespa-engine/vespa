// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/iattributemanager.h>

namespace search {
namespace attribute {
namespace test {

class MockAttributeManager : public search::IAttributeManager {
protected:
    using AttributeMap = std::map<string, AttributeVector::SP>;

    AttributeMap _attributes;

    AttributeVector::SP
    findAttribute(const vespalib::string &name) const {
        AttributeMap::const_iterator itr = _attributes.find(name);
        if (itr != _attributes.end()) {
            return itr->second;
        }
        return AttributeVector::SP();
    }

public:
    MockAttributeManager() : _attributes() {}

    virtual AttributeGuard::UP getAttribute(const vespalib::string &name) const override {
        AttributeVector::SP attr = findAttribute(name);
        return AttributeGuard::UP(new AttributeGuard(attr));
    }

    virtual AttributeGuard::UP getAttributeStableEnum(const vespalib::string &name) const override {
        AttributeVector::SP attr = findAttribute(name);
        return AttributeGuard::UP(new AttributeEnumGuard(attr));
    }

    virtual void getAttributeList(std::vector<AttributeGuard> &list) const override {
        list.reserve(_attributes.size());
        for (const auto &attr : _attributes) {
            list.push_back(AttributeGuard(attr.second));
        }
    }

    virtual IAttributeContext::UP createContext() const override {
        return IAttributeContext::UP(new AttributeContext(*this));
    }

    void addAttribute(const vespalib::string &name, const AttributeVector::SP &attr) {
        attr->addReservedDoc();
        _attributes[name] = attr;
    }
};

}
}
}
