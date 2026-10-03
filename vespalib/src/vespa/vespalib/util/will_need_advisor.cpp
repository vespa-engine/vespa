// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "will_need_advisor.h"

#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cassert>
#include <cerrno>
#include <climits>

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

// Advise ranges[first, last) with process_madvise(). Returns number of ranges fully advised,
// or 0 (and disables batching) if process_madvise is not usable.
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
    while (done < last) {
        size_t batch_end = std::min(last, done + max_iov);
        iov.clear();
        size_t batch_bytes = 0;
        for (size_t i = done; i < batch_end; ++i) {
            size_t size = ranges[i].end - ranges[i].start;
            iov.push_back(iovec{reinterpret_cast<void*>(ranges[i].start), size});
            batch_bytes += size;
        }
        ssize_t res = syscall(SYS_process_madvise, fd, iov.data(), iov.size(), MADV_WILLNEED, 0u);
        ++result.syscalls;
        if (res < 0) {
            if (process_madvise_unusable(errno)) {
                batching_failed.store(true, std::memory_order_relaxed);
            }
            // Otherwise the first range in the batch failed (e.g. ENOMEM for an unmapped range);
            // the per range fallback handles the rest.
            return done - first;
        }
        // The kernel stops at the first range it fails to advise; count the ranges it completed.
        size_t advised = static_cast<size_t>(res);
        size_t i = done;
        while (i < batch_end && advised >= (ranges[i].end - ranges[i].start)) {
            size_t size = ranges[i].end - ranges[i].start;
            advised -= size;
            ++result.ranges;
            result.bytes += size;
            ++i;
        }
        if (static_cast<size_t>(res) != batch_bytes) {
            // Partial result: let the per range fallback handle (and skip) the failing range.
            return i - first;
        }
        done = batch_end;
    }
    return last - first;
}

#endif

} // namespace

WillNeedAdvisor::WillNeedAdvisor() : _ranges() {
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
    merge_page_ranges(_ranges, page_size);
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

} // namespace vespalib::alloc
