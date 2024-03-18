// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "query.h"
#include "near_query_node.h"
#include "onear_query_node.h"
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
QueryConnector::addChild(QueryNode::UP child) {
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
QueryConnector::create(ParseItem::ItemType type)
{
    switch (type) {
        case search::ParseItem::ITEM_AND:          return std::make_unique<AndQueryNode>();
        case search::ParseItem::ITEM_OR:
        case search::ParseItem::ITEM_WEAK_AND:     return std::make_unique<OrQueryNode>();
        case search::ParseItem::ITEM_NOT:          return std::make_unique<AndNotQueryNode>();
        case search::ParseItem::ITEM_NEAR:         return std::make_unique<NearQueryNode>();
        case search::ParseItem::ITEM_ONEAR:        return std::make_unique<ONearQueryNode>();
        case search::ParseItem::ITEM_RANK:         return std::make_unique<RankWithQueryNode>();
        default: return nullptr;
    }
}

bool
TrueNode::evaluate() const
{
    return true;
}

bool FalseNode::evaluate() const {
    return false;
}

bool
AndQueryNode::evaluate() const
{
    for (const auto & qn : getChildren()) {
        if ( ! qn->evaluate() ) return false;
    }
    return true;
}

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

bool
OrQueryNode::evaluate() const {
    for (const auto & qn : getChildren()) {
        if (qn->evaluate()) return true;
    }
    return false;
}

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

Query::Query() = default;

Query::Query(const QueryNodeResultFactory & factory, vespalib::stringref queryRep)
    : _root()
{
    build(factory, queryRep);
}

bool
Query::evaluate() const {
    return valid() && _root->evaluate();
}

bool
Query::build(const QueryNodeResultFactory & factory, vespalib::stringref queryRep)
{
    search::SimpleQueryStackDumpIterator stack(queryRep);
    if (stack.next()) {
        _root = QueryNode::Build(nullptr, factory, stack, true);
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
