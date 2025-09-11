// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initialization_status_collector.h"

namespace proton {

AttributeInitializationStatusCollector::AttributeInitializationStatusCollector(std::vector<std::shared_ptr<search::attribute::AttributeInitializationStatus>>& initialization_statuses)
    : _initialization_statuses(initialization_statuses) {
}

void AttributeInitializationStatusCollector::visitAttributeInitializer(AttributeInitializer& attribute_initializer) {
    _initialization_statuses.push_back(attribute_initializer.get_attribute_initialization_status());
}

} // proton
