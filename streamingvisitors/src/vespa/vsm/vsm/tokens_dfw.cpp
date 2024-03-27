// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tokens_dfw.h"
#include <vespa/searchsummary/docsummary/i_docsum_store_document.h>
#include "tokens_converter.h"

using search::docsummary::IDocsumStoreDocument;
using search::docsummary::GetDocsumsState;

namespace vsm {

TokensDFW::TokensDFW(const vespalib::string& input_field_name, bool exact_match, search::Normalizing normalize_mode)
    : DocsumFieldWriter(),
      _input_field_name(input_field_name),
      _exact_match(exact_match),
      _normalize_mode(normalize_mode)
{
}

TokensDFW::~TokensDFW() = default;

bool
TokensDFW::isGenerated() const
{
    return false;
}

void
TokensDFW::insertField(uint32_t, const IDocsumStoreDocument* doc, GetDocsumsState&, vespalib::slime::Inserter& target) const
{
    if (doc != nullptr) {
        TokensConverter converter(_exact_match, _normalize_mode);
        doc->insert_summary_field(_input_field_name, target, &converter);
    }
}

}
