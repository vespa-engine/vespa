// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include "attribute_initialization_progress_reporter.h"
#include "attribute_initializer.h"

namespace proton {

class AttributeInitializationProgressReporterCollectingVisitor : public initializer::InitializerTaskVisitor {
private:
    std::vector<initializer::IInitializationProgressReporter::SP>& _attributes;

public:
    AttributeInitializationProgressReporterCollectingVisitor(std::vector<initializer::IInitializationProgressReporter::SP>& attributes);
    void visitAttributeInitializer(AttributeInitializer& attributeInitializer) override;
};

} // proton

