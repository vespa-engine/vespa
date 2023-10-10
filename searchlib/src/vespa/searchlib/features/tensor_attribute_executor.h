// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::tensor { class ITensorAttribute; }
namespace search::features {

class TensorAttributeExecutor : public fef::FeatureExecutor
{
private:
    const search::tensor::ITensorAttribute& _attribute;
    std::unique_ptr<vespalib::eval::Value> _emptyTensor;
    std::unique_ptr<vespalib::eval::Value> _tensor;

public:
    TensorAttributeExecutor(const search::tensor::ITensorAttribute& attribute);
    void execute(uint32_t docId) override;
};

}
