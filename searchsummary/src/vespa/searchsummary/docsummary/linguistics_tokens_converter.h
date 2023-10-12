// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_string_field_converter.h"

namespace search::docsummary {

/*
 * Class converting a string field value with annotations into an array
 * containing the index terms. Multiple index terms at same position are
 * placed in a nested array.
 */
class LinguisticsTokensConverter : public IStringFieldConverter
{
    vespalib::stringref    _text;

    template <typename ForwardIt>
    void handle_alternative_index_terms(ForwardIt it, ForwardIt last, vespalib::slime::Inserter& inserter);
    void handle_index_term(vespalib::stringref word, vespalib::slime::Inserter& inserter);
    void handle_indexing_terms(const document::StringFieldValue& value, vespalib::slime::Inserter& inserter);
public:
    LinguisticsTokensConverter();
    ~LinguisticsTokensConverter() override;
    void convert(const document::StringFieldValue &input, vespalib::slime::Inserter& inserter) override;
};

}
