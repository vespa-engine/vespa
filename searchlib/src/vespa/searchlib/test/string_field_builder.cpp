// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_field_builder.h"
#include "doc_builder.h"
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/vespalib/text/utf8.h>

#include <cassert>

using document::Annotation;
using document::AnnotationType;
using document::FixedTypeRepo;
using document::StringFieldValue;
using document::Span;
using document::SpanList;
using document::SpanNode;
using document::SpanTree;
using vespalib::Utf8Reader;
using vespalib::Utf8Writer;

namespace search::test {

namespace {

const vespalib::string SPANTREE_NAME("linguistics");

}

StringFieldBuilder::StringFieldBuilder(const DocBuilder& doc_builder)
    : _value(),
      _span_start(0u),
      _span_list(nullptr),
      _span_tree(),
      _last_span(nullptr),
      _repo(doc_builder.get_repo(), doc_builder.get_document_type())
{
}

StringFieldBuilder::~StringFieldBuilder() = default;

void
StringFieldBuilder::start_annotate()
{
    auto span_list_up = std::make_unique<SpanList>();
    _span_list = span_list_up.get();
    _span_tree = std::make_unique<SpanTree>(SPANTREE_NAME, std::move(span_list_up));
}

void
StringFieldBuilder::add_span()
{
    assert(_value.size() > _span_start);
    const SpanNode &span = _span_list->add(std::make_unique<Span>(_span_start, _value.size() - _span_start));
    _last_span = &span;
    _span_start = _value.size();
}

StringFieldBuilder&
StringFieldBuilder::token(const vespalib::string& val, bool is_word)
{
    if (val.empty()) {
        return *this;
    }
    if (!_span_tree) {
        start_annotate();
    }
    _span_start = _value.size();
    _value.append(val);
    add_span();
    if (is_word) {
        _span_tree->annotate(*_last_span, *AnnotationType::TERM);
    }
    return *this;
}

StringFieldBuilder&
StringFieldBuilder::alt_word(const vespalib::string& val)
{
    assert(_last_span != nullptr);
    _span_tree->annotate(*_last_span,
                         Annotation(*AnnotationType::TERM,
                                    std::make_unique<StringFieldValue>(val)));
    return *this;
}

StringFieldBuilder&
StringFieldBuilder::tokenize(const vespalib::string& val)
{
    Utf8Reader reader(val);
    vespalib::string token_buffer;
    Utf8Writer writer(token_buffer);
    uint32_t c = 0u;
    bool old_word = false;

    while (reader.hasMore()) {
        c = reader.getChar();
        bool new_word = Fast_UnicodeUtil::IsWordChar(c);
        if (old_word != new_word) {
            if (!token_buffer.empty()) {
                token(token_buffer, old_word);
                token_buffer.clear();
            }
            old_word = new_word;
        }
        writer.putChar(c);
    }
    if (!token_buffer.empty()) {
        token(token_buffer, old_word);
    }
    return *this;
}


document::StringFieldValue
StringFieldBuilder::build()
{
    StringFieldValue value(_value);
    // Also drop all spans no annotation for now
    if (_span_tree && _span_tree->numAnnotations() > 0u) {
        StringFieldValue::SpanTrees trees;
        trees.emplace_back(std::move(_span_tree));
        value.setSpanTrees(trees, _repo);
    } else {
        _span_tree.reset();
    }
    _span_list = nullptr;
    _last_span = nullptr;
    _span_start = 0u;
    _value.clear();
    return value;
}

}
