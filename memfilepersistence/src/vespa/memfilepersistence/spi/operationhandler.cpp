// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationhandler.h"
#include <vespa/memfilepersistence/common/exceptions.h>
#include <vespa/document/select/parser.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.memfile.handler.operation");

namespace storage::memfile {

OperationHandler::OperationHandler(Environment& env)
    : _env(env)
{
}

OperationHandler::ReadResult
OperationHandler::read(MemFile& file, const DocumentId& id,
                       Timestamp maxTimestamp, GetFlag getFlags) const
{
    if (maxTimestamp == Timestamp(0)) {
        maxTimestamp = MAX_TIMESTAMP;
    }
    const MemSlot* slot(file.getSlotWithId(id, maxTimestamp));
    if (slot == 0 || slot->deleted()) {
        return ReadResult(Document::UP(), Timestamp(0));
    }
    return ReadResult(file.getDocument(*slot, getFlags), slot->getTimestamp());
}

OperationHandler::ReadResult
OperationHandler::read(MemFile& file, Timestamp timestamp,
                       GetFlag getFlags) const
{
    const MemSlot* slot(file.getSlotAtTime(timestamp));
    if (slot == 0 || slot->deleted()) {
        return ReadResult(Document::UP(), Timestamp(0));
    }

    return ReadResult(file.getDocument(*slot, getFlags), slot->getTimestamp());
}

Types::Timestamp
OperationHandler::remove(MemFile& file,
                         const DocumentId& id,
                         Timestamp timestamp,
                         RemoveType persistRemove)
{
    LOG(debug, "remove(%s, %s, %zu, %s)",
        file.getFile().getPath().c_str(),
        id.toString().c_str(),
        timestamp.getTime(),
        persistRemove ? "always persist" : "persist only if put is found");

    const MemSlot* slotAtTime(file.getSlotAtTime(timestamp));
    if (slotAtTime) {
        if (slotAtTime->deleted()) {
            LOG(spam,
                "Slot %s already existed at timestamp %zu but was already "
                "deleted; not doing anything",
                slotAtTime->toString().c_str(),
                timestamp.getTime());
            return Timestamp(0);
        }
        LOG(spam,
            "Slot %s already existed at timestamp %zu, delegating to "
            "unrevertableRemove",
            slotAtTime->toString().c_str(),
            timestamp.getTime());
        return unrevertableRemove(file, id, timestamp);
    }

    const MemSlot* slot(file.getSlotWithId(id));

    if (slot == 0 || slot->getTimestamp() > timestamp) {
        LOG(spam, "No slot existed, or timestamp was higher");

        if (persistRemove == ALWAYS_PERSIST_REMOVE) {
            file.addRemoveSlotForNonExistingEntry(
                    id, timestamp, MemFile::REGULAR_REMOVE);
        }
        return Timestamp(0);
    }

    if (slot->deleted()) {
        LOG(spam, "Document %s was already deleted.",
            id.toString().c_str());

        if (persistRemove == ALWAYS_PERSIST_REMOVE) {
            file.addRemoveSlot(*slot, timestamp);
        }

        return Timestamp(0);
    }

    Timestamp oldTs(slot->getTimestamp());
    file.addRemoveSlot(*slot, timestamp);
    return oldTs;
}

Types::Timestamp
OperationHandler::unrevertableRemove(MemFile& file,
                                     const DocumentId& id,
                                     Timestamp timestamp)
{
    LOG(debug, "unrevertableRemove(%s, %s, %zu)",
        file.getFile().getPath().c_str(),
        id.toString().c_str(),
        timestamp.getTime());

    const MemSlot* slot(file.getSlotAtTime(timestamp));
    if (slot == 0) {
        file.addRemoveSlotForNonExistingEntry(
                id, timestamp, MemFile::UNREVERTABLE_REMOVE);
        return Timestamp(0);
    }
    if (slot->getGlobalId() != id.getGlobalId()) {
        // Should Not Happen(tm) case: given timestamp+document id does not
        // match the document ID stored on file for the timestamp. In this
        // case we throw out the old slot and insert a new unrevertable remove
        // slot with the new document ID.
        LOG(error, "Unrevertable remove for timestamp %zu with document id %s "
            "does not match the document id %s of the slot stored at this "
            "timestamp! Existing slot: %s. Removing old slot to get in sync.",
            timestamp.getTime(),
            id.toString().c_str(),
            file.getDocumentId(*slot).toString().c_str(),
            slot->toString().c_str());
        file.removeSlot(*slot);
        file.addRemoveSlotForNonExistingEntry(
                id, timestamp, MemFile::UNREVERTABLE_REMOVE);
        return timestamp;
    }

    MemSlot newSlot(*slot);
    newSlot.turnToUnrevertableRemove();
    file.modifySlot(newSlot);
    return timestamp;
}

void
OperationHandler::write(MemFile& file, const Document& doc, Timestamp time)
{
    const MemSlot* slot(file.getSlotAtTime(time));
    if (slot != 0) {
        if (doc.getId().getGlobalId() == slot->getGlobalId() &&
            !slot->deleted())
        {
            LOG(debug, "Tried to put already existing document %s at time "
                "%zu into file %s. Probably sent here by merge from other "
                "copy. Flagging put ok and doing nothing.",
                doc.getId().toString().c_str(),
                time.getTime(),
                file.getFile().getPath().c_str());
            return;
        } else {
            std::ostringstream ost;
            ost << "Failed adding document " << doc.getId().toString()
                << " to slotfile '" << file.getFile().getPath()
                << "'. Entry " << *slot << " already exists at that timestamp";
            LOG(warning, "%s", ost.str().c_str());
            throw TimestampExistException(
                    ost.str(), file.getFile(), time, VESPA_STRLOC);
        }
    }

    file.addPutSlot(doc, time);
}

bool
OperationHandler::update(MemFile& file, const Document& header,
                         Timestamp newTime, Timestamp existingTime)
{
    const MemSlot* slot;
    if (existingTime == Timestamp(0)) {
        slot = file.getSlotWithId(header.getId());
    } else {
        slot = file.getSlotAtTime(existingTime);
        if (slot == NULL) {
            return false;
        }

        DocumentId docId = file.getDocumentId(*slot);
        if (docId != header.getId()) {
            std::ostringstream ost;
            ost << "Attempted update of doc " << header.getId() << " with "
                << "timestamp " << existingTime << " failed as non-matching "
                << "doc " << docId << " existed at timestamp.";
            throw MemFileIoException(ost.str(), file.getFile(),
                    MemFileIoException::INTERNAL_FAILURE, VESPA_STRLOC);
        }
    }
    if (slot == 0 || slot->deleted()) return false;

    file.addUpdateSlot(header, *slot, newTime);
    return true;
}

std::vector<Types::Timestamp>
OperationHandler::select(MemFile& file,
                         SlotMatcher& checker,
                         uint32_t iteratorFlags,
                         Timestamp fromTimestamp,
                         Timestamp toTimestamp)
{
    verifyLegalFlags(iteratorFlags, LEGAL_ITERATOR_FLAGS, "select");
    checker.preload(file);
    std::vector<Timestamp> result;
    result.reserve(file.getSlotCount());
    for (MemFile::const_iterator it = file.begin(iteratorFlags,
                                                 fromTimestamp,
                                                 toTimestamp);
         it != file.end(); ++it)
    {
        if (checker.match(SlotMatcher::Slot(*it, file))) {
            result.push_back(it->getTimestamp());
        }
    }
    reverse(result.begin(), result.end());
    return result;
}

void
OperationHandler::verifyBucketMapping(const DocumentId& id,
                                      const BucketId& bucket) const
{
    BucketId docBucket(_env._bucketFactory.getBucketId(id));
    docBucket.setUsedBits(bucket.getUsedBits());
    if (bucket != docBucket) {
        docBucket = _env._bucketFactory.getBucketId(id);
        throw vespalib::IllegalStateException("Document " + id.toString()
                + " (bucket " + docBucket.toString() + ") does not belong in "
                + "bucket " + bucket.toString() + ".", VESPA_STRLOC);
    }
}

MemFilePtr
OperationHandler::getMemFile(const spi::Bucket& b, bool keepInCache)
{
    return getMemFile(b.getBucketId(), b.getPartition(), keepInCache);
}

MemFilePtr
OperationHandler::getMemFile(const document::BucketId& id, Directory& dir,
                             bool keepInCache) {
    return _env._cache.get(id, _env, dir, keepInCache);
}

MemFilePtr
OperationHandler::getMemFile(const document::BucketId& id, uint16_t diskIndex,
                             bool keepInCache)
{
    return getMemFile(id, _env.getDirectory(diskIndex), keepInCache);
}

document::FieldSet::UP
OperationHandler::parseFieldSet(const std::string& fieldSet)
{
   document::FieldSetRepo fsr;
   return fsr.parse(_env.repo(), fieldSet);
}

std::unique_ptr<document::select::Node>
OperationHandler::parseDocumentSelection(
        const std::string& documentSelection, bool allowLeaf)
{
    std::unique_ptr<document::select::Node> ret;
    try {
        document::select::Parser parser(
                _env.repo(), _env._bucketFactory);
        ret = parser.parse(documentSelection);
    } catch (document::select::ParsingFailedException& e) {
        LOG(debug, "Failed to parse document selection '%s': %s",
            documentSelection.c_str(), e.getMessage().c_str());
        return std::unique_ptr<document::select::Node>();
    }
    if (ret->isLeafNode() && !allowLeaf) {
        LOG(debug, "Document selection results in a single leaf node: '%s'",
            documentSelection.c_str());
        return std::unique_ptr<document::select::Node>();
    }
    return ret;
}

}
