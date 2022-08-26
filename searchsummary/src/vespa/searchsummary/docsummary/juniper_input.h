// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_store_field_value.h"
#include <vespa/vespalib/stllike/string.h>

namespace document { class FieldValue; }

namespace search::docsummary {

/*
 * Class containing input for juniper processing.
 */
class JuniperInput {
    DocsumStoreFieldValue _field_value_with_markup;
    vespalib::stringref _value;
public:
    JuniperInput();
    explicit JuniperInput(DocsumStoreFieldValue value);
    ~JuniperInput();
    bool empty() const noexcept { return _value.empty(); }
    vespalib::stringref get_value() const noexcept { return _value; };
};

}
