// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfile_v1_verifier.h"
#include "memfilemapper.h"
#include "simplememfileiobuffer.h"
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.memfilev1.verifier");

namespace storage::memfile {

namespace {

void alignUp(uint32_t& value, uint32_t offset = 0, uint32_t block = 512) {
    uint32_t blocks = (value + offset + block - 1) / block;
    value = blocks * block - offset;
}

struct TimestampSlotOrder
    : public std::binary_function<MetaSlot*, MetaSlot*, bool>
{
    bool operator()(const MetaSlot* slot1,
                    const MetaSlot* slot2) const
    {
        return (slot1->_timestamp < slot2->_timestamp);
    }
};

struct HeaderSlotOrder
    : public std::binary_function<MetaSlot*,
                                  MetaSlot*, bool>
{
    bool operator()(const MetaSlot* slot1,
                    const MetaSlot* slot2) const
    {
        if (slot1->_headerPos == slot2->_headerPos) {
            return (slot1->_headerSize < slot2->_headerSize);
        }
        return (slot1->_headerPos < slot2->_headerPos);
    }
};

struct BodySlotOrder
    : public std::binary_function<MetaSlot*,
                                  MetaSlot*, bool>
{
    bool operator()(const MetaSlot* slot1,
                    const MetaSlot* slot2) const
    {
        if (slot1->_bodyPos == slot2->_bodyPos) {
            return (slot1->_bodySize < slot2->_bodySize);
        }
        return (slot1->_bodyPos < slot2->_bodyPos);
    }
};

uint32_t calculateChecksum(const void* pos, uint32_t size) {
    vespalib::crc_32_type calculator;
    calculator.process_bytes(pos, size);
    return calculator.checksum();
}

template<typename T>
bool verifyBodyBlock(const T& id, vespalib::asciistream & error,
                     const char* data, uint32_t size)
{
    uint32_t bodyLen = size - sizeof(uint32_t);
    const char* bodyCrcPos = data + bodyLen;
    const uint32_t bodyCrc = *reinterpret_cast<const uint32_t*>(bodyCrcPos);
    uint32_t calculatedChecksum = calculateChecksum(data, bodyLen);
    if (calculatedChecksum != bodyCrc) {
        error << "Body checksum mismatch for " << id
              << ": Stored checksum is 0x" << std::hex << bodyCrc
              << " while calculated one is 0x" << calculatedChecksum << ".";
        return false;
        }
    return true;
}

template<typename T>
bool verifyHeaderBlock(const T& id, vespalib::asciistream & error,
                       const char* data, uint32_t size,
                       Types::DocumentId* documentId = 0)
{
    if (size <= 3 * sizeof(uint32_t)) {
        error << "Error in header for " << id << ": " << size << " byte "
              << "header is too small to contain required data.";
        return false;
    }
    const char* nameCrcPos = data + size - sizeof(uint32_t);
    const uint32_t nameCrc = *reinterpret_cast<const uint32_t*>(nameCrcPos);
    const char* nameLenPos = nameCrcPos - sizeof(uint32_t);
    const uint32_t nameLen = *reinterpret_cast<const uint32_t*>(nameLenPos);
    if (size < 3 * sizeof(uint32_t) + nameLen) {
        error << "Error in header for " << id << ": " << size << " byte "
              << "header is not big enough to contain a document "
              << "identifier " << nameLen << " bytes long.";
        return false;
    }
    const char *namePos = nameLenPos - nameLen;
    uint32_t calculatedNameCrc(
            calculateChecksum(namePos, nameLen + sizeof(uint32_t)));
    if (calculatedNameCrc != nameCrc) {
        error << "Document identifier checksum mismatch for " << id
              << ": Stored checksum is 0x" << std::hex << nameCrc
              << " while calculated one is 0x" << calculatedNameCrc << ".";
        return false;
    }
    const char* blobCrcPos = namePos - sizeof(uint32_t);
    const uint32_t blobCrc = *reinterpret_cast<const uint32_t*>(blobCrcPos);
    uint32_t blobLen = size - nameLen - 3 * sizeof(uint32_t);
    uint32_t calculatedChecksum = calculateChecksum(data, blobLen);
    if (calculatedChecksum != blobCrc) {
        error << "Header checksum mismatch for " << id
              << ": Stored checksum is 0x" << std::hex << blobCrc
              << " while calculated one is 0x" << calculatedChecksum << ".";
        return false;
    }
    if (documentId != 0) {
        *documentId = Types::DocumentId(Types::String(namePos, nameLen));
    }
    return true;
}

}

// Utility classes for simplifying creating report from verify
struct MemFileV1Verifier::ReportCreator {
    bool _ok;
    const MemFile& _file;
    std::ostream& _report;

    ReportCreator(const MemFile& file, std::ostream& out)
        : _ok(true), _file(file), _report(out) {}

    void addMessage(const std::string& msg) {
        LOG(warning, "verify(%s): %s",
            _file.getFile().getPath().c_str(), msg.c_str());
        _report << msg << "\n";
        _ok = false;
    }
};

namespace {
    struct ReportMessage {
        MemFileV1Verifier::ReportCreator& _report;
        mutable std::ostringstream _ost;

        ReportMessage(MemFileV1Verifier::ReportCreator& rc)
            : _report(rc), _ost() {}
        ~ReportMessage() {
            _report.addMessage(_ost.str());
        }
            // Copy constructor must exist for compiler not to complain
        ReportMessage(const ReportMessage& o) : _report(o._report), _ost() {}
    };

    std::ostream& getReportStream(const ReportMessage& m) { return m._ost; }
}

#define REPORT(report) getReportStream(ReportMessage(report))

bool
MemFileV1Verifier::verifyBlock(Types::DocumentPart part,
                               uint32_t id,
                               vespalib::asciistream & error,
                               const char* data, uint32_t size)
{
    return (part == Types::HEADER
            ?  verifyHeaderBlock(id, error, data, size)
            :  verifyBodyBlock(id, error, data, size));
}

const Header*
MemFileV1Verifier::verifyHeader(ReportCreator& report,
                                   const Buffer& buffer, size_t fileSize) const
{
    const Header& header = *reinterpret_cast<const Header*>(buffer.getBuffer());
    if (header._checksum != header.calcHeaderChecksum()) {
        REPORT(report) << "Header checksum mismatch. Was " << std::hex
                       << header.calcHeaderChecksum() << ", stored "
                       << header._checksum;
        return 0;
    }
    FileInfo data(header, fileSize);
    if (data.getBodyBlockStartIndex() > fileSize) {
        REPORT(report) << "Header indicates file is bigger than it physically "
                       << "is. File size is " << fileSize << " bytes, but "
                       << "header reports that it contains "
                       << header._metaDataListSize
                       << " meta data entries and a headerblock of "
                       << header._headerBlockSize << " bytes, thus the minimum "
                      << "file size is "
                      << (header._metaDataListSize * sizeof(MetaSlot)
                          + sizeof(Header) + header._headerBlockSize);
        return 0;
    }
    return &header;
}

bool
MemFileV1Verifier::verifyDocumentBody(
        ReportCreator& report, const MetaSlot& slot, const Buffer& buffer,
        uint32_t blockIndex, uint32_t blockSize) const
{
    if (slot._bodySize == 0) return true;
    if (slot._bodyPos > blockSize ||
        slot._bodyPos + slot._bodySize > blockSize ||
        slot._bodyPos + slot._bodySize < slot._bodyPos)
    {
        REPORT(report) << slot << " has body size/pos not contained within "
                       << "body block of size " << blockSize << ".";
        return false;
    }
    if (slot._bodySize <= sizeof(uint32_t)) {
        REPORT(report) << slot << " body is not big enough to possibly "
                       << "contain a body.";
        return false;
    }
    vespalib::asciistream error;
    if (!verifyBodyBlock(slot, error,
                         buffer.getBuffer() + blockIndex + slot._bodyPos,
                         slot._bodySize))
    {
        REPORT(report) << error.str();
        return false;
    }
    return true;
}

void
MemFileV1Verifier::verifyMetaDataBlock(
        ReportCreator& report, const Buffer& buffer,
        const Header& header, const BucketInfo& info,
        std::vector<const MetaSlot*>& slots) const
{
    assert(slots.size() == 0);
    slots.reserve(header._metaDataListSize);
    Timestamp lastTimestamp(0);
    bool foundNotInUse = false;
    bool foundUsedAfterUnused = false;
    bool wrongOrder = false;
    for (uint32_t i=0, n=header._metaDataListSize; i<n; ++i) {
        const MetaSlot& slot(*reinterpret_cast<const MetaSlot*>(
                buffer.getBuffer() + sizeof(Header) + i * sizeof(MetaSlot)));
        if (slot._checksum != slot.calcSlotChecksum()) {
            REPORT(report) << "Slot " << i << " at timestamp "
                           << slot._timestamp << " failed checksum "
                           << "verification. Was " << std::hex
                           << slot.calcSlotChecksum()
                           << ", stored " << slot._checksum;
            continue;
        }
        if (!slot.inUse()) {
            foundNotInUse = true;
            continue;
        }
        if (foundNotInUse) {
            if (!foundUsedAfterUnused) {
                REPORT(report) << "Slot " << i << " found after unused entries";
            }
            foundUsedAfterUnused = true;
        }
            // Handle timestamp collisions later
        if (slot._timestamp < lastTimestamp) {
            wrongOrder = true;
            REPORT(report) << "Slot " << i << " is out of timestamp order. ("
                           << slot._timestamp << " <= " << lastTimestamp
                           << ")";
        }
        slots.push_back(&slot);
        lastTimestamp = slot._timestamp;
    }
    if (info.getChecksum() != header._fileChecksum) {
        REPORT(report) << "File checksum should have been 0x" << std::hex
            << info.getChecksum() << " according to metadata found, but is set "
            << "to 0x" << header._fileChecksum << ".";
    }
    if (wrongOrder) {
        std::sort(slots.begin(), slots.end(), TimestampSlotOrder());
    }
}

void
MemFileV1Verifier::verifyInBounds(
        ReportCreator& report, const Header& header, bool doHeader,
        const FileInfo& data, std::vector<const MetaSlot*>& slots) const
{
        // Gather all information different for header and body parts,
        // to avoid differences further down.
    uint32_t blockSize = (doHeader ? header._headerBlockSize
                                   : data._bodyBlockSize);
    uint32_t minSize = (doHeader ? 3*sizeof(uint32_t) : 0);
    std::string part(doHeader ? "Header" : "Body");
    std::vector<const MetaSlot*> okSlots;
    okSlots.reserve(slots.size());
        // Go through all slots ordered, and remove illegal ones.
    for (uint32_t i=0, n=slots.size(); i<n; ++i) {
        uint32_t pos(doHeader ? slots[i]->_headerPos : slots[i]->_bodyPos);
        uint32_t size(doHeader ? slots[i]->_headerSize : slots[i]->_bodySize);
        if (size < minSize) {
            REPORT(report) << part << " of slot (" << *slots[i] << ") "
                           << "is too small to be valid";
        } else if (size != 0 &&
                   (pos >= blockSize || pos + size > blockSize ||
                    pos + size < pos)) // 3 checks as + can overflow
        {
            REPORT(report) << part << " of slot (" << *slots[i] << ") goes out "
                           << "of bounds. (Blocksize " << blockSize << ")";
        } else if (size == 0 && pos != 0) {
            REPORT(report) << part << " of slot (" << *slots[i] << ") "
                           << "has size 0 but is not positioned at pos 0 "
                           << "as zero sized blocks should be";
        } else {
            okSlots.push_back(slots[i]);
        }
    }
    okSlots.swap(slots);
}

void
MemFileV1Verifier::verifyDataBlock(
        ReportCreator& report, Environment& env, const Buffer& buffer,
        const FileInfo& data, const BucketId& bucket,
        std::vector<const MetaSlot*>& slots, bool doHeader) const
{
    std::vector<const MetaSlot*> okSlots;
    okSlots.reserve(slots.size());
    for (uint32_t i=0, n=slots.size(); i<n; ++i) {
        if (!doHeader && slots[i]->_bodySize == 0) {
            okSlots.push_back(slots[i]);
            continue;
        }
        if (doHeader) {
            DocumentId id;
            if (!verifyDocumentHeader(report, *slots[i], buffer, id,
                                      data.getHeaderBlockStartIndex(),
                                      data._headerBlockSize))
            {
                continue;
            }
            BucketId foundBucket(env._bucketFactory.getBucketId(id));
            foundBucket.setUsedBits(bucket.getUsedBits());
            foundBucket = foundBucket.stripUnused();
            if (id.getGlobalId() != slots[i]->_gid) {
                REPORT(report) << *slots[i]
                               << " has gid " << slots[i]->_gid.toString()
                               << " but its header block contains document id "
                               << id << " with " << id.getGlobalId().toString();
            }
            else if (bucket == foundBucket) {
                okSlots.push_back(slots[i]);
            } else {
                REPORT(report) << "Slot " << *slots[i]
                               << " belongs to bucket " << foundBucket
                               << " not in bucket " << bucket;
            }
        } else {
            if (!verifyDocumentBody(report, *slots[i], buffer,
                                    data.getBodyBlockStartIndex(),
                                    data._bodyBlockSize))
            {
                continue;
            }
            okSlots.push_back(slots[i]);
        }
    }
    slots.swap(okSlots);
}

bool
MemFileV1Verifier::verifyDocumentHeader(
        ReportCreator& report, const MetaSlot& slot, const Buffer& buffer,
        DocumentId& did, uint32_t blockIndex, uint32_t blockSize) const
{
    if (slot._headerPos > blockSize ||
        slot._headerPos + slot._headerSize > blockSize ||
        slot._headerPos + slot._headerSize < slot._headerPos)
    {
        REPORT(report) << slot << " has header size/pos not contained within "
                       << "header block of size " << blockSize << ".";
        return false;
    }
    vespalib::asciistream error;
    if (!verifyHeaderBlock(slot, error,
                           buffer.getBuffer() + blockIndex + slot._headerPos,
                           slot._headerSize, &did))
    {
        REPORT(report) << error.str();
        return false;
    }
    return true;
}

namespace {
// Helper function for verifyNonOverlap
    void verifySlotsAtSamePosition(
            MemFileV1Verifier::ReportCreator& report,
            bool header,
            std::vector<const MetaSlot*>& slots,
            vespalib::hash_set<const MetaSlot*,
            vespalib::hash<void *> >& faultySlots)
    {
        const Types::GlobalId& gid(slots[0]->_gid);
        for (uint32_t i=1; i<slots.size(); ++i) {
            if (slots[i]->_gid != gid) {
                REPORT(report) << "Multiple slots with different gids use same "
                               << (header ? "header" : "body")
                               << " position. For instance slot "
                               << *slots[0] << " and " << *slots[i]
                               << ". Repairing will delete all " << slots.size()
                               << " slots using this position, as we don't "
                               << "know who is correct.";
                for (uint32_t j=0; j<slots.size(); ++j) {
                    faultySlots.insert(slots[j]);
                }
                break;
            }
        }
    }
}

void
MemFileV1Verifier::verifyNonOverlap(
        ReportCreator& report, bool doHeader,
        std::vector<const MetaSlot*>& slots) const
{
        // Gather all information different for header and body parts,
        // to avoid differences further down.
    std::string part(doHeader ? "Header" : "Body");
    std::vector<const MetaSlot*> order(slots);
        // Using stable sort to sort slots, makes slots in same position
        // keep timestamp order. (Thus we can use that if we want to remove
        // oldest or newest illegally at same timestamp)
    if (doHeader) {
        std::stable_sort(order.begin(), order.end(), HeaderSlotOrder());
    } else {
        std::stable_sort(order.begin(), order.end(), BodySlotOrder());
    }
        // Temporary store slots that need to be removed
    vespalib::hash_set<const MetaSlot*, vespalib::hash<void *> > failedSlots;
        // Slots that points to the same area within a block.
    std::vector<const MetaSlot*> local;
    uint32_t lastPos = 0, lastSize = 0;
        // Go through all slots ordered, and remove illegal ones.
    for (uint32_t i=0, n=order.size(); i<n; ++i) {
        uint32_t pos(doHeader ? order[i]->_headerPos : order[i]->_bodyPos);
        uint32_t size(doHeader ? order[i]->_headerSize : order[i]->_bodySize);
        if (size == 0) {
            // Ignore zero sized entries
        } else if (pos == lastPos && size == lastSize) {
            local.push_back(order[i]);
        } else if (pos < lastPos + lastSize) {
            std::ostringstream ost;
            if (!local.empty()) {
                for (uint32_t j=0; j<local.size(); ++j) {
                    failedSlots.insert(local[j]);
                    if (j != 0) ost << ", ";
                    ost << *local[j];
                }
            }
            failedSlots.insert(order[i]);
            if (local.empty()) {
                REPORT(report) << part << " of slot(" << *order[i] << ") "
                               << "overlaps with previously removed slots.";
            } else {
                REPORT(report) << part << " of slot (" << *order[i] << ") "
                               << "overlaps with "
                               << (local.size() == 1 ? "slot"
                                                     : "the following slots")
                               << " " << ost.str() << ".";
            }
            local.clear();
            lastPos = pos;
            lastSize = size;
        } else {
            if (local.size() > 1) {
                verifySlotsAtSamePosition(report, doHeader, local, failedSlots);
            }
            local.clear();
            local.push_back(order[i]);
            lastPos = pos;
            lastSize = size;
        }
    }
    if (local.size() > 1) {
        verifySlotsAtSamePosition(report, doHeader, local, failedSlots);
    }
    if (failedSlots.size() == 0) return;
    std::vector<const MetaSlot*> okSlots;
    okSlots.reserve(slots.size() - failedSlots.size());
    for (uint32_t i=0, n=slots.size(); i<n; ++i) {
        if (failedSlots.find(slots[i]) == failedSlots.end()) {
            okSlots.push_back(slots[i]);
        }
    }
    okSlots.swap(slots);
}



bool
MemFileV1Verifier::verify(MemFile& file, Environment& env,
                          std::ostream& reportStream,
                          bool repairErrors, uint16_t fileVerifyFlags)
{
    bool verifyHeaderData = ((fileVerifyFlags & DONT_VERIFY_HEADER) == 0);
    bool verifyBodyData = ((fileVerifyFlags & DONT_VERIFY_BODY) == 0);

    LOG(debug, "verify(%s%s%s%s)",
        file.getFile().toString().c_str(),
        repairErrors ? ", repairing errors" : "",
        verifyHeaderData ? ", verifying header block" : "",
        verifyBodyData ? ", verifying body block" : "");

    SimpleMemFileIOBuffer& ioBuf(
            static_cast<SimpleMemFileIOBuffer&>(file.getMemFileIO()));

    framework::MilliSecTimer startTimer(env._clock);
    ReportCreator report(file, reportStream);
    file.verifyConsistent();
    if (!file.fileExists()) return report._ok;

    // First read at least the header from disk
    size_t fileSize = ioBuf.getFileHandle().getFileSize();
    if (fileSize < sizeof(Header)) {
        REPORT(report) << "File was only " << fileSize
                       << " B long and cannot be valid. Delete file to repair.";
        if (repairErrors) {
            env._memFileMapper.deleteFile(file, env);
        }
        return report._ok;
    }
    const size_t initialIndexRead(
            env.acquireConfigReadLock().options()->_initialIndexRead);
    Buffer buffer(std::min(fileSize, initialIndexRead));
    size_t readBytes = ioBuf.getFileHandle().read(buffer, buffer.getSize(), 0);

    // Exception should have been thrown by read if mismatch here.
    assert(readBytes == buffer.getSize());

    // Ensure slotfile header is ok. If not just delete whole file.
    const Header* header = verifyHeader(report, buffer, fileSize);
    if (header == 0) {
        if (repairErrors) {
            env._memFileMapper.deleteFile(file, env);
        }
        return report._ok;
    }

    FileInfo data(*header, fileSize);

    // Read remaining data needed in check, if any
    size_t lastNeededByte = sizeof(Header)
                         + sizeof(MetaSlot) * header->_metaDataListSize;
    if (verifyBodyData) {
        lastNeededByte = fileSize;
    } else if (verifyHeaderData) {
        lastNeededByte += header->_headerBlockSize;
    }
    if (buffer.getSize() < lastNeededByte) {
        buffer.resize(lastNeededByte);
        header = reinterpret_cast<const Header*>(buffer.getBuffer());
    }
    if (lastNeededByte > readBytes) {
        readBytes += ioBuf.getFileHandle().read(
                buffer + readBytes, buffer.getSize() - readBytes, readBytes);
    }

    // Exception should have been thrown by read if mismatch here.
    assert(readBytes == buffer.getSize());

    // Build list of slots. Do simple checking.
    std::vector<const MetaSlot*> slots;
    verifyMetaDataBlock(report, buffer, *header, file.getBucketInfo(), slots);
    verifyInBounds(report, *header, true, data, slots);
    verifyInBounds(report, *header, false, data, slots);

    // Check header and body blocks if wanted
    if (verifyHeaderData) {
        verifyDataBlock(report, env, buffer, data, file.getFile().getBucketId(),
                        slots, true);
    }
    if (verifyBodyData) {
        verifyDataBlock(report, env, buffer, data, file.getFile().getBucketId(),
                        slots, false);
    }
        // Check for overlapping slots last, in case only one of the slots
        // actually overlapped pointed to a legal document, we may have
        // already removed the problem.
    verifyNonOverlap(report, true, slots);
    verifyNonOverlap(report, false, slots);
    verifyUniqueTimestamps(report, slots);
        // If the slotlist is altered from what we read from disk, we need
        // to write it back if we're gonna repair the errors.
    if (!report._ok && repairErrors) {
            // Remove bad entries from the memfile instance
            // Entries that are cached in full may be removed from file and just
            // tagged not in file anymore in cache.
        std::vector<Timestamp> keep;
        for (uint32_t i=0; i<slots.size(); ++i) {
            keep.push_back(slots[i]->_timestamp);
        }
        env._memFileMapper.removeAllSlotsExcept(
                const_cast<MemFile&>(file), keep);

            // Edit header and metadata part of buffer to only keep wanted data
            // Since both source and target is the same buffer, create new meta
            // data in new buffer and memcpy back afterwards
        Buffer metaData(header->_metaDataListSize * sizeof(MetaSlot));
        BucketInfo info(file.getBucketInfo());
        const_cast<Header*>(header)->_fileChecksum = info.getChecksum();
        for (uint32_t i=0; i<header->_metaDataListSize; ++i) {
            MetaSlot* slot(reinterpret_cast<MetaSlot*>(
                    metaData.getBuffer() + i * sizeof(MetaSlot)));
            if (i >= slots.size()) {
                *slot = MetaSlot();
            } else if (slot != slots[i]) {
                *slot = *slots[i];
            }
        }
        memcpy(buffer.getBuffer() + sizeof(Header), metaData.getBuffer(),
               metaData.getSize());
            // Then rewrite metadata section to disk leaving out bad entries
        uint32_t dataToWrite(sizeof(Header)
                             + sizeof(MetaSlot) * header->_metaDataListSize);
        alignUp(dataToWrite);
        ioBuf.getFileHandle().write(buffer, dataToWrite, 0);

        // Tag memfile up to date
        uint32_t memFileFlags = FILE_EXIST
                              | HEADER_BLOCK_READ
                              | BODY_BLOCK_READ;
        for (MemFile::const_iterator it = file.begin(ITERATE_REMOVED);
             it != file.end(); ++it)
        {
            if (!ioBuf.isCached(it->getLocation(BODY), BODY)) {
                memFileFlags &= ~BODY_BLOCK_READ;
            }
            if (!ioBuf.isCached(it->getLocation(HEADER), HEADER)) {
                memFileFlags &= ~HEADER_BLOCK_READ;
            }

            if (!ioBuf.isPersisted(it->getLocation(BODY), BODY)
                || !ioBuf.isPersisted(it->getLocation(HEADER), HEADER))
            {
                memFileFlags |= SLOTS_ALTERED;
            }

            if (it->alteredInMemory()) {
                memFileFlags |= SLOTS_ALTERED;
            }
        }
        assert(file.fileExists());
        const_cast<MemFile&>(file).clearFlag(LEGAL_MEMFILE_FLAGS);
        const_cast<MemFile&>(file).setFlag(memFileFlags);
        LOG(warning, "verify(%s): Errors repaired", file.toString().c_str());
    } else if (report._ok) {
        LOG(debug, "verify(%s): Ok", file.toString().c_str());
    } else {
        LOG(debug, "verify(%s): Not repairing errors", file.toString().c_str());
    }

//    env._metrics.slotfileMetrics._verifyLatencyTotal.addValue(startTimer);
    return report._ok;
}

void
MemFileV1Verifier::verifyUniqueTimestamps(
        ReportCreator& report, std::vector<const MetaSlot*>& slots) const
{
    std::vector<const MetaSlot*> okSlots;
    okSlots.reserve(slots.size());
        // Slots should already be in order as verifyMetaDataBlock has run
    Timestamp last(0);
    for (uint32_t i=0, n=slots.size(); i<n; ++i) {
        if (slots[i]->_timestamp == last && i != 0) {
            REPORT(report) << "Slot " << i << " (" << *slots[i]
                           << ") has same timestamp as slot " << (i-1)
                           << " (" << *slots[i-1] << ").";
        } else {
            okSlots.push_back(slots[i]);
            last = slots[i]->_timestamp;
        }
    }
    okSlots.swap(slots);
}

}
