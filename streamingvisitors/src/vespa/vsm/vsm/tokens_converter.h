// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/i_string_field_converter.h>
#include <vespa/searchlib/query/query_normalization.h>

namespace vsm {

/*
 * Class converting a string field value into an array
 * containing the tokens.
 */
class TokensConverter : public search::docsummary::IStringFieldConverter
{
    std::string_view _text;
    bool                _exact_match;
    search::Normalizing _normalize_mode;

public:
    TokensConverter(bool exact_match, search::Normalizing normalize_mode);
    ~TokensConverter() override;
    void convert(const document::StringFieldValue &input, vespalib::slime::Inserter& inserter) override;
    bool render_weighted_set_as_array() const override;
};

}
