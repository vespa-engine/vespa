// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/valuenodes.h>

namespace search { class ReadableAttributeVector; }
namespace proton {

class AttributeFieldValueNode : public document::select::FieldValueNode
{
    using Context = document::select::Context;
    uint32_t _attr_guard_index;

public:
    // Precondition: attribute must be of a single-value type.
    AttributeFieldValueNode(const vespalib::string& doctype,
                            const vespalib::string& field,
                            uint32_t attr_guard_index);

    std::unique_ptr<document::select::Value> getValue(const Context &context) const override;
    std::unique_ptr<document::select::Value> traceValue(const Context &context, std::ostream& out) const override;
    document::select::ValueNode::UP clone() const override;
};

} // namespace proton
