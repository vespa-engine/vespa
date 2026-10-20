// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "filter_predicate_node.h"
#include "resultnode.h"
#include <set>
#include <vector>

namespace search::expression {

/**
 * Keeps each scalar key tuple once per document within each parent group.
 *
 * The filter remembers keys, so it must only see rows the rest of the filter
 * has accepted. It may therefore only be the whole filter or the last
 * condition of an 'and', not inside 'or' or 'not'. A misplaced filter, or a
 * key that is not a single value, rejects every row and reports one issue.
 */
class OncePerDocumentPredicateNode : public FilterPredicateNode {
    // Orders seen keys, stored as entry numbers into _parents and _values.
    struct EntryLess {
        const OncePerDocumentPredicateNode* self;
        bool operator()(uint32_t a, uint32_t b) const;
    };
    std::vector<ExpressionTree> _arguments;
    // Keys seen in the current document. Entry i has parent group _parents[i] and
    // key values _values[i * arguments, (i + 1) * arguments). Values are reused across documents.
    std::vector<const void*> _parents;
    std::vector<ResultNode::UP> _values;
    std::set<uint32_t, EntryLess> _seen;
    DocId _docid;
    const void* _parent_group;
    bool _misplaced;
    bool _error_reported;

    void reset_seen();
    void store_value(size_t slot, const ResultNode& value);
    bool reject_row(const char* message);

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

    /** Sets the group that the rows evaluated next are grouped under. Keys are kept apart per parent group. */
    void set_parent_group(const void* group) noexcept { _parent_group = group; }
    void set_misplaced(bool misplaced) noexcept { _misplaced = misplaced; }

    /**
     * Returns the onceperdoc filter that is the whole filter or its last 'and' condition, or nullptr.
     * Marks every other onceperdoc filter in the tree as misplaced.
     */
    static OncePerDocumentPredicateNode* find_last_condition(FilterPredicateNode& filter);
};

} // namespace search::expression
