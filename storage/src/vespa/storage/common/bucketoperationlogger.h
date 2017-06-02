// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <map>
#include <list>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/vstringfmt.h>

/**
 * Enable this to log most slotfile operations (such as all mutations) as
 * well as common bucket operations such as splitting, joining and bucket db
 * updates. Each log entry contains the stack frames for the logging callsite,
 * a timestamp, the ID of the thread performing the operation as well as a
 * message. The stack trace is cheaply acquired and does thus not affect runtime
 * performance to a great degree. Expect some overhead from the logging itself
 * since it requires a global mutex around the log state.
 *
 * All relevant bucket/slotfile operations are checked to ensure that the
 * filestor lock is held during the operation and that the thread performing
 * it is the same as the one that acquired the lock.
 *
 * Similarly, code has been added to distributor bucket database and ideal
 * state handling to log these.
 *
 * In the case of an invariant violation (such as a locking bug), the last
 * BUCKET_OPERATION_LOG_ENTRIES log entries will be dumped to the vespalog.
 * Code may also dump the logged history for a bucket by calling
 *   DUMP_LOGGED_BUCKET_OPERATIONS(bucketid)
 */
//#define ENABLE_BUCKET_OPERATION_LOGGING
#define BUCKET_OPERATION_LOG_ENTRIES 40

#ifdef ENABLE_BUCKET_OPERATION_LOGGING
#define LOG_BUCKET_OPERATION_NO_LOCK(bucket, string) \
debug::BucketOperationLogger::getInstance().log( \
            (bucket), (string), false)

#define LOG_BUCKET_OPERATION(bucket, string) \
debug::BucketOperationLogger::getInstance().log( \
            (bucket), (string), true)

#define LOG_BUCKET_OPERATION_SPECIFY_LOCKED(bucket, string, require_locked) \
debug::BucketOperationLogger::getInstance().log( \
            (bucket), (string), (require_locked))

#define LOG_BUCKET_OPERATION_SET_LOCK_STATE(bucket, string, require_locked, new_state) \
debug::BucketOperationLogger::getInstance().log( \
            (bucket), (string), (require_locked), (new_state))

#define DUMP_LOGGED_BUCKET_OPERATIONS(bucket) \
    debug::BucketOperationLogger::getInstance().dumpHistoryToLog(bucket)

namespace storage {

// Debug stuff for tracking the last n operations to buckets
namespace debug {

struct BucketOperationLogger
{
    static const std::size_t MAX_ENTRIES = BUCKET_OPERATION_LOG_ENTRIES;
    static const std::size_t MAX_STACK_FRAMES = 25;

    struct LogEntry
    {
        void* _stackFrames[MAX_STACK_FRAMES];
        vespalib::string _text;
        framework::MicroSecTime _timestamp;
        int _frameCount;
        int32_t _threadId;
    };

    struct State
    {
        typedef std::list<LogEntry> LogEntryListType;
        enum LockUpdate
        {
            NO_UPDATE = 0,
            BUCKET_LOCKED = 1,
            BUCKET_UNLOCKED = 2
        };
        LogEntryListType _history;
        uint32_t _lockedByThread;
    };

    typedef std::map<document::BucketId, State> BucketMapType;

    vespalib::Lock _logLock;
    BucketMapType _bucketMap;

    void log(const document::BucketId& id,
             const vespalib::string& text,
             bool requireLock = true,
             State::LockUpdate update = State::NO_UPDATE);

    vespalib::string getHistory(const document::BucketId& id) const;
    void dumpHistoryToLog(const document::BucketId& id) const;
    //void dumpAllBucketHistoriesToFile(const vespalib::string& filename) const;
    /**
     * Search through all bucket history entry descriptions to find substring,
     * creating a itemized list of buckets containing it as well as a preview.
     * @param sub the exact substring to search for.
     * @param urlPrefix the URL used for creating bucket links.
     */
    vespalib::string searchBucketHistories(const vespalib::string& sub,
                                           const vespalib::string& urlPrefix) const;
    static BucketOperationLogger& getInstance();
};

}

}

#else

#define LOG_BUCKET_OPERATION_NO_LOCK(bucket, string)
#define LOG_BUCKET_OPERATION(bucket, string)
#define LOG_BUCKET_OPERATION_SPECIFY_LOCKED(bucket, string, require_locked)
#define DUMP_LOGGED_BUCKET_OPERATIONS(bucket)
#define LOG_BUCKET_OPERATION_SET_LOCK_STATE(bucket, string, require_locked, new_state)

#endif

