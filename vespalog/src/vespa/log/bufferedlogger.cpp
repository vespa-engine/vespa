// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferedlogger.h"
#include "internal.h"

#include <cstdarg>
#include <iomanip>
#include <map>
#include <set>
#include <mutex>
#include <sstream>
#include <vector>

using namespace std::literals::chrono_literals;

namespace ns_log {

// implementation details for BufferedLogger
class BackingBuffer {
    BackingBuffer(const BackingBuffer & rhs);
    BackingBuffer & operator = (const BackingBuffer & rhs);
public:
    std::unique_ptr<Timer> _timer;
    /** Lock needed to access cache. */
    mutable std::mutex _mutex;

    static duration _countFactor;

    /** Struct keeping information about log message. */
    struct Entry {
        uint64_t number = 0;
        Logger::LogLevel _level;
        std::string _file;
        int _line;
        std::string _token;
        std::string _message;
        mutable uint32_t _count;
        system_time _timestamp;
        Logger* _logger;

        Entry(const Entry &);
        Entry & operator=(const Entry &);
        Entry(Entry &&) noexcept;
        Entry & operator=(Entry &&) noexcept;
        Entry(Logger::LogLevel level, const char* file, int line,
              const std::string& token, const std::string& message,
              system_time timestamp, Logger&);
        ~Entry();

        bool operator==(const Entry& entry) const;
        bool operator<(const Entry& entry) const;

        system_time getAgeFactor() const;

        std::string toString() const;
    };

    struct Cache {
        uint64_t _nextNumber = 1;
        using Entries = std::set<Entry>;
        using EntryOrder = std::map<uint64_t, Entries::iterator>;

        Entries _entry_map;
        EntryOrder _entry_order;
        const Entry& getOrAdd(Entry& entry) {
            entry.number = _nextNumber;
            auto [iter, added] = _entry_map.emplace(entry);
            if (added) {
                fprintf(stderr, "new entry %zd token '%s'\n", entry.number, entry._token.c_str());
                _entry_order[_nextNumber++] = iter;
                fprintf(stderr, "sizes %zd / %zd\n", _entry_map.size(), _entry_order.size());
                fprintf(stderr, "added: %p\n", &*iter);
            } else {
                fprintf(stderr, "already have %zd for token '%s'\n", iter->number, entry._token.c_str());
            }
            return *iter;
        }

        void remove(const Entry& entry) {
            remove(entry.number);
        }
        void remove(uint64_t number) {
            fprintf(stderr, "try remove number %zd\n", number);
            auto place = _entry_order.find(number);
            if (place != _entry_order.end()) {
                fprintf(stderr, "remove token '%s'\n", place->second->_token.c_str());
                _entry_map.erase(place->second);
                _entry_order.erase(place);
                fprintf(stderr, "sizes %zd / %zd\n", _entry_map.size(), _entry_order.size());
            }
        }

        struct iterator {
            EntryOrder::iterator place;
            const Entry& operator* () {
                // fprintf(stderr, "op * : %p\n", &place->second->second);
                return *place->second;
            }
            const Entry* operator-> () {
                // fprintf(stderr, "op -> : %p\n", &place->second->second);
                return &*place->second;
            }
            iterator& operator++() { ++place; return *this; }
            bool operator== (const iterator& other) { return place == other.place; }
            bool operator!= (const iterator& other) { return place != other.place; }
        };

        iterator begin() {
            return iterator(_entry_order.begin());
        }
        iterator end() {
            return iterator(_entry_order.end());
        }

        void clear() {
               fprintf(stderr, "clear, sizes %zd / %zd\n", _entry_map.size(), _entry_order.size());
            _entry_order.clear();
            _entry_map.clear();
        }
        size_t size() const { return _entry_map.size(); }
    };

    /** Entry container, lookup by token or iterate insertion order */
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
    void log(const Entry& e) const;

    BackingBuffer();
    ~BackingBuffer();

    void logImpl(Logger& l, Logger::LogLevel level,
                 const char *file, int line,
                 const std::string& token,
                 const std::string& message);

};

// Let each hit count for 5 seconds
duration BackingBuffer::_countFactor = VESPA_LOG_COUNTAGEFACTOR * 1s;

BackingBuffer::Entry::Entry(Logger::LogLevel level, const char* file, int line,
                             const std::string& token, const std::string& msg,
                             system_time timestamp, Logger& l)
    : _level(level),
      _file(file),
      _line(line),
      _token(token),
      _message(msg),
      _count(0),
      _timestamp(timestamp),
      _logger(&l)
{
}

BackingBuffer::Entry::Entry(const Entry &) = default;
BackingBuffer::Entry & BackingBuffer::Entry::operator =(const Entry &) = default;
BackingBuffer::Entry::Entry(Entry &&) noexcept = default;
BackingBuffer::Entry & BackingBuffer::Entry::operator=(Entry &&) noexcept = default;
BackingBuffer::Entry::~Entry() = default;

bool
BackingBuffer::Entry::operator==(const Entry& entry) const
{
    return (_logger == entry._logger && _token == entry._token);
}

bool
BackingBuffer::Entry::operator<(const Entry& entry) const
{
        // Don't let tokens from different loggers match each other
    if (_logger != entry._logger) {
        return _logger < entry._logger;
    }
        // If in the same logger, you should have full control. Overlapping
        // tokens if you want is a feature.
    return (_token < entry._token);
}

std::string
BackingBuffer::Entry::toString() const
{
    std::ostringstream ost;
    ost << "Entry(" << _level << ", " << _file << ":" << _line << ": "
        << _message << " [" << _token << "], count " << _count
        << ", timestamp " << count_us(_timestamp.time_since_epoch()) << ")";
    return ost.str();
}

system_time
BackingBuffer::Entry::getAgeFactor() const
{
    return _timestamp + _countFactor * _count;
}

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

namespace {

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
    Entry newEntry(level, file, line, token, message, _timer->getTimestamp(), l);

    std::lock_guard<std::mutex> guard(_mutex);
    const Entry &entry = _cache.getOrAdd(newEntry);
       fprintf(stderr, "entry %zd with count %d\n", entry.number, entry._count);
    if (entry._count++ == 0) {
        // If entry didn't already exist, log it
        l.doLogCore(TimeStampWrapper(entry._timestamp), level, file, line, message.c_str(), message.size());
    }
    trimCache(entry._timestamp);
}

void
BackingBuffer::flush()
{
       fprintf(stderr, "flush\n");
    std::lock_guard<std::mutex> guard(_mutex);
    for (const auto & entry : _cache) {
        log(entry);
    }
    _cache.clear();
}

void
BufferedLogger::flush() {
    _backing->flush();
}

void
BackingBuffer::trimCache(system_time currentTime)
{
    double s = count_s(currentTime.time_since_epoch());
    std::vector<uint64_t> removeList;
    for (const auto & entry : _cache) {
        // Remove entries that have been in here too long.
        if (entry._timestamp + _maxEntryAge < currentTime) {
            fprintf(stderr, "at %f, remove cause of max entry age: %zd\n", s, entry.number);
            log(entry);
            removeList.push_back(entry.number);
        } else {
            fprintf(stderr, "no more old entries\n");
            break;
        }
    }
    for (uint64_t number : removeList) {
        _cache.remove(number);
    }
    if (_cache.size() > _maxCacheSize) {
        fprintf(stderr, "remove %zd > %d\n", _cache.size(), _maxCacheSize);
        long toRemove = _cache.size() - _maxCacheSize;
        long numCheck = _cache.size() - (_maxCacheSize / 2);
        fprintf(stderr, "remove %ld\n", toRemove);
        std::map<system_time, const Entry *> byAgeFactor;
        for (const auto & entry : _cache) {
            system_time adjusted = entry.getAgeFactor();
            byAgeFactor[adjusted] = &entry;
            // fprintf(stderr, "x[a] = %p\n", &entry);
            if (numCheck-- == 0) break;
        }
        auto iter = byAgeFactor.begin();
        while (iter != byAgeFactor.end() && toRemove-- > 0) {
            fprintf(stderr, "at %f, remove cause using byAgeFactor: %zd\n", s, iter->second->number);
            log(*iter->second);
            _cache.remove(iter->second->number);
            ++iter;
            fprintf(stderr, "remove %ld\n", toRemove);
        }
    }
}

void
BufferedLogger::trimCache()
{
    _backing->trimCache();
}

void
BackingBuffer::log(const Entry& e) const
{
    if (e._count > 1) {
        std::ostringstream ost;
        ost << e._message << " (Repeated " << (e._count - 1)
            << " times since " << count_s(e._timestamp.time_since_epoch()) << "."
            << std::setw(6) << std::setfill('0') << (count_us(e._timestamp.time_since_epoch()) % 1000000)
            << ")";
        e._logger->doLogCore(*_timer, e._level, e._file.c_str(),
                             e._line, ost.str().c_str(), ost.str().size());
    }
}

std::string
BackingBuffer::toString()
{
    std::ostringstream ost;
    ost << "Cache content:\n";
    std::lock_guard<std::mutex> guard(_mutex);
    for (const auto & entry : _cache) {
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

void
BufferedLogger::setCountFactor(uint64_t seconds) {
    _backing->_countFactor = std::chrono::seconds(seconds);
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
