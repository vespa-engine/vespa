// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class ns_log::BufferedLogger
 * \ingroup log
 *
 * \brief Utility class for writing log less spammy.
 *
 * Uses string tokens to identify what messages are equal. Equal tokens means
 * similar message. Use tokens similar to the messages, excluding the variable
 * parts you don't want to mess up equality.
 *
 * Keeps a cache of log entries. When something is logged, check if the message
 * is already in the log cache. If so, increase the count of the log entry. If
 * it wasn't in the cache, log it to file and add it to the log cache.
 *
 * Trim the log cache if it is above maximum size or contains entries that are
 * older than the max age they can live.
 *
 * There are no separate threads polling buffer to see if log entries are older
 * than max age. Each doLog() call to the logbuffer will trigger a check.
 *
 * The log cache is a single static object, common for all loggers. This has
 * the following advantages:
 *    - Only one instance to trigger to check for old entries.
 *    - We can keep a bigger cache, preventing issues with allocating large
 *      caches to lots of loggers or having to small of a cache for heavily used
 *      loggers.
 * The disadvantage is that the cache itself needs to be able to use little
 * resources even though there's many entries in the cache.
 *
 * For the log cache to be efficient, even if it gets large, it is indexed 3
 * ways. This means that there's no operation (except flushing) that needs to
 * go through all log entries. Currently, ordered indexes accessed through
 * binary search is used to give log N complexity.
 *
 * - We need to look up entries by token to figure out if entry already exist.
 * - We need to get oldest entries to see whether they are older than max age.
 * - We need to identify entry to remove when cache is too large.
 *
 * Deciding on what entry to remove when the cache is too large is not straight
 * forward. We don't want a burst of unique log entries to clear the whole
 * buffer. We also need to make sure that the low count entries aren't starved
 * out by the higher count entries, such that no new high count entries can
 * come in.
 *
 * Suggested algorithm for deciding what entry to remove next:
 *    - Split the cache in two by timestamp. The first half of the cache
 *      contains the newest entries added. When entries are added, the last
 *      entry in the first half is moved into the last half (if first half is
 *      full). This ensures that new entries are never starved, and will always
 *      be able to build up their counts.
 *    - In the second half, when an entry needs to be removed, calculate each
 *      entry's timestamp + X * count, and remove the entry with the lowest
 *      score. This will let higher count entries live longer, but
 *      will prevent them from living forever. X is the age factor, saying
 *      how much each count counts for. For instance, if X counts for 5 seconds,
 *      this means that a log entry that you will rather remove an entry seen
 *      once 10 seconds ago, than an entry seen twice less than 15 seconds ago.
 *
 * For efficient access of the log buffer itself, boost' multi_index is used.
 * This is a container that keeps separate indexes. In the log buffer case it
 * will keep 3 indexes.
 *    - Index 0 will be the insert order. This will also be the timestamp order
 *      of the log entries, such that new entries goes in the back, and if we
 *      need to remove an entry due to old age, it will always be the first.
 *    - Index 1 will be the token. This is used for efficient lookup to find
 *      whether we have seen this entry before.
 *    - Index 2 will be the timestamp + X * count, used to find which entry to
 *      remove next if the buffer is too full. Note that this index is only
 *      needed in the second half of the buffer.
 *
 * The buffer itself is split into two parts, because this was the only way I
 * could think of to keep it log N, as the sequenced index don't have random
 * access, such that finding the middle element has N complexity, also, the
 * first half of the entries would pollute the timestamp + X * count index, such
 * that one potentially would have to check many entries before finding the
 * correct one to remove.
 */
#pragma once

#define VESPA_LOG_LOGBUFFERSIZE 1000 // Max log entries cached.
#define VESPA_LOG_LOGENTRYMAXAGE 300 // Max seconds an entry can be cached
#define VESPA_LOG_COUNTAGEFACTOR   5 // How many seconds each count counts for

#include <vespa/log/log.h>
#include <sstream>
#include <string>

// Define LOG if using log buffer for regular LOG calls
// If logger logs debug messages, bypass buffer.
#ifdef VESPA_LOG_USELOGBUFFERFORREGULARLOG
#define LOG(level, ...)                                            \
    do {                                                           \
        if (LOG_WOULD_LOG(level)) {                                \
            if (LOG_WOULD_LOG(debug)) {                            \
                ns_log_logger.doLog(ns_log::Logger::level,         \
                             __FILE__, __LINE__, __VA_ARGS__);     \
                ns_log::BufferedLogger::instance().trimCache();    \
            } else {                                               \
                ns_log::BufferedLogger::instance().doLog(ns_log_logger, \
                        ns_log::Logger::level, __FILE__, __LINE__, \
                        "", __VA_ARGS__);                          \
            }                                                      \
        }                                                          \
    } while (false)
#endif

// Define LOGBM macro for logging buffered, using the message itself as a
// token. This is the same as LOG defined above if
// VESPA_LOG_USELOGBUFFERFORREGULARLOG is defined.
#define LOGBM(level, ...)                                          \
    do {                                                           \
        if (LOG_WOULD_LOG(level)) {                                \
            if (LOG_WOULD_LOG(debug)) {                            \
                ns_log_logger.doLog(ns_log::Logger::level,         \
                             __FILE__, __LINE__, __VA_ARGS__);     \
                ns_log::BufferedLogger::instance().trimCache();    \
            } else {                                               \
                ns_log::BufferedLogger::instance().doLog(ns_log_logger, \
                        ns_log::Logger::level, __FILE__, __LINE__, \
                        "", __VA_ARGS__);                          \
            }                                                      \
        }                                                          \
    } while (false)

// Define LOGBP macro for logging buffered, using the call point as token.
// (File/line of macro caller)
#define LOGBP(level, ARGS...)                                      \
    do {                                                           \
        if (LOG_WOULD_LOG(level)) {                                \
            if (LOG_WOULD_LOG(debug)) {                            \
                ns_log_logger.doLog(ns_log::Logger::level,         \
                             __FILE__, __LINE__, ##ARGS);          \
                ns_log::BufferedLogger::instance().trimCache();    \
            } else {                                               \
                std::ostringstream ost123;                         \
                ost123 << __FILE__ << ":" << __LINE__;             \
                ns_log::BufferedLogger::instance().doLog(ns_log_logger, \
                        ns_log::Logger::level,                     \
                        __FILE__, __LINE__, ost123.str(), ##ARGS); \
            }                                                      \
        }                                                          \
    } while (false)

// Define LOGT calls for using the buffer specifically stating token
#define LOGBT(level, token, ...)                                 \
    do {                                                         \
        if (LOG_WOULD_LOG(level)) {                              \
            if (LOG_WOULD_LOG(debug)) {                          \
                ns_log_logger.doLog(ns_log::Logger::level,       \
                             __FILE__, __LINE__, __VA_ARGS__);   \
                ns_log::BufferedLogger::instance().trimCache();  \
            } else {                                             \
                ns_log::BufferedLogger::instance().doLog(ns_log_logger, \
                        ns_log::Logger::level,                   \
                        __FILE__, __LINE__, token, __VA_ARGS__); \
            }                                                    \
        }                                                        \
    } while (false)

#define LOGB_FLUSH() \
    ns_log::BufferedLogger::instance().flush()

namespace ns_log {

class BackingBuffer;

class BufferedLogger {
    BackingBuffer *_backing;

public:
    BufferedLogger(const BufferedLogger & buf) = delete;
    BufferedLogger & operator = (const BufferedLogger & buf) = delete;
    BufferedLogger();
    ~BufferedLogger();

    // Not intended to be used within real applications. Unit tests use these,
    // to easier be able to test all aspects of the buffer, and be independent
    // of the default settings for applications
    void setMaxCacheSize(uint32_t size);
    void setMaxEntryAge(uint64_t seconds);
    void setCountFactor(uint64_t seconds);

    void doLog(Logger&, Logger::LogLevel level, const char *file, int line,
               const std::string& token,
               const char *fmt, ...) __attribute__((format(printf,7,8)));

    /** Empty buffer and write all log entries in it. */
    void flush();

    /** Set a fake timer to use for log messages. Used in unit testing. */
    void setTimer(std::unique_ptr<Timer> timer);

    /** Trim the buffer. Removing old messages if wanted. */
    void trimCache();

    static BufferedLogger& instance();
};

} // ns_log

