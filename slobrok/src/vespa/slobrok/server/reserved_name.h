// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "named_service.h"
#include <chrono>

namespace slobrok {

//-----------------------------------------------------------------------------

/**
 * @class ReservedName
 * @brief A reservation for a name
 *
 * a reservation expires 15 seconds after it is created.
 **/

class ReservedName: public NamedService
{
private:
    using steady_clock = std::chrono::steady_clock;
    steady_clock::time_point _reservedTime;
    int64_t milliseconds() const;
public:
    const bool isLocal;

    ReservedName(const std::string &name, const std::string &spec, bool local);
    bool stillReserved() const;
};

//-----------------------------------------------------------------------------

} // namespace slobrok

