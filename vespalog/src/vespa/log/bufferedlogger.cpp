// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferedlogger.h"
#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>

#include <iomanip>
#include <sstream>
#include <vector>
#include <cstdarg>
#include <mutex>

namespace ns_log {

// implementation details for BufferedLogger
class BackingBuffer {
    BackingBuffer(const BackingBuffer & rhs);
    BackingBuffer & operator = (const BackingBuffer & rhs);
public:
    std::unique_ptr<Timer> _timer;
    /** Lock needed to access cache. */
    mutable std::mutex _mutex;

    static uint64_t _countFactor;

    /** Struct keeping information about log message. */
    struct Entry {
        Logger::LogLevel _level;
        std::string _file;
        int _line;
        std::string _token;
        std::string _message;
        uint32_t _count;
        uint64_t _timestamp;
        Logger* _logger;

        Entry(const Entry &);
        Entry & operator=(const Entry &);
        Entry(Entry &&) noexcept;
        Entry & operator=(Entry &&) noexcept;
        Entry(Logger::LogLevel level, const char* file, int line,
              const std::string& token, const std::string& message,
              uint64_t timestamp, Logger&);
        ~Entry();

        bool operator==(const Entry& entry) const;
        bool operator<(const Entry& entry) const;

        uint64_t getAgeFactor() const;

        std::string toString() const;
    };

    typedef boost::multi_index_container<
        Entry,
        boost::multi_index::indexed_by<
            boost::multi_index::sequenced<>, // Timestamp sorted
            boost::multi_index::ordered_unique<
                boost::multi_index::identity<Entry>
            >
        >
    > LogCacheFront;
    typedef boost::multi_index_container<
        Entry,
        boost::multi_index::indexed_by<
            boost::multi_index::sequenced<>, // Timestamp sorted
            boost::multi_index::ordered_unique<
                boost::multi_index::identity<Entry>
            >,
            boost::multi_index::ordered_non_unique<
                boost::multi_index::const_mem_fun<
                    Entry, uint64_t, &Entry::getAgeFactor
                >
            >
        >
    > LogCacheBack;

    /** Entry container indexes on insert order and token. */
    LogCacheFront _cacheFront;
    /** Entry container indexed on insert order, token and age function. */
    LogCacheBack _cacheBack;

    uint32_t _maxCacheSize;
    uint64_t _maxEntryAge;

    /** Empty buffer and write all log entries in it. */
    void flush();

    /** Gives all current content of log buffer. Useful for debugging. */
    std::string toString() const;

    /**
     * Flush parts of cache, so we're below max size and only have messages of
     * acceptable age. Calling this, _mutex should already be locked.
     */
    void trimCache(uint64_t currentTime);

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
uint64_t BackingBuffer::_countFactor = VESPA_LOG_COUNTAGEFACTOR * 1000 * 1000;

BackingBuffer::Entry::Entry(Logger::LogLevel level, const char* file, int line,
                             const std::string& token, const std::string& msg,
                             uint64_t timestamp, Logger& l)
    : _level(level),
      _file(file),
      _line(line),
      _token(token),
      _message(msg),
      _count(1),
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
    return (_token == entry._token);
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
        << ", timestamp " << _timestamp << ")";
    return ost.str();
}

uint64_t
BackingBuffer::Entry::getAgeFactor() const
{
    return _timestamp + _countFactor * _count;
}

BackingBuffer::BackingBuffer()
    : _timer(new Timer),
      _mutex(),
      _cacheFront(),
      _cacheBack(),
      _maxCacheSize(VESPA_LOG_LOGBUFFERSIZE),
      _maxEntryAge(VESPA_LOG_LOGENTRYMAXAGE * 1000 * 1000)
{
}

BackingBuffer::~BackingBuffer()
{
}

BufferedLogger::BufferedLogger()
{
    _backing = new BackingBuffer();
}

BufferedLogger::~BufferedLogger()
{
    delete _backing; _backing = NULL;
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
        l.doLogCore(entry._timestamp, level, file, line, message.c_str(), message.size());
        _cacheFront.push_back(entry);
    }
    trimCache(entry._timestamp);
}

void
BackingBuffer::flush()
{
    std::lock_guard<std::mutex> guard(_mutex);
    for (LogCacheBack::const_iterator it = _cacheBack.begin();
         it != _cacheBack.end(); ++it)
    {
        log(*it);
    }
    _cacheBack.clear();
    for (LogCacheFront::const_iterator it = _cacheFront.begin();
         it != _cacheFront.end(); ++it)
    {
        log(*it);
    }
    _cacheFront.clear();
}

void
BufferedLogger::flush() {
    _backing->flush();
}

void
BackingBuffer::trimCache(uint64_t currentTime)
{
        // Remove entries that have been in here too long.
    while (!_cacheBack.empty() &&
           _cacheBack.front()._timestamp + _maxEntryAge < currentTime)
    {
        log(_cacheBack.front());
        _cacheBack.pop_front();
    }
    while (!_cacheFront.empty() &&
           _cacheFront.front()._timestamp + _maxEntryAge < currentTime)
    {
        log(_cacheFront.front());
        _cacheFront.pop_front();
    }
        // If cache front is larger than half max size, move to back.
    for (uint32_t i = _cacheFront.size(); i > _maxCacheSize / 2; --i) {
        Entry e(_cacheFront.front());
        _cacheFront.pop_front();
        _cacheBack.push_back(e);
    }
        // Remove entries from back based on count modified age.
    for (uint32_t i = _cacheFront.size() + _cacheBack.size();
         i > _maxCacheSize; --i)
    {
        log(*_cacheBack.get<2>().begin());
        _cacheBack.get<2>().erase(_cacheBack.get<2>().begin());
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
            << " times since " << (e._timestamp / 1000000) << "."
            << std::setw(6) << std::setfill('0') << (e._timestamp % 1000000)
            << ")";
        e._logger->doLogCore(_timer->getTimestamp(), e._level, e._file.c_str(),
                             e._line, ost.str().c_str(), ost.str().size());
    }
}

std::string
BackingBuffer::toString() const
{
    std::ostringstream ost;
    ost << "Front log cache content:\n";
    std::lock_guard<std::mutex> guard(_mutex);
    for (LogCacheFront::const_iterator it = _cacheFront.begin();
         it != _cacheFront.end(); ++it)
    {
        ost << "  " << it->toString() << "\n";
    }
    ost << "Back log cache content:\n";
    for (LogCacheBack::const_iterator it = _cacheBack.begin();
         it != _cacheBack.end(); ++it)
    {
        ost << "  " << it->toString() << "\n";
    }
    return ost.str();
}


void
BufferedLogger::setMaxCacheSize(uint32_t size) {
    _backing->_maxCacheSize = size;
}

void
BufferedLogger::setMaxEntryAge(uint64_t seconds) {
    _backing->_maxEntryAge = seconds * 1000000;
}

void
BufferedLogger::setCountFactor(uint64_t factor) {
    _backing->_countFactor = factor * 1000000;
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
