// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>

namespace search::docsummary {

/**
 * This class converts a summary field for docsum fetching.
 */
class SummaryFieldConverter
{
public:
    static document::FieldValue::UP convertSummaryField(bool markup, const document::FieldValue &value);

    /**
     * Converts the given field value to slime, only keeping the elements that are contained in the matching elements vector.
     *
     * Filtering occurs when the field value is an ArrayFieldValue or MapFieldValue.
     */
    static document::FieldValue::UP convert_field_with_filter(bool markup,
                                                              const document::FieldValue& value,
                                                              const std::vector<uint32_t>& matching_elems);
};

}

