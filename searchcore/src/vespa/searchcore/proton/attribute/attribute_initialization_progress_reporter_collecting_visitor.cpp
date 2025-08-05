// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initialization_progress_reporter_collecting_visitor.h"

namespace proton {

AttributeInitializationProgressReporterCollectingVisitor::AttributeInitializationProgressReporterCollectingVisitor(std::vector<initializer::IInitializationProgressReporter::SP>& attributes)
    : _attributes(attributes) {
}

void AttributeInitializationProgressReporterCollectingVisitor::visitAttributeInitializer(AttributeInitializer& attributeInitializer) {
    _attributes.push_back(attributeInitializer.getProgressReporter());
}

} // proton
