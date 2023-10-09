// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sblist.h"
#include <vespa/vespalib/util/random.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.list");

using LockGuard = std::lock_guard<std::mutex>;

namespace slobrok::api {

SlobrokList::SlobrokList()
    : _lock(),
      _slobrokSpecs(),
      _nextSpec(0),
      _currSpec(1),
      _retryCount(0)
{
}


bool
SlobrokList::contains(const std::string &spec)
{
    LockGuard guard(_lock);
    if (_currSpec < _slobrokSpecs.size()) {
        if (spec == _slobrokSpecs[_currSpec]) return true;
    }
    for (size_t i = 0; i < _slobrokSpecs.size(); ++i) {
        if (spec ==  _slobrokSpecs[i]) {
            _currSpec = i;
            return true;
        }
    }
    return false;
}


std::string
SlobrokList::nextSlobrokSpec()
{
    LockGuard guard(_lock);
    std::string v;
    _currSpec = _nextSpec;
    if (_nextSpec < _slobrokSpecs.size()) {
        ++_nextSpec;
        v = _slobrokSpecs[_currSpec];
    } else {
        _nextSpec = 0;
        ++_retryCount;
    }
    return v;
}


std::string
SlobrokList::logString()
{
    LockGuard guard(_lock);
    if (_slobrokSpecs.size() == 0) {
        return "[empty service location broker list]";
    }
    std::string v = "[";
    for (size_t i = 0 ; i < _slobrokSpecs.size(); ++i) {
        if (i > 0) v += ", ";
        v += _slobrokSpecs[i];
    }
    v += "]";
    return v;
}


void
SlobrokList::setup(const std::vector<std::string> &specList)
{
    if (specList.size() == 0) return;
    size_t cfgSz = specList.size();
    LockGuard guard(_lock);
    _slobrokSpecs.clear();
    _nextSpec = 0;
    _currSpec = cfgSz;
    for (size_t i = 0; i < cfgSz; ++i) {
        _slobrokSpecs.push_back(specList[i]);
    }

    vespalib::RandomGen randomizer(time(nullptr));
    // randomize order
    for (size_t i = 0; i + 1 < cfgSz; ++i) {
        size_t lim = cfgSz - i;
        size_t x = randomizer.nextUint32() % lim;
        if (x != 0) {
            std::swap(_slobrokSpecs[i], _slobrokSpecs[i+x]);
        }
    }
}

} // namespace slobrok::api
