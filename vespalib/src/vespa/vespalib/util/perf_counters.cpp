// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "perf_counters.h"

#ifdef __linux__

#include <algorithm>
#include <cassert>
#include <cstring>
#include <filesystem>
#include <linux/perf_event.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

namespace vespalib {

namespace {

// https://man7.org/linux/man-pages/man2/perf_event_open.2.html:
// "The existence of the perf_event_paranoid file is the
//  official method for determining if a kernel supports
//  perf_event_open()"
[[nodiscard]] bool kernel_supports_perf_event_open() noexcept {
    std::error_code ec;
    return std::filesystem::exists("/proc/sys/kernel/perf_event_paranoid", ec) && ec == std::error_code{};
}

// perf_event_open does not have a libc syscall wrapper, so the officially
// documented way to do this is to manually invoke a syscall with its ID.
// See https://man7.org/linux/man-pages/man2/perf_event_open.2.html
[[nodiscard]] long perf_event_open(perf_event_attr* hw_event, pid_t pid, int cpu, int group_fd, unsigned long flags) {
    return syscall(SYS_perf_event_open, hw_event, pid, cpu, group_fd, flags);
}

[[nodiscard]] uint64_t to_perf_event_type(PerfCounters::Event ev) noexcept {
    switch (ev) {
    case PerfCounters::Event::SW_CPU_CLOCK:
    case PerfCounters::Event::SW_PAGE_FAULTS:
        return PERF_TYPE_SOFTWARE;
    case PerfCounters::Event::HW_CYCLE_COUNT:
    case PerfCounters::Event::HW_INSTRUCTION_COUNT:
    case PerfCounters::Event::HW_CACHE_REFERENCES:
    case PerfCounters::Event::HW_CACHE_MISSES:
        return PERF_TYPE_HARDWARE;
    }
    abort();
}

[[nodiscard]] uint64_t to_perf_event_config(PerfCounters::Event ev) noexcept {
    // Prefer CPU frequency scaling invariant cycle counting if supported by the kernel
#ifdef PERF_COUNT_HW_REF_CPU_CYCLES
    constexpr uint64_t cpu_cycle_event_config = PERF_COUNT_HW_REF_CPU_CYCLES;
#else
    constexpr uint64_t cpu_cycle_event_config = PERF_COUNT_HW_CPU_CYCLES;
#endif
    switch (ev) {
    case PerfCounters::Event::SW_CPU_CLOCK:         return PERF_COUNT_SW_CPU_CLOCK;
    case PerfCounters::Event::SW_PAGE_FAULTS:       return PERF_COUNT_SW_PAGE_FAULTS;
    case PerfCounters::Event::HW_CYCLE_COUNT:       return cpu_cycle_event_config;
    case PerfCounters::Event::HW_INSTRUCTION_COUNT: return PERF_COUNT_HW_INSTRUCTIONS;
    case PerfCounters::Event::HW_CACHE_REFERENCES:  return PERF_COUNT_HW_CACHE_REFERENCES;
    case PerfCounters::Event::HW_CACHE_MISSES:      return PERF_COUNT_HW_CACHE_MISSES;
    }
    abort();
}

[[nodiscard]] int register_perf_event_fd(const PerfCounters::Event ev, const int group_fd) noexcept {
    perf_event_attr pe{};
    memset(&pe, 0, sizeof(pe));
    pe.size        = sizeof(pe);
    pe.type        = to_perf_event_type(ev);
    pe.config      = to_perf_event_config(ev);
    // We want all counters that are part of the group to be read simultaneously
    // as part of a single group fd read() call.
    pe.read_format = PERF_FORMAT_GROUP;
    // Reduce capabilities needed to sample counters. Note that this means that
    // we can't sample context switches (despite being software events), since
    // these are considered kernel-level.
    pe.exclude_kernel = 1;
    pe.exclude_hv     = 1;
    // Regarding `pe.disabled`, the manpage says:
    //  "When creating an event group, typically the group leader is
    //   initialized with disabled set to 1 and any child events are
    //   initialized with disabled set to 0.  Despite disabled being
    //   0, the child events will not start until the group leader
    //   is enabled."
    // However, the Highway code says:
    //  "Do not set disable=1 (sic), so that perf_event_open verifies all events
    //   in the group can be scheduled together."
    // To err on the side of caution, we do things like Highway and start with
    // events enabled before then explicitly disabling and resetting them.

    constexpr pid_t pid   = 0;  // Sample this PID only
    constexpr int   cpu   = -1; // Sample on any CPU
    constexpr int   flags = PERF_FLAG_FD_CLOEXEC;

    while (true) {
        int fd = static_cast<int>(perf_event_open(&pe, pid, cpu, group_fd, flags));
        if (fd != -1) {
            return fd;
        }
        if (errno != EINTR) {
            break;
        }
    }
    return -1;
}

[[nodiscard]] ssize_t read_with_eintr_retry(const int fd, void* buf, const size_t bytes) noexcept {
    size_t n = 0;
    char* out = static_cast<char*>(buf);
    while (n < bytes) {
        ssize_t r = read(fd, out + n, bytes - n);
        if (r == -1 && errno == EINTR) {
            continue;
        }
        if (r <= 0) {
            break; // non-EINTR error or EOF
        }
        n += r;
    }
    return static_cast<ssize_t>(n);
}

} // namespace

PerfCounters::PerfCounters(std::initializer_list<Event> events)
    : _events(),
      _event_to_idx(),
      _group_fd(-1)
{
    _event_to_idx.fill(-1);
    if (!is_supported()) {
        return;
    }
    for (const Event ev : events) {
        int fd = register_perf_event_fd(ev, _group_fd);
        if (fd != -1) {
            _event_to_idx[static_cast<size_t>(ev)] = static_cast<int>(_events.size());
            _events.emplace_back(ev, fd, 0);
            if (_group_fd == -1) {
                _group_fd = fd;
            }
        }
    }
    if (_group_fd != -1) {
        // Counters start out as enabled, so stop and reset them now
        if (ioctl(_group_fd, PERF_EVENT_IOC_DISABLE, 0) != -1) {
            (void)ioctl(_group_fd, PERF_EVENT_IOC_RESET, 0);
        }
    }
}

PerfCounters::~PerfCounters() {
    for (auto& e : _events) {
        close(e._fd);
    }
}

bool PerfCounters::is_supported() noexcept {
    static const bool kernel_perf = kernel_supports_perf_event_open();
    return kernel_perf;
}

void PerfCounters::start() noexcept {
    if (_group_fd == -1) {
        return;
    }
    // TODO toggle an error flag if any of these fail
    if (ioctl(_group_fd, PERF_EVENT_IOC_RESET, 0) != -1) {
        (void)ioctl(_group_fd, PERF_EVENT_IOC_ENABLE, 0);
    }
}

void PerfCounters::stop() noexcept {
    if (_group_fd == -1) {
        return;
    }
    // TODO toggle an error flag if any of these fail
    if (ioctl(_group_fd, PERF_EVENT_IOC_DISABLE, 0) == -1) {
        return;
    }
    //
    // "If PERF_FORMAT_GROUP was specified to allow reading all events
    //  in a group at once:
    //
    //    struct read_format {
    //        u64 nr;            /* The number of events */
    //        u64 time_enabled;  /* if PERF_FORMAT_TOTAL_TIME_ENABLED */
    //        u64 time_running;  /* if PERF_FORMAT_TOTAL_TIME_RUNNING */
    //        struct {
    //            u64 value;     /* The value of the event */
    //            u64 id;        /* if PERF_FORMAT_ID */
    //            u64 lost;      /* if PERF_FORMAT_LOST */
    //        } values[nr];
    //    };"
    //
    // We don't specify any extra fields, so we only have `u64 nr` + (`u64 value` * nr) entries
    uint64_t buf[1 + EventCount]; // worst-case buffer
    const ssize_t want_bytes = sizeof(uint64_t) + sizeof(uint64_t) * _events.size();
    assert(want_bytes <= static_cast<ssize_t>(sizeof(buf)));
    if ((read_with_eintr_retry(_group_fd, buf, want_bytes) == want_bytes) && (buf[0] == _events.size())) {
        for (size_t i = 0; i < _events.size(); ++i) {
            _events[i]._sampled_value = buf[i + 1];
        }
    }
}

#else // not __linux__

PerfCounters::PerfCounters(std::initializer_list<Event>)
    : _events(),
      _event_to_idx(),
      _group_fd(-1)
{
    _event_to_idx.fill(-1);
}

PerfCounters::~PerfCounters() = default;

bool PerfCounters::is_supported() noexcept {
    return false;
}
void PerfCounters::start() noexcept { /*no-op*/ }
void PerfCounters::stop() noexcept { /*no-op*/ }

#endif // __linux__

} // namespace vespalib
