// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/vespalib/util/doom.h>
#include <limits>

namespace search::queryeval {

class FakeRequestContext : public IRequestContext
{
public:
    FakeRequestContext(attribute::IAttributeContext * context = nullptr,
                       fastos::SteadyTimeStamp soft=fastos::SteadyTimeStamp::FUTURE,
                       fastos::SteadyTimeStamp hard=fastos::SteadyTimeStamp::FUTURE);
    ~FakeRequestContext();
    const vespalib::Doom & getDoom() const override { return _doom; }
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
        if (_query_tensor && (tensor_name == _query_tensor_name)) {
            return vespalib::tensor::DefaultTensorEngine::ref().from_spec(*_query_tensor);
        }
        return vespalib::eval::Value::UP();
    }
    void set_query_tensor(const vespalib::string& name, const vespalib::eval::TensorSpec& tensor_spec) {
        _query_tensor_name = name;
        _query_tensor = std::make_unique<vespalib::eval::TensorSpec>(tensor_spec);
    }

private:
    vespalib::Clock _clock;
    const vespalib::Doom _doom;
    attribute::IAttributeContext *_attributeContext;
    vespalib::string _query_tensor_name;
    std::unique_ptr<vespalib::eval::TensorSpec> _query_tensor;
};

}
