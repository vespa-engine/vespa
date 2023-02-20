// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchers.h"
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/searchcore/proton/matching/ranking_expressions.h>
#include <vespa/searchcore/proton/matching/onnx_models.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace proton {

using matching::RankingExpressions;
using matching::OnnxModels;
using matching::Matcher;
using matching::MatchingStats;
using namespace vespalib::make_string_short;

Matchers::Matchers(const vespalib::Clock &clock,
                   matching::QueryLimiter &queryLimiter,
                   const matching::RankingAssetsRepo &rankingAssetsRepo)
    : _rpmap(),
      _ranking_assets_repo(rankingAssetsRepo),
      _fallback(std::make_shared<Matcher>(search::index::Schema(), search::fef::Properties(), clock, queryLimiter,
                                          _ranking_assets_repo, -1)),
      _default()
{ }

Matchers::~Matchers() = default;

void
Matchers::add(const vespalib::string &name, std::shared_ptr<Matcher> matcher)
{
    if ((name == "default") || ! _default) {
        _default = matcher;
    }
    _rpmap[name] = std::move(matcher);
}

MatchingStats
Matchers::getStats() const
{
    MatchingStats stats;
    for (const auto & entry : _rpmap) {
        stats.add(entry.second->getStats());
    }
    return stats;
}

MatchingStats
Matchers::getStats(const vespalib::string &name) const
{
    auto it = _rpmap.find(name);
    return it != _rpmap.end() ? it->second->getStats() : MatchingStats();
}

std::shared_ptr<Matcher>
Matchers::lookup(const vespalib::string &name) const
{
    Map::const_iterator found(_rpmap.find(name));
    if (found == _rpmap.end()) {
        if (_default) {
            vespalib::Issue::report(fmt("Failed to find rank-profile '%s'. Falling back to 'default'", name.c_str()));
            return _default;
        } else {
            vespalib::Issue::report(fmt("Failed to find rank-profile '%s'. Most likely a configuration issue.", name.c_str()));
            return _fallback;
        }
    }
    return found->second;
}

} // namespace proton
