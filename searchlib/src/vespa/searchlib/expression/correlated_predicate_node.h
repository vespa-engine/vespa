// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "filter_predicate_node.h"
#include <vespa/searchlib/common/correlation_bindings.h>
#include <functional>
#include <span>

namespace search::expression {

/**
 * Restricts a grouping row to bindings accepted by a labeled correlated query.
 * A bad request never throws: the row is rejected and the error is reported
 * once as an issue.
 */
class CorrelatedPredicateNode : public FilterPredicateNode {
    std::string _label;
    std::vector<std::pair<std::string, ExpressionTree>> _fields;

public:
    using Matcher = std::function<bool(DocId, std::span<const CorrelationBinding>)>;

private:
    Matcher _matcher;
    // Reused for every row, so evaluating a row does not allocate.
    CorrelationBindings _bindings;
    std::vector<std::string> _strings;
    bool _error_reported;

    void prepare_bindings();
    bool reject_row(const char* message);

public:
    CorrelatedPredicateNode();
    CorrelatedPredicateNode(std::string label, std::vector<std::string> variables,
                            std::vector<ExpressionNode::UP> fields);
    CorrelatedPredicateNode(const CorrelatedPredicateNode& other);
    CorrelatedPredicateNode& operator=(const CorrelatedPredicateNode& other);
    ~CorrelatedPredicateNode() override;
    CorrelatedPredicateNode* clone() const override { return new CorrelatedPredicateNode(*this); }
    const std::string& label() const noexcept { return _label; }
    std::vector<std::string> variables() const;
    void set_matcher(Matcher matcher) { _matcher = std::move(matcher); }

    DECLARE_IDENTIFIABLE_NS2(search, expression, CorrelatedPredicateNode);
    DECLARE_NBO_SERIALIZE;
    bool allow(DocId docid, HitRank rank) override;
    bool allow(const document::Document&, HitRank) override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;
};

} // namespace search::expression
