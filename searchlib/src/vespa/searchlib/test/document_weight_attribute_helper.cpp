// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_weight_attribute_helper.h"
#include <vespa/searchcommon/attribute/config.h>

namespace search::test {

AttributeVector::SP
DocumentWeightAttributeHelper::make_attr() {
    attribute::Config cfg(attribute::BasicType::INT64, attribute::CollectionType::WSET);
    cfg.setFastSearch(true);
    return AttributeFactory::createAttribute("my_attribute", cfg);
}

DocumentWeightAttributeHelper::~DocumentWeightAttributeHelper() = default;

}
