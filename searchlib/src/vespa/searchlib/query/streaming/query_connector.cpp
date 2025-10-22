// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_connector.h"
#include <vespa/vespalib/objects/visit.hpp>

using search::common::ElementIds;
using search::fef::IIndexEnvironment;
using search::fef::MatchData;

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
QueryConnector::unpack_match_data(uint32_t docid, MatchData& match_data, const IIndexEnvironment& index_env,
                                  ElementIds element_ids)
{
    if (evaluate()) {
        for (const auto &node: _children) {
            node->unpack_match_data(docid, match_data, index_env, element_ids);
        }
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

}
