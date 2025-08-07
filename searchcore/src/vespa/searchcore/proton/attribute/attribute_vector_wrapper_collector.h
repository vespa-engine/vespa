// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include "attribute_vector_wrapper.h"
#include "attribute_initializer.h"

namespace proton {

class AttributeVectorWrapperCollector : public initializer::InitializerTaskVisitor {
private:
    std::vector<AttributeVectorWrapper::SP>& _attributes;

public:
    AttributeVectorWrapperCollector(std::vector<AttributeVectorWrapper::SP>& attributes);
    void visitAttributeInitializer(AttributeInitializer& attributeInitializer) override;
};

} // proton

