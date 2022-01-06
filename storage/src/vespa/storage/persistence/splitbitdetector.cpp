// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "splitbitdetector.h"
#include "bucketprocessor.h"
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <sstream>
#include <cassert>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.split.bitdetector");

namespace storage {

namespace {

size_t keepFirstCount = 15;

/**
 * Detect what bit we need to use for splitting to split given bucket into
 * two pieces.
 *
 * We do this by assuming it is bit 58. And then we go through all
 * buckets, and reduce the bit number if both buckets arent equal below this
 * bit. Thus we should end up pointing to the rightmost bit differing
 * between the buckets, or 58 (which is an illegal number as only bits 0-57
 * are possible, if it is impossible to split the bucket in two.
 */
struct BucketVisitor : public BucketProcessor::EntryProcessor {
    const document::BucketIdFactory& _factory;
    // Bit index of first bit that is not identical for all documents,
    // starting at index 0.
    mutable uint32_t _splitBit;
    // Contain 1 for all bits lower than splitBit
    mutable uint64_t _splitMask;
    mutable document::DocumentId _refId;
    mutable document::BucketId _refBucket;
    mutable document::DocumentId _conflictId;
    mutable document::BucketId _conflictBucket;
    uint32_t _docCount;
    struct DocInfo {
        uint64_t timestamp;
        document::DocumentId docId;
        document::BucketId bucketId;

        DocInfo(uint64_t ts, const document::DocumentId& id,
                document::BucketId& bid)
            : timestamp(ts),
              docId(id),
              bucketId(bid)
        {
        }
    };
    std::vector<DocInfo> _firstDocs;

    BucketVisitor(const document::BucketIdFactory& factory);
    ~BucketVisitor();

    void process(spi::DocEntry& entry) override {
        assert(entry.getDocumentId());
        ++_docCount;

        const document::DocumentId& id(*entry.getDocumentId());
        document::BucketId bucket = _factory.getBucketId(id);
        if (_firstDocs.size() < keepFirstCount) {
            _firstDocs.push_back(DocInfo(entry.getTimestamp(), id, bucket));
        }

        if (_refBucket.getRawId() == 0) {
            _refId = id;
            _refBucket = bucket;
            return;
        }

        while ((bucket.getRawId() & _splitMask) !=
               (_refBucket.getRawId() & _splitMask))
        {
            --_splitBit;
            _splitMask = _splitMask >> 1;
            _conflictId = id;
            _conflictBucket = bucket;
        }

        return;
    }

    void printEntrySummary(std::ostream& out) {
        for (uint32_t i=0; i<_firstDocs.size(); ++i) {
            out << "\n" << _firstDocs[i].timestamp << ' '
                << _firstDocs[i].bucketId << ' '
                << _firstDocs[i].docId;
        }
    }

};

BucketVisitor::BucketVisitor(const document::BucketIdFactory& factory)
    : _factory(factory), _splitBit(58),
      _splitMask(0), _refId(), _refBucket(),
      _conflictId(), _conflictBucket(),
      _docCount(0), _firstDocs()
{
    _firstDocs.reserve(keepFirstCount);
    for (uint32_t i=0; i<_splitBit; ++i) {
        _splitMask = (_splitMask << 1) | 1;
    }
}
BucketVisitor::~BucketVisitor() = default;

bool
smallerThanSizeLimit(uint32_t minCount,
                     uint32_t minSize,
                     const spi::Bucket& b,
                     spi::PersistenceProvider& provider)
{
    if (minCount == 0 && minSize == 0) return false;
    spi::BucketInfo info = provider.getBucketInfo(b).getBucketInfo();
    if ((minCount != 0 && info.getDocumentCount() < minCount)
        && (minSize != 0 && (info.getDocumentCount() == 1
                             || info.getDocumentSize() < minSize)))
    { // (A bucket with a single document is never too large size wise
        return true;
    }
    return false;
}

bool
deduceBucketIsInconsistentlySplit(uint32_t minCount)
{
    // If the bucket split command was sent with a minimum doc limit of 0,
    // it was sent because the bucket is inconsistently split. Regular splits
    // triggered by bucket size always contain values > 0 from the config.
    return (minCount == 0);
}

} // anonymous

SplitBitDetector::Result
SplitBitDetector::detectSplit(spi::PersistenceProvider& provider,
                              const spi::Bucket& source,
                              uint32_t maxSplitBits,
                              spi::Context& context,
                              uint32_t minCount, uint32_t minSize)
{
    if (maxSplitBits <= source.getBucketId().getUsedBits()) {
        std::ostringstream error;
        error << "No use in trying to split " << source << " when max split "
              << "bit is set to " << maxSplitBits << ".";
        LOG(warning, "split(%s): %s",
            source.getBucketId().toString().c_str(), error.str().c_str());
        return Result(error.str());
    }
    document::BucketIdFactory factory;
    BucketVisitor detector(factory);

    BucketProcessor::iterateAll(
            provider, source, "", std::make_shared<document::DocIdOnly>(),
            detector, spi::ALL_VERSIONS, context);

    uint16_t splitBit = detector._splitBit;

    assert(splitBit <= 58);
        // Handle empty source bucket case
    if (detector._refBucket.getRawId() == 0) {
        return Result();
    }
        // If max split bits is set and we are trying to split above that,
        // correct
    bool singleTarget = false;
    if (maxSplitBits != 0 && maxSplitBits < splitBit) {
        splitBit = maxSplitBits - 1;
        singleTarget = true;
        LOG(debug, "split(%s) - Found split bit %u but max is %u.",
            source.toString().c_str(), splitBit, maxSplitBits);
    }
        // If size limits are set, but bucket is not too large, limit split to
        // current + 1
    if (smallerThanSizeLimit(minCount, minSize, source, provider)) {
        if (LOG_WOULD_LOG(debug)) {
            spi::BucketInfo info(
                    provider.getBucketInfo(source).getBucketInfo());
            LOG(debug, "split(%s) - Bucket too small to trigger split. "
                       "%u docs, %u size. (Split size at %u/%u). "
                       "Only splitting to %u.",
                source.toString().c_str(), info.getDocumentCount(),
                info.getDocumentSize(), minCount, minSize,
                source.getBucketId().getUsedBits());
        }
        splitBit = source.getBucketId().getUsedBits();
    }
    if (splitBit == 58) {
        // We're in a situation where multiple unique documents map to the
        // same 58-bit bucket ID and no differing bit may be found.
        // If the split is sent in an inconsistent split context, we must
        // always split the bucket or the bucket tree might forever remain
        // inconsistent. If we're unable to deduce a split bit from the bucket
        // contents (which is why we're in this branch) we have no other
        // practical choice than to increase the split level by 1 bit to force
        // the bucket further down into the tree.
        // Otherwise, we can really do no better than either fail the operation
        // or force the bucket to 58 bits. Failing the operation makes the
        // distributor retry it ad inifinitum (forever smashing its head against
        // the wall), so the latter is our chosen approach.
        if (deduceBucketIsInconsistentlySplit(minCount)) {
            splitBit = source.getBucketId().getUsedBits();
        } else {
            std::ostringstream error;
            error << "Could not find differing bit to split bucket contents "
                     "around due to bucket ID collisions. Forcing resulting "
                     "bucket to be 58 bits. Bucket has "
                  << detector._docCount << " docs.";
            detector.printEntrySummary(error);
            LOGBT(warning,
                  source.getBucketId().toString(),
                  "split(%s): %s",
                  source.getBucketId().toString().c_str(),
                  error.str().c_str());
            splitBit = 57; // + 1 below.
        }
    }

    if (splitBit < source.getBucketId().getUsedBits()) {
        LOG(error, "split(%s): Document(s) in wrong bucket, and thus "
                   "inaccessible! Split bit detector detected split bit %u but "
                   "the bucket is already split on %u bits. Conflicting "
                   "entries were document %s (%s) and document %s (%s).",
            source.getBucketId().toString().c_str(),
            splitBit,
            source.getBucketId().getUsedBits(),
            detector._refId.toString().c_str(),
            detector._refBucket.toString().c_str(),
            detector._conflictId.toString().c_str(),
            detector._conflictBucket.toString().c_str());
        assert(false);
    }

    document::BucketId base(splitBit,
                            detector._refBucket.getRawId());
    document::BucketId target1(splitBit + 1, base.getId());
    document::BucketId target2(splitBit + 1, base.getId()
                                             | (uint64_t(1) << splitBit));
    return Result(target1, target2, singleTarget);
}

void
SplitBitDetector::Result::print(std::ostream& out, bool verbose,
                                const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "SplitTargets(";
    switch (_result) {
        case OK:
            out << _target1.getUsedBits() << ": " << _target1 << ", ";
            if (_singleTarget) out << "[ ";
            out << _target2;
            if (_singleTarget) out << " ]";
            break;
        case EMPTY: out << "source empty"; break;
        case ERROR: out << "error: " << _reason; break;
    }
    out << ")";
}

} // storage
