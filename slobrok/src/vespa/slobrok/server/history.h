// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/gencnt.h>
#include <vector>
#include <string>
#include <set>

namespace slobrok {

class History
{
private:
    struct HistoryEntry {
        std::string      name;
        vespalib::GenCnt gen;
    };

    std::vector<HistoryEntry> _entries;

    typedef std::vector<HistoryEntry>::const_iterator citer_t;

    void verify() const;
public:
    void add(const std::string &name, vespalib::GenCnt gen);

    bool has(vespalib::GenCnt gen) const;

    std::set<std::string> since(vespalib::GenCnt gen) const;

    History() : _entries() {}
    ~History() {}
};

//-----------------------------------------------------------------------------

} // namespace slobrok

