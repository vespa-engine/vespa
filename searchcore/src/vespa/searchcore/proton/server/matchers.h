// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace proton {

class Matchers {
private:
    typedef vespalib::hash_map<vespalib::string, matching::Matcher::SP> Map;
    Map                   _rpmap;
    matching::Matcher::SP _fallback;
    matching::Matcher::SP _default;
public:
    typedef std::shared_ptr<Matchers> SP;
    typedef std::unique_ptr<Matchers> UP;
    Matchers(const vespalib::Clock &clock,
             matching::QueryLimiter &queryLimiter,
             const matching::IConstantValueRepo &constantValueRepo);
    ~Matchers();
    void add(const vespalib::string &name, matching::Matcher::SP matcher);
    matching::MatchingStats getStats() const;
    matching::MatchingStats getStats(const vespalib::string &name) const;
    matching::Matcher::SP lookup(const vespalib::string &name) const;
};

} // namespace proton

