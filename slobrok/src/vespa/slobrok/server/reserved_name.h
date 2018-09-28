// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "named_service.h"
#include <vespa/fastos/time.h>

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
    FastOS_Time _reservedTime;
public:
    const bool isLocal;

    ReservedName(const std::string &name, const std::string &spec, bool local)
        : NamedService(name, spec), _reservedTime(), isLocal(local)
    {
        _reservedTime.SetNow();
    }
    bool stillReserved() const {
        return (_reservedTime.MilliSecsToNow() < 15000);
    }
    int seconds() const { return _reservedTime.MilliSecsToNow() / 1000; }
};

//-----------------------------------------------------------------------------

} // namespace slobrok

