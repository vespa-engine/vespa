// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/i_field_length_inspector.h>

namespace search::index::test {

/**
 * Mock of IFieldLengthInspector returning empty field info for all fields.
 */
class MockFieldLengthInspector : public IFieldLengthInspector {
    FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override {
        (void) field_name;
        return FieldLengthInfo();
    }
};

}
