// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_length_info.h"
#include <string>

namespace search::index {

/**
 * Interface used to inspect field length info for various index fields.
 */
class IFieldLengthInspector {
public:
    virtual ~IFieldLengthInspector() {}

    /**
     * Returns the field length info for the given index field, or empty info if the field is not found.
     */
    virtual FieldLengthInfo get_field_length_info(const std::string& field_name) const = 0;
};

}
