// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>
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
    static RUsage createSelf(vespalib::steady_time since);
    /**
     * Will create an RUsage and initialize member with RUSAGE_CHILDREN
     **/
    static RUsage createChildren();
    static RUsage createChildren(vespalib::steady_time since);
    /**
     * Will create an RUsage and initialize member with RUSAGE_CHILDREN
     **/
    vespalib::string toString();
    RUsage & operator -= (const RUsage & rhs);
private:
    vespalib::duration _time;
};

RUsage operator -(const RUsage & a, const RUsage & b);
timeval operator -(const timeval & a, const timeval & b);

}

