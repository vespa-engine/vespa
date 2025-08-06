// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include "attribute_initialization_status_wrapper.h"
#include "attribute_initializer.h"

namespace proton {

class AttributeInitializationStatusWrapperCollector : public initializer::InitializerTaskVisitor {
private:
    std::vector<AttributeInitializationStatusWrapper::SP>& _attributes;

public:
    AttributeInitializationStatusWrapperCollector(std::vector<AttributeInitializationStatusWrapper::SP>& attributes);
    void visitAttributeInitializer(AttributeInitializer& attributeInitializer) override;
};

} // proton

