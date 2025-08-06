// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initialization_status_wrapper_collector.h"

namespace proton {

AttributeInitializationStatusWrapperCollector::AttributeInitializationStatusWrapperCollector(std::vector<AttributeInitializationStatusWrapper::SP>& attributes)
    : _attributes(attributes) {
}

void AttributeInitializationStatusWrapperCollector::visitAttributeInitializer(AttributeInitializer& attributeInitializer) {
    _attributes.push_back(attributeInitializer.getInitializationStatusWrapper());
}

} // proton
