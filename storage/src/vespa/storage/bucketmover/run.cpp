// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "run.h"
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/bucketdb/lockablemap.hpp>
#include <vespa/storageframework/generic/clock/clock.h>
#include <iomanip>

#include <vespa/log/log.h>
LOG_SETUP(".bucketmover.run");

namespace storage {
namespace bucketmover {

Run::Run(StorBucketDatabase& db,
         lib::Distribution::SP distribution,
         const lib::NodeState& nodeState,
         uint16_t nodeIndex,
         framework::Clock& clock)
    : _bucketDatabase(db),
      _distribution(distribution),
      _nodeState(nodeState),
      _nodeIndex(nodeIndex),
      _entries(),
      _iterationDone(false),
      _statistics(distribution->getDiskDistribution(), clock, nodeState),
      _aborted(false)
{
}

namespace {
    struct BucketIterator {
        const lib::Distribution& _distribution;
        const lib::NodeState& _nodeState;
        RunStatistics& _statistics;
        std::list<Move>& _entries;
        uint16_t _nodeIndex;
        uint32_t _maxBucketsToIterateAtOnce;
        uint32_t _bucketsVisited;
        document::BucketId _firstBucket;

        BucketIterator(const lib::Distribution& d, const lib::NodeState& ns,
                       uint16_t nodeIndex, RunStatistics& stats,
                       std::list<Move>& entries)
            : _distribution(d),
              _nodeState(ns),
              _statistics(stats),
              _entries(entries),
              _nodeIndex(nodeIndex),
              _maxBucketsToIterateAtOnce(10000),
              _bucketsVisited(0),
              _firstBucket(stats._lastBucketVisited)
        {
        }

        StorBucketDatabase::Decision
        operator()(document::BucketId::Type revId,
                   StorBucketDatabase::Entry& entry)
        {
            document::BucketId bucket(document::BucketId::keyToBucketId(revId));
            if (bucket == _firstBucket) {
                return StorBucketDatabase::CONTINUE;
            }
            uint16_t idealDisk = _distribution.getIdealDisk(
                    _nodeState, _nodeIndex, bucket,
                    lib::Distribution::IDEAL_DISK_EVEN_IF_DOWN);
            RunStatistics::DiskData& diskData(
                    _statistics._diskData[entry.disk]);
            bool idealDiskDown(
                    _statistics._diskData[idealDisk]._diskDisabled);
            if (entry.disk == idealDisk || idealDiskDown) {
                diskData._bucketSize += entry.getBucketInfo().getTotalDocumentSize();
                ++diskData._bucketsFoundOnCorrectDisk;
            } else {
                _entries.push_back(Move(
                            entry.disk, idealDisk, bucket, entry.getBucketInfo().getTotalDocumentSize()));
            }
            _statistics._lastBucketVisited = bucket;
            if (++_bucketsVisited >= _maxBucketsToIterateAtOnce) {
                return StorBucketDatabase::ABORT;
            }
            return StorBucketDatabase::CONTINUE;
        }
    };
}

Move
Run::getNextMove()
{
    if (_aborted) {
        LOG(debug, "Run aborted. Returning undefined move.");
        return Move();
    }
    if (_iterationDone) {
        LOG(debug, "Run completed. End time set. Returning undefined move.");
        return Move();
    }
    while (true) {
        // Process cached entries until we either found one to move, or
        // we have no more
        while (!_entries.empty()) {
            Move e(_entries.front());
            _entries.pop_front();

            if (!_statistics._diskData[e.getTargetDisk()]._diskDisabled) {
                _pending.push_back(e);
                _statistics._lastBucketProcessed = e.getBucketId();
                _statistics._lastBucketProcessedTime
                    = _statistics._clock->getTimeInSeconds();
                return e;
            }
        }

        // Cache more entries
        BucketIterator it(*_distribution, _nodeState, _nodeIndex, _statistics,
                          _entries);
        _bucketDatabase.all(it, "bucketmover::Run",
                            _statistics._lastBucketVisited.toKey());
        if (it._bucketsVisited == 0) {
            _iterationDone = true;
            if (_pending.empty()) {
                finalize();
            }
            LOG(debug, "Last bucket visited. Done iterating buckets in run.");
            return Move();
        }
    }
}

void
Run::depleteMoves()
{
    while (true) {
            // Cache more entries
        BucketIterator bi(*_distribution, _nodeState, _nodeIndex, _statistics,
                          _entries);
        _bucketDatabase.all(bi, "bucketmover::depleteMoves",
                            _statistics._lastBucketVisited.toKey());
        if (bi._bucketsVisited == 0) {
            break;
        }
        for (std::list<Move>::const_iterator it = _entries.begin();
             it != _entries.end(); ++it)
        {
            ++_statistics._diskData[it->getSourceDisk()][it->getTargetDisk()]
                ._bucketsLeftOnWrongDisk;
            uint32_t size = it->getTotalDocSize();
            _statistics._diskData[it->getSourceDisk()]._bucketSize += size;
        }
        _entries.clear();
    }
    finalize();
}

void
Run::finalize()
{
    _statistics._endTime = _statistics._clock->getTimeInSeconds();
}

void
Run::removePending(Move& move)
{
    bool foundPending = false;
    for (std::list<Move>::iterator it = _pending.begin(); it != _pending.end();
         ++it)
    {
        if (it->getBucketId() == move.getBucketId()) {
            _pending.erase(it);
            foundPending = true;
            break;
        }
    }
    if (!foundPending) {
        LOG(warning, "Got answer for %s that was not in the pending list.",
            move.getBucketId().toString().c_str());
        return;
    }
    if (_iterationDone && _pending.empty()) {
        finalize();
    }
}

void
Run::moveOk(Move& move)
{
    ++_statistics._diskData[move.getSourceDisk()][move.getTargetDisk()]
            ._bucketsMoved;
    removePending(move);
    uint32_t size = move.getTotalDocSize();

    _statistics._diskData[move.getSourceDisk()]._bucketSize -= size;
    _statistics._diskData[move.getTargetDisk()]._bucketSize += size;
}

void
Run::moveFailedBucketNotFound(Move& move)
{
    ++_statistics._diskData[move.getSourceDisk()][move.getTargetDisk()]
            ._bucketsNotFoundAtExecutionTime;
    removePending(move);
}

void
Run::moveFailed(Move& move)
{
    ++_statistics._diskData[move.getSourceDisk()][move.getTargetDisk()]
            ._bucketsFailedMoving;
    _statistics._diskData[move.getTargetDisk()]._diskDisabled = true;
    removePending(move);
}

void
Run::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "Run(";
    if (_aborted) {
        out << "Aborted";
    } else if (_statistics._endTime.isSet()) {
        if (_entries.empty()) {
            out << "Completed";
        } else {
            out << "Iteration done";
        }
    }
    out << ") {\n" << indent << "  ";
    _statistics.print(out, verbose, indent + "  ");
    if (!_entries.empty()) {
        out << "\n" << indent << "  Pending possible moves:";
        uint32_t i = 0;
        for (std::list<Move>::const_iterator it = _entries.begin();
             it != _entries.end() && ++i <= 10; ++it)
        {
            out << "\n" << indent << "    " << *it;
        }
        uint32_t size = _entries.size();
        if (size > 10) {
            out << "\n" << indent << "    ... and " << (size - 10)
                << " more.";
        }
    }
    if (!_statistics._endTime.isSet()) {
        out << "\n" << indent << "  Bucket iterator: "
            << _statistics._lastBucketVisited;
    }
    out << "\n" << indent << "}";
}

} // bucketmover
} // storage
