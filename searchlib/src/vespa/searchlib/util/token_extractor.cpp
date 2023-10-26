// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "token_extractor.h"
#include "linguisticsannotation.h"
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantreevisitor.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.util.token_extractor");

using document::AlternateSpanList;
using document::Annotation;
using document::AnnotationType;
using document::Document;
using document::FieldValue;
using document::SimpleSpanList;
using document::Span;
using document::SpanList;
using document::SpanNode;
using document::SpanTreeVisitor;
using document::StringFieldValue;
using vespalib::Utf8Reader;

namespace search::linguistics {

namespace {

class SpanFinder : public SpanTreeVisitor {
public:
    int32_t begin_pos;
    int32_t end_pos;

    SpanFinder() : begin_pos(0x7fffffff), end_pos(-1) {}
    Span span() { return Span(begin_pos, end_pos - begin_pos); }

    void visit(const Span &node) override {
        begin_pos = std::min(begin_pos, node.from());
        end_pos = std::max(end_pos, node.from() + node.length());
    }
    void visit(const SpanList &node) override {
        for (const auto & span_ : node) {
            span_->accept(*this);
        }
    }
    void visit(const SimpleSpanList &node) override {
        for (const auto & span_ : node) {
            span_.accept(*this);
        }
    }
    void visit(const AlternateSpanList &node) override {
        for (size_t i = 0; i < node.getNumSubtrees(); ++i) {
            visit(node.getSubtree(i));
        }
    }
};

Span
getSpan(const SpanNode &span_node)
{
    SpanFinder finder;
    span_node.accept(finder);
    return finder.span();
}

vespalib::stringref
get_span_string_or_alternative(vespalib::stringref s, const Span &span, const FieldValue* fv)
{
    if (fv != nullptr) {
        auto raw = fv->getAsRaw();
        return {raw.first, raw.second};
    } else {
        return {s.data() + span.from(), static_cast<size_t>(span.length())};
    }
}

size_t
truncated_word_len(vespalib::stringref word, size_t max_byte_len)
{
    Utf8Reader reader(word);
    while (reader.hasMore()) {
        auto last_pos = reader.getPos();
        (void) reader.getChar();
        if (reader.getPos() > max_byte_len) {
            return last_pos;
        }
    }
    return reader.getPos(); // No truncation
}

constexpr size_t max_fmt_len = 100; // Max length of word in logs

}

TokenExtractor::TokenExtractor(const vespalib::string& field_name, size_t max_word_len)
    : _field_name(field_name),
      _max_word_len(max_word_len)
{
}

TokenExtractor::~TokenExtractor() = default;

vespalib::stringref
TokenExtractor::sanitize_word(vespalib::stringref word, const document::Document* doc) const
{
    size_t len = strnlen(word.data(), word.size());
    if (len < word.size()) {
        size_t old_len = word.size();
        len = truncated_word_len(word, len);
        word = word.substr(0, len);
        if (doc != nullptr) {
            LOG(error, "Detected NUL byte in word, length reduced from %zu to %zu, document %s field %s, truncated word prefix is %.*s", old_len, word.size(), doc->getId().toString().c_str(), _field_name.c_str(), (int) truncated_word_len(word, max_fmt_len), word.data());
        }
    }
    if (word.size() > _max_word_len) {
        if (doc != nullptr) {
            LOG(warning, "Dropped too long word (len %zu > max len %zu) from document %s field %s, word prefix is %.*s", word.size(), _max_word_len, doc->getId().toString().c_str(), _field_name.c_str(), (int) truncated_word_len(word, max_fmt_len), word.data());
        }
        return {};
    }
    return word;
}

void
TokenExtractor::consider_word(std::vector<SpanTerm>& terms, vespalib::stringref text, const Span& span, const FieldValue* fv, const Document* doc) const
{
    if (span.length() > 0 && span.from() >= 0 &&
        static_cast<size_t>(span.from()) + static_cast<size_t>(span.length()) <= text.size()) {
        auto word = get_span_string_or_alternative(text, span, fv);
        word = sanitize_word(word, doc);
        if (!word.empty()) {
            terms.emplace_back(span, word, fv != nullptr);
        }
    }
}

void
TokenExtractor::extract(std::vector<SpanTerm>& terms, const document::StringFieldValue::SpanTrees& trees, vespalib::stringref text, const Document* doc) const
{
    auto tree = StringFieldValue::findTree(trees, SPANTREE_NAME);
    if (tree == nullptr) {
        /* field might not be annotated if match type is exact */
        consider_word(terms, text, Span(0, text.size()), nullptr, doc);
        return;
    }
    for (const Annotation & annotation : *tree) {
        const SpanNode *span = annotation.getSpanNode();
        if ((span != nullptr) && annotation.valid() &&
            (annotation.getType() == *AnnotationType::TERM))
        {
            Span sp = getSpan(*span);
            consider_word(terms, text, sp, annotation.getFieldValue(), doc);
        }
    }
    std::sort(terms.begin(), terms.end());
}

}
