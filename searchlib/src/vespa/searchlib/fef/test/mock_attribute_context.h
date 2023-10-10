// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <memory>
#include <map>

#pragma once

namespace search::fef::test {

class AttributeMap;

/**
 * Simple IAttributeContext implementation which forwards all attribute lookups
 * to a referenced AttributeMap.
 *
 * Note that the attribute mapping does not use any kind of snapshot visibility;
 * changes to the associated AttributeMap after the context has been created will
 * be reflected in subsequent calls to the attribute context.
 */
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

    void
    asyncForAttribute(const vespalib::string &name, std::unique_ptr<attribute::IAttributeFunctor> func) const override;
};

}
