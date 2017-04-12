// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/valuenode.h>
#include <vespa/searchlib/attribute/attributevector.h>

namespace proton
{


class AttributeFieldValueNode : public document::select::FieldValueNode
{
    search::AttributeVector::SP _attribute;

public:
    AttributeFieldValueNode(const vespalib::string& doctype,
                            const vespalib::string& field,
                            const search::AttributeVector::SP &attribute);

    virtual std::unique_ptr<document::select::Value>
    getValue(const document::select::Context &context) const override;

    virtual std::unique_ptr<document::select::Value>
    traceValue(const document::select::Context &context,
               std::ostream& out) const override;

    document::select::ValueNode::UP
    clone(void) const override
    {
        return wrapParens(new AttributeFieldValueNode(getDocType(),
                                                      getFieldName(),
                                                      _attribute));
    }
};

} // namespace proton


