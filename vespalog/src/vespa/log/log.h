// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <new>         // for placement new
#include <sys/types.h> // for pid_t

/**
 * If this macro is defined, the regular LOG calls will go through the
 * buffered logger, using the whole messages as tokens.
 *
 * If this macro is not defined, only LOGB calls will go through log buffer
 */
//#define VESPA_LOG_USELOGBUFFERFORREGULARLOG 1

// Used to use anonymous namespaces, but they fail miserably in gdb 5.3

#define LOG_SETUP(...)                          \
static ns_log::Logger ns_log_logger(__VA_ARGS__)  // NOLINT

#define LOG_SETUP_INDIRECT(x, id)                   \
static ns_log::Logger *ns_log_indirect_logger=nullptr; \
static bool logInitialised = false;                 \
static const char *logName = x;                     \
static const char *indirectRcsId = id

#define LOG_WOULD_LOG(level) ns_log_logger.wants(ns_log::Logger::level)
#define LOG_WOULD_VLOG(level) ns_log_logger.wants(level)
#define LOG_INDIRECT_WOULD_LOG(levelName) \
    ns_log_indirect_logger->wants(ns_log::Logger::levelName)

// Define LOG if not using log buffer. Otherwise log buffer will define them
#ifndef VESPA_LOG_USELOGBUFFERFORREGULARLOG
#define LOG(level, ...)                                       \
do {                                                          \
    if (__builtin_expect(LOG_WOULD_LOG(level), false)) {      \
        ns_log_logger.doLog(ns_log::Logger::level,            \
                            __FILE__, __LINE__, __VA_ARGS__); \
    }                                                         \
} while (false)

#define VLOG(level, ...)                                      \
do {                                                          \
    if (__builtin_expect(LOG_WOULD_VLOG(level), false)) {     \
        ns_log_logger.doLog(level,                            \
                            __FILE__, __LINE__, __VA_ARGS__); \
    }                                                         \
} while (false)
#endif

// Must use placement new in the following definition, since the variable
// "logger" must be a valid logger object DURING the construction of the
// logger object itself.
#define LOG_INDIRECT(level, ...)                                        \
do {                                                                    \
    if (!logInitialised) {                                              \
        logInitialised = true;                                          \
        ns_log_indirect_logger =                                        \
                static_cast<Logger *>(                                  \
                        malloc(sizeof *ns_log_indirect_logger));        \
        new (ns_log_indirect_logger) Logger(logName, indirectRcsId);    \
    }                                                                   \
    if (LOG_INDIRECT_WOULD_LOG(level)) {                                \
        ns_log_indirect_logger->doLog(ns_log::Logger::level,            \
                                      __FILE__, __LINE__, __VA_ARGS__); \
    }                                                                   \
} while (false)


#define EV_STARTING(name)                    \
do {                                         \
    if (LOG_WOULD_LOG(event)) {              \
        ns_log_logger.doEventStarting(name); \
    }                                        \
} while (false)

#define EV_STOPPING(name,why)                     \
do {                                              \
    if (LOG_WOULD_LOG(event)) {                   \
        ns_log_logger.doEventStopping(name, why); \
    }                                             \
} while (false)

#define EV_STARTED(name)                        \
do {                                            \
    if (LOG_WOULD_LOG(event)) {                 \
        ns_log_logger.doEventStarted(name);     \
    }                                           \
} while (false)

#define EV_STOPPED(name,pid,exitcode)           \
do {                                            \
    if (LOG_WOULD_LOG(event)) {                 \
        ns_log_logger.doEventStopped(name, pid, \
                                     exitcode); \
    }                                           \
} while (false)

#define EV_CRASH(name,pid,signal)                      \
do {                                                   \
    if (LOG_WOULD_LOG(event)) {                        \
        ns_log_logger.doEventCrash(name, pid, signal); \
    }                                                  \
} while (false)

#define EV_PROGRESS(name, ...)                      \
do {                                                \
    if (LOG_WOULD_LOG(event)) {                     \
        ns_log_logger.doEventProgress(name,         \
                                      __VA_ARGS__); \
    }                                               \
} while (false)

#define EV_COUNT(name,value)                     \
    do {                                         \
    if (LOG_WOULD_LOG(event)) {                  \
        ns_log_logger.doEventCount(name, value); \
    }                                            \
} while (false)

#define EV_VALUE(name,value)                     \
do {                                             \
    if (LOG_WOULD_LOG(event)) {                  \
        ns_log_logger.doEventValue(name, value); \
    }                                            \
} while (false)

#define EV_STATE(name,value)                     \
do {                                             \
    if (LOG_WOULD_LOG(event)) {                  \
        ns_log_logger.doEventState(name, value); \
    }                                            \
} while (false)

namespace ns_log {

class LogTarget;
class ControlFile;
struct Timer;

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
    Logger(const Logger &) = delete;
    Logger & operator=(const Logger &) = delete;
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
    void doLogCore(const Timer &, LogLevel level,
                   const char *file, int line, const char *msg, size_t msgSize);
    void doEventStarting(const char *name);
    void doEventStopping(const char *name, const char *why);
    void doEventStarted(const char *name);
    void doEventStopped(const char *name, pid_t pid, int exitCode);
    void doEventCrash(const char *name, pid_t pid, int signal);
    void doEventProgress(const char *name, double value, double total = 0);
    void doEventCount(const char *name, uint64_t value);
    void doEventValue(const char *name, double value);
    void doEventState(const char *name, const char *value);

    // Only for unit testing
    void setTimer(std::unique_ptr<Timer> timer);

    // Only for internal use
    static LogTarget *getCurrentTarget();
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

[[noreturn]] extern void log_assert_fail(const char *assertion, const char *file, uint32_t line);

[[noreturn]] extern void log_abort(const char *message, const char *file, uint32_t line);

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
