// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_vector_wrapper_collector.h"

#include "attribute_initializer.h"

namespace proton {

AttributeVectorWrapperCollector::AttributeVectorWrapperCollector(std::vector<std::shared_ptr<AttributeVectorWrapper>>& attributes)
    : _attributes(attributes) {
}

void AttributeVectorWrapperCollector::visitAttributeInitializer(AttributeInitializer& attributeInitializer) {
    _attributes.push_back(attributeInitializer.getAttributeVectorWrapper());
}

} // proton
