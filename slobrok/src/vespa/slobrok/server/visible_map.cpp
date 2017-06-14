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
    for (uint32_t i = 0; i < waitList.size(); ++i) {
        waitList[i]->updated(*this);
    }
}


void
VisibleMap::aborted()
{
    WaitList waitList;
    std::swap(waitList, _waitList);
    for (uint32_t i = 0; i < waitList.size(); ++i) {
        waitList[i]->aborted(*this);
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

std::vector<const NamedService *>
VisibleMap::lookupPattern(const char *pattern) const
{
    std::vector<const NamedService *> retval;
    for (iter_t it = _map.iterator(); it.valid(); it.next())
    {
        if (match(it.key(), pattern)) {
            retval.push_back(it.value());
        }
    }
    return retval;
}


std::vector<const NamedService *>
VisibleMap::allVisible() const
{
    std::vector<const NamedService *> retval;
    // get list of all names in myrpcsrvmap
    for (iter_t it = _map.iterator(); it.valid(); it.next())
    {
        retval.push_back(it.value());
    }
    return retval;
}



void
VisibleMap::addNew(NamedService *rpcsrv)
{
    LOG_ASSERT(rpcsrv != NULL);
    LOG_ASSERT(_map.isSet(rpcsrv->getName()) == false);
    _map.set(rpcsrv->getName(), rpcsrv);

    _history.add(rpcsrv->getName(), _genCnt);
    updated();
}


NamedService *
VisibleMap::remove(const char *name) {

    NamedService *d = _map.remove(name);
    if (d != NULL) {
        _history.add(name, _genCnt);
        updated();
    }
    return d;
}


NamedService *
VisibleMap::update(NamedService *rpcsrv) {
    LOG_ASSERT(rpcsrv != NULL);

    NamedService *d = _map.remove(rpcsrv->getName());
    LOG_ASSERT(d != NULL);

    _map.set(rpcsrv->getName(), rpcsrv);

    _history.add(rpcsrv->getName(), _genCnt);
    updated();

    return d;
}

VisibleMap::MapDiff
VisibleMap::history(const vespalib::GenCnt& gen) const
{
    MapDiff retval;
    std::set<std::string> names = _history.since(gen);
    for (std::set<std::string>::iterator it = names.begin();
         it != names.end();
         ++it)
    {
        const NamedService *val = lookup(it->c_str());
        if (val == NULL) {
            retval.removed.push_back(*it);
        } else {
            retval.updated.push_back(val);
        }
    }
    return retval;
}

VisibleMap::MapDiff::MapDiff() {}
VisibleMap::MapDiff::~MapDiff() {}

VisibleMap::VisibleMap()
    : _map(NULL),
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
    LOG_ASSERT(name != NULL);
    LOG_ASSERT(pattern != NULL);
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
