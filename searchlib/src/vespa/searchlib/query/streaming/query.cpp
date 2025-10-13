// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "query.h"
#include "near_query_node.h"
#include "onear_query_node.h"
#include "query_builder.h"
#include "same_element_query_node.h"
#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <cassert>
#include <span>

using search::fef::IIndexEnvironment;
using search::fef::MatchData;

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

void
QueryConnector::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "Operator", _opName);
}

QueryConnector::QueryConnector(const char * opName) noexcept
    : QueryNode(),
      _opName(opName),
      _index(),
      _children(),
      _cached_evaluate_result()
{
}

void
QueryConnector::addChild(std::unique_ptr<QueryNode> child) {
    _children.push_back(std::move(child));
}

QueryConnector::~QueryConnector() = default;

const HitList &
QueryConnector::evaluateHits(HitList & hl)
{
    if (evaluate()) {
        hl.emplace_back(0, 0, 1, 1);
    }
    return hl;
}

void
QueryConnector::unpack_match_data(uint32_t docid, MatchData& match_data, const IIndexEnvironment& index_env)
{
    for (const auto & node : _children) {
        node->unpack_match_data(docid, match_data, index_env);
    }
}

void
QueryConnector::reset()
{
    for (const auto & node : _children) {
        node->reset();
    }
    _cached_evaluate_result.reset();
}

void
QueryConnector::getLeaves(QueryTermList & tl)
{
    for (const auto & node : _children) {
        node->getLeaves(tl);
    }
}

void
QueryConnector::getLeaves(ConstQueryTermList & tl) const
{
    for (const auto & node : _children) {
        node->getLeaves(tl);
    }
}

size_t
QueryConnector::depth() const
{
    size_t d(0);
    for (const auto & node : _children) {
        size_t t = node->depth();
        if (t > d) {
            d = t;
        }
    }
    return d+1;
}

size_t
QueryConnector::width() const
{
  size_t w(0);
  for (const auto & node : _children) {
    w += node->width();
  }

  return w;
}

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

TrueNode::~TrueNode() = default;

bool
TrueNode::evaluate()
{
    return true;
}

void
TrueNode::get_element_ids(std::vector<uint32_t>&)
{
}

FalseNode::~FalseNode() = default;

bool FalseNode::evaluate()
{
    return false;
}

void
FalseNode::get_element_ids(std::vector<uint32_t>&)
{
}

AndQueryNode::~AndQueryNode() = default;

bool
AndQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    bool result = !getChildren().empty();
    for (const auto& qn : getChildren()) {
        if (!qn->evaluate()) {
            result = false;
            break;
        }
    }
    _cached_evaluate_result.emplace(result);
    return result;
}

void
AndQueryNode::get_element_ids(std::vector<uint32_t>& element_ids)
{
    auto& children = getChildren();
    if (children.empty()) {
        return;
    }
    children.front()->get_element_ids(element_ids);
    std::span others(children.begin() + 1, children.end());
    if (others.empty() || element_ids.empty()) {
        return;
    }
    std::vector<uint32_t> temp_element_ids;
    std::vector<uint32_t> result;
    for (auto& child : others) {
        temp_element_ids.clear();
        result.clear();
        child->get_element_ids(temp_element_ids);
        std::set_intersection(element_ids.begin(), element_ids.end(),
                              temp_element_ids.begin(), temp_element_ids.end(),
                              std::back_inserter(result));
        std::swap(element_ids, result);
        if (element_ids.empty()) {
            return;
        }
    }
}

AndNotQueryNode::~AndNotQueryNode() = default;

bool
AndNotQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    auto it = getChildren().begin();
    auto mt = getChildren().end();
    bool result = it != mt && (*it)->evaluate();
    if (result) {
        for (++it; it != mt; it++) {
            if ((*it)->evaluate()) {
                result = false;
                break;
            }
        }
    }
    _cached_evaluate_result.emplace(result);
    return result;
}

void
AndNotQueryNode::get_element_ids(std::vector<uint32_t>&)
{
}

OrQueryNode::~OrQueryNode() = default;

bool
OrQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    bool result = false;
    for (const auto & qn : getChildren()) {
        if (qn->evaluate()) {
            result = true;
            break;
        }
    }
    _cached_evaluate_result.emplace(result);
    return result;
}

void
OrQueryNode::get_element_ids(std::vector<uint32_t>& element_ids)
{
    auto& children = getChildren();
    if (children.empty()) {
        return;
    }
    std::vector<uint32_t> temp_element_ids;
    std::vector<uint32_t> result;
    for (auto& child : children) {
        temp_element_ids.clear();
        child->get_element_ids(temp_element_ids);
        if (!temp_element_ids.empty()) {
            result.clear();
            std::set_union(element_ids.begin(), element_ids.end(),
                           temp_element_ids.begin(), temp_element_ids.end(),
                           std::back_inserter(result));
            std::swap(element_ids, result);
        }
    }
}

RankWithQueryNode::~RankWithQueryNode() = default;

bool
RankWithQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    bool result = !getChildren().empty() && getChildren().front()->evaluate();
    _cached_evaluate_result.emplace(result);
    return result;
}

void
RankWithQueryNode::get_element_ids(std::vector<uint32_t>&)
{
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
