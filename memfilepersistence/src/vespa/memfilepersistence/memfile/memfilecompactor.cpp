// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfilecompactor.h"
#include "memfile.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/log/log.h>

LOG_SETUP(".persistence.memfile.compactor");

namespace storage {
namespace memfile {

struct DocumentVersionInfo {
    document::DocumentId _id;
    uint32_t _versions;
    bool _tombstoned;

    DocumentVersionInfo(const document::DocumentId& docId, bool tombstoned)
        : _id(docId),
          _versions(1),
          _tombstoned(tombstoned)
    { }

    bool newerVersionExists() const noexcept {
        return (_versions != 1);
    }
};

namespace {

bool
isTombstone(const MemSlot& slot)
{
    return slot.deleted();
}

// Deduct with underflow protection
template<typename T>
T deduct(T a, T b) {
    return (a > b ? a - b : T(0));
}

struct CompactSlotInfo : private Types {
    typedef std::list<DocumentVersionInfo> DocList;
    typedef vespalib::hash_map<GlobalId, DocList, GlobalId::hash> Map;
    Map _info;
    const MemFile& _memFile;

    CompactSlotInfo(const MemFile& memFile)
        : _info(2 * memFile.getSlotCount()),
          _memFile(memFile)
    {
    }

    /**
     * Registers a particular document version as having been seen in the file,
     * keeping track of how many newer versions have been observed thus far and
     * whether at least one of these was a tombstone (remove entry).
     *
     * Potential GID collisions are handled by utilizing the actual document
     * ID to track specific documents.
     *
     * Returns a reference to the currently tracked version state for the
     * document the slot is for. Returned reference is valid until the next
     * invocation of registerSeen() or the owning CompactSlotInfo instance
     * is destructed.
     */
    DocumentVersionInfo& registerSeen(const MemSlot& slot) {
        document::DocumentId id = _memFile.getDocumentId(slot);
        DocList& gidDocs(_info[slot.getGlobalId()]);
        auto matchesId = [&](const DocumentVersionInfo& doc) {
            return (id == doc._id);
        };
        auto existing = std::find_if(
                gidDocs.begin(), gidDocs.end(), matchesId);

        if (existing == gidDocs.end()) { // (Very) common case
            gidDocs.emplace_back(id, isTombstone(slot));
            return gidDocs.back();
        } else {
            ++existing->_versions;
            if (isTombstone(slot)) {
                existing->_tombstoned = true;
            }
            return *existing;
        }
    }
};

class DecreasingTimestampSlotRange
{
public:
    DecreasingTimestampSlotRange(const MemFile& memFile)
        : _memFile(memFile)
    {
    }
    MemFile::const_iterator begin() const {
        return _memFile.begin(Types::ITERATE_REMOVED);
    }
    MemFile::const_iterator end() const {
        return _memFile.end();
    }
private:
    const MemFile& _memFile;
};

DecreasingTimestampSlotRange
allSlotsInDecreasingTimestampOrder(const MemFile& memFile)
{
    return {memFile};
}

}

MemFileCompactor::MemFileCompactor(
        framework::MicroSecTime currentTime,
        const CompactionOptions& options)
    : _options(options),
      _currentTime(currentTime),
      _revertTimePoint(deduct(currentTime, options._revertTimePeriod)),
      _keepRemoveTimePoint(deduct(currentTime, options._keepRemoveTimePeriod))
{
    assert(_options._maxDocumentVersions != 0);
}

/*
 * Cases to handle:
 *  - Document has too many versions; always remove slot
 *     - But otherwise, only remove if older than revert time.
 *  - Remove entry is too old; remove slot if older than revert time AND keep
 *    remove time.
 *     - Tombstoned entries are not resurrected as they are either compacted
 *       away due to being outside the revert time period or their tombstone
 *       survives by being inside the revert time period. The "keep remove
 *       time" period is also forced to be at least as high as the revert time
 *       period at configuration time.
 *  - Otherwise, keep the slot.
 */
MemFileCompactor::SlotList
MemFileCompactor::getSlotsToRemove(const MemFile& memFile)
{
    memFile.ensureHeaderBlockCached();

    std::vector<const MemSlot*> removeSlots;
    CompactSlotInfo slots(memFile);

    LOG(spam,
        "Running compact on %s. Using revertTime=%zu, "
        "keepRemoveTime=%zu, maxDocumentVersions=%u",
        memFile.toString(true).c_str(),
        _revertTimePoint.getTime(),
        _keepRemoveTimePoint.getTime(),
        _options._maxDocumentVersions);

    for (auto& slot : allSlotsInDecreasingTimestampOrder(memFile)) {
        DocumentVersionInfo& info(slots.registerSeen(slot));

        if (exceededVersionCount(info)) {
            alwaysCompact(slot, removeSlots);
        } else if (info.newerVersionExists()) {
            // A tombstone also counts as a newer version.
            compactIfNotRevertible(slot, removeSlots);
        } else if (isTombstone(slot) && keepRemoveTimeExpired(slot)) {
            compactIfNotRevertible(slot, removeSlots);
        } // else: keep slot since it's the newest or within revert period.
    }

    std::reverse(removeSlots.begin(), removeSlots.end());
    return removeSlots;
}

bool
MemFileCompactor::exceededVersionCount(
        const DocumentVersionInfo& info) const noexcept
{
    return (info._versions > _options._maxDocumentVersions);
}

bool
MemFileCompactor::keepRemoveTimeExpired(const MemSlot& slot) const noexcept
{
    return (slot.getTimestamp() < _keepRemoveTimePoint);
}

void
MemFileCompactor::compactIfNotRevertible(
        const MemSlot& slot,
        SlotList& slotsToRemove) const
{
    // May compact slot away if its timestamp is older than the point in time
    // where we expect reverts may be sent.
    if (slot.getTimestamp() < _revertTimePoint) {
        alwaysCompact(slot, slotsToRemove);
    }
}

void
MemFileCompactor::alwaysCompact(const MemSlot& slot,
                                SlotList& slotsToRemove) const
{
    LOG(spam, "Compacting slot %s", slot.toString().c_str());
    slotsToRemove.push_back(&slot);
}


} // memfile
} // storage
