// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "reserved_name.h"

using std::chrono::duration_cast;

namespace slobrok {

ReservedName::ReservedName(const std::string &name, const std::string &spec, bool local)
    : NamedService(name, spec),
      _reservedTime(steady_clock::now()),
      isLocal(local)
{ }

bool
ReservedName::stillReserved() const {
    return (milliseconds() < 15000);
}

int64_t ReservedName::milliseconds() const {
    return duration_cast<std::chrono::seconds>(steady_clock::now() - _reservedTime).count();
}

}