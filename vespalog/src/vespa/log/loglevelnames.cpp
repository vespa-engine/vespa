#include <string.h>
#include <vespa/log/log.h>

namespace ns_log {

enum Logger::LogLevel
Logger::parseLevel(const char *lname)
{
    if (strcmp(lname, "fatal") == 0) return fatal;
    if (strcmp(lname, "error") == 0) return error;
    if (strcmp(lname, "warning") == 0) return warning;
    if (strcmp(lname, "config") == 0) return config;
    if (strcmp(lname, "info") == 0) return info;
    if (strcmp(lname, "event") == 0) return event;
    if (strcmp(lname, "debug") == 0) return debug;
    if (strcmp(lname, "spam") == 0) return spam;
    // bad level name signaled by NUM_LOGLEVELS
    return NUM_LOGLEVELS;
}

const char *Logger::logLevelNames[] = {
    "fatal",
    "error",
    "warning",
    "config",
    "info",
    "event",
    "debug",
    "spam",
    0 // converting NUM_LOGLEVELS gives null pointer
};

} // namespace
