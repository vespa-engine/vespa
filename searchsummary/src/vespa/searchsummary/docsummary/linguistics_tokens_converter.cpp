// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "linguistics_tokens_converter.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/util/linguisticsannotation.h>
#include <vespa/searchlib/util/token_extractor.h>
#include <vespa/vespalib/data/slime/slime.h>

using document::StringFieldValue;
using search::linguistics::TokenExtractor;
using search::memoryindex::FieldInverter;
using vespalib::Memory;
using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;

namespace search::docsummary {

namespace {

vespalib::string dummy_field_name;

}

LinguisticsTokensConverter::LinguisticsTokensConverter()
    : IStringFieldConverter(),
      _text()
{
}

LinguisticsTokensConverter::~LinguisticsTokensConverter() = default;

template <typename ForwardIt>
void
LinguisticsTokensConverter::handle_alternative_index_terms(ForwardIt it, ForwardIt last, Inserter& inserter)
{
    Cursor& a = inserter.insertArray();
    ArrayInserter ai(a);
    for (;it != last; ++it) {
        handle_index_term(it->word, ai);
    }
}

void
LinguisticsTokensConverter::handle_index_term(vespalib::stringref word, Inserter& inserter)
{
    inserter.insertString(Memory(word));
}

void
LinguisticsTokensConverter::handle_indexing_terms(const StringFieldValue& value, vespalib::slime::Inserter& inserter)
{
    Cursor& a = inserter.insertArray();
    ArrayInserter ai(a);
    using SpanTerm = TokenExtractor::SpanTerm;
    std::vector<SpanTerm> terms;
    auto span_trees = value.getSpanTrees();
    TokenExtractor token_extractor(dummy_field_name, FieldInverter::max_word_len);
    token_extractor.extract(terms, span_trees, _text, nullptr);
    auto it = terms.begin();
    auto ite = terms.end();
    auto itn = it;
    for (; it != ite; it = itn) {
        for (; itn != ite && itn->span == it->span; ++itn);
        if ((itn - it) > 1) {
            handle_alternative_index_terms(it, itn, ai);
        } else {
            handle_index_term(it->word, ai);
        }
    }
}

void
LinguisticsTokensConverter::convert(const StringFieldValue &input, vespalib::slime::Inserter& inserter)
{
    _text = input.getValueRef();
    handle_indexing_terms(input, inserter);
}

}
