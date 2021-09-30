// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/util/stash.h>

namespace search::features {

/**
 * Feature executor that returns a constant tensor.
 */
class ConstantTensorExecutor : public fef::FeatureExecutor
{
private:
    vespalib::eval::Value::UP _tensor;

public:
    ConstantTensorExecutor(vespalib::eval::Value::UP tensor)
        : _tensor(std::move(tensor))
    {}
    bool isPure() override { return true; }
    void execute(uint32_t) override {
        outputs().set_object(0, *_tensor);
    }
    static fef::FeatureExecutor &create(std::unique_ptr<vespalib::eval::Value> tensor, vespalib::Stash &stash) {
        return stash.create<ConstantTensorExecutor>(std::move(tensor));
    }
    static fef::FeatureExecutor &createEmpty(const vespalib::eval::ValueType &valueType, vespalib::Stash &stash) {
        const auto &factory = vespalib::eval::FastValueBuilderFactory::get();
        auto spec = vespalib::eval::TensorSpec(valueType.to_spec());
        return stash.create<ConstantTensorExecutor>(vespalib::eval::value_from_spec(spec, factory));
    }
    static fef::FeatureExecutor &createEmpty(vespalib::Stash &stash) {
        return createEmpty(vespalib::eval::ValueType::double_type(), stash);
    }
};

}
