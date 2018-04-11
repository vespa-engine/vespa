// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "propertiesmap.h"
#include <vespa/fastos/timestamp.h>

namespace search::engine {

class Request
{
public:
    Request(const fastos::TimeStamp &start_time);
    virtual ~Request();
    void setTimeout(const fastos::TimeStamp & timeout);
    fastos::TimeStamp getStartTime() const { return _startTime; }
    fastos::TimeStamp getTimeOfDoom() const { return _timeOfDoom; }
    fastos::TimeStamp getTimeout() const { return _timeOfDoom -_startTime; }
    fastos::TimeStamp getTimeUsed() const;
    fastos::TimeStamp getTimeLeft() const;
    bool expired() const { return getTimeLeft() <= 0l; }

    const vespalib::stringref getStackRef() const {
        return vespalib::stringref(&stackDump[0], stackDump.size());
    }

    bool should_drop_sort_data() const;

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

}
