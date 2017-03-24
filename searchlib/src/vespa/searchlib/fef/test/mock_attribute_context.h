// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <memory>
#include <map>

#pragma once

namespace search {
namespace fef {
namespace test {

class AttributeMap;

class MockAttributeContext : public attribute::IAttributeContext {
    const AttributeMap& _attributes;
public:
    using IAttributeVector = attribute::IAttributeVector;

    explicit MockAttributeContext(const AttributeMap& attributes)
        : _attributes(attributes)
    {
    }

    const IAttributeVector * getAttribute(const string & name) const override;
    const IAttributeVector * getAttributeStableEnum(const string & name) const override;
    void getAttributeList(std::vector<const IAttributeVector *> & list) const override;
};

} // test
} // fef
} // search
