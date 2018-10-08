// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "history.h"

#include <vespa/log/log.h>
LOG_SETUP(".history");

namespace slobrok {

void
History::verify() const
{
    if (_entries.size() > 0) {
        citer_t i = _entries.cbegin();
        vespalib::GenCnt gen = i->gen;

        while (++i != _entries.cend()) {
            gen.add();
            LOG_ASSERT(gen == i->gen);
        }
    }
}

void
History::add(const std::string &name, vespalib::GenCnt gen)
{
    _entries.emplace_back(name, gen);

    if (_entries.size() > 1500) {
        _entries.erase(_entries.begin(), _entries.begin() + 500);
        LOG(debug, "history size after trim: %lu",
            (unsigned long)_entries.size());
    }
    verify();
}


bool
History::has(vespalib::GenCnt gen) const
{
    if (_entries.size() == 0)
        return false;

    vespalib::GenCnt first = _entries.front().gen;
    vespalib::GenCnt last  = _entries.back().gen;

    return gen.inRangeInclusive(first, last);
}


std::set<std::string>
History::since(vespalib::GenCnt gen) const
{
    citer_t i = _entries.cbegin();
    citer_t end = _entries.cend();
    while (i != end) {
        if (i->gen == gen) break;
        ++i;
    }
    std::set<std::string> ret;
    while (i != end) {
        ret.insert(i->name);
        ++i;
    }
    LOG_ASSERT(ret.size() > 0);
    return ret;
}

//-----------------------------------------------------------------------------

} // namespace slobrok
