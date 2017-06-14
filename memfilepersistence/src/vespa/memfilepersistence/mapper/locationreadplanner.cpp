// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "locationreadplanner.h"
#include <vespa/memfilepersistence/memfile/memfileiointerface.h>

namespace storage {
namespace memfile {

LocationDiskIoPlanner::LocationDiskIoPlanner(
        const MemFileIOInterface& io,
        DocumentPart part,
        const std::vector<DataLocation>& desiredLocations,
        uint32_t maxGap,
        uint32_t blockStartIndex)
    : _io(io),
      _operations(),
      _part(part),
      _blockStartIndex(blockStartIndex)
{
    processLocations(desiredLocations, maxGap);
}

namespace {
    uint32_t alignDown(uint32_t value) {
        uint32_t blocks = value / 512;
        return blocks * 512;
    };

    uint32_t alignUp(uint32_t value) {
        uint32_t blocks = (value + 512 - 1) / 512;
        return blocks * 512;
    };
}

void
LocationDiskIoPlanner::scheduleLocation(DataLocation loc,
                                        std::vector<DataLocation>& ops)
{
    if (!_io.isCached(loc, _part) && loc._size) {
        // Convert the relative location from the buffer to an
        // absolute location.
        ops.push_back(DataLocation(loc._pos + _blockStartIndex,
                                   loc._size));
    }
}

void
LocationDiskIoPlanner::processLocations(
        const std::vector<DataLocation>& desiredLocations,
        uint32_t maxGap)
{
    // Build list of disk read operations to do
    std::vector<DataLocation> allOps;

    // Create list of all locations we need to read
    for (std::size_t i = 0; i < desiredLocations.size(); ++i) {
        scheduleLocation(desiredLocations[i], allOps);
    }

    // Sort list, and join elements close together into single IO ops
    std::sort(allOps.begin(), allOps.end());
    for (size_t i = 0; i < allOps.size(); ++i) {
        uint32_t start = alignDown(allOps[i]._pos);
        uint32_t stop = alignUp(allOps[i]._pos + allOps[i]._size);
        if (i != 0) {
            uint32_t lastStop = _operations.back()._pos
                              + _operations.back()._size;
            if (lastStop >= start || start - lastStop < maxGap) {
                _operations.back()._size += (stop - lastStop);
                continue;
            }
        }

        _operations.push_back(DataLocation(start, stop - start));
    }
}

uint32_t
LocationDiskIoPlanner::getTotalBufferSize() const
{
    uint32_t totalSize = 0;
    for (size_t i = 0; i < _operations.size(); ++i) {
        totalSize += _operations[i]._size;
    }
    return totalSize;
}

void
LocationDiskIoPlanner::print(std::ostream& out, bool verbose,
                             const std::string& indent) const
{
    (void) verbose; (void) indent;
    for (std::size_t i = 0; i < _operations.size(); ++i) {
        if (i > 0) out << ",";
        out << "[" << _operations[i]._pos << ","
            << (_operations[i]._size + _operations[i]._pos) << "]";
    }
}

} // memfile
} // storage
