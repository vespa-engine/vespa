// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "request.h"

namespace search::engine {

Request::Request(const fastos::TimeStamp &start_time)
    : _startTime(start_time),
      _timeOfDoom(fastos::TimeStamp(fastos::TimeStamp::FUTURE)),
      ranking(),
      queryFlags(0),
      location(),
      propertiesMap(),
      stackItems(0),
      stackDump()
{
}

void Request::setTimeout(const fastos::TimeStamp & timeout)
{
    _timeOfDoom = _startTime + timeout;
}

fastos::TimeStamp Request::getTimeUsed() const
{
    return fastos::TimeStamp(fastos::ClockSystem::now()) - _startTime;
}

fastos::TimeStamp Request::getTimeLeft() const
{
    return _timeOfDoom - fastos::TimeStamp(fastos::ClockSystem::now());
}

Request::~Request() = default;

}
