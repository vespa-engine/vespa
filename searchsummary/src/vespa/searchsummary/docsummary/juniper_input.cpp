// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniper_input.h"
#include "summaryfieldconverter.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>

namespace search::docsummary {

JuniperInput::JuniperInput()
    : _field_value_with_markup(),
      _value()
{
}

JuniperInput::JuniperInput(DocsumStoreFieldValue value)
    : _field_value_with_markup(std::move(value)),
      _value()
{
    if (_field_value_with_markup && _field_value_with_markup->isA(document::FieldValue::Type::STRING)) {
        const auto& string_field_value_with_markup = static_cast<const document::StringFieldValue&>(*_field_value_with_markup);
        _value = string_field_value_with_markup.getValueRef();
    }
}

JuniperInput::~JuniperInput() = default;

}
