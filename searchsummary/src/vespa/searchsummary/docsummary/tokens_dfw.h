// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"
#include <vespa/searchlib/util/token_extractor.h>
#include <memory>

namespace search::docsummary {

/*
 * Class for writing annotated string field values from document as
 * arrays containing the tokens.
 */
class TokensDFW : public DocsumFieldWriter
{
private:
    std::string            _input_field_name;
    linguistics::TokenExtractor _token_extractor;

public:
    explicit TokensDFW(const std::string& input_field_name);
    ~TokensDFW() override;
    bool isGenerated() const override;
    void insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                      ElementIds selected_elements,
                      vespalib::slime::Inserter& target) const override;
};

}
