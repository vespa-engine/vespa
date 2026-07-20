// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/fef/featureexecutor.h>

#include <string>

namespace search::tensor {
class ITensorAttribute;
class TensorDequantizer;
} // namespace search::tensor
namespace search::features {

/*
 * Attribute executor that only operates on quantized tensor attributes,
 * transparently dequantizing any retrieved tensors.
 */
class DequantizingTensorAttributeExecutor : public fef::FeatureExecutor {
private:
    const search::tensor::ITensorAttribute&    _attribute;
    std::unique_ptr<tensor::TensorDequantizer> _dequantizer;
    std::unique_ptr<vespalib::eval::Value>     _empty_tensor;
    std::unique_ptr<vespalib::eval::Value>     _tensor;

public:
    DequantizingTensorAttributeExecutor(const search::tensor::ITensorAttribute& attribute);
    ~DequantizingTensorAttributeExecutor() override;
    void execute(uint32_t doc_id) override;
};

} // namespace search::features
