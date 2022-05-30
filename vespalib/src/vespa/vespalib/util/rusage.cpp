// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rusage.h"
#include <stdexcept>
#include <cerrno>
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib {

RUsage::RUsage() :
    rusage(),
    _time(0)
{
    ru_utime.tv_sec = 0;
    ru_utime.tv_usec = 0;
    ru_stime.tv_sec = 0;
    ru_stime.tv_usec = 0;
    ru_maxrss = 0;
    ru_ixrss = 0;
    ru_idrss = 0;
    ru_isrss = 0;
    ru_minflt = 0;
    ru_majflt = 0;
    ru_nswap = 0;
    ru_inblock = 0;
    ru_oublock = 0;
    ru_msgsnd = 0;
    ru_msgrcv = 0;
    ru_nsignals = 0;
    ru_nvcsw = 0;
    ru_nivcsw = 0;
}

RUsage
RUsage::createSelf()
{
    return createSelf(vespalib::steady_time());
}

RUsage
RUsage::createChildren()
{
    return createChildren(vespalib::steady_time());
}

RUsage
RUsage::createSelf(vespalib::steady_time since)
{
    RUsage r;
    r._time = vespalib::steady_clock::now() - since;
    if (getrusage(RUSAGE_SELF, &r) != 0) {
        throw std::runtime_error(vespalib::make_string("getrusage failed with errno = %d", errno).c_str());
    }
    return r;
}

RUsage
RUsage::createChildren(vespalib::steady_time since)
{
    RUsage r;
    r._time = vespalib::steady_clock::now() - since;
    if (getrusage(RUSAGE_CHILDREN, &r) != 0) {
        throw std::runtime_error(vespalib::make_string("getrusage failed with errno = %d", errno).c_str());
    }
    return r;
}

vespalib::string
RUsage::toString()
{
    vespalib::string s;
    if (_time != duration::zero()) s += make_string("duration = %1.6f\n", vespalib::to_s(_time));
    if (from_timeval(ru_utime) > duration::zero()) s += make_string("user time = %1.6f\n", to_s(from_timeval(ru_utime)));
    if (from_timeval(ru_stime) > duration::zero()) s += make_string("system time = %1.6f\n", to_s(from_timeval(ru_stime)));
    if (ru_maxrss != 0) s += make_string("ru_maxrss = %ld\n", ru_maxrss);
    if (ru_ixrss != 0) s += make_string("ru_ixrss = %ld\n", ru_ixrss);
    if (ru_idrss != 0) s += make_string("ru_idrss = %ld\n", ru_idrss);
    if (ru_isrss != 0) s += make_string("ru_isrss = %ld\n", ru_isrss);
    if (ru_minflt != 0) s += make_string("ru_minflt = %ld\n", ru_minflt);
    if (ru_majflt != 0) s += make_string("ru_majflt = %ld\n", ru_majflt);
    if (ru_nswap != 0) s += make_string("ru_nswap = %ld\n", ru_nswap);
    if (ru_inblock != 0) s += make_string("ru_inblock = %ld\n", ru_inblock);
    if (ru_oublock != 0) s += make_string("ru_oublock = %ld\n", ru_oublock);
    if (ru_msgsnd != 0) s += make_string("ru_msgsnd = %ld\n", ru_msgsnd);
    if (ru_msgrcv != 0) s += make_string("ru_msgrcv = %ld\n", ru_msgrcv);
    if (ru_nsignals != 0) s += make_string("ru_nsignals = %ld\n", ru_nsignals);
    if (ru_nvcsw != 0) s += make_string("ru_nvcsw = %ld\n", ru_nvcsw);
    if (ru_nivcsw != 0) s += make_string("ru_nivcsw = %ld", ru_nivcsw);
    return s;
}

RUsage &
RUsage::operator -= (const RUsage & b)
{
    _time -= b._time;
    ru_utime = ru_utime - b.ru_utime;
    ru_stime = ru_stime - b.ru_stime;
    ru_maxrss -= b.ru_maxrss;
    ru_ixrss -= b.ru_ixrss;
    ru_idrss -= b.ru_idrss;
    ru_isrss -= b.ru_isrss;
    ru_minflt -= b.ru_minflt;
    ru_majflt -= b.ru_majflt;
    ru_nswap -= b.ru_nswap;
    ru_inblock -= b.ru_inblock;
    ru_oublock -= b.ru_oublock;
    ru_msgsnd -= b.ru_msgsnd;
    ru_msgrcv -= b.ru_msgrcv;
    ru_nsignals -= b.ru_nsignals;
    ru_nvcsw -= b.ru_nvcsw;
    ru_nivcsw -= b.ru_nivcsw;
    return *this;
}

timeval
operator - (const timeval & a, const timeval & b)
{
    timeval tmp;
    if (a.tv_usec >= b.tv_usec) {
        tmp.tv_usec = a.tv_usec - b.tv_usec;
        tmp.tv_sec = a.tv_sec - b.tv_sec;
    } else {
        tmp.tv_usec = (a.tv_usec + 1000000) - b.tv_usec;
        tmp.tv_sec = a.tv_sec - 1 - b.tv_sec;
    }
    return tmp;
}

RUsage operator -(const RUsage & a, const RUsage & b)
{
    RUsage tmp(a);
    tmp -= b;
    return tmp;
}

}
