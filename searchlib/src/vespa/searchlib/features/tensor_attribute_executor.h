// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/eval/tensor/default_tensor.h>

namespace search {
namespace tensor { class TensorAttribute; }
namespace features {

class TensorAttributeExecutor : public fef::FeatureExecutor
{
private:
    const search::tensor::TensorAttribute *_attribute;
    std::unique_ptr<vespalib::eval::Tensor> _emptyTensor;
    vespalib::eval::TensorValue _tensor;

public:
    TensorAttributeExecutor(const search::tensor::TensorAttribute *attribute);
    virtual void execute(uint32_t docId);
};

} // namespace features
} // namespace search
