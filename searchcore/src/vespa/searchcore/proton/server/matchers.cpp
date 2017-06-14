// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchers.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace proton {

Matchers::Matchers(const vespalib::Clock &clock,
                   matching::QueryLimiter &queryLimiter,
                   const matching::IConstantValueRepo &constantValueRepo)
    : _rpmap(),
      _fallback(new matching::Matcher(search::index::Schema(), search::fef::Properties(),
                                      clock, queryLimiter, constantValueRepo, -1)),
      _default()
{ }

Matchers::~Matchers() { }

void
Matchers::add(const vespalib::string &name, matching::Matcher::SP matcher)
{
    _rpmap[name] = matcher;
    if (name == "default" || _default.get() == 0) {
        _default = matcher;
    }
}

matching::MatchingStats
Matchers::getStats() const
{
    matching::MatchingStats stats;
    for (Map::const_iterator it(_rpmap.begin()), mt(_rpmap.end()); it != mt; it++) {
        stats.add(it->second->getStats());
    }
    return stats;
}

matching::MatchingStats
Matchers::getStats(const vespalib::string &name) const
{
    auto it = _rpmap.find(name);
    return it != _rpmap.end() ? it->second->getStats() :
        matching::MatchingStats();
}

matching::Matcher::SP
Matchers::lookup(const vespalib::string &name) const
{
    Map::const_iterator found(_rpmap.find(name));
    return (found != _rpmap.end()) ? found->second : _default;
    //TODO add warning log message when not found, may want to use "_fallback" in most cases here
}

} // namespace proton
