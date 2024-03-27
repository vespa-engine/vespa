// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/docsum_field_writer.h>
#include <vespa/searchlib/query/query_normalization.h>

namespace vsm {

/*
 * Class for writing string field values from document as
 * arrays containing the tokens. Tokenization is performed
 * on the fly using the exact_match and normalize_mode settings.
 */
class TokensDFW : public search::docsummary::DocsumFieldWriter
{
private:
    vespalib::string            _input_field_name;
    bool                        _exact_match;
    search::Normalizing         _normalize_mode;

public:
    explicit TokensDFW(const vespalib::string& input_field_name, bool exact_match, search::Normalizing normalize_mode);
    ~TokensDFW() override;
    bool isGenerated() const override;
    void insertField(uint32_t docid, const search::docsummary::IDocsumStoreDocument* doc, search::docsummary::GetDocsumsState& state, vespalib::slime::Inserter& target) const override;
};

}
