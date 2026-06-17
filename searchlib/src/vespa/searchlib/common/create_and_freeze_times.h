// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <cstdint>

namespace vespalib {
class GenericHeader;
}

namespace search::common {

/*
 * Create and freeze times for files, measured in microseconds since epoch for utc clock.
 */
class CreateAndFreezeTimes {
    int64_t _create_time;
    int64_t _freeze_time;

public:
    CreateAndFreezeTimes() noexcept : _create_time(0), _freeze_time(0) {}
    CreateAndFreezeTimes(int64_t create_time_in, int64_t freeze_time_in) noexcept
        : _create_time(create_time_in), _freeze_time(freeze_time_in) {}
    explicit CreateAndFreezeTimes(const vespalib::GenericHeader& header);
    [[nodiscard]] static int64_t to_utc_us(std::chrono::system_clock::time_point system_time);
    [[nodiscard]] static std::chrono::system_clock::time_point from_utc_us(uint64_t us);
    void merge(const CreateAndFreezeTimes& rhs) noexcept;
    [[nodiscard]] std::chrono::steady_clock::duration get_flush_duration() const;
    [[nodiscard]] static std::chrono::steady_clock::duration
    make_flush_duration(const std::chrono::steady_clock::time_point& create_time);
    [[nodiscard]] uint64_t create_time() const noexcept { return _create_time; }
    [[nodiscard]] uint64_t freeze_time() const noexcept { return _freeze_time; }
};

} // namespace search::common
