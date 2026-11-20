// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/distance_metric.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <string>

namespace document {
class FieldValue;
}

namespace streaming {

// Creates in-memory ext attributes for document fields (defined in searchvisitor.cpp).
search::AttributeVector::SP createAttribute(const std::string& name, const document::FieldValue& fv,
                                            search::attribute::DistanceMetric dm);
search::AttributeVector::SP createMultiValueAttribute(const std::string& name, const document::FieldValue& fv,
                                                      bool arrayType);

} // namespace streaming
