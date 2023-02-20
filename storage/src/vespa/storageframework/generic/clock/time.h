// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>

namespace vespalib {
    class asciistream;
}

namespace storage::framework {

/**
 * \class storage::framework::MicroSecTime
 * \ingroup clock
 *
 * \brief Wrapper class for a timestamp in seconds.
 *
 * To prevent errors where one passes time in one granularity to a function
 * requiring time in another granularity. This little wrapper class exist to
 * make sure that will conflict in types
 */
class MicroSecTime
{
public:
    explicit MicroSecTime(uint64_t t) noexcept : _time(t) {}

    [[nodiscard]] uint64_t getTime() const noexcept { return _time; }

    bool operator<(const MicroSecTime& o) const noexcept { return (_time < o._time); }
    bool operator<=(const MicroSecTime& o) const noexcept { return (_time <= o._time); }
    bool operator>=(const MicroSecTime& o) const noexcept { return (_time >= o._time); }
    bool operator>(const MicroSecTime& o) const noexcept { return (_time > o._time); }
    bool operator==(const MicroSecTime& o) const noexcept { return (_time == o._time); }

    [[nodiscard]] uint32_t getSeconds() const noexcept { return (getTime() / 1000000); }

    static MicroSecTime max() noexcept { return MicroSecTime(std::numeric_limits<uint64_t>::max()); }
private:
    // time_t may be signed. Negative timestamps is just a source for bugs. Enforce unsigned.
    uint64_t _time;
};

std::ostream& operator<<(std::ostream& out, const MicroSecTime & t);

vespalib::asciistream& operator<<(vespalib::asciistream& out, const MicroSecTime & t);

}
