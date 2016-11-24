// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/timestamp.h>
#include <vespa/vespalib/stllike/string.h>
#include "propertiesmap.h"

namespace search {
namespace engine {

class Request
{
public:
    Request();
    virtual ~Request();
    void setTimeout(const fastos::TimeStamp & timeout);
    fastos::TimeStamp  getStartTime() const { return _startTime; }
    fastos::TimeStamp getTimeOfDoom() const { return _timeOfDoom; }
    fastos::TimeStamp getTimeout() const { return _timeOfDoom -_startTime; }
    fastos::TimeStamp getTimeUsed() const;
    fastos::TimeStamp getTimeLeft() const;
    bool expired() const { return getTimeLeft() > 0l; }

    const vespalib::stringref getStackRef() const {
        return vespalib::stringref(&stackDump[0], stackDump.size());
    }

private:
    const fastos::TimeStamp _startTime;
    fastos::TimeStamp       _timeOfDoom;
public:
    /// Everything here should move up to private section and have accessors
    vespalib::string   ranking;
    uint32_t           queryFlags;
    vespalib::string   location;
    PropertiesMap      propertiesMap;
    uint32_t           stackItems;
    std::vector<char>  stackDump;
};

} // namespace engine
} // namespace search

