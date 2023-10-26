// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <map>

namespace search::attribute::test {

class MockAttributeContext : public IAttributeContext
{
private:
    using Map = std::map<string, IAttributeVector *>;
    Map _vectors;

public:
    ~MockAttributeContext() override;
    void add(IAttributeVector *attr);

    const IAttributeVector *get(const string &name) const;
    const IAttributeVector * getAttribute(const string &name) const override;
    const IAttributeVector * getAttributeStableEnum(const string &name) const override;
    void getAttributeList(std::vector<const IAttributeVector *> & list) const override;
    void asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor>) const override;
};

}
