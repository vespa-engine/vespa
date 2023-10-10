// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "token_extractor.h"
#include "linguisticsannotation.h"
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantreevisitor.h>

using document::AlternateSpanList;
using document::Annotation;
using document::AnnotationType;
using document::SimpleSpanList;
using document::Span;
using document::SpanList;
using document::SpanNode;
using document::SpanTreeVisitor;
using document::StringFieldValue;

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

}

bool
TokenExtractor::extract(bool allow_zero_length_tokens, std::vector<SpanTerm>& terms, const document::StringFieldValue::SpanTrees& trees)
{
    auto tree = StringFieldValue::findTree(trees, SPANTREE_NAME);
    if (tree == nullptr) {
        return false;
    }
    terms.clear();
    for (const Annotation & annotation : *tree) {
        const SpanNode *span = annotation.getSpanNode();
        if ((span != nullptr) && annotation.valid() &&
            (annotation.getType() == *AnnotationType::TERM))
        {
            Span sp = getSpan(*span);
            if (sp.length() != 0 || allow_zero_length_tokens) {
                terms.emplace_back(sp, annotation.getFieldValue());
            }
        }
    }
    std::sort(terms.begin(), terms.end());
    return true;
}

}
