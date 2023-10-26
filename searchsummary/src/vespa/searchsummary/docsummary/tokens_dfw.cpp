// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tokens_dfw.h"
#include "i_docsum_store_document.h"
#include "tokens_converter.h"
#include <vespa/searchlib/memoryindex/field_inverter.h>

using search::memoryindex::FieldInverter;

namespace search::docsummary {

TokensDFW::TokensDFW(const vespalib::string& input_field_name)
    : DocsumFieldWriter(),
      _input_field_name(input_field_name),
      _token_extractor(_input_field_name, FieldInverter::max_word_len)
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
        TokensConverter converter(_token_extractor);
        doc->insert_summary_field(_input_field_name, target, &converter);
    }
}

}
