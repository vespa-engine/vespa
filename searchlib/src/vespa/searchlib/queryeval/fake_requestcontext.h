// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "irequestcontext.h"
#include "create_blueprint_params.h"
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/thread_bundle.h>
#include <limits>

namespace vespalib { class TestClock; }
namespace search::queryeval {

class FakeRequestContext : public IRequestContext
{
public:
    FakeRequestContext();
    explicit FakeRequestContext(attribute::IAttributeContext * context,
                                vespalib::steady_time soft=vespalib::steady_time::max(),
                                vespalib::steady_time hard=vespalib::steady_time::max());
    ~FakeRequestContext() override;
    const vespalib::Doom & getDoom() const override { return _doom; }
    vespalib::ThreadBundle & thread_bundle() const override { return vespalib::ThreadBundle::trivial(); }
    const attribute::IAttributeVector *getAttribute(std::string_view name) const override {
        return _attributeContext
                   ? _attributeContext->getAttribute(name)
                   : nullptr;
    }
    const attribute::IAttributeVector *getAttributeStableEnum(std::string_view name) const override {
        return _attributeContext
                   ? _attributeContext->getAttribute(name)
                   : nullptr;
    }
    vespalib::eval::Value* get_query_tensor(const std::string& tensor_name) const override {
        if (_query_tensor && (tensor_name == _query_tensor_name)) {
            return _query_tensor.get();
        }
        return nullptr;
    }
    void set_query_tensor(const std::string& name, const vespalib::eval::TensorSpec& tensor_spec) {
        _query_tensor_name = name;
        _query_tensor = vespalib::eval::value_from_spec(tensor_spec, vespalib::eval::FastValueBuilderFactory::get());
    }

    const CreateBlueprintParams& get_create_blueprint_params() const override;
    const MetaStoreReadGuardSP * getMetaStoreReadGuard() const override { return nullptr; }

    CreateBlueprintParams& get_create_blueprint_params() { return _create_blueprint_params; }
private:
    std::unique_ptr<vespalib::TestClock> _clock;
    const vespalib::Doom _doom;
    attribute::IAttributeContext *_attributeContext;
    std::string _query_tensor_name;
    std::unique_ptr<vespalib::eval::Value> _query_tensor;
    CreateBlueprintParams _create_blueprint_params;
};

}
