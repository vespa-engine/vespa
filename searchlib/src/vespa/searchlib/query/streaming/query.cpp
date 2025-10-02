// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "query.h"
#include "near_query_node.h"
#include "onear_query_node.h"
#include "query_builder.h"
#include "same_element_query_node.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <cassert>

namespace search::streaming {

void
QueryConnector::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "Operator", _opName);
}

QueryConnector::QueryConnector(const char * opName) noexcept
    : QueryNode(),
      _opName(opName),
      _index(),
      _children()
{
}

void
QueryConnector::addChild(std::unique_ptr<QueryNode> child) {
    _children.push_back(std::move(child));
}

QueryConnector::~QueryConnector() = default;

const HitList &
QueryConnector::evaluateHits(HitList & hl) const
{
    if (evaluate()) {
        hl.emplace_back(0, 0, 1, 1);
    }
    return hl;
}

void
QueryConnector::reset()
{
    for (const auto & node : _children) {
        node->reset();
    }
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
TrueNode::evaluate() const
{
    return true;
}

void
TrueNode::get_element_ids(std::vector<uint32_t>&) const
{
}

FalseNode::~FalseNode() = default;

bool FalseNode::evaluate() const
{
    return false;
}

void
FalseNode::get_element_ids(std::vector<uint32_t>&) const
{
}

AndQueryNode::~AndQueryNode() = default;

bool
AndQueryNode::evaluate() const
{
    for (const auto & qn : getChildren()) {
        if ( ! qn->evaluate() ) return false;
    }
    return true;
}

void
AndQueryNode::get_element_ids(std::vector<uint32_t>& element_ids) const
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
AndNotQueryNode::evaluate() const {
    if (getChildren().empty()) return true;
    auto it = getChildren().begin();
    auto mt = getChildren().end();
    if ((*it)->evaluate()) {
        for (++it; it != mt; it++) {
            if ((*it)->evaluate()) return false;
        }
        return true;
    }
    return false;
}

void
AndNotQueryNode::get_element_ids(std::vector<uint32_t>&) const
{
}

OrQueryNode::~OrQueryNode() = default;

bool
OrQueryNode::evaluate() const {
    for (const auto & qn : getChildren()) {
        if (qn->evaluate()) return true;
    }
    return false;
}

void
OrQueryNode::get_element_ids(std::vector<uint32_t>& element_ids) const
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
RankWithQueryNode::evaluate() const {
    bool first = true;
    bool firstOk = false;
    for (const auto & qn : getChildren()) {
        if (qn->evaluate()) {
            if (first) firstOk = true;
        }
        first = false;
    }
    return firstOk;
}

void
RankWithQueryNode::get_element_ids(std::vector<uint32_t>&) const
{
}

Query::Query() = default;

Query::Query(const QueryNodeResultFactory & factory, std::string_view queryRep)
    : _root()
{
    build(factory, queryRep);
}

Query::Query(Query&&) noexcept = default;

Query::~Query() = default;

Query& Query::operator=(Query&&) noexcept = default;

bool
Query::evaluate() const {
    return valid() && _root->evaluate();
}

bool
Query::build(const QueryNodeResultFactory & factory, std::string_view queryRep)
{
    search::SimpleQueryStackDumpIterator stack(queryRep);
    if (stack.next()) {
        _root = QueryBuilder().build(nullptr, factory, stack, true);
    }
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
