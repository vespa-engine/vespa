// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "filter_predicate_node.h"
#include "resultnode.h"
#include <set>
#include <vector>

namespace search::expression {

/** Keeps each scalar key tuple once per document within this grouping level. */
class OncePerDocumentPredicateNode : public FilterPredicateNode {
    using Key = std::vector<ResultNode::UP>;
    struct KeyLess {
        bool operator()(const Key& a, const Key& b) const;
    };
    std::vector<ExpressionTree> _arguments;
    std::set<Key, KeyLess> _seen;
    DocId _docid;

public:
    OncePerDocumentPredicateNode();
    explicit OncePerDocumentPredicateNode(std::vector<ExpressionNode::UP> keys);
    OncePerDocumentPredicateNode(const OncePerDocumentPredicateNode& other);
    OncePerDocumentPredicateNode& operator=(const OncePerDocumentPredicateNode& other);
    ~OncePerDocumentPredicateNode() override;
    OncePerDocumentPredicateNode* clone() const override { return new OncePerDocumentPredicateNode(*this); }
    DECLARE_IDENTIFIABLE_NS2(search, expression, OncePerDocumentPredicateNode);
    DECLARE_NBO_SERIALIZE;
    bool allow(DocId docid, HitRank rank) override;
    bool allow(const document::Document&, HitRank) override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;
};

} // namespace search::expression
