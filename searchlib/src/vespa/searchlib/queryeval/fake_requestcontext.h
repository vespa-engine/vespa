// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <limits>

namespace search::queryeval {

class FakeRequestContext : public IRequestContext
{
public:
    FakeRequestContext(attribute::IAttributeContext * context = nullptr, fastos::SteadyTimeStamp doom=fastos::SteadyTimeStamp(fastos::TimeStamp::FUTURE));
    const vespalib::Doom & getSoftDoom() const override { return _doom; }
    const attribute::IAttributeVector *getAttribute(const vespalib::string &name) const override {
        return _attributeContext
                   ? _attributeContext->getAttribute(name)
                   : nullptr;
    }
    const attribute::IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const override {
        return _attributeContext
                   ? _attributeContext->getAttribute(name)
                   : nullptr;
    }
    vespalib::eval::Value::UP get_query_tensor(const vespalib::string& tensor_name) const override {
        (void) tensor_name;
        return vespalib::eval::Value::UP();
    }

private:
    vespalib::Clock _clock;
    const vespalib::Doom _doom;
    attribute::IAttributeContext *_attributeContext;
};

}
