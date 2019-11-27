// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/matching/matching_stats.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace vespalib { class Clock; }

namespace proton {

namespace matching {
    class Matcher;
    class QueryLimiter;
    class IConstantValueRepo;
}

class Matchers {
private:
    typedef vespalib::hash_map<vespalib::string, std::shared_ptr<matching::Matcher>> Map;
    Map                                _rpmap;
    std::shared_ptr<matching::Matcher> _fallback;
    std::shared_ptr<matching::Matcher> _default;
public:
    typedef std::shared_ptr<Matchers> SP;
    typedef std::unique_ptr<Matchers> UP;
    Matchers(const vespalib::Clock &clock,
             matching::QueryLimiter &queryLimiter,
             const matching::IConstantValueRepo &constantValueRepo);
    ~Matchers();
    void add(const vespalib::string &name, std::shared_ptr<matching::Matcher> matcher);
    matching::MatchingStats getStats() const;
    matching::MatchingStats getStats(const vespalib::string &name) const;
    std::shared_ptr<matching::Matcher> lookup(const vespalib::string &name) const;
};

} // namespace proton

