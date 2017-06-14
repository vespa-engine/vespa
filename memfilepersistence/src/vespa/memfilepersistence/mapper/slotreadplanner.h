// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::SlotDiskIoPlanner
 * \ingroup memfile
 *
 * \brief Creates list of minimal IO operations to do versus disk.
 *
 * When accessing many locations on disk, it is not necessarily ideal to do a
 * disk access per location. This class creates a minimal set of locations to
 * access to avoid accessing more than a maximum gap of uninteresting data.
 */
#pragma once

#include <storage/persistence/memfile/common/types.h>

namespace storage {
namespace memfile {

class MemSlot;

class SlotDiskIoPlanner : public Types, public vespalib::Printable
{
public:
    SlotDiskIoPlanner(const std::vector<const MemSlot*> desiredSlots,
                      DocumentPart highestPartNeeded,
                      uint32_t maxGap,
                      uint32_t headerBlockStartIndex,
                      uint32_t bodyBlockStartIndex);

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
               const std::string& indent) const;

private:
    std::vector<DataLocation> _operations;
    std::vector<uint32_t> _startIndexes;

    void processSlots(
            const std::vector<const MemSlot*> desiredSlots,
            DocumentPart highestPartNeeded,
            uint32_t maxGap);

    void scheduleLocation(const MemSlot&, DocumentPart,
                          std::vector<DataLocation>&);
};

} // memfile
} // storage

