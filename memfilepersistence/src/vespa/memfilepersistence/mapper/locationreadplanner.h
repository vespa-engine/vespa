// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::LocationDiskIoPlanner
 * \ingroup memfile
 *
 * \brief Creates list of minimal IO operations to do versus disk.
 *
 * When accessing many locations on disk, it is not necessarily ideal to do a
 * disk access per location. This class creates a minimal set of locations to
 * access to avoid accessing more than a maximum gap of uninteresting data.
 */
#pragma once

#include <vespa/memfilepersistence/common/types.h>

namespace storage {
namespace memfile {

class MemSlot;

class MemFileIOInterface;

class LocationDiskIoPlanner : public Types, public vespalib::Printable
{
public:
    LocationDiskIoPlanner(const MemFileIOInterface& io,
                          DocumentPart part,
                          const std::vector<DataLocation>& desiredLocations,
                          uint32_t maxGap,
                          uint32_t blockStartIndex);

    const std::vector<DataLocation>& getIoOperations() const {
        return _operations;
    }

    /**
     * Get the total amount of space needed to hold all the data from all
     * locations identified to be accessed. Useful to create a buffer of correct
     * size.
     */
    uint32_t getTotalBufferSize() const;

    void print(std::ostream& out, bool verbose,
               const std::string& indent) const override;

private:
    const MemFileIOInterface& _io;
    std::vector<DataLocation> _operations;
    DocumentPart _part;
    uint32_t _blockStartIndex;

    void processLocations(
            const std::vector<DataLocation>& desiredLocations,
            uint32_t maxGap);

    void scheduleLocation(DataLocation loc,
                          std::vector<DataLocation>&);
};

} // memfile
} // storage

