// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>

namespace search::tensor { class DirectTensorAttribute; }
namespace search::features {

class DirectTensorAttributeExecutor : public fef::FeatureExecutor
{
public:
    using DirectTensorAttribute = search::tensor::DirectTensorAttribute;
    DirectTensorAttributeExecutor(const DirectTensorAttribute &attribute);
    void execute(uint32_t docId) override;
private:
    const DirectTensorAttribute &_attribute;
};

}
