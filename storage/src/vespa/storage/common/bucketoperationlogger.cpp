// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketoperationlogger.h"
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/bucketdb/bucketcopy.h>

#include <vespa/storageapi/buckets/bucketinfo.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/stllike/asciistream.h>

#ifdef ENABLE_BUCKET_OPERATION_LOGGING
#include <vespa/log/log.h>
LOG_SETUP(".debuglogger");

namespace storage {

namespace debug {

BucketOperationLogger opLogger;

void
BucketOperationLogger::log(const document::BucketId& id,
                           const vespalib::string& text,
                           bool requireLock,
                           State::LockUpdate lockUpdate)
{
    LogEntry entry;
    framework::defaultimplementation::RealClock rclock;
    entry._frameCount = vespalib::getStackTraceFrames(entry._stackFrames, MAX_STACK_FRAMES);
    entry._text = text;
    entry._timestamp = rclock.getTimeInMicros();
    entry._threadId = FastOS_Thread::GetCurrentThreadId() & 0xffff;
    uint32_t lockedByThread = 0;
    bool hasError = false;

    {
        vespalib::LockGuard lock(_logLock);
        BucketMapType::iterator i = _bucketMap.lower_bound(id);
        if (i != _bucketMap.end() && i->first == id) {
            if (i->second._history.size() >= MAX_ENTRIES) {
                i->second._history.pop_front();
            }
            i->second._history.push_back(entry);
            if (lockUpdate == State::BUCKET_LOCKED) {
                if (i->second._lockedByThread != 0) {
                    LOG(warning, "Attempting to acquire lock, but lock "
                        "is already held by thread %u", i->second._lockedByThread);
                    hasError = true;
                }
                i->second._lockedByThread = entry._threadId;
            }
            lockedByThread = i->second._lockedByThread;
            if (lockUpdate == State::BUCKET_UNLOCKED) {
                if (i->second._lockedByThread == 0) {
                    LOG(warning, "Attempting to release lock, but lock "
                        "is not held");
                    hasError = true;
                }
                i->second._lockedByThread = 0;
            }
        } else {
            State addState;
            addState._lockedByThread = 0;
            addState._history.push_back(entry);
            if (lockUpdate == State::BUCKET_LOCKED) {
                addState._lockedByThread = entry._threadId;
            } else if (lockUpdate == State::BUCKET_UNLOCKED) {
                LOG(warning, "Attempting to release lock, but lock "
                        "is not held");
                hasError = true;
            }
            _bucketMap.insert(i, BucketMapType::value_type(id, addState));
        }
    }

    if (requireLock && !lockedByThread) {
        LOG(warning, "Operation '%s' requires lock, but lock is "
                "not registered as held", text.c_str());
        hasError = true;
    }
    if (hasError) {
        LOG(warning, "%s", getHistory(id).c_str());
    }
}

namespace {

// Must hold logger lock
template <typename LineHandler>
void
processHistory(const BucketOperationLogger& opLogger,
               const document::BucketId& id, LineHandler& handler)
{
    BucketOperationLogger::BucketMapType::const_iterator i(
            opLogger._bucketMap.find(id));
    if (i == opLogger._bucketMap.end()) {
        vespalib::asciistream ss;
        ss << "No history recorded for bucket '"
           << id.toString() << "'";
        handler(ss.str());
        return;
    }

    {
        vespalib::asciistream ss;
        ss << "Showing last " << i->second._history.size() << " operations on "
           << "bucket " << id.toString() << " (newest first):";
        handler(ss.str());
    }
    for (BucketOperationLogger::State::LogEntryListType::const_reverse_iterator j(
                 i->second._history.rbegin()), end(i->second._history.rend());
         j != end; ++j)
    {
        vespalib::asciistream ss;
        ss << storage::framework::getTimeString(
                j->_timestamp.getTime(),
                storage::framework::DATETIME_WITH_MICROS)
           << " " << j->_threadId << " "
           << j->_text << ". "
           << vespalib::getStackTrace(1, j->_stackFrames, j->_frameCount);
        handler(ss.str());
    }
}

struct LogWarnAppender
{
    void operator()(const vespalib::string& line)
    {
        LOG(warning, "%s", line.c_str());
    }
};

struct LogStringBuilder
{
    vespalib::asciistream ss;
    void operator()(const vespalib::string& line)
    {
        ss << line << "\n";
    }
};

}

void
BucketOperationLogger::dumpHistoryToLog(const document::BucketId& id) const
{
    LogWarnAppender handler;
    vespalib::LockGuard lock(_logLock);
    processHistory(*this, id, handler);
}

vespalib::string
BucketOperationLogger::getHistory(const document::BucketId& id) const
{
    LogStringBuilder handler;
    vespalib::LockGuard lock(_logLock);
    processHistory(*this, id, handler);
    return handler.ss.str();
}

vespalib::string
BucketOperationLogger::searchBucketHistories(
        const vespalib::string& sub,
        const vespalib::string& urlPrefix) const
{
    vespalib::asciistream ss;
    ss << "<ul>\n";
    // This may block for a while... Assuming such searches run when system
    // is otherwise idle.
    vespalib::LockGuard lock(_logLock);
    for (BucketMapType::const_iterator
             bIt(_bucketMap.begin()), bEnd(_bucketMap.end());
         bIt != bEnd; ++bIt)
    {
        for (State::LogEntryListType::const_iterator
                 sIt(bIt->second._history.begin()),
                 sEnd(bIt->second._history.end());
             sIt != sEnd; ++sIt)
        {
            if (sIt->_text.find(sub.c_str()) != vespalib::string::npos) {
                ss << "<li><a href=\"" << urlPrefix
                   << "0x" << vespalib::hex << bIt->first.getId()
                   << vespalib::dec << "\">" << bIt->first.toString()
                   << "</a>:\n";
                ss << sIt->_text << "</li>\n";
            }
        }
    }
    ss << "</ul>\n";
    return ss.str();
}

BucketOperationLogger&
BucketOperationLogger::getInstance()
{
    return opLogger;
}

// Storage node
void logBucketDbInsert(uint64_t key, const bucketdb::StorageBucketInfo& entry)
{
    LOG_BUCKET_OPERATION_NO_LOCK(
            document::BucketId(document::BucketId::keyToBucketId(key)),
            vespalib::make_vespa_string(
                    "bucketdb insert Bucket(crc=%x, docs=%u, size=%u, "
                    "metacount=%u, usedfilesize=%u, ready=%s, "
                    "active=%s, lastModified=%zu) disk=%u",
                    entry.info.getChecksum(),
                    entry.info.getDocumentCount(),
                    entry.info.getTotalDocumentSize(),
                    entry.info.getMetaCount(),
                    entry.info.getUsedFileSize(),
                    (entry.info.isReady() ? "true" : "false"),
                    (entry.info.isActive() ? "true" : "false"),
                    entry.info.getLastModified(),
                    entry.disk));
}

void logBucketDbErase(uint64_t key, const TypeTag<bucketdb::StorageBucketInfo>&)
{
    LOG_BUCKET_OPERATION_NO_LOCK(
            document::BucketId(document::BucketId::keyToBucketId(key)),
            "bucketdb erase");
}

// Distributor
void
checkAllConsistentNodesImpliesTrusted(
        const document::BucketId& bucket,
        const BucketInfo& entry)
{
    // If all copies are consistent, they should also be trusted
    if (entry.validAndConsistent() && entry.getNodeCount() > 1) {
        for (std::size_t i = 0; i < entry.getNodeCount(); ++i) {
            const BucketCopy& copy = entry.getNodeRef(i);
            if (copy.trusted() == false) {
                LOG(warning, "Bucket DB entry %s for %s is consistent, but "
                    "contains non-trusted copy %s", entry.toString().c_str(),
                    bucket.toString().c_str(), copy.toString().c_str());
                DUMP_LOGGED_BUCKET_OPERATIONS(bucket);
            }
        }
    }
}

std::size_t
firstTrustedNode(const BucketInfo& entry)
{
    for (std::size_t i = 0; i < entry.getNodeCount(); ++i) {
        const distributor::BucketCopy& copy = entry.getNodeRef(i);
        if (copy.trusted()) {
            return i;
        }
    }
    return std::numeric_limits<std::size_t>::max();
}

void
checkNotInSyncImpliesNotTrusted(
        const document::BucketId& bucket,
        const BucketInfo& entry)
{
    // If there are copies out of sync, different copies should not
    // be set to trusted
    std::size_t trustedNode = firstTrustedNode(entry);
    if (trustedNode != std::numeric_limits<std::size_t>::max()) {
        // Ensure all other trusted copies match the metadata of the
        // first trusted bucket
        const BucketCopy& trustedCopy = entry.getNodeRef(trustedNode);
        for (std::size_t i = 0; i < entry.getNodeCount(); ++i) {
            if (i == trustedNode) {
                continue;
            }
            const BucketCopy& copy = entry.getNodeRef(i);
            const api::BucketInfo& copyInfo = copy.getBucketInfo();
            const api::BucketInfo& trustedInfo = trustedCopy.getBucketInfo();
            if (copy.trusted()
                && ((copyInfo.getChecksum() != trustedInfo.getChecksum())))
                    //|| (copyInfo.getTotalDocumentSize() != trustedInfo.getTotalDocumentSize())))
            {
                LOG(warning, "Bucket DB entry %s for %s has trusted node copy "
                    "with differing metadata %s", entry.toString().c_str(),
                    bucket.toString().c_str(), copy.toString().c_str());
                DUMP_LOGGED_BUCKET_OPERATIONS(bucket);
            }
        }
    }
}

void
checkInvalidImpliesNotTrusted(
        const document::BucketId& bucket,
        const BucketInfo& entry)
{
    for (std::size_t i = 0; i < entry.getNodeCount(); ++i) {
        const BucketCopy& copy = entry.getNodeRef(i);
        if (!copy.valid() && copy.trusted()) {
            LOG(warning, "Bucket DB entry %s for %s has invalid copy %s "
                "marked as trusted", entry.toString().c_str(),
                bucket.toString().c_str(), copy.toString().c_str());
            DUMP_LOGGED_BUCKET_OPERATIONS(bucket);
        }
    }
}

void
logBucketDbInsert(uint64_t key, const BucketInfo& entry)
{
    document::BucketId bucket(document::BucketId::keyToBucketId(key));
    LOG_BUCKET_OPERATION_NO_LOCK(
            bucket, vespalib::make_vespa_string(
                    "bucketdb insert of %s", entry.toString().c_str()));
    // Do some sanity checking of the inserted entry
    checkAllConsistentNodesImpliesTrusted(bucket, entry);
    checkNotInSyncImpliesNotTrusted(bucket, entry);
    checkInvalidImpliesNotTrusted(bucket, entry);
}

void
logBucketDbErase(uint64_t key, const TypeTag<BucketInfo>&)
{
    document::BucketId bucket(document::BucketId::keyToBucketId(key));
    LOG_BUCKET_OPERATION_NO_LOCK(bucket, "bucketdb erase");
}

} // namespace debug

} // namespace storage

#endif // ENABLE_BUCKET_OPERATION_LOGGING
