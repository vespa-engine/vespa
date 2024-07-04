// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace search::attribute::test {

class MockAttributeContext : public IAttributeContext
{
private:
    using Map = vespalib::hash_map<string, std::shared_ptr<const IAttributeVector>>;
    Map _vectors;

public:
    MockAttributeContext();
    ~MockAttributeContext() override;
    void add(std::shared_ptr<const IAttributeVector> attr);

    const IAttributeVector *get(std::string_view name) const;
    const IAttributeVector * getAttribute(std::string_view name) const override;
    const IAttributeVector * getAttributeStableEnum(std::string_view name) const override;
    void getAttributeList(std::vector<const IAttributeVector *> & list) const override;
    void asyncForAttribute(std::string_view, std::unique_ptr<IAttributeFunctor>) const override;
};

}
