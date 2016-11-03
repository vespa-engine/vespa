// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/eval/value.h>
#include <vespa/vespalib/tensor/dense/mutable_dense_tensor_view.h>

namespace search {
namespace attribute { class DenseTensorAttribute; }
namespace features {

/**
 * Executor for extracting dense tensors from an underlying dense tensor attribute
 * without copying cells data.
 */
class DenseTensorAttributeExecutor : public fef::FeatureExecutor
{
private:
    const search::attribute::DenseTensorAttribute *_attribute;
    vespalib::tensor::MutableDenseTensorView *_tensorView;
    vespalib::eval::TensorValue::UP _tensor;

public:
    DenseTensorAttributeExecutor(const search::attribute::DenseTensorAttribute *attribute);
    virtual void execute(fef::MatchData &data);
};

} // namespace features
} // namespace search
