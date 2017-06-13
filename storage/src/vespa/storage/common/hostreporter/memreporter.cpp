// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memreporter.h"
#include "kernelmetrictool.h"

namespace storage {
namespace {
using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;
using kernelmetrictool::readFile;
using kernelmetrictool::getLine;
using kernelmetrictool::getToken;
using kernelmetrictool::toLong;}

MemReporter::MemReporter() {}
MemReporter::~MemReporter() {}

void MemReporter::report(vespalib::JsonStream& jsonreport) {
    /* Parse /proc/meminfo. Expected format
     * MemTotal:     36969940 kB
     * MemFree:      13856316 kB
     * Buffers:        612476 kB
     * Cached:       18603000 kB
     * SwapCached:      71064 kB
     * Active:       13504144 kB
     * Inactive:      7781768 kB
     * HighTotal:           0 kB
     * HighFree:            0 kB
     * LowTotal:     36969940 kB
     * LowFree:      13856316 kB
     * SwapTotal:    33554424 kB
     * SwapFree:     33465824 kB
     * Dirty:            1416 kB
     * Writeback:           0 kB
     * Mapped:        1225592 kB
     * Slab:          1669252 kB
     * CommitLimit:  52039392 kB
     * Committed_AS:  2337076 kB
     * PageTables:      12992 kB
     * VmallocTotal: 536870908 kB
     * VmallocUsed:    377960 kB
     * VmallocChunk: 536492708 kB
     */
    vespalib::string content = readFile("/proc/meminfo");
    // Usable RAM - Physical memory minus reserved bits and kernel code
    uint64_t memTotal = toLong(getToken(1, getLine("MemTotal:", content))) * 1024;
    // LowFree + HighFree
    uint64_t memFree = toLong(getToken(1, getLine("MemFree:", content))) * 1024;
    // Disk data cached in memory
    uint64_t cached = toLong(getToken(1, getLine("Cached:", content))) * 1024;
    // Memory used recently.
    uint64_t active = toLong(getToken(1, getLine("Active:", content))) * 1024;
    uint64_t inActive = toLong(getToken(1, getLine("Inactive:", content))) * 1024;
    uint64_t swapTotal = toLong(getToken(1, getLine("SwapTotal:", content))) * 1024;
    uint64_t swapFree = toLong(getToken(1, getLine("SwapFree:", content))) * 1024;
    // Bytes that may need to be written to disk soon. Swap or disk.
    uint64_t dirty = toLong(getToken(1, getLine("Dirty:", content))) * 1024;

    jsonreport << "memory" << Object()
                          << "total memory" << memTotal
                          << "free memory" << memFree
                          << "disk cache" << cached
                          << "active memory" << active
                          << "inactive memory" << inActive
                          << "swap total" << swapTotal
                          << "swap free" << swapFree
                          << "dirty" << dirty
                          << End();
}

} /* namespace storage */
