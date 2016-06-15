// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <limits>

namespace search {
namespace queryeval {

class FakeRequestContext : public IRequestContext
{
public:
    FakeRequestContext(attribute::IAttributeContext * context = nullptr, fastos::TimeStamp doom=std::numeric_limits<int64_t>::max());
    const vespalib::Doom & getDoom() const override { return _doom; }
    const AttributeVector * getAttribute(const vespalib::string & name) const override {
        return _attributeContext
                   ? dynamic_cast<const AttributeVector *>(_attributeContext->getAttribute(name))
                   : nullptr;
    }
    const AttributeVector * getAttributeStableEnum(const vespalib::string & name) const override {
        return _attributeContext
                   ? dynamic_cast<const AttributeVector *>(_attributeContext->getAttribute(name))
                   : nullptr;
    }
private:
    vespalib::Clock      _clock;
    const vespalib::Doom _doom;
    attribute::IAttributeContext   * _attributeContext;
};

}
}
