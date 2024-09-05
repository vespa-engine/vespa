// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/matching/matching_stats.h>
#include <vespa/searchlib/fef/ranking_assets_repo.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace proton {

namespace matching {
    class Matcher;
    class QueryLimiter;
}

class Matchers {
private:
    using Map = vespalib::hash_map<std::string, std::shared_ptr<matching::Matcher>>;
    Map                                  _rpmap;
    const search::fef::RankingAssetsRepo _ranking_assets_repo;
    std::shared_ptr<matching::Matcher>   _fallback;
    std::shared_ptr<matching::Matcher>   _default;
public:
    using SP = std::shared_ptr<Matchers>;
    Matchers(const std::atomic<vespalib::steady_time> & now_ref,
             matching::QueryLimiter &queryLimiter,
             const search::fef::RankingAssetsRepo &rankingAssetsRepo);
    Matchers(const Matchers &) = delete;
    Matchers & operator =(const Matchers &) = delete;
    ~Matchers();
    void add(const std::string &name, std::shared_ptr<matching::Matcher> matcher);
    matching::MatchingStats getStats() const;
    matching::MatchingStats getStats(const std::string &name) const;
    std::shared_ptr<matching::Matcher> lookup(const std::string &name) const;
    const search::fef::RankingAssetsRepo& get_ranking_assets_repo() const noexcept { return _ranking_assets_repo; }
};

} // namespace proton

