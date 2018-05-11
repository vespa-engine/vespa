// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "log.h"
LOG_SETUP_INDIRECT(".log", "$Id$");

#undef LOG
#define LOG LOG_INDIRECT

#include "lock.h"
#include "log-target.h"
#include "internal.h"
#include "control-file.h"
#include "bufferedlogger.h"

#include <vespa/defaults.h>
#include <cassert>
#include <cstdarg>
#include <unistd.h>
#include <sys/time.h>

namespace ns_log {

uint64_t Timer::getTimestamp() const {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    uint64_t timestamp = tv.tv_sec;
    timestamp *= 1000000;
    timestamp += tv.tv_usec;
    return timestamp;
}

LogTarget *Logger::_target = 0;
int        Logger::_numInstances = 0;
bool Logger::fakePid = false;
char Logger::_prefix[64] = { '\0' };
const char Logger::_hexdigit[17] = "0123456789abcdef";
char Logger::_controlName[1024] = { '\0' };
char Logger::_hostname[1024] = { '\0'};
char Logger::_serviceName[1024] = {'\0' };
ControlFile *Logger::_controlFile = 0;

void
Logger::ensureControlName()
{
    if (_controlName[0] == '\0') {
        if (!ControlFile::makeName(_serviceName, _controlName,
                                   sizeof _controlName)) {
            LOG(spam, "Neither $VESPA_LOG_CONTROL_FILE nor "
                "$VESPA_LOG_CONTROL_DIR + $VESPA_SERVICE_NAME are set, "
                "runtime log-control is therefore disabled.");
            strcpy(_controlName, "///undefined///");
        }
    }
}


void
Logger::ensureServiceName()
{
    if (_serviceName[0] == '\0') {
        const char *name = getenv("VESPA_SERVICE_NAME");
        snprintf(_serviceName, sizeof _serviceName, "%s", name ? name : "-");
    }
}

void
Logger::setTarget()
{
    try {
        char *name = getenv("VESPA_LOG_TARGET");
        if (name) {
            LogTarget *target = LogTarget::makeTarget(name);
            delete _target;
            _target = target;
        } else {
            LOG(spam, "$VESPA_LOG_TARGET is not set, logging to stderr");
        }
    } catch (InvalidLogException& x) {
        LOG(error, "Log target problem: %s. Logging to stderr. "
            "($VESPA_LOG_TARGET=\"%s\")",
            x.what(), getenv("VESPA_LOG_TARGET"));
    }
}


void
Logger::ensurePrefix(const char *name)
{
    const char *start = name;
    if (name[0] != '\0' && name[0] != '.') {
        const char *end = strchr(name, '.');
        int len = end ? end - name : strlen(name);
        start += len;

        if (_prefix[0]) {
            // Make sure the prefix already set is identical to this one
            if ((len != int(strlen(_prefix)))
                || memcmp(name, _prefix, len) != 0) {
                LOG(error, "Fatal: Tried to set log component name '%s' which "
                    "conflicts with existing root component '%s'. ABORTING",
                    name, _prefix);
                throwInvalid("Bad config component name '%s' conflicts "
                             "with existing name '%s'", name, _prefix);
            }
        } else {
            // No one has set the prefix yet, so we do it
            snprintf(_prefix, sizeof _prefix, "%.*s", len, name);
            LOG(debug, "prefix was set to '%s'", _prefix);
        }
    }
}

void
Logger::ensureHostname()
{
    if (_hostname[0] == '\0') {
        snprintf(_hostname, sizeof _hostname, "%s", vespa::Defaults::vespaHostname());
    }
}


Logger::Logger(const char *name, const char *rcsId)
    : _logLevels(ControlFile::defaultLevels()),
      _timer(new Timer())
{
    _numInstances++;
    memset(_rcsId, 0, sizeof(_rcsId));
    memset(_appendix, 0, sizeof(_appendix));
    const char *app(strchr(name, '.') ? strchr(name, '.') : "");
    assert(strlen(app) < sizeof(_appendix));
    strcpy(_appendix, app);
    if (!_target) {
        // Set up stderr first as a target so we can log even if target
        // cannot be found
        _target = LogTarget::defaultTarget();
        setTarget();
    }
    ensureServiceName();
    if (rcsId) {
        setRcsId(rcsId);
    }
    ensureControlName();
    ensurePrefix(name);
    ensureHostname();

    // Only read log levels from a file if we are using a file!
    if (strcmp(_controlName, "///undefined///") != 0) {
        try {
            if (!_controlFile) {
                _controlFile = new ControlFile(_controlName, ControlFile::CREATE);
            }
            _logLevels = _controlFile->getLevels(_appendix);
            _controlFile->setPrefix(_prefix);
        } catch (InvalidLogException& x) {
            LOG(error, "Problems initialising logging: %s.", x.what());
            LOG(warning, "Log control disabled, using default levels.");
        }
    }
}


Logger::~Logger()
{
  _numInstances--;
  if ((_numInstances == 1)) {
    if (logger != nullptr) {
      logger->~Logger();
      free(logger);
      logger = nullptr;
    }
  } else if (_numInstances == 0) {
    delete _controlFile;
    logInitialised = false;
    delete _target;
    _target = nullptr;
  }
}


int
Logger::setRcsId(const char *id)
{
    const char *start = strchr(id, ',');
    if (start) {
        start += std::min((size_t)3, strlen(start)); // Skip 3 chars
    } else {
        start = id;
    }

    int len = strlen(start);
    const char *end = strchr(start, ' ');
    if (!end) {
        end = start + len;
    }

    assert(size_t(len + 8) < sizeof(_rcsId));
    sprintf(_rcsId, "(%.*s): ", (int)(end - start), start);
    LOG(spam, "rcs id was set to '%s'", _rcsId);
    return 0;
}

int
Logger::tryLog(int sizeofPayload, LogLevel level, const char *file, int line, const char *fmt, va_list args)
{
    char * payload(new char[sizeofPayload]);
    const int actualSize = vsnprintf(payload, sizeofPayload, fmt, args);

    if (actualSize < sizeofPayload) {
        uint64_t timestamp = _timer->getTimestamp();
        doLogCore(timestamp, level, file, line, payload, actualSize);
    }
    delete[] payload;
    return actualSize;
}

void
Logger::doLog(LogLevel level, const char *file, int line, const char *fmt, ...)
{
    int sizeofPayload(0x400);
    int actualSize(sizeofPayload-1);
    do {
        sizeofPayload = actualSize+1;
        va_list args;
        va_start(args, fmt);
        actualSize = tryLog(sizeofPayload, level, file, line, fmt, args);
        va_end(args);
    } while (sizeofPayload < actualSize);
    ns_log::BufferedLogger::logger.trimCache();
}

void Logger::doLogCore(uint64_t timestamp, LogLevel level,
                       const char *file, int line, const char *msg, size_t msgSize)
{
    const size_t sizeofEscapedPayload(msgSize*4+1);
    const size_t sizeofTotalMessage(sizeofEscapedPayload + 1000);
    char * bigBuffer(new char[sizeofEscapedPayload+sizeofTotalMessage]);
    char * escapedPayload(bigBuffer);
    char * totalMessage(escapedPayload + sizeofEscapedPayload);

    char *dst = escapedPayload;
    for (size_t i(0); (i < msgSize) && msg[i]; i++) {
         unsigned char c = static_cast<unsigned char>(msg[i]);
        if ((c >= 32) && (c != '\\') && (c != 127)) {
            *dst++ = static_cast<char>(c);
        } else {
            *dst++ = '\\';
            if (c == '\\') {
                *dst++ = '\\';
            } else if (c == '\r') {
                *dst++ = 'r';
            } else if (c == '\n') {
                *dst++ = 'n';
            } else if (c == '\t') {
                *dst++ = 't';
            } else {
                *dst++ = 'x';
                *dst++ = _hexdigit[c >> 4];
                *dst++ = _hexdigit[c & 0xf];
            }
        }
    }
    *dst = 0;

        // The only point of tracking thread id is to be able to distinguish log
        // from multiple threads from each other. As one aren't using that many
        // threads, only showing the least significant bits will hopefully
        // distinguish between all threads in your application. Alter later if
        // found to be too inaccurate.
    int32_t tid = (fakePid ? -1 : pthread_self() % 0xffff);

    if (_target->makeHumanReadable()) {
        time_t secs = static_cast<time_t>(timestamp / 1000000);
        struct tm tmbuf;
        localtime_r(&secs, &tmbuf);
        char timebuf[100];
        strftime(timebuf, 100, "%Y-%m-%d %H:%M:%S", &tmbuf);
        snprintf(totalMessage, sizeofTotalMessage,
                 "[%s.%06u] %d/%d (%s%s) %s: %s\n",
                 timebuf, static_cast<unsigned int>(timestamp % 1000000),
                 fakePid ? -1 : getpid(), tid,
                 _prefix, _appendix,
                 levelName(level), msg);
    } else if (level == debug || level == spam) {
        snprintf(totalMessage, sizeofTotalMessage,
                 "%u.%06u\t%s\t%d/%d\t%s\t%s%s\t%s\t%s:%d %s%s\n",
                 static_cast<unsigned int>(timestamp / 1000000),
                 static_cast<unsigned int>(timestamp % 1000000),
                 _hostname, fakePid ? -1 : getpid(), tid,
                 _serviceName, _prefix,
                 _appendix, levelName(level), file, line,
                 _rcsId,
                 escapedPayload);
    } else {
        snprintf(totalMessage, sizeofTotalMessage,
                 "%u.%06u\t%s\t%d/%d\t%s\t%s%s\t%s\t%s\n",
                 static_cast<unsigned int>(timestamp / 1000000),
                 static_cast<unsigned int>(timestamp % 1000000),
                 _hostname, fakePid ? -1 : getpid(), tid,
                 _serviceName, _prefix,
                 _appendix, levelName(level), escapedPayload);
    }

    _target->write(totalMessage, strlen(totalMessage));
    delete [] bigBuffer;
}

const char *
Logger::levelName(LogLevel level)
{
    switch (level) {
    case fatal: return "fatal"; // Deprecated, remove this later.
    case error: return "error";
    case warning: return "warning";
    case event: return "event";
    case config: return "config";
    case info: return "info";
    case debug: return "debug";
    case spam: return "spam";
    case NUM_LOGLEVELS: break;
    }
    return "--unknown--";
}

void
Logger::doEventStarting(const char *name)
{
    doLog(event, "", 0, "starting/1 name=\"%s\"", name);
}

void
Logger::doEventStopping(const char *name, const char *why)
{
    doLog(event, "", 0, "stopping/1 name=\"%s\" why=\"%s\"", name, why);
}

void
Logger::doEventStarted(const char *name)
{
    doLog(event, "", 0, "started/1 name=\"%s\"", name);
}

void
Logger::doEventStopped(const char *name, pid_t pid, int exitCode)
{
    doLog(event, "", 0, "stopped/1 name=\"%s\" pid=%d exitcode=%d", name,
          static_cast<int>(pid), exitCode);
}

void
Logger::doEventReloading(const char *name)
{
    doLog(event, "", 0, "reloading/1 name=\"%s\"", name);
}

void
Logger::doEventReloaded(const char *name)
{
    doLog(event, "", 0, "reloaded/1 name=\"%s\"", name);
}

void
Logger::doEventCrash(const char *name, pid_t pid, int signal)
{
    doLog(event, "", 0, "crash/1 name=\"%s\" pid=%d signal=\"%s\"", name, pid,
          strsignal(signal));
}

void
Logger::doEventProgress(const char *name, double value, double total)
{
    if (total > 0) {
        doLog(event, "", 0, "progress/1 name=\"%s\" value=%.18g total=%.18g",
              name, value, total);
    } else {
        doLog(event, "", 0, "progress/1 name=\"%s\" value=%.18g", name, value);
    }
}

void
Logger::doEventCount(const char *name, uint64_t value)
{
    doLog(event, "", 0, "count/1 name=\"%s\" value=%lu", name, value);
}

void
Logger::doEventValue(const char *name, double value)
{
    doLog(event, "", 0, "value/1 name=\"%s\" value=%.18g", name, value);
}

void
Logger::doEventState(const char *name, const char *value)
{
    doLog(event, "", 0, "state/1 name=\"%s\" value=\"%s\"", name, value);
}

} // end namespace ns_log
