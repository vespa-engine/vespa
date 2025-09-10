// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferedlogger.h"
#include "internal.h"

#include <cassert>
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
    Logger* const _logger;
    const std::string _token;
    std::strong_ordering operator<=>(const EntryKey& entry) const = default;
    ~EntryKey();
};

EntryKey::~EntryKey() = default;

struct PayLoad {
    Logger::LogLevel level;
    std::string file;
    int line;
    std::string message;
    system_time timestamp;
};

/** Struct keeping information about log message. */
struct Entry : EntryKey {
    uint64_t sequenceId = 0;
    uint32_t _count = 1;
    const PayLoad payload;

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
    _count(1),
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
    using Entries = std::map<EntryKey, Entry>;
    using EntryOrder = std::map<uint64_t, Entries::iterator>;
    static const Entry& deref(EntryOrder::const_iterator place) {
        // place -> pair<uint64_t, Entries::iterator> -> pair<EntryKey, Entry>
        return place->second->second;
    }
    struct ByAgeCmp {
        const EntryOrder &map;
        bool operator() (uint64_t a, uint64_t b) const {
            const auto & ea = deref(map.find(a));
            const auto & eb = deref(map.find(b));
            system_time ta = ea.getAgeFactor();
            system_time tb = eb.getAgeFactor();
            if (ta < tb) return true;
            if (tb < ta) return false;
            return a < b;
        }
    };
    using AgeOrder = std::set<uint64_t, ByAgeCmp>;

    Entries _entry_map;
    EntryOrder _entry_order;
    AgeOrder _age_order;

    Cache() : _entry_map(), _entry_order(), _age_order(ByAgeCmp(_entry_order)) {}

    // Note: if the EntryKey already exists in the map,
    // we will return the stored Entry value after
    // incrementing its counter:
    const Entry& getOrAdd(Entry& entry) {
        entry.sequenceId = _nextSequenceId;
        auto [iter, added] = _entry_map.try_emplace(entry, entry);
        Entry &curEntry = iter->second;
        if (added) {
            _entry_order[_nextSequenceId] = iter;
            _age_order.insert(_nextSequenceId);
            ++_nextSequenceId;
        } else {
            // In order to add one to count, we need to remove and re-insert in age order set.
            uint64_t oldId = curEntry.sequenceId;
            auto it = _age_order.find(oldId);
            assert(it != _age_order.end());
            _age_order.erase(it);
            curEntry._count++;
            _age_order.insert(oldId);
        }
        assert(_entry_map.size() == _entry_order.size());
        assert(_entry_order.size() == _age_order.size());
        return curEntry;
    }

    void remove(uint64_t sequenceId) {
        auto place = _entry_order.find(sequenceId);
        assert(place != _entry_order.end());
        // NB keep order of erase calls:
        _age_order.erase(sequenceId);
        _entry_map.erase(place->second);
        _entry_order.erase(place);
        assert(_entry_map.size() == _entry_order.size());
        assert(_entry_order.size() == _age_order.size());
    }

    struct iterator {
        EntryOrder::iterator place;
        const Entry& operator* () { return deref(place); }
        iterator& operator++() { ++place; return *this; }
        bool operator== (const iterator& other) { return place == other.place; }
        bool operator!= (const iterator& other) { return place != other.place; }
    };

    iterator begin() { return iterator(_entry_order.begin()); }
    iterator end()   { return iterator(_entry_order.end()); }

    void clear() {
        _age_order.clear();
        _entry_order.clear();
        _entry_map.clear();
    }
    size_t size() const { return _entry_map.size(); }

    const Entry& oldestNonImmune(size_t numImmune) const {
        uint64_t limit = _nextSequenceId - numImmune;
        for (uint64_t seq : _age_order) {
            if (seq < limit) {
                auto place = _entry_order.find(seq);
                assert(place != _entry_order.end());
                return deref(place);
            }
        }
        assert(false);
    }
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
    if (entry._count == 1) {
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
    while (_cache.size() > _maxCacheSize) {
        const Entry& entry = _cache.oldestNonImmune(_maxCacheSize/2);
        logIfRepeated(entry);
        _cache.remove(entry.sequenceId);
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
