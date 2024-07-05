// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <map>

namespace search::attribute::test {

class MockAttributeManager : public search::IAttributeManager {
protected:
    using AttributeMap = std::map<string, AttributeVector::SP>;

    AttributeMap _attributes;

    AttributeVector::SP findAttribute(const vespalib::string &name) const;
public:
    MockAttributeManager();
    ~MockAttributeManager() override;

    AttributeGuard::UP getAttribute(const vespalib::string &name) const override;
    void asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor>) const override;
    std::unique_ptr<AttributeReadGuard> getAttributeReadGuard(const vespalib::string &name, bool stableEnumGuard) const override;
    void getAttributeList(std::vector<AttributeGuard> &list) const override;
    IAttributeContext::UP createContext() const override;
    void addAttribute(const vespalib::string &name, const AttributeVector::SP &attr);
    void addAttribute(const AttributeVector::SP &attr);
    std::shared_ptr<attribute::ReadableAttributeVector> readable_attribute_vector(const string& name) const override;
};

}
