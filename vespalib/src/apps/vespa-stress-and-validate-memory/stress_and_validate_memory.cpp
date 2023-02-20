// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/mmap_file_allocator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/time.h>
#include <thread>
#include <vector>
#include <atomic>
#include <cstring>
#include <mutex>
#include <filesystem>
#include <iostream>

std::atomic<bool> stopped = false;
std::mutex log_mutex;
using namespace vespalib;

const char * description =
        "Runs stress test of memory by slowly growing a heap filled with 0.\n"
        "Each core on the node will then continously read back and verify random memory sections still being zero.\n"
        "-h heap_in_GB(1) and -t run_time_in_seconds(10) are the options available.\n"
        "Memory will grow slowly during the first half of the test and then stay put.\n"
        "There is also the option to include stress testing of swap files by using -s <directory>.\n"
        "The swap will grow to twice the heap size in the same manner.\n"
        "Swap memory is stressed by constant random writing from all cores.\n";

class Config {
public:
    Config(size_t heap_size, size_t nprocs, size_t allocs_per_thread, duration alloc_time)
        : _heap_size(heap_size),
          _nprocs(nprocs),
          _allocs_per_thread(allocs_per_thread),
          _alloc_time(alloc_time)
    {}
    size_t allocs_per_thread() const { return _allocs_per_thread; }
    duration alloc_time() const { return _alloc_time; }
    size_t alloc_size() const { return _heap_size / _nprocs / _allocs_per_thread; }
    size_t nprocs() const { return _nprocs; }
    size_t heap_size() const { return _heap_size; }
private:
    const size_t                         _heap_size;
    const size_t                         _nprocs;
    const size_t                         _allocs_per_thread;
    const duration                       _alloc_time;
};

class Allocations {
public:
    Allocations(const Config & config);
    ~Allocations();
    size_t make_and_load_alloc_per_thread();
    size_t verify_random_allocation(unsigned int *seed) const;
    const Config & cfg() const { return _cfg; }
    size_t verify_and_report_errors() const {
        std::lock_guard guard(_mutex);
        for (const auto & alloc : _allocations) {
            _total_errors += verify_allocation(alloc.get());
        }
        return _total_errors;
    }
private:
    size_t verify_allocation(const char *) const;
    const Config                       & _cfg;
    mutable std::mutex                   _mutex;
    mutable size_t                       _total_errors;
    std::vector<std::unique_ptr<char[]>> _allocations;
};

Allocations::Allocations(const Config & config)
    : _cfg(config),
      _mutex(),
      _total_errors(0),
      _allocations()
{
    _allocations.reserve(config.nprocs() * config.allocs_per_thread());
    std::cout << "Starting memory stress with " << config.nprocs() << " threads and heap size " << (config.heap_size()/1_Mi) << " mb. Allocation size = " << config.alloc_size() << std::endl;
}

Allocations::~Allocations() = default;

size_t
Allocations::make_and_load_alloc_per_thread() {
    auto alloc = std::make_unique<char[]>(cfg().alloc_size());
    memset(alloc.get(), 0, cfg().alloc_size());
    std::lock_guard guard(_mutex);
    _allocations.push_back(std::move(alloc));
    return 1;
}

size_t
Allocations::verify_random_allocation(unsigned int *seed) const {
    const char * alloc;
    {
        std::lock_guard guard(_mutex);
        alloc = _allocations[rand_r(seed) % _allocations.size()].get();
    }
    size_t error_count = verify_allocation(alloc);
    std::lock_guard guard(_mutex);
    _total_errors += error_count;
    return error_count;
}

size_t
Allocations::verify_allocation(const char * alloc) const {
    size_t error_count = 0;
    for (size_t i = 0; i < cfg().alloc_size(); i++) {
        if (alloc[i] != 0) {
            error_count++;
            std::lock_guard guard(log_mutex);
            std::cout << "Thread " << std::this_thread::get_id() << ": Unexpected byte(" << std::hex << int(alloc[i]) << ") at " << static_cast<const void *>(alloc + i) << std::endl;
        }
    }
    return error_count;
}

class FileBackedMemory {
public:
    FileBackedMemory(const Config & config, std::string dir);
    ~FileBackedMemory();
    const Config & cfg() const { return _cfg; }
    size_t make_and_load_alloc_per_thread();
    void random_write(unsigned int *seed);
private:
    using PtrAndSize = std::pair<void *, size_t>;
    const Config           & _cfg;
    mutable std::mutex       _mutex;
    alloc::MmapFileAllocator _allocator;
    std::vector<PtrAndSize>  _allocations;
};

FileBackedMemory::FileBackedMemory(const Config & config, std::string dir)
    : _cfg(config),
      _mutex(),
      _allocator(dir),
      _allocations()
{
    _allocations.reserve(config.nprocs() * config.allocs_per_thread());
    std::cout << "Starting mmapped stress in '" << dir << "' with " << config.nprocs() << " threads and heap size " << (config.heap_size()/1_Mi) << " mb. Allocation size = " << config.alloc_size() << std::endl;
}

FileBackedMemory::~FileBackedMemory() {
    std::lock_guard guard(_mutex);
    for (auto ptrAndSize : _allocations) {
        _allocator.free(ptrAndSize);
    }
}


size_t
FileBackedMemory::make_and_load_alloc_per_thread() {
    PtrAndSize alloc;
    {
        std::lock_guard guard(_mutex);
        alloc = _allocator.alloc(cfg().alloc_size());
    }
    memset(alloc.first, 0, cfg().alloc_size());
    std::lock_guard guard(_mutex);
    _allocations.push_back(std::move(alloc));
    return 1;
}

void
FileBackedMemory::random_write(unsigned int *seed) {
    PtrAndSize ptrAndSize;
    {
        std::lock_guard guard(_mutex);
        ptrAndSize = _allocations[rand_r(seed) % _allocations.size()];
    }
    memset(ptrAndSize.first, rand_r(seed)%256, ptrAndSize.second);
}

void
stress_and_validate_heap(Allocations *allocs) {
    size_t num_verifications = 0;
    size_t num_errors = 0;
    size_t num_allocs = allocs->make_and_load_alloc_per_thread();
    const size_t max_allocs = allocs->cfg().allocs_per_thread();
    const double alloc_time = to_s(allocs->cfg().alloc_time());
    steady_time start = steady_clock::now();
    unsigned int seed = start.time_since_epoch().count()%4294967291ul;
    for (;!stopped; num_verifications++) {
        num_errors += allocs->verify_random_allocation(&seed);
        double ratio = to_s(steady_clock::now() - start) / alloc_time;
        if (num_allocs < std::min(size_t(ratio*max_allocs), max_allocs)) {
            num_allocs += allocs->make_and_load_alloc_per_thread();
        }
    }
    std::lock_guard guard(log_mutex);
    std::cout << "Thread " << std::this_thread::get_id() << ": Completed " << num_verifications << " verifications with " << num_errors << std::endl;
}

void
stress_file_backed_memory(FileBackedMemory * mmapped) {
    size_t num_writes = 0;
    size_t num_allocs = mmapped->make_and_load_alloc_per_thread();
    const size_t max_allocs = mmapped->cfg().allocs_per_thread();
    const double alloc_time = to_s(mmapped->cfg().alloc_time());
    steady_time start = steady_clock::now();
    unsigned int seed = start.time_since_epoch().count()%4294967291ul;
    for (;!stopped; num_writes++) {
        mmapped->random_write(&seed);
        double ratio = to_s(steady_clock::now() - start) / alloc_time;
        if (num_allocs < std::min(size_t(ratio*max_allocs), max_allocs)) {
            num_allocs += mmapped->make_and_load_alloc_per_thread();
        }
    }
    std::lock_guard guard(log_mutex);
    std::cout << "Thread " << std::this_thread::get_id() << ": Completed " << num_writes << " writes" << std::endl;
}

int
main(int argc, char *argv[]) {
    size_t heapSize = 1_Gi;
    duration runTime = 10s;
    std::string swap_dir;
    std::cout << description << std::endl;
    for (int i = 1; i+2 <= argc; i+=2) {
        char option = argv[i][strlen(argv[i]) - 1];
        char *arg = argv[i+1];
        switch (option) {
            case 'h': heapSize = atof(arg) * 1_Gi; break;
            case 's': swap_dir = arg; break;
            case 't': runTime = from_s(atof(arg)); break;
            default:
                std::cerr << "Option " << option << " not in allowed set [h,s,t]" << std::endl;
                break;
        }
    }
    size_t nprocs = std::thread::hardware_concurrency();
    size_t allocations_per_thread = 1024;

    Config cfgHeap(heapSize, nprocs, allocations_per_thread, runTime/2);
    Config cfgFile(heapSize*2, nprocs, allocations_per_thread, runTime/2);
    Allocations allocations(cfgHeap);
    std::unique_ptr<FileBackedMemory> filebackedMemory;

    std::vector<std::thread> heapValidators;
    heapValidators.reserve(nprocs*2);
    for (unsigned int i = 0; i < nprocs; i++) {
        heapValidators.emplace_back(stress_and_validate_heap, &allocations);
    }
    if ( ! swap_dir.empty()) {
        std::filesystem::create_directories(swap_dir);
        filebackedMemory = std::make_unique<FileBackedMemory>(cfgFile, swap_dir);
        for (unsigned int i = 0; i < nprocs; i++) {
            heapValidators.emplace_back(stress_file_backed_memory, filebackedMemory.get());
        }
    }
    std::cout << "Running memory stresstest for " << to_s(runTime) << " seconds" << std::endl;
    steady_time eot = steady_clock::now() + runTime;
    while (steady_clock::now() < eot) {
        std::this_thread::sleep_for(1s);
    }
    stopped = true;
    for (auto & th : heapValidators) {
        th.join();
    }
    heapValidators.clear();
    size_t num_errors = allocations.verify_and_report_errors();
    std::cout << "Completed stresstest with " << num_errors << " errors" << std::endl;
    return num_errors == 0 ? 0 : 1;
}
