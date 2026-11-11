// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "will_need_advisor.h"

#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cassert>
#include <cerrno>
#include <climits>
#include <filesystem>
#include <fstream>
#include <optional>

#ifdef __linux__
#include <sys/sysmacros.h>
#endif

#if defined(__linux__) && defined(SYS_process_madvise) && defined(SYS_pidfd_open)
#define VESPA_HAVE_PROCESS_MADVISE 1
#endif

namespace vespalib::alloc {

namespace {

const size_t page_size = getpagesize();

std::atomic<bool> batching_failed(false);

#ifdef VESPA_HAVE_PROCESS_MADVISE

// pidfd for this process, reopened if we are a forked child of the process that opened it.
class SelfPidFd {
    pid_t _pid;
    int   _fd;

public:
    SelfPidFd() : _pid(getpid()), _fd(static_cast<int>(syscall(SYS_pidfd_open, _pid, 0))) {}
    ~SelfPidFd() {
        if (_fd >= 0) {
            close(_fd);
        }
    }
    int fd() const noexcept { return (_pid == getpid()) ? _fd : -1; }
};

int self_pidfd() {
    static SelfPidFd pidfd;
    return pidfd.fd();
}

// Errors telling that process_madvise() can't be used by this process at all.
bool process_madvise_unusable(int error) noexcept {
    return error == ENOSYS || error == EPERM || error == EINVAL || error == EBADF || error == ESRCH;
}

// The number of failing ranges to skip with process_madvise() before leaving the rest to madvise() per range.
// Each failing range costs an extra process_madvise() call for the ranges after it.
constexpr size_t max_skipped_ranges = 8;

// Advise ranges[first, last) with process_madvise(), skipping ranges the kernel fails to advise.
// Returns the number of ranges handled (advised or skipped). This is less than (last - first) if
// process_madvise is not usable (batching is then disabled) or too many ranges failed; the caller
// must handle the rest.
size_t process_madvise_ranges(const std::vector<WillNeedAdvisor::Range>& ranges, size_t first, size_t last,
                              WillNeedAdvisor::Result& result) {
    int fd = self_pidfd();
    if (fd < 0) {
        batching_failed.store(true, std::memory_order_relaxed);
        return 0;
    }
    constexpr size_t   max_iov = IOV_MAX;
    std::vector<iovec> iov;
    iov.reserve(std::min(last - first, max_iov));
    size_t done = first;
    size_t skipped = 0;
    while (done < last && skipped <= max_skipped_ranges) {
        size_t batch_end = std::min(last, done + max_iov);
        iov.clear();
        for (size_t i = done; i < batch_end; ++i) {
            iov.push_back(iovec{reinterpret_cast<void*>(ranges[i].start), ranges[i].end - ranges[i].start});
        }
        ssize_t res = syscall(SYS_process_madvise, fd, iov.data(), iov.size(), MADV_WILLNEED, 0u);
        ++result.syscalls;
        if (res < 0) {
            if (process_madvise_unusable(errno)) {
                batching_failed.store(true, std::memory_order_relaxed);
                break;
            }
            // The first range in the batch failed (e.g. ENOMEM for an unmapped range). Skip it.
            ++done;
            ++skipped;
            continue;
        }
        // The kernel stops at the first range it fails to advise; count the ranges it completed.
        size_t advised = static_cast<size_t>(res);
        while (done < batch_end && advised >= (ranges[done].end - ranges[done].start)) {
            size_t size = ranges[done].end - ranges[done].start;
            advised -= size;
            ++result.ranges;
            result.bytes += size;
            ++done;
        }
        if (done < batch_end) {
            // Partial result: skip the failing range.
            ++done;
            ++skipped;
        }
    }
    return done - first;
}

#endif

#ifdef __linux__

// Read a sysfs file holding a single number of kibibytes.
std::optional<size_t> read_sysfs_kb(const std::string& path) {
    std::ifstream in(path);
    size_t        value = 0;
    if (in >> value) {
        return value;
    }
    return std::nullopt;
}

#endif

} // namespace

WillNeedAdvisor::WillNeedAdvisor(size_t max_range_size) : _ranges(), _max_range_size(max_range_size) {
}

WillNeedAdvisor::~WillNeedAdvisor() = default;

void WillNeedAdvisor::add(const void* buf, size_t size) {
    if (buf == nullptr || size == 0) {
        return;
    }
    auto start = reinterpret_cast<uintptr_t>(buf);
    _ranges.push_back(Range{start, start + size});
}

WillNeedAdvisor::Result WillNeedAdvisor::advise(Method method) {
    Result result;
    merge_page_ranges(_ranges, page_size, _max_range_size);
    size_t done = 0;
#ifdef VESPA_HAVE_PROCESS_MADVISE
    // A single range gains nothing from batching.
    if (method == Method::BATCHED && _ranges.size() > 1 && !batching_disabled()) {
        done = process_madvise_ranges(_ranges, 0, _ranges.size(), result);
    }
#else
    (void)method;
#endif
    for (size_t i = done; i < _ranges.size(); ++i) {
        size_t size = _ranges[i].end - _ranges[i].start;
        ++result.syscalls;
        if (madvise(reinterpret_cast<void*>(_ranges[i].start), size, MADV_WILLNEED) == 0) {
            ++result.ranges;
            result.bytes += size;
        }
    }
    _ranges.clear();
    return result;
}

void WillNeedAdvisor::merge_page_ranges(std::vector<Range>& ranges, size_t page_size_in, size_t max_range_size) {
    assert(page_size_in > 0 && (page_size_in & (page_size_in - 1)) == 0);
    const uintptr_t page_mask = ~static_cast<uintptr_t>(page_size_in - 1);
    for (auto& range : ranges) {
        range.start &= page_mask;
        range.end = (range.end + (page_size_in - 1)) & page_mask;
    }
    std::sort(ranges.begin(), ranges.end(), [](const Range& a, const Range& b) { return a.start < b.start; });
    auto out = ranges.begin();
    for (auto it = ranges.begin(); it != ranges.end(); ++it) {
        if (out != ranges.begin() && it->start <= (out - 1)->end) {
            (out - 1)->end = std::max((out - 1)->end, it->end);
        } else {
            *out++ = *it;
        }
    }
    ranges.erase(out, ranges.end());
    const uintptr_t chunk =
        std::max(static_cast<uintptr_t>(max_range_size) & page_mask, static_cast<uintptr_t>(page_size_in));
    if (std::any_of(ranges.begin(), ranges.end(), [chunk](const Range& r) { return r.end - r.start > chunk; })) {
        std::vector<Range> chunked;
        for (const auto& range : ranges) {
            for (uintptr_t start = range.start; start < range.end; start += chunk) {
                chunked.push_back(Range{start, std::min(start + chunk, range.end)});
            }
        }
        ranges = std::move(chunked);
    }
}

bool WillNeedAdvisor::batching_disabled() noexcept {
    return batching_failed.load(std::memory_order_relaxed);
}

size_t WillNeedAdvisor::max_range_size_for_path(const std::string& path) {
#ifdef __linux__
    // Find the device holding the path, or its closest existing parent directory.
    std::error_code ec;
    auto            existing = std::filesystem::absolute(std::filesystem::path(path), ec);
    struct stat     st {};
    while (ec || stat(existing.c_str(), &st) != 0) {
        if (ec || !existing.has_relative_path()) {
            return default_max_range_size;
        }
        existing = existing.parent_path();
    }
    // The kernel reads ahead at most max(bdi->io_pages, ra_pages) pages for one MADV_WILLNEED range
    // (cf. force_page_cache_ra()). For a partition, the limits are found on the disk it is part of.
    std::string dev = "/sys/dev/block/" + std::to_string(major(st.st_dev)) + ":" + std::to_string(minor(st.st_dev));
    size_t      max_kb = 0;
    for (const char* name : {"/bdi/read_ahead_kb", "/../bdi/read_ahead_kb", "/queue/max_sectors_kb",
                             "/../queue/max_sectors_kb"}) {
        if (auto kb = read_sysfs_kb(dev + name)) {
            max_kb = std::max(max_kb, *kb);
        }
    }
    if (max_kb != 0) {
        return std::max(max_kb * 1024, page_size);
    }
#else
    (void)path;
#endif
    return default_max_range_size;
}

} // namespace vespalib::alloc
