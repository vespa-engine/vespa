// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib { class ObjectVisitor; }

namespace search::attribute {

class IAttributeVector;

/**
 * Function used to visit the basic properties of an IAttributeVector.
 */
void visit_attribute(vespalib::ObjectVisitor& visitor, const IAttributeVector& attr);

}
