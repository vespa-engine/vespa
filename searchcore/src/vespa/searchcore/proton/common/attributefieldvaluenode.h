// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/valuenodes.h>

namespace search { class AttributeVector; }
namespace proton {

class AttributeFieldValueNode : public document::select::FieldValueNode
{
    using Context = document::select::Context;
    std::shared_ptr<search::AttributeVector> _attribute;

public:
    AttributeFieldValueNode(const vespalib::string& doctype,
                            const vespalib::string& field,
                            const std::shared_ptr<search::AttributeVector> &attribute);

    std::unique_ptr<document::select::Value> getValue(const Context &context) const override;
    std::unique_ptr<document::select::Value> traceValue(const Context &context, std::ostream& out) const override;
    document::select::ValueNode::UP clone() const override;
};

} // namespace proton
