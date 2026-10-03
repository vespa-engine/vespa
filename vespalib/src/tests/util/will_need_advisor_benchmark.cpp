// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Micro benchmark for WillNeedAdvisor, simulating second phase ranking reading one value per hit from
// paged (file backed, MADV_RANDOM) memory at random addresses.
//
// warm: all pages are in the page cache. Measures the pure overhead of advising (per range vs. batched),
//       compared to touching the pages.
// cold: the pages are evicted from the page cache before each iteration. Compares touching the pages
//       one by one (serial page faults, as without prefetch) with advising first, then touching.
//
// Usage: vespalib_util_will_need_advisor_benchmark_app [hits=100] [file_mib=1024] [iterations=20] [dir=.]

#include <vespa/vespalib/util/will_need_advisor.h>

#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <random>
#include <string>
#include <vector>

using vespalib::alloc::WillNeedAdvisor;
using Method = WillNeedAdvisor::Method;
using clock_type = std::chrono::steady_clock;

namespace {

const size_t page_size = getpagesize();

double us_since(clock_type::time_point start) {
    return std::chrono::duration<double, std::micro>(clock_type::now() - start).count();
}

struct Stats {
    std::vector<double> samples;
    void add(double v) { samples.push_back(v); }
    double pct(double p) {
        std::sort(samples.begin(), samples.end());
        return samples[std::min(samples.size() - 1, static_cast<size_t>(p * samples.size()))];
    }
    void print(const char* name) { printf("  %-34s p50 %9.1f us   p90 %9.1f us\n", name, pct(0.5), pct(0.9)); }
};

struct MappedFile {
    std::string path;
    int         fd;
    size_t      size;
    char*       data;
    MappedFile(std::string path_in, size_t size_in) : path(std::move(path_in)), fd(-1), size(size_in), data(nullptr) {
        fd = open(path.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0600);
        if (fd < 0) {
            perror("open");
            exit(1);
        }
        std::vector<char> chunk(1 << 20, 1);
        for (size_t done = 0; done < size; done += chunk.size()) {
            if (write(fd, chunk.data(), chunk.size()) != static_cast<ssize_t>(chunk.size())) {
                perror("write");
                exit(1);
            }
        }
        fsync(fd);
        data = static_cast<char*>(mmap(nullptr, size, PROT_READ, MAP_SHARED, fd, 0));
        if (data == MAP_FAILED) {
            perror("mmap");
            exit(1);
        }
        madvise(data, size, MADV_RANDOM); // as MmapFileAllocator does
    }
    ~MappedFile() {
        munmap(data, size);
        close(fd);
        unlink(path.c_str());
    }
    void evict() {
        madvise(data, size, MADV_DONTNEED);           // drop our page table entries
        posix_fadvise(fd, 0, 0, POSIX_FADV_DONTNEED); // drop the (clean) pages from the page cache
    }
    double resident_fraction(const std::vector<size_t>& pages) {
        unsigned char vec = 0;
        size_t        resident = 0;
        for (size_t p : pages) {
            if (mincore(data + p * page_size, page_size, &vec) == 0 && (vec & 1)) {
                ++resident;
            }
        }
        return double(resident) / pages.size();
    }
};

volatile char sink;

void touch(const MappedFile& f, const std::vector<size_t>& pages) {
    char sum = 0;
    for (size_t p : pages) {
        sum += f.data[p * page_size + 17];
    }
    sink = sum;
}

void advise(const MappedFile& f, const std::vector<size_t>& pages, Method method, size_t& syscalls) {
    WillNeedAdvisor advisor;
    advisor.reserve(pages.size());
    for (size_t p : pages) {
        advisor.add(f.data + p * page_size + 17, 384); // e.g. a 384 byte int8 embedding
    }
    syscalls = advisor.advise(method).syscalls;
}

} // namespace

int main(int argc, char** argv) {
    size_t      hits = (argc > 1) ? strtoul(argv[1], nullptr, 10) : 100;
    size_t      file_mib = (argc > 2) ? strtoul(argv[2], nullptr, 10) : 1024;
    size_t      iterations = (argc > 3) ? strtoul(argv[3], nullptr, 10) : 20;
    std::string dir = (argc > 4) ? argv[4] : ".";
    MappedFile  f(dir + "/will_need_advisor_benchmark.dat", file_mib << 20);
    size_t      num_pages = f.size / page_size;

    std::mt19937_64                       rnd(42);
    std::uniform_int_distribution<size_t> page_dist(0, num_pages - 2);

    auto random_pages = [&]() {
        std::vector<size_t> pages;
        for (size_t i = 0; i < hits; ++i) {
            pages.push_back(page_dist(rnd));
        }
        return pages;
    };
    size_t syscalls_per_range = 0;
    size_t syscalls_batched = 0;
    printf("hits=%zu file=%zu MiB iterations=%zu page_size=%zu\n", hits, file_mib, iterations, page_size);

    // warm
    {
        Stats touch_only, per_range, batched;
        for (size_t i = 0; i < iterations * 10; ++i) {
            auto pages = random_pages();
            touch(f, pages); // make resident and mapped
            auto start = clock_type::now();
            touch(f, pages);
            touch_only.add(us_since(start));
            start = clock_type::now();
            advise(f, pages, Method::PER_RANGE, syscalls_per_range);
            per_range.add(us_since(start));
            start = clock_type::now();
            advise(f, pages, Method::BATCHED, syscalls_batched);
            batched.add(us_since(start));
        }
        printf("warm (pages in page cache):\n");
        touch_only.print("touch pages (no prefetch)");
        per_range.print("advise, madvise per range");
        batched.print("advise, batched");
        printf("  syscalls: per range %zu, batched %zu%s\n", syscalls_per_range, syscalls_batched,
               WillNeedAdvisor::batching_disabled() ? " (process_madvise not usable, fell back)" : "");
    }
    // cold
    {
        Stats  serial, per_range, per_range_advise, batched, batched_advise;
        double resident = 0.0;
        for (size_t i = 0; i < iterations; ++i) {
            auto pages = random_pages();
            f.evict();
            resident += f.resident_fraction(pages);
            auto start = clock_type::now();
            touch(f, pages);
            serial.add(us_since(start));

            pages = random_pages();
            f.evict();
            start = clock_type::now();
            advise(f, pages, Method::PER_RANGE, syscalls_per_range);
            per_range_advise.add(us_since(start));
            touch(f, pages);
            per_range.add(us_since(start));

            pages = random_pages();
            f.evict();
            start = clock_type::now();
            advise(f, pages, Method::BATCHED, syscalls_batched);
            batched_advise.add(us_since(start));
            touch(f, pages);
            batched.add(us_since(start));
        }
        printf("cold (pages evicted, %.1f%% resident after eviction):\n", 100.0 * resident / iterations);
        serial.print("touch pages (serial faults)");
        per_range.print("advise per range + touch");
        per_range_advise.print("  of which advise");
        batched.print("advise batched + touch");
        batched_advise.print("  of which advise");
    }
    return 0;
}
