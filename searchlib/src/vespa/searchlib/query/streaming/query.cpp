// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "query.h"
#include "near_query_node.h"
#include "onear_query_node.h"
#include "query_builder.h"
#include "query_connector.h"
#include "same_element_query_node.h"
#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>

namespace search::streaming {

namespace {

std::unique_ptr<QueryNode>
build_query_tree(const QueryNodeResultFactory& factory, const SerializedQueryTree& queryTree)
{
    auto stack = queryTree.makeIterator();
    if (stack->next()) {
        return QueryBuilder().build(nullptr, factory, *stack, true);
    }
    return {};
}

}  // namespace

std::unique_ptr<QueryConnector>
QueryConnector::create(ParseItem::ItemType type, const QueryNodeResultFactory& factory)
{
    switch (type) {
        case search::ParseItem::ITEM_AND:          return std::make_unique<AndQueryNode>();
        case search::ParseItem::ITEM_OR:
        case search::ParseItem::ITEM_WEAK_AND:     return std::make_unique<OrQueryNode>();
        case search::ParseItem::ITEM_NOT:          return std::make_unique<AndNotQueryNode>();
        case search::ParseItem::ITEM_NEAR:         return std::make_unique<NearQueryNode>(factory.get_element_gap_inspector());
        case search::ParseItem::ITEM_ONEAR:        return std::make_unique<ONearQueryNode>(factory.get_element_gap_inspector());
        case search::ParseItem::ITEM_RANK:         return std::make_unique<RankWithQueryNode>();
        default: return nullptr;
    }
}

Query::Query() = default;

Query::Query(const QueryNodeResultFactory & factory, const SerializedQueryTree& queryTree)
    : _root()
{
    build(factory, queryTree);
}

Query::Query(Query&&) noexcept = default;

Query::~Query() = default;

Query& Query::operator=(Query&&) noexcept = default;

bool
Query::evaluate() const {
    return valid() && _root->evaluate();
}

bool
Query::build(const QueryNodeResultFactory & factory, const SerializedQueryTree& queryTree)
{
    _root = build_query_tree(factory, queryTree);
    return valid();
}

void
Query::getLeaves(QueryTermList & tl) {
    if (valid()) {
        _root->getLeaves(tl);
    }
}

void
Query::getLeaves(ConstQueryTermList & tl) const {
    if (valid()) {
        _root->getLeaves(tl);
    }
}

void
Query::reset() {
    if (valid()) {
        _root->reset();
    }
}

size_t
Query::depth() const {
    return valid() ? _root->depth() : 0;
}

size_t
Query::width() const {
    return valid() ? _root->width() : 0;
}

}
