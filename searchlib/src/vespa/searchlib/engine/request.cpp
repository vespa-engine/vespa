// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "request.h"

namespace search::engine {

Request::Request(RelativeTime relativeTime)
    : _relativeTime(std::move(relativeTime)),
      _timeOfDoom(vespalib::steady_time::max()),
      dumpFeatures(false),
      ranking(),
      location(),
      propertiesMap(),
      stackItems(0),
      stackDump(),
      _trace(_relativeTime, 0)
{
}

Request::~Request() = default;

void Request::setTimeout(vespalib::duration timeout)
{
    _timeOfDoom = getStartTime() + timeout;
}

vespalib::duration Request::getTimeUsed() const
{
    return _relativeTime.timeSinceDawn();
}

vespalib::duration Request::getTimeLeft() const
{
    return _timeOfDoom - _relativeTime.now();
}

}
