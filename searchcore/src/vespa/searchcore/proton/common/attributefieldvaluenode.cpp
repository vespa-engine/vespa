// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefieldvaluenode.h"
#include "selectcontext.h"
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.common.attribute_field_value_node");

namespace proton {

using document::select::Context;
using document::select::FloatValue;
using document::select::IntegerValue;
using document::select::NullValue;
using document::select::StringValue;
using document::select::Value;
using document::select::ValueNode;
using document::select::Visitor;
using search::AttributeVector;
using search::attribute::AttributeContent;
using search::attribute::BasicType;
using search::attribute::IAttributeVector;
using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::make_string;

AttributeFieldValueNode::
AttributeFieldValueNode(const vespalib::string& doctype,
                        const vespalib::string& field,
                        uint32_t attr_guard_index)
    : FieldValueNode(doctype, field),
      _attr_guard_index(attr_guard_index)
{
}


std::unique_ptr<document::select::Value>
AttributeFieldValueNode::
getValue(const Context &context) const
{
    const auto &sc(static_cast<const SelectContext &>(context));
    uint32_t docId(sc._docId); 
    assert(docId != 0u);
    const auto& v = sc.guarded_attribute_at_index(_attr_guard_index);
    if (v.isUndefined(docId)) {
        return std::make_unique<NullValue>();
    }
    switch (v.getBasicType()) {
        case BasicType::STRING:
            {
                AttributeContent<const char *> content;
                content.fill(v, docId);
                assert(content.size() == 1u);
                return std::make_unique<StringValue>(content[0]);
            };
        case BasicType::BOOL:
        case BasicType::UINT2:
        case BasicType::UINT4:
        case BasicType::INT8:
        case BasicType::INT16:
        case BasicType::INT32:
        case BasicType::INT64:
            {
                AttributeContent<IAttributeVector::largeint_t> content;
                content.fill(v, docId);
                assert(content.size() == 1u);
                return std::make_unique<IntegerValue>(content[0], false);
            }
        case BasicType::FLOAT:
        case BasicType::DOUBLE:
            {
                AttributeContent<double> content;
                content.fill(v, docId);
                assert(content.size() == 1u);
                return std::make_unique<FloatValue>(content[0]);
            };
        case BasicType::NONE:
        case BasicType::PREDICATE:
        case BasicType::TENSOR:
        case BasicType::REFERENCE:
            throw IllegalArgumentException(make_string("Attribute '%s' of type '%s' can not be used for selection",
                                                       v.getName().c_str(), BasicType(v.getBasicType()).asString()));
        case BasicType::MAX_TYPE:
            throw IllegalStateException(make_string("Attribute '%s' has illegal type '%d'", v.getName().c_str(), v.getBasicType()));
    }
    return std::make_unique<NullValue>();;

}


std::unique_ptr<Value>
AttributeFieldValueNode::traceValue(const Context &context, std::ostream& out) const
{
    return defaultTrace(getValue(context), out);
}


document::select::ValueNode::UP
AttributeFieldValueNode::clone() const
{
    return wrapParens(std::make_unique<AttributeFieldValueNode>(getDocType(), getFieldName(), _attr_guard_index));
}

} // namespace proton

