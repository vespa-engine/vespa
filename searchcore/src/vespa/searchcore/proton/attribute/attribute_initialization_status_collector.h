// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_initializer.h"
#include <vespa/searchcommon/attribute/attribute_initialization_status.h>
#include <vespa/searchcore/proton/initializer/initializer_task.h>

namespace proton {

/**
 * Visitor that allows you to get the AttributeVectorWrappers created by the InitializerTask and its dependencies.
 *
 * It visits all AttributeInitializers in an InitializerTask and its dependencies.
 * For every AttributeInitializer, it collects a pointer to its AttributeVectorWrapper
 * in the given vector.
 */
class AttributeInitializationStatusCollector : public initializer::InitializerTaskVisitor {
private:
    // Cries in fourth declension
    std::vector<std::shared_ptr<search::attribute::AttributeInitializationStatus>>& _initialization_statuses;

public:
    AttributeInitializationStatusCollector(std::vector<std::shared_ptr<search::attribute::AttributeInitializationStatus>>& initialization_statuses);
    void visitAttributeInitializer(AttributeInitializer& attribute_initializer) override;
};

} // proton

