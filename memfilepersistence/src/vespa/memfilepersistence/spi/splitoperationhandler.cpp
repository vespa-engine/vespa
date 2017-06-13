// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "splitoperationhandler.h"
#include "cacheevictionguard.h"

#include <vespa/log/log.h>
LOG_SETUP(".persistence.memfile.handler.split");

namespace storage {
namespace memfile {

SplitOperationHandler::SplitOperationHandler(Environment& env)
    : OperationHandler(env)
{
}

namespace {

struct BucketMatcher : public SlotMatcher {
    const document::BucketIdFactory& _factory;
    document::BucketId _bid;

    BucketMatcher(const document::BucketIdFactory& factory, const document::BucketId& bid)
        : SlotMatcher(PRELOAD_HEADER),
          _factory(factory),
          _bid(bid) {}

    bool match(const Slot& slot) override {
        document::DocumentId id(slot.getDocumentId());
        document::BucketId bucket = _factory.getBucketId(id);
        bucket.setUsedBits(_bid.getUsedBits());

        if (bucket.stripUnused() == _bid.stripUnused()) {
            return true;
        } else {
            return false;
        }
    }
};

}

void
SplitOperationHandler::copyTimestamps(
        const MemFile& source,
        MemFile& target,
        const std::vector<Timestamp>& timestamps)
{
    std::vector<const MemSlot*> slotsToCopy;
    slotsToCopy.reserve(timestamps.size());
    for (uint32_t i = 0; i < timestamps.size(); i++) {
        const MemSlot* slot = source.getSlotAtTime(timestamps[i]);

        if (!target.getSlotAtTime(timestamps[i])) {
            slotsToCopy.push_back(slot);
        }
    }
    target.copySlotsFrom(source, slotsToCopy);
}

uint32_t
SplitOperationHandler::splitIntoFile(MemFile& source,
                                     const spi::Bucket& target)
{
    BucketMatcher matcher(_env._bucketFactory, target.getBucketId());

    std::vector<Timestamp> ts = select(source, matcher, ITERATE_REMOVED);

    MemFileCacheEvictionGuard targetFile(getMemFile(target, false));

    LOG(debug,
        "Found %zu slots to move from file %s to file %s",
        ts.size(),
        source.getFile().toString().c_str(),
        targetFile->getFile().toString().c_str());

    copyTimestamps(source, *targetFile, ts);

    targetFile->flushToDisk();
    targetFile.unguard();
    return ts.size();
}

spi::Result
SplitOperationHandler::split(const spi::Bucket& source,
                             const spi::Bucket& target1,
                             const spi::Bucket& target2)
{
    MemFileCacheEvictionGuard file(getMemFile(source, false));
    file->ensureBodyBlockCached();

    uint32_t totalDocsMoved = 0;
    totalDocsMoved += splitIntoFile(*file, target1);
    if (target2.getBucketId().getRawId() != 0) {
        totalDocsMoved += splitIntoFile(*file, target2);
    }
    if (file->getBucketInfo().getEntryCount() != totalDocsMoved) {
        LOG(error, "Split(%s) code moved only %u of %u entries out of source "
                   "file.",
            source.getBucketId().toString().c_str(),
            totalDocsMoved, file->getBucketInfo().getEntryCount());
        assert(false);
    }
    file.get().deleteFile();
    file.unguard();
    return spi::Result();
}

} // memfile
} // storage
