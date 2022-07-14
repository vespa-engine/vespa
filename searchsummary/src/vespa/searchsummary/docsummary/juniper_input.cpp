// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniper_input.h"
#include "summaryfieldconverter.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>

namespace search::docsummary {

JuniperInput::JuniperInput(vespalib::stringref value)
    : _field_value_with_markup(),
      _value(value)
{
}

JuniperInput::JuniperInput(const document::FieldValue* value)
    : _field_value_with_markup(),
      _value()
{
    if (value != nullptr) {
        _field_value_with_markup = SummaryFieldConverter::convertSummaryField(true, *value);
    }
    if (_field_value_with_markup && _field_value_with_markup->isA(document::FieldValue::Type::STRING)) {
        const auto& string_field_value_with_markup = static_cast<document::StringFieldValue&>(*_field_value_with_markup);
        _value = string_field_value_with_markup.getValueRef();
    }
}

JuniperInput::~JuniperInput() = default;

}
