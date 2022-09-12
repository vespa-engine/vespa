// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>

namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

class IJuniperConverter;

/**
 * This class converts a summary field for docsum fetching.
 */
class SummaryFieldConverter
{
public:
    static document::FieldValue::UP convertSummaryField(bool markup, const document::FieldValue &value);

    static document::FieldValue::UP convert_field_with_filter(bool markup,
                                                              const document::FieldValue& value,
                                                              const std::vector<uint32_t>& matching_elems);

    static void insert_summary_field(const document::FieldValue& value, vespalib::slime::Inserter& inserter);
    /**
     * Insert the given field value, but only the elements that are contained in the matching_elems vector.
     */
    static void insert_summary_field_with_filter(const document::FieldValue& value, vespalib::slime::Inserter& inserter, const std::vector<uint32_t>& matching_elems);
    static void insert_juniper_field(const document::FieldValue& value, vespalib::slime::Inserter& inserter, bool tokenize, IJuniperConverter& converter);
};

}

