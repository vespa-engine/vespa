// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>

namespace search {
namespace tensor { class DenseTensorAttribute; }
namespace features {

/**
 * Executor for extracting dense tensors from an underlying dense tensor attribute
 * without copying cells data.
 */
class DenseTensorAttributeExecutor : public fef::FeatureExecutor
{
private:
    const search::tensor::DenseTensorAttribute *_attribute;
    vespalib::tensor::MutableDenseTensorView _tensorView;
    vespalib::eval::TensorValue _tensor;

public:
    DenseTensorAttributeExecutor(const search::tensor::DenseTensorAttribute *attribute);
    void execute(uint32_t docId) override;
};

} // namespace features
} // namespace search
