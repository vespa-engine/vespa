// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sys/types.h> // for pid_t

#include <cstdarg>
#include <cstdint>
#include <memory>

namespace ns_log {

class LogTarget;
class ControlFile;
struct Timer;

class Logger {
private:
    static constexpr unsigned int CHARS_TO_UINT(unsigned char a, unsigned char b, unsigned char c, unsigned char d) {
        unsigned int aa = a;
        unsigned int bb = b;
        unsigned int cc = c;
        unsigned int dd = d;
        return aa | (bb << 8u) | (cc << 16u) | (dd << 24u);
    }

public:
    enum LogLevel { fatal, error, warning, config, info, event, debug, spam, NUM_LOGLEVELS };
    static const char* logLevelNames[];
    static enum LogLevel parseLevel(const char* lname);

    static constexpr unsigned int encoded_on_value() { return CHARS_TO_UINT(' ', ' ', 'O', 'N'); }
    static constexpr unsigned int encoded_off_value() { return CHARS_TO_UINT(' ', 'O', 'F', 'F'); }

    /**
     * Set by unit tests to avoid needing to match different pids. Will be
     * false by default.
     */
    static bool fakePid;

private:
    unsigned int* _logLevels;

    static char       _prefix[64];
    static LogTarget* _target;
    char              _rcsId[256];
    static int        _numInstances;

    static char         _controlName[1024];
    static char         _hostname[1024];
    static char         _serviceName[1024];
    static const char   _hexdigit[17];
    static ControlFile* _controlFile;

    static void setTarget();

    char _appendix[256];

    std::unique_ptr<Timer> _timer;

    void ensurePrefix(const char* name);
    void ensureHostname();
    void ensureControlName();
    void ensureServiceName();

    int tryLog(int sizeofPayload, LogLevel level, const char* file, int line, const char* fmt, va_list args);

public:
    ~Logger();
    explicit Logger(const char* name, const char* rcsId = nullptr);
    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;
    int setRcsId(const char* rcsId);
    static const char* levelName(LogLevel level);

    inline bool wants(LogLevel level);
    void doLog(LogLevel level, const char* file, int line, const char* fmt, ...)
        __attribute__((format(printf, 5, 6)));
    /**
     * The log buffer creates timestamp and creates the log message itself.
     * Thus the core log functionality has been moved here to avoid doing it
     * twice. doLogCore is called from doLog and from the log buffer.
     *
     * @param timestamp Time in microseconds.
     */
    void doLogCore(const Timer&, LogLevel level, const char* file, int line, const char* msg, size_t msgSize);
    void doEventStarting(const char* name);
    void doEventStopping(const char* name, const char* why);
    void doEventStarted(const char* name);
    void doEventStopped(const char* name, pid_t pid, int exitCode);
    void doEventCrash(const char* name, pid_t pid, int signal);
    void doEventProgress(const char* name, double value, double total = 0);
    void doEventCount(const char* name, uint64_t value);
    void doEventValue(const char* name, double value);
    void doEventState(const char* name, const char* value);

    [[nodiscard]] const Timer& timer() const noexcept { return *_timer; }

    // Only for unit testing
    void setTimer(std::unique_ptr<Timer> timer);

    // Only for internal use
    static LogTarget* getCurrentTarget();
};

inline bool Logger::wants(LogLevel level) {
    return _logLevels[level] == encoded_on_value();
}

} // namespace ns_log
