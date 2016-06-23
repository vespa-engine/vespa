// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/eval/value.h>
#include <vespa/vespalib/tensor/default_tensor.h>
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/tensor/tensor_mapper.h>
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
    static fef::FeatureExecutor::LP create(vespalib::tensor::Tensor::UP tensor) {
        return FeatureExecutor::LP(new ConstantTensorExecutor
                (std::make_unique<vespalib::eval::TensorValue>(std::move(tensor))));
    }
    static fef::FeatureExecutor::LP createEmpty() {
        // XXX: we should use numbers instead of empty tensors
        vespalib::tensor::DefaultTensor::builder builder;
        return FeatureExecutor::LP(new ConstantTensorExecutor
                                   (std::make_unique<vespalib::eval::TensorValue>
                                           (builder.build())));
    }
    static fef::FeatureExecutor::LP createEmpty(const vespalib::tensor::TensorType &tensorType) {
        vespalib::tensor::DefaultTensor::builder builder;
        vespalib::tensor::TensorMapper mapper(tensorType);
        return FeatureExecutor::LP(new ConstantTensorExecutor
                                   (std::make_unique<vespalib::eval::TensorValue>
                                           (mapper.map(*builder.build()))));
    }
};

} // namespace features
} // namespace search
