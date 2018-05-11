// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <cstdint>
#include <sys/types.h> // for pid_t
#include <new>         // for placement new
#include <cstdlib>     // for malloc
#include <cstring>     // for memset

/**
 * If this macro is defined, the regular LOG calls will go through the
 * buffered logger, using the whole messages as tokens.
 *
 * If this macro is not defined, only LOGB calls will go through log buffer
 */
//#define VESPA_LOG_USELOGBUFFERFORREGULARLOG 1

// Used to use anonymous namespaces, but they fail miserably in gdb 5.3

#define LOG_SETUP(...)                          \
static ns_log::Logger logger(__VA_ARGS__)

#define LOG_SETUP_INDIRECT(x, id)               \
static ns_log::Logger *logger=NULL;             \
static bool logInitialised = false;             \
static const char *logName = x;                 \
static const char *rcsId = id


#define LOG_RCSID(x)                                            \
static int log_dummmy __attribute__((unused)) = logger.setRcsId(x)


// Define LOG if not using log buffer. Otherwise log buffer will define them
#ifndef VESPA_LOG_USELOGBUFFERFORREGULARLOG
#define LOG(level, ...)                                                       \
do {                                                                          \
    if (logger.wants(ns_log::Logger::level)) {                                \
        logger.doLog(ns_log::Logger::level, __FILE__, __LINE__, __VA_ARGS__); \
    }                                                                         \
} while (false)
#define VLOG(level, ...)                                      \
do {                                                          \
    if (logger.wants(level)) {                                \
        logger.doLog(level, __FILE__, __LINE__, __VA_ARGS__); \
    }                                                         \
} while (false)
#endif

// Must use placement new in the following definition, since the variable
// "logger" must be a valid logger object DURING the construction of the
// logger object itself.
#define LOG_INDIRECT_MUST                                                       \
    if (!logInitialised) {                                                      \
        logInitialised = true;                                                  \
        logger = static_cast<Logger *>(malloc(sizeof *logger));                 \
        new (logger) Logger(logName, rcsId);                                    \
    }
#define LOG_INDIRECT(level, ...)                                                \
do {                                                                            \
    LOG_INDIRECT_MUST                                                           \
    if (logger->wants(ns_log::Logger::level)) {                                 \
        logger->doLog(ns_log::Logger::level, __FILE__, __LINE__, __VA_ARGS__);  \
    }                                                                           \
} while (false)

#define LOG_WOULD_LOG(level) logger.wants(ns_log::Logger::level)
#define LOG_WOULD_VLOG(level) logger.wants(level)

#define EV_STARTING(name)                       \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventStarting(name);           \
    }                                           \
} while (false)

#define EV_STOPPING(name,why)                   \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventStopping(name, why);      \
    }                                           \
} while (false)

#define EV_STARTED(name)                        \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventStarted(name);            \
    }                                           \
} while (false)

#define EV_STOPPED(name,pid,exitcode)                   \
do {                                                    \
    if (logger.wants(ns_log::Logger::event)) {          \
        logger.doEventStopped(name, pid, exitcode);     \
    }                                                   \
} while (false)

#define EV_RELOADING(name)                      \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventReloading(name);          \
    }                                           \
} while (false)

#define EV_RELOADED(name)                       \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventReloaded(name);           \
    }                                           \
} while (false)

#define EV_CRASH(name,pid,signal)               \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventCrash(name, pid, signal); \
    }                                           \
} while (false)

#define EV_PROGRESS(name, ...)                          \
do {                                                    \
    if (logger.wants(ns_log::Logger::event)) {          \
        logger.doEventProgress(name, __VA_ARGS__);      \
    }                                                   \
} while (false)

#define EV_COUNT(name,value)                    \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventCount(name, value);       \
    }                                           \
} while (false)

#define EV_VALUE(name,value)                    \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventValue(name, value);       \
    }                                           \
} while (false)

#define EV_STATE(name,value)                   \
do {                                            \
    if (logger.wants(ns_log::Logger::event)) {  \
        logger.doEventState(name, value);      \
    }                                           \
} while (false)

namespace ns_log {

class LogTarget;
class ControlFile;

// XXX this is way too complicated, must be some simpler way to do this
/** Timer class used to retrieve timestamp, such that we can override in test */
struct Timer {
    virtual ~Timer() = default;
    virtual uint64_t getTimestamp() const;
};

/** Test timer returning just a given time. Used in tests to fake time. */
struct TestTimer : public Timer {
    uint64_t & _time;
    TestTimer(uint64_t & timeVar) : _time(timeVar) { }
    uint64_t getTimestamp() const override { return _time; }
};

class Logger {
public:
    enum LogLevel {
        fatal, error, warning, config, info, event, debug, spam, NUM_LOGLEVELS
    };
    static const char *logLevelNames[];
    static enum LogLevel parseLevel(const char *lname);

    /**
     * Set by unit tests to avoid needing to match different pids. Will be
     * false by default.
     */
    static bool fakePid;

private:
    Logger(const Logger &);
    Logger& operator =(const Logger &);

    unsigned int *_logLevels;

    static char _prefix[64];
    static LogTarget *_target;
    char _rcsId[256];
    static int _numInstances;

    static char _controlName[1024];
    static char _hostname[1024];
    static char _serviceName[1024];
    static const char _hexdigit[17];
    static ControlFile *_controlFile;

    static void setTarget();

    char _appendix[256];

    std::unique_ptr<Timer> _timer;

    void ensurePrefix(const char *name);
    void ensureHostname();
    void ensureControlName();
    void ensureServiceName();

    int tryLog(int sizeofPayload, LogLevel level, const char *file, int line,
               const char *fmt, va_list args);
public:
    ~Logger();
    explicit Logger(const char *name, const char *rcsId = nullptr);
    int setRcsId(const char *rcsId);
    static const char *levelName(LogLevel level);

    inline bool wants(LogLevel level);
    void doLog(LogLevel level, const char *file, int line,
               const char *fmt, ...) __attribute__((format(printf,5,6)));
    /**
     * The log buffer creates timestamp and creates the log message itself.
     * Thus the core log functionality has been moved here to avoid doing it
     * twice. doLogCore is called from doLog and from the log buffer.
     *
     * @param timestamp Time in microseconds.
     */
    void doLogCore(uint64_t timestamp, LogLevel level,
                   const char *file, int line, const char *msg, size_t msgSize);
    void doEventStarting(const char *name);
    void doEventStopping(const char *name, const char *why);
    void doEventStarted(const char *name);
    void doEventStopped(const char *name, pid_t pid, int exitCode);
    void doEventReloading(const char *name);
    void doEventReloaded(const char *name);
    void doEventCrash(const char *name, pid_t pid, int signal);
    void doEventProgress(const char *name, double value, double total = 0);
    void doEventCount(const char *name, uint64_t value);
    void doEventValue(const char *name, double value);
    void doEventState(const char *name, const char *value);

    // Only for unit testing
    void setTimer(std::unique_ptr<Timer> timer) { _timer = std::move(timer); }
};


#define CHARS_TO_UINT(a,b,c,d)                                          \
(static_cast<unsigned char>(a)                                          \
 | static_cast<unsigned int>(static_cast<unsigned char>(b) << 8u)       \
 | static_cast<unsigned int>(static_cast<unsigned char>(c) << 16u)      \
 | static_cast<unsigned int>(static_cast<unsigned char>(d) << 24u))

inline bool Logger::wants(LogLevel level)
{
    return _logLevels[level] == CHARS_TO_UINT(' ', ' ', 'O', 'N');
}

#define LOG_noreturn __attribute__((__noreturn__))

extern void log_assert_fail(const char *assertion,
                            const char *file,
                            uint32_t line) LOG_noreturn;

extern void log_abort(const char *message,
                      const char *file,
                      uint32_t line) LOG_noreturn;

#undef LOG_noreturn

} // end namespace log

//======================================//
// LOG_ASSERT and LOG_ABORT definitions //
//======================================//

#ifndef __STRING
#define __STRING(x) #x
#endif

#define LOG_ABORT(msg) \
  (ns_log::log_abort(msg, __FILE__, __LINE__))

#ifndef NDEBUG
#define LOG_ASSERT(expr) \
  ((void) ((expr) ? 0 : \
           (ns_log::log_assert_fail(__STRING(expr), \
                                    __FILE__, __LINE__), 0)))
#else
#define LOG_ASSERT(expr)
#endif // #ifndef NDEBUG

#ifndef PRId64
    #define PRId64 "ld"
    #define PRIu64 "lu"
    #define PRIx64 "lx"
#endif
