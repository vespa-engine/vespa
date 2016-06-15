// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::MemFileCompactor
 * \ingroup memfile
 *
 * \brief Class containing logic to find what slots in a memfile can be removed.
 */
#pragma once

#include <vespa/memfilepersistence/common/types.h>
#include <limits>

namespace storage {
namespace memfile {

class MemFile;
class MemSlot;

struct CompactionOptions
{
    framework::MicroSecTime _revertTimePeriod;
    framework::MicroSecTime _keepRemoveTimePeriod;
    uint32_t _maxDocumentVersions {std::numeric_limits<uint32_t>::max()};

    CompactionOptions& revertTimePeriod(framework::MicroSecTime t) {
        _revertTimePeriod = t;
        return *this;
    }

    CompactionOptions& keepRemoveTimePeriod(framework::MicroSecTime t) {
        _keepRemoveTimePeriod = t;
        return *this;
    }

    CompactionOptions& maxDocumentVersions(uint32_t maxVersions) {
        _maxDocumentVersions = maxVersions;
        return *this;
    }
};

class DocumentVersionInfo;

class MemFileCompactor : public Types
{
public:
    using SlotList = std::vector<const MemSlot*>;

    MemFileCompactor(framework::MicroSecTime currentTime,
                     const CompactionOptions& options);

    SlotList getSlotsToRemove(const MemFile& memFile);
private:
    bool exceededVersionCount(const DocumentVersionInfo&) const noexcept;
    bool keepRemoveTimeExpired(const MemSlot& slot) const noexcept;
    void compactIfNotRevertible(const MemSlot& slot,
                                SlotList& slotsToRemove) const;
    void alwaysCompact(const MemSlot& slot, SlotList& slotsToRemove) const;

    CompactionOptions _options;
    framework::MicroSecTime _currentTime;
    framework::MicroSecTime _revertTimePoint;
    framework::MicroSecTime _keepRemoveTimePoint;
};

} // memfile
} // storage

