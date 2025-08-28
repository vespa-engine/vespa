// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferedlogger.h"
#include "internal.h"
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>

#include <iomanip>
#include <sstream>
#include <vector>
#include <cstdarg>
#include <mutex>

using namespace std::literals::chrono_literals;

namespace ns_log {

namespace {

// Let each hit count for 5 seconds
duration global_countFactor = VESPA_LOG_COUNTAGEFACTOR * 1s;

// Don't let tokens from different loggers match each other, but
// if in the same logger, you should have full control. Overlapping
// tokens if you want is a feature.
struct EntryKey {
    Logger* _logger;
    std::string _token;
    std::strong_ordering operator<=>(const EntryKey& entry) const = default;
};

struct PayLoad {
    Logger::LogLevel level;
    std::string file;
    int line;
    std::string message;
    system_time timestamp;
};

/** Struct keeping information about log message. */
struct Entry : EntryKey {
    uint32_t _count;
    PayLoad payload;

    Entry(const Entry &);
    Entry & operator=(const Entry &);
    Entry(Entry &&) noexcept;
    Entry & operator=(Entry &&) noexcept;
    Entry(Logger::LogLevel level, const char* file, int line,
          const std::string& token, const std::string& message,
          system_time timestamp, Logger&);
    ~Entry();

    system_time getAgeFactor() const;

    std::string toString() const;

    std::string repeatedMessage() const {
        auto time_since_epoch = payload.timestamp.time_since_epoch();
        auto s = count_s(time_since_epoch);
        auto us = count_us(time_since_epoch) % 1000000;
        std::ostringstream ost;
        ost << payload.message << " (Repeated " << (_count - 1)
            << " times since " << s << "."
            << std::setw(6) << std::setfill('0') << us
            << ")";
        return ost.str();
    }

    void log(Timer &timer, const std::string& msg) const {
        _logger->doLogCore(timer,
                           payload.level, payload.file.c_str(), payload.line,
                           msg.c_str(), msg.size());
    }

};

Entry::Entry(Logger::LogLevel level, const char* file, int line,
             const std::string& token, const std::string& msg,
             system_time timestamp, Logger& l)
  : EntryKey(&l, token),
    _count(1),
    payload(level, file, line, msg, timestamp)
{
}

Entry::Entry(const Entry &) = default;
Entry & Entry::operator =(const Entry &) = default;
Entry::~Entry() = default;

std::string
Entry::toString() const
{
    std::ostringstream ost;
    ost << "Entry(" << payload.level << ", " << payload.file << ":" << payload.line << ": "
        << payload.message << " [" << _token << "], count " << _count
        << ", timestamp " << count_us(payload.timestamp.time_since_epoch()) << ")";
    return ost.str();
}

system_time
Entry::getAgeFactor() const
{
    return payload.timestamp + global_countFactor * _count;
}

} // namespace <unnamed>

// implementation details for BufferedLogger
class BackingBuffer {
    BackingBuffer(const BackingBuffer & rhs);
    BackingBuffer & operator = (const BackingBuffer & rhs);
public:
    std::unique_ptr<Timer> _timer;
    /** Lock needed to access cache. */
    mutable std::mutex _mutex;

    typedef boost::multi_index_container<
        Entry,
        boost::multi_index::indexed_by<
            boost::multi_index::sequenced<>, // Timestamp sorted
            boost::multi_index::ordered_unique<
                boost::multi_index::identity<EntryKey>
            >
        >
    > LogCacheFront;
    typedef boost::multi_index_container<
        Entry,
        boost::multi_index::indexed_by<
            boost::multi_index::sequenced<>, // Timestamp sorted
            boost::multi_index::ordered_unique<
                boost::multi_index::identity<EntryKey>
            >,
            boost::multi_index::ordered_non_unique<
                boost::multi_index::const_mem_fun<
                    Entry, system_time, &Entry::getAgeFactor
                >
            >
        >
    > LogCacheBack;

    /** Entry container indexes on insert order and token. */
    LogCacheFront _cacheFront;
    /** Entry container indexed on insert order, token and age function. */
    LogCacheBack _cacheBack;

    uint32_t _maxCacheSize;
    duration _maxEntryAge;

    /** Empty buffer and write all log entries in it. */
    void flush();

    /** Gives all current content of log buffer. Useful for debugging. */
    std::string toString() const;

    /**
     * Flush parts of cache, so we're below max size and only have messages of
     * acceptable age. Calling this, _mutex should already be locked.
     */
    void trimCache(system_time currentTime);

    /**
     * Trim the cache up to current time. Used externally to check if we
     * need to empty buffer before new log messages arive.
     */
    void trimCache() {
        std::lock_guard<std::mutex> guard(_mutex);
        trimCache(_timer->getTimestamp());
    }

    /**
     * Log a given entry to underlying logger. Used when removing from cache.
     * Calling this, _mutex should already be locked.
     */
    void logIfRepeated(const Entry& e) const;

    BackingBuffer();
    ~BackingBuffer();

    void logImpl(Logger& l, Logger::LogLevel level,
                 const char *file, int line,
                 const std::string& token,
                 const std::string& message);

};

BackingBuffer::BackingBuffer()
  : _timer(new Timer),
    _mutex(),
    _cacheFront(),
    _cacheBack(),
    _maxCacheSize(VESPA_LOG_LOGBUFFERSIZE),
    _maxEntryAge(VESPA_LOG_LOGENTRYMAXAGE * 1000 * 1000)
{
}

BackingBuffer::~BackingBuffer() = default;

BufferedLogger::BufferedLogger()
{
    _backing = new BackingBuffer();
}

BufferedLogger::~BufferedLogger()
{
    delete _backing; _backing = nullptr;
}

namespace {

typedef boost::multi_index::nth_index<
        BackingBuffer::LogCacheFront, 0>::type LogCacheFrontTimestamp;
typedef boost::multi_index::nth_index<
        BackingBuffer::LogCacheFront, 1>::type LogCacheFrontToken;
typedef boost::multi_index::nth_index<
        BackingBuffer::LogCacheBack, 0>::type LogCacheBackTimestamp;
typedef boost::multi_index::nth_index<
        BackingBuffer::LogCacheBack, 1>::type LogCacheBackToken;
typedef boost::multi_index::nth_index<
        BackingBuffer::LogCacheBack, 2>::type LogCacheBackAge;

struct TimeStampWrapper : public Timer {
    TimeStampWrapper(system_time timeStamp) : _timeStamp(timeStamp) {}
    system_time getTimestamp() const noexcept override { return _timeStamp; }

    system_time _timeStamp;
};

}

void
BufferedLogger::doLog(Logger& l, Logger::LogLevel level,
                      const char *file, int line,
                      const std::string& mytoken, const char *fmt, ...)
{
    std::string token(mytoken);
    va_list args;
    va_start(args, fmt);

    const size_t sizeofPayload(4000);
    std::vector<char> buffer(sizeofPayload);
    vsnprintf(&buffer[0], buffer.capacity(), fmt, args);
    std::string message(&buffer[0]);
    // Empty token means to use message itself as token
    if (token.empty()) token = message;

    _backing->logImpl(l, level, file, line, token, message);
}

void
BackingBuffer::logImpl(Logger& l, Logger::LogLevel level,
                       const char *file, int line,
                       const std::string& token,
                       const std::string& message)
{
    Entry entry(level, file, line, token, message, _timer->getTimestamp(), l);

    std::lock_guard<std::mutex> guard(_mutex);
    LogCacheFrontToken::iterator it1 = _cacheFront.get<1>().find(entry);
    LogCacheBackToken::iterator it2 = _cacheBack.get<1>().find(entry);
    if (it1 != _cacheFront.get<1>().end()) {
        Entry copy(*it1);
        ++copy._count;
        _cacheFront.get<1>().replace(it1, copy);
    } else if (it2 != _cacheBack.get<1>().end()) {
        Entry copy(*it2);
        ++copy._count;
        _cacheBack.get<1>().replace(it2, copy);
    } else {
        // If entry didn't already exist, add it to the cache and log it
        TimeStampWrapper wrapper(entry.payload.timestamp);
        entry.log(wrapper, message);
        _cacheFront.push_back(entry);
    }
    trimCache(entry.payload.timestamp);
}

void
BackingBuffer::flush()
{
    std::lock_guard<std::mutex> guard(_mutex);
    for (const auto & entry : _cacheBack) {
        logIfRepeated(entry);
    }
    _cacheBack.clear();
    for (const auto & entry : _cacheFront) {
        logIfRepeated(entry);
    }
    _cacheFront.clear();
}

void
BufferedLogger::flush() {
    _backing->flush();
}

void
BackingBuffer::trimCache(system_time currentTime)
{
    // Remove entries that have been in here too long.
    while (!_cacheBack.empty() &&
           _cacheBack.front().payload.timestamp + _maxEntryAge < currentTime)
    {
        logIfRepeated(_cacheBack.front());
        _cacheBack.pop_front();
    }
    while (!_cacheFront.empty() &&
           _cacheFront.front().payload.timestamp + _maxEntryAge < currentTime)
    {
        logIfRepeated(_cacheFront.front());
        _cacheFront.pop_front();
    }
    // If cache front is larger than half max size, move to back.
    for (uint32_t i = _cacheFront.size(); i > _maxCacheSize / 2; --i) {
        Entry e(_cacheFront.front());
        _cacheFront.pop_front();
        _cacheBack.push_back(e);
    }
    // Remove entries from back based on count modified age.
    for (uint32_t i = _cacheFront.size() + _cacheBack.size(); i > _maxCacheSize; --i) {
        logIfRepeated(*_cacheBack.get<2>().begin());
        _cacheBack.get<2>().erase(_cacheBack.get<2>().begin());
    }
}

void
BufferedLogger::trimCache()
{
    _backing->trimCache();
}

void
BackingBuffer::logIfRepeated(const Entry& e) const
{
    if (e._count > 1) {
        std::string msg = e.repeatedMessage();
        e.log(*_timer, msg);
    }
}

std::string
BackingBuffer::toString() const
{
    std::ostringstream ost;
    ost << "Front log cache content:\n";
    std::lock_guard<std::mutex> guard(_mutex);
    for (const auto & entry : _cacheFront) {
        ost << "  " << entry.toString() << "\n";
    }
    ost << "Back log cache content:\n";
    for (const auto & entry : _cacheBack) {
        ost << "  " << entry.toString() << "\n";
    }
    return ost.str();
}


void
BufferedLogger::setMaxCacheSize(uint32_t size) {
    _backing->_maxCacheSize = size;
}

void
BufferedLogger::setMaxEntryAge(uint64_t seconds) {
    _backing->_maxEntryAge = std::chrono::seconds(seconds);
}

// only used for unit tests:
void
BufferedLogger::setCountFactor(uint64_t seconds) {
    global_countFactor = std::chrono::seconds(seconds);
}

/** Set a fake timer to use for log messages. Used in unit testing. */
void
BufferedLogger::setTimer(std::unique_ptr<Timer> timer)
{
    _backing->_timer = std::move(timer);
}

BufferedLogger&
BufferedLogger::instance()
{
    static BufferedLogger logger;
    return logger;
}

} // ns_log
