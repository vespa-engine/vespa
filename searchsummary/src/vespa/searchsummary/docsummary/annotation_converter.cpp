// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotation_converter.h"
#include "i_juniper_converter.h"
#include "linguisticsannotation.h"
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/annotation/spantreevisitor.h>
#include <vespa/document/datatype/annotationtype.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/juniper/juniper_separators.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <utility>

using document::AlternateSpanList;
using document::Annotation;
using document::AnnotationType;
using document::FieldValue;
using document::SimpleSpanList;
using document::Span;
using document::SpanList;
using document::SpanNode;
using document::SpanTree;
using document::SpanTreeVisitor;
using document::StringFieldValue;

namespace search::docsummary {

namespace {

vespalib::stringref
getSpanString(vespalib::stringref s, const Span &span)
{
    return {s.data() + span.from(), static_cast<size_t>(span.length())};
}

struct SpanFinder : SpanTreeVisitor {
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

Span getSpan(const SpanNode &span_node) {
    SpanFinder finder;
    span_node.accept(finder);
    return finder.span();
}

const StringFieldValue &ensureStringFieldValue(const FieldValue &value) __attribute__((noinline));

const StringFieldValue &ensureStringFieldValue(const FieldValue &value) {
    if (!value.isA(FieldValue::Type::STRING)) {
        throw vespalib::IllegalArgumentException("Illegal field type. " + value.toString(), VESPA_STRLOC);
    }
    return static_cast<const StringFieldValue &>(value);
}

}

AnnotationConverter::AnnotationConverter(IJuniperConverter& juniper_converter)
    : IStringFieldConverter(),
      _juniper_converter(juniper_converter),
      _text(),
      _out()
{
}

AnnotationConverter::~AnnotationConverter() = default;

template <typename ForwardIt>
void
AnnotationConverter::handleAnnotations(const document::Span& span, ForwardIt it, ForwardIt last) {
    int annCnt = (last - it);
    if (annCnt > 1 || (annCnt == 1 && it->second)) {
        annotateSpans(span, it, last);
    } else {
        _out << getSpanString(_text, span) << juniper::separators::unit_separator_string;
    }
}

template <typename ForwardIt>
void
AnnotationConverter::annotateSpans(const document::Span& span, ForwardIt it, ForwardIt last) {
    _out << juniper::separators::interlinear_annotation_anchor_string  // ANCHOR
         << (getSpanString(_text, span))
         << juniper::separators::interlinear_annotation_separator_string; // SEPARATOR
    while (it != last) {
        if (it->second) {
            _out << ensureStringFieldValue(*it->second).getValue();
        } else {
            _out << getSpanString(_text, span);
        }
        if (++it != last) {
            _out << " ";
        }
    }
    _out << juniper::separators::interlinear_annotation_terminator_string  // TERMINATOR
         << juniper::separators::unit_separator_string;
}

void
AnnotationConverter::handleIndexingTerms(const StringFieldValue& value)
{
    StringFieldValue::SpanTrees trees = value.getSpanTrees();
    const SpanTree *tree = StringFieldValue::findTree(trees, linguistics::SPANTREE_NAME);
    using SpanTerm = std::pair<Span, const FieldValue *>;
    using SpanTermVector = std::vector<SpanTerm>;
    if (!tree) {
        // Treat a string without annotations as a single span.
        SpanTerm str(Span(0, _text.size()),
                     static_cast<const FieldValue*>(nullptr));
        handleAnnotations(str.first, &str, &str + 1);
        return;
    }
    SpanTermVector terms;
    for (const Annotation& annotation : *tree) {
        // For now, skip any composite spans.
        const auto *span = dynamic_cast<const Span*>(annotation.getSpanNode());
        if ((span != nullptr) && annotation.valid() &&
            (annotation.getType() == *AnnotationType::TERM)) {
            terms.push_back(std::make_pair(getSpan(*span),
                                           annotation.getFieldValue()));
        }
    }
    sort(terms.begin(), terms.end());
    auto it = terms.begin();
    auto ite = terms.end();
    int32_t endPos = 0;
    for (; it != ite; ) {
        auto it_begin = it;
        if (it_begin->first.from() >  endPos) {
            Span tmpSpan(endPos, it_begin->first.from() - endPos);
            handleAnnotations(tmpSpan, it, it);
            endPos = it_begin->first.from();
        }
        for (; it != ite && it->first == it_begin->first; ++it);
        handleAnnotations(it_begin->first, it_begin, it);
        endPos = it_begin->first.from() + it_begin->first.length();
    }
    int32_t wantEndPos = _text.size();
    if (endPos < wantEndPos) {
        Span tmpSpan(endPos, wantEndPos - endPos);
        handleAnnotations(tmpSpan, ite, ite);
    }
}

void
AnnotationConverter::convert(const StringFieldValue &input, vespalib::slime::Inserter& inserter)
{
    _out.clear();
    _text = input.getValueRef();
    handleIndexingTerms(input);
    _juniper_converter.convert(_out.str(), inserter);
}

}
