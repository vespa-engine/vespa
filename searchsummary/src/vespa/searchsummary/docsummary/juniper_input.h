// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace document { class FieldValue; }

namespace search::docsummary {

/*
 * Class containing input for juniper processing.
 */
class JuniperInput {
    std::unique_ptr<document::FieldValue> _field_value_with_markup;
    vespalib::stringref _value;
public:
    JuniperInput(vespalib::stringref value);
    JuniperInput(const document::FieldValue* value);
    ~JuniperInput();
    bool empty() const noexcept { return _value.empty(); }
    vespalib::stringref get_value() const noexcept { return _value; };
};

}
