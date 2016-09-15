// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/eval/tensor_spec.h>
#include <vespa/vespalib/eval/value.h>
#include <vespa/vespalib/eval/value_type.h>
#include <vespa/vespalib/tensor/default_tensor_engine.h>
#include <memory>

namespace search {
namespace features {

/**
 * Feature executor that returns a constant tensor.
 */
class ConstantTensorExecutor : public fef::FeatureExecutor
{
private:
    const vespalib::eval::TensorValue::UP _tensor;

public:
    ConstantTensorExecutor(vespalib::eval::TensorValue::UP tensor)
        : _tensor(std::move(tensor))
    {}
    virtual bool isPure() override { return true; }
    virtual void execute(fef::MatchData &data) override {
        *data.resolve_object_feature(outputs()[0]) = *_tensor;
    }
    static fef::FeatureExecutor::LP create(std::unique_ptr<vespalib::eval::Tensor> tensor) {
        return FeatureExecutor::LP(new ConstantTensorExecutor
                                   (std::make_unique<vespalib::eval::TensorValue>(std::move(tensor))));
    }
    static fef::FeatureExecutor::LP createEmpty(const vespalib::eval::ValueType &valueType) {
        return create(vespalib::tensor::DefaultTensorEngine::ref()
                      .create(vespalib::eval::TensorSpec(valueType.to_spec())));
    }
    static fef::FeatureExecutor::LP createEmpty() {
        return createEmpty(vespalib::eval::ValueType::double_type());
    }
};

} // namespace features
} // namespace search
