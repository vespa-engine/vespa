// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferedlogger.h"
#include "internal.h"
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>

#include <cstdarg>
#include <iomanip>
#include <map>
#include <mutex>
#include <set>
#include <sstream>
#include <vector>

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
    uint64_t sequenceId;
    mutable uint32_t _count = 0;
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

    void log(const Timer &timer, const std::string& msg) const {
        _logger->doLogCore(timer,
                           payload.level, payload.file.c_str(), payload.line,
                           msg.c_str(), msg.size());
    }

};

Entry::Entry(Logger::LogLevel level, const char* file, int line,
             const std::string& token, const std::string& msg,
             system_time timestamp, Logger& l)
  : EntryKey(&l, token),
    _count(0),
    payload(level, file, line, msg, timestamp)
{
}

Entry::Entry(const Entry &) = default;
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

struct Cache {
    uint64_t _nextSequenceId = 1;
    using Entries = std::set<Entry>;
    using EntryOrder = std::map<uint64_t, Entries::iterator>;

    Entries _entry_map;
    EntryOrder _entry_order;

    const Entry& getOrAdd(Entry& entry) {
        entry.sequenceId = _nextSequenceId;
        auto [iter, added] = _entry_map.emplace(entry);
        if (added) {
            _entry_order[_nextSequenceId++] = iter;
        }
        return *iter;
    }

    void remove(uint64_t sequenceId) {
        auto place = _entry_order.find(sequenceId);
        if (place != _entry_order.end()) {
            _entry_map.erase(place->second);
            _entry_order.erase(place);
        }
    }

    struct iterator {
        EntryOrder::iterator place;
        const Entry& operator* () {
            return *place->second;
        }
        iterator& operator++() { ++place; return *this; }
        bool operator== (const iterator& other) { return place == other.place; }
        bool operator!= (const iterator& other) { return place != other.place; }
    };

    iterator begin() { return iterator(_entry_order.begin()); }
    iterator end()   { return iterator(_entry_order.end()); }

    void clear() {
        _entry_order.clear();
        _entry_map.clear();
    }
    size_t size() const { return _entry_map.size(); }
};

struct TimeStampWrapper : public Timer {
    TimeStampWrapper(system_time timeStamp) : _timeStamp(timeStamp) {}
    system_time getTimestamp() const noexcept override { return _timeStamp; }

    system_time _timeStamp;
};

} // namespace <unnamed>

// implementation details for BufferedLogger
class BackingBuffer {
    BackingBuffer(const BackingBuffer & rhs);
    BackingBuffer & operator = (const BackingBuffer & rhs);
public:
    std::unique_ptr<Timer> _timer;
    /** Lock needed to access cache. */
    mutable std::mutex _mutex;

    /** Entry container, lookup by token or iterate in insertion order */
    Cache _cache;

    uint32_t _maxCacheSize;
    duration _maxEntryAge;

    /** Empty buffer and write all log entries in it. */
    void flush();

    /** Gives all current content of log buffer. Useful for debugging. */
    std::string toString();

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
    _cache(),
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

void BufferedLogger::doLog(Logger& l, Logger::LogLevel level,
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

void BackingBuffer::logImpl(Logger& l, Logger::LogLevel level,
                            const char *file, int line,
                            const std::string& token,
                            const std::string& message)
{
    Entry newEntry(level, file, line, token, message, _timer->getTimestamp(), l);

    std::lock_guard<std::mutex> guard(_mutex);
    const Entry &entry = _cache.getOrAdd(newEntry);
    if (entry._count++ == 0) {
        // If entry didn't already exist, log it
        TimeStampWrapper wrapper(entry.payload.timestamp);
        entry.log(wrapper, message);
    }
    trimCache(entry.payload.timestamp);

}

void BackingBuffer::flush() {
    std::lock_guard<std::mutex> guard(_mutex);
    for (const auto & entry : _cache) {
        logIfRepeated(entry);
    }
    _cache.clear();
}

void BufferedLogger::flush() {
    _backing->flush();
}

void BackingBuffer::trimCache(system_time currentTime) {
    std::vector<uint64_t> removeList;
    for (const auto & entry : _cache) {
        // Remove entries that have been in here too long.
        if (entry.payload.timestamp + _maxEntryAge < currentTime) {
            logIfRepeated(entry);
            // can't remove() while iterating
            removeList.push_back(entry.sequenceId);
        } else {
            // no more old entries
            break;
        }
    }
    for (uint64_t sequenceId : removeList) {
        _cache.remove(sequenceId);
    }
    // could really be if, since we trim each time we add to cache
    while (_cache.size() > _maxCacheSize) {
        // the newest entries are immune to removal:
        size_t immune = (_maxCacheSize / 2);
        size_t numToCheck = _cache.size() - immune;
        system_time oldest;
        const Entry *toRemove = nullptr;
        for (const auto & entry : _cache) {
            system_time adjusted = entry.getAgeFactor();
            if (toRemove == nullptr || adjusted < oldest) {
                oldest = adjusted;
                toRemove = &entry;
            }
            if (--numToCheck == 0) break;
        }
        logIfRepeated(*toRemove);
        _cache.remove(toRemove->sequenceId);
    }
}

void BufferedLogger::trimCache() {
    _backing->trimCache();
}

void BackingBuffer::logIfRepeated(const Entry& e) const {
    if (e._count > 1) {
        std::string msg = e.repeatedMessage();
        e.log(*_timer, msg);
    }
}

std::string BackingBuffer::toString() {
    std::ostringstream ost;
    ost << "Cache content:\n";
    std::lock_guard<std::mutex> guard(_mutex);
    for (const auto & entry : _cache) {
        ost << "  " << entry.toString() << "\n";
    }
    return ost.str();
}


void BufferedLogger::setMaxCacheSize(uint32_t size) {
    _backing->_maxCacheSize = size;
}

void BufferedLogger::setMaxEntryAge(uint64_t seconds) {
    _backing->_maxEntryAge = std::chrono::seconds(seconds);
}

// only used for unit tests:
void BufferedLogger::setCountFactor(uint64_t seconds) {
    global_countFactor = std::chrono::seconds(seconds);
}

/** Set a fake timer to use for log messages. Used in unit testing. */
void BufferedLogger::setTimer(std::unique_ptr<Timer> timer) {
    _backing->_timer = std::move(timer);
}

BufferedLogger& BufferedLogger::instance() {
    static BufferedLogger logger;
    return logger;
}

} // ns_log
