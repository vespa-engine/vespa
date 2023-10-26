// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>

namespace search::tensor { class ITensorAttribute; }
namespace search::features {

class DirectTensorAttributeExecutor : public fef::FeatureExecutor
{
public:
    using ITensorAttribute = search::tensor::ITensorAttribute;
    DirectTensorAttributeExecutor(const ITensorAttribute &attribute);
    void execute(uint32_t docId) override;
private:
    const ITensorAttribute &_attribute;
};

}
