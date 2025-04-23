// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"
#include <vespa/vespalib/regex/regex.h>

namespace search::expression {

/**
 **/
class RegexPredicateNode : public FilterPredicateNode {
private:
    struct RE {
        std::string pattern;
        vespalib::Regex regex;
        RE() = default;
        RE(const RE& other) : pattern(other.pattern) { compile(); }
        RE& operator= (const RE& other) {
            pattern = other.pattern;
            compile();
            return *this;
        }
        void compile();
    };

    RE _re;
    ExpressionTree _argument;

public:
    RegexPredicateNode() noexcept;
    ~RegexPredicateNode();
    RegexPredicateNode* clone() const override { return new RegexPredicateNode(*this); }

    // for unit testing::
    RegexPredicateNode(std::string regex, ExpressionNode::UP input);

    DECLARE_IDENTIFIABLE_NS2(search, expression, RegexPredicateNode);
    DECLARE_NBO_SERIALIZE;
    bool allow(DocId docId, HitRank rank) override;
    // bool allow(const document::Document &, HitRank) { return true; }
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate,
                       vespalib::ObjectOperation& operation) override;
};

} // namespace search::expression
