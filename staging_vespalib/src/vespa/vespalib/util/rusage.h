// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/fastos/timestamp.h>
#include <sys/resource.h>

namespace vespalib {

class RUsage : private rusage {
public:
    /**
     * Create an rusage with all member set to zero.
     **/
    RUsage();
    /**
     * Will create an RUsage and initialize member with RUSAGE_SELF
     **/
    static RUsage createSelf();
    static RUsage createSelf(fastos::SteadyTimeStamp since);
    /**
     * Will create an RUsage and initialize member with RUSAGE_CHILDREN
     **/
    static RUsage createChildren();
    static RUsage createChildren(fastos::SteadyTimeStamp since);
    /**
     * Will create an RUsage and initialize member with RUSAGE_CHILDREN
     **/
    vespalib::string toString();
    RUsage & operator -= (const RUsage & rhs);
private:
    fastos::TimeStamp _time;
};

RUsage operator -(const RUsage & a, const RUsage & b);
timeval operator -(const timeval & a, const timeval & b);

}

