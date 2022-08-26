// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "propertiesmap.h"
#include "trace.h"

namespace search::engine {

class Request
{
public:
    Request(RelativeTime relativeTime);
    Request(RelativeTime relativeTime, uint32_t reservePropMaps);
    Request(const Request &) = delete;
    Request & operator =(const Request &) = delete;
    virtual ~Request();
    void setTimeout(vespalib::duration timeout);
    vespalib::steady_time getStartTime() const { return _relativeTime.timeOfDawn(); }
    vespalib::steady_time getTimeOfDoom() const { return _timeOfDoom; }
    vespalib::duration getTimeout() const { return _timeOfDoom - getStartTime(); }
    vespalib::duration getTimeUsed() const;
    vespalib::duration getTimeLeft() const;
    bool expired() const { return getTimeLeft() <= vespalib::duration::zero(); }

    const vespalib::stringref getStackRef() const {
        return vespalib::stringref(&stackDump[0], stackDump.size());
    }

    void setTraceLevel(uint32_t level, uint32_t minLevel) const {
        _trace.setLevel(level);
        _trace.start(minLevel);
    }

    Trace & trace() const { return _trace; }
private:
    RelativeTime           _relativeTime;
    vespalib::steady_time  _timeOfDoom;
public:
    /// Everything here should move up to private section and have accessors
    bool               dumpFeatures;
    vespalib::string   ranking;
    vespalib::string   location;
    PropertiesMap      propertiesMap;
    std::vector<char>  stackDump;
private:
    mutable Trace      _trace;
};

}
