// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "propertiesmap.h"
#include "trace.h"
#include <vespa/fastos/timestamp.h>

namespace search::engine {

class Request
{
public:
    Request(RelativeTime relativeTime);
    Request(const Request &) = delete;
    Request & operator =(const Request &) = delete;
    virtual ~Request();
    void setTimeout(const fastos::TimeStamp & timeout);
    fastos::SteadyTimeStamp getStartTime() const { return _relativeTime.timeOfDawn(); }
    fastos::SteadyTimeStamp getTimeOfDoom() const { return _timeOfDoom; }
    fastos::TimeStamp getTimeout() const { return _timeOfDoom - getStartTime(); }
    fastos::TimeStamp getTimeUsed() const;
    fastos::TimeStamp getTimeLeft() const;
    const RelativeTime & getRelativeTime() const { return _relativeTime; }
    bool expired() const { return getTimeLeft() <= 0l; }

    const vespalib::stringref getStackRef() const {
        return vespalib::stringref(&stackDump[0], stackDump.size());
    }

    void setTraceLevel(uint32_t level, uint32_t minLevel) const {
        _trace.setLevel(level);
        _trace.start(minLevel);
    }
    Request & setTraceLevel(uint32_t level) { _trace.setLevel(level); return *this; }
    uint32_t getTraceLevel() const { return _trace.getLevel(); }

    Trace & trace() const { return _trace; }
private:
    RelativeTime             _relativeTime;
    fastos::SteadyTimeStamp  _timeOfDoom;
public:
    /// Everything here should move up to private section and have accessors
    bool               dumpFeatures;
    vespalib::string   ranking;
    vespalib::string   location;
    PropertiesMap      propertiesMap;
    uint32_t           stackItems;
    std::vector<char>  stackDump;
private:
    mutable Trace      _trace;
};

}
