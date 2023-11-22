// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_object_visitor.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <sstream>

namespace search::attribute {

namespace {

vespalib::string
get_type(const IAttributeVector& attr)
{
    auto coll_type = CollectionType(attr.getCollectionType());
    auto basic_type = BasicType(attr.getBasicType());
    if (coll_type.type() == CollectionType::SINGLE) {
        return basic_type.asString();
    }
    std::ostringstream oss;
    oss << coll_type.asString() << "<" << basic_type.asString() << ">";
    return oss.str();
}

}

void
visit_attribute(vespalib::ObjectVisitor& visitor, const IAttributeVector& attr)
{
    visitor.openStruct("attribute", "IAttributeVector");
    visitor.visitString("name", attr.getName());
    visitor.visitString("type", get_type(attr));
    visitor.visitBool("fast_search", attr.getIsFastSearch());
    visitor.visitBool("filter", attr.getIsFilter());
    visitor.closeStruct();
}

}
