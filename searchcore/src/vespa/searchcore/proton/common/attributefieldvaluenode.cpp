// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefieldvaluenode.h"
#include "selectcontext.h"
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/attribute/attributevector.h>

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
using search::attribute::CollectionType;
using search::attribute::IAttributeVector;

AttributeFieldValueNode::
AttributeFieldValueNode(const vespalib::string& doctype,
                        const vespalib::string& field,
                        const std::shared_ptr<search::AttributeVector> &attribute)
    : FieldValueNode(doctype, field),
      _attribute(attribute)
{
    const AttributeVector &v(*_attribute);
    // Only handle single value attribute vectors for now
    assert(v.getCollectionType() == CollectionType::SINGLE);
    (void) v;
}


std::unique_ptr<document::select::Value>
AttributeFieldValueNode::
getValue(const Context &context) const
{
    const SelectContext &sc(static_cast<const SelectContext &>(context)); 
    uint32_t docId(sc._docId); 
    assert(docId != 0u);
    const AttributeVector &v(*_attribute);
    if (v.isUndefined(docId)) {
        return Value::UP(new NullValue);
    }
    switch (v.getBasicType()) {
    case BasicType::STRING:
        do {
            AttributeContent<const char *> content;
            content.fill(v, docId);
            assert(content.size() == 1u);
            return Value::UP(new StringValue(content[0]));
        } while (0);
        break;
    case BasicType::UINT1:
    case BasicType::UINT2:
    case BasicType::UINT4:
    case BasicType::INT8:
    case BasicType::INT16:
    case BasicType::INT32:
    case BasicType::INT64:
        do {
            AttributeContent<IAttributeVector::largeint_t> content;
            content.fill(v, docId);
            assert(content.size() == 1u);
            return Value::UP(new IntegerValue(content[0], false));
        } while (0);
        break;
    case BasicType::FLOAT:
    case BasicType::DOUBLE:
        do {
            AttributeContent<double> content;
            content.fill(v, docId);
            assert(content.size() == 1u);
            return Value::UP(new FloatValue(content[0]));
        } while (0);
        break;
    default:
        LOG_ABORT("should not be reached");
    }
    return Value::UP();

}


std::unique_ptr<Value>
AttributeFieldValueNode::traceValue(const Context &context,
                                    std::ostream& out) const
{
    return defaultTrace(getValue(context), out);
}


document::select::ValueNode::UP
AttributeFieldValueNode::clone() const
{
    return wrapParens(new AttributeFieldValueNode(getDocType(), getFieldName(), _attribute));
}

} // namespace proton

