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
    vespalib::string            _input_field_name;
    linguistics::TokenExtractor _token_extractor;

public:
    explicit TokensDFW(const vespalib::string& input_field_name);
    ~TokensDFW() override;
    bool isGenerated() const override;
    void insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state, vespalib::slime::Inserter& target) const override;
};

}
