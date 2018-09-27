// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visible_map.h"

#include <vespa/log/log.h>
LOG_SETUP(".vismap");

namespace slobrok {

void
VisibleMap::updated()
{
    _genCnt.add();
    WaitList waitList;
    std::swap(waitList, _waitList);
    for (auto & entry : waitList) {
        entry->updated(*this);
    }
}

void
VisibleMap::aborted()
{
    WaitList waitList;
    std::swap(waitList, _waitList);
    for (auto & entry : waitList) {
        entry->aborted(*this);
    }
}

void
VisibleMap::addUpdateListener(IUpdateListener *l)
{
    _waitList.push_back(l);
}


void
VisibleMap::removeUpdateListener(IUpdateListener *l)
{
    uint32_t i = 0;
    uint32_t size = _waitList.size();
    while (i < size) {
        if (_waitList[i] == l) {
            std::swap(_waitList[i], _waitList[size - 1]);
            _waitList.pop_back();
            --size;
        } else {
            ++i;
        }
    }
    LOG_ASSERT(size == _waitList.size());
}

//-----------------------------------------------------------------------------

NamedService *
VisibleMap::lookup(const char *name) const {
    auto found = _map.find(name);
    return (found == _map.end()) ? nullptr : found->second;
}

std::vector<const NamedService *>
VisibleMap::lookupPattern(const char *pattern) const
{
    std::vector<const NamedService *> retval;
    for (const auto & entry : _map) {
        if (match(entry.first.c_str(), pattern)) {
            retval.push_back(entry.second);
        }
    }
    return retval;
}


std::vector<const NamedService *>
VisibleMap::allVisible() const
{
    std::vector<const NamedService *> retval;
    // get list of all names in myrpcsrvmap
    for (const auto & entry : _map) {
        retval.push_back(entry.second);
    }
    return retval;
}



void
VisibleMap::addNew(NamedService *rpcsrv)
{
    LOG_ASSERT(rpcsrv != nullptr);
    LOG_ASSERT(_map.find(rpcsrv->getName()) == _map.end());
    _map[rpcsrv->getName()] = rpcsrv;

    _history.add(rpcsrv->getName(), _genCnt);
    updated();
}


NamedService *
VisibleMap::remove(const char *name) {

    NamedService *d = _map[name];
    _map.erase(name);
    if (d != nullptr) {
        _history.add(name, _genCnt);
        updated();
    }
    return d;
}


NamedService *
VisibleMap::update(NamedService *rpcsrv) {
    LOG_ASSERT(rpcsrv != nullptr);

    NamedService *d = rpcsrv;
    std::swap(d, _map[rpcsrv->getName()]);
    LOG_ASSERT(d != nullptr);

    _history.add(rpcsrv->getName(), _genCnt);
    updated();

    return d;
}

VisibleMap::MapDiff
VisibleMap::history(const vespalib::GenCnt& gen) const
{
    MapDiff retval;
    std::set<std::string> names = _history.since(gen);
    for (const auto & name : names)
    {
        const NamedService *val = lookup(name.c_str());
        if (val == nullptr) {
            retval.removed.push_back(name);
        } else {
            retval.updated.push_back(val);
        }
    }
    return retval;
}

VisibleMap::MapDiff::MapDiff() = default;
VisibleMap::MapDiff::~MapDiff() = default;

VisibleMap::VisibleMap()
    : _map(),
      _waitList(),
      _genCnt(1)
{
}
VisibleMap::~VisibleMap()
{
    aborted();
}


bool
VisibleMap::match(const char *name, const char *pattern)
{
    LOG_ASSERT(name != nullptr);
    LOG_ASSERT(pattern != nullptr);
    while (*pattern != '\0') {
        if (*name == *pattern) {
            ++name;
            ++pattern;
        } else if (*pattern == '*') {
            ++pattern;
            while (*name != '/' && *name != '\0') {
                ++name;
            }
        } else {
            return false;
        }
    }
    return (*name == *pattern);
}

//-----------------------------------------------------------------------------

} // namespace slobrok
