// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include "attribute_vector_wrapper.h"

namespace proton {

class AttributeInitializer;

/**
 * Visitor that allows you to get the AttributeVectorWrappers created by the InitializerTask and its dependencies.
 *
 * It visits all AttributeInitializers in an InitializerTask and its dependencies.
 * For every AttributeInitializer, it collects a pointer to its AttributeVectorWrapper
 * in the given vector.
 */
class AttributeVectorWrapperCollector : public initializer::InitializerTaskVisitor {
private:
    std::vector<std::shared_ptr<AttributeVectorWrapper>>& _attributes;

public:
    AttributeVectorWrapperCollector(std::vector<std::shared_ptr<AttributeVectorWrapper>>& attributes);
    void visitAttributeInitializer(AttributeInitializer& attributeInitializer) override;
};

} // proton

