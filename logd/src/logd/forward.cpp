// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "forward.h"
#include "errhandle.h"
#include <vespa/vespalib/component/vtag.h>

LOG_SETUP("");
LOG_RCSID("$Id$");

namespace logdemon {

Forwarder::Forwarder()
    : _logserverfd(-1),
      _forwardMap(),
      _levelparser(),
      knownServices(),
      _badLines(0)
{}
Forwarder::~Forwarder() {}

void
Forwarder::forwardText(const char *text, int len)
{
    int wsize = write(_logserverfd, text, len);

    if (wsize != len) {
        if (wsize > 0) {
            LOG(warning, "only wrote %d of %d bytes to logserver", wsize, len);
        } else {
            LOG(warning, "problem sending data to logserver: %s", strerror(errno));
        }

        throw ConnectionException("problem sending data");
    }
}

void
Forwarder::sendMode()
{
    char buf[1024];
    snprintf(buf, 1024, "mode logd %s\n", vespalib::VersionTag);
    int len = strlen(buf);
    if (len < 100) {
            forwardText(buf, len);
    } else {
        LOG(warning, "too long mode line: %s", buf);
    }
}

void
Forwarder::forwardLine(const char *line, const char *eol)
{
    int linelen = eol - line;

    assert(_logserverfd >= 0);
    assert (linelen > 0);
    assert (linelen < 1024*1024);
    assert (line[linelen - 1] == '\n');

    if (parseline(line, eol)) {
        forwardText(line, linelen);
    }
}

bool
Forwarder::parseline(const char *linestart, const char *lineend)
{
    using vespalib::string;

    int llength = lineend - linestart;

    const char *fieldstart = linestart;
    // time
    const char *tab = strchr(fieldstart, '\t');
    if (tab == NULL || tab == fieldstart) {
        LOG(spam, "bad logline no 1. tab: %.*s",
            llength, linestart);
        ++_badLines;
        return false;
    }
    char *eod;
    double logtime = strtod(fieldstart, &eod);
    if (eod != tab) {
        int fflen = tab - linestart;
        LOG(spam, "bad logline first field not strtod parsable: %.*s",
            fflen, linestart);
        ++_badLines;
        return false;
    }
    time_t now = time(NULL);
    if (logtime - 864000 > now) {
        int fflen = tab - linestart;
        LOG(warning, "bad logline, time %.*s > 10 days in the future",
            fflen, linestart);
        ++_badLines;
        return false;
    }
    if (logtime + 8640000 < now) {
        int fflen = tab - linestart;
        LOG(warning, "bad logline, time %.*s > 100 days in the past",
            fflen, linestart);
        ++_badLines;
        return false;
    }

    // hostname
    fieldstart = tab + 1;
    tab = strchr(fieldstart, '\t');
    if (tab == NULL) {
        LOG(spam, "bad logline no 2. tab: %.*s",
            llength, linestart);
        ++_badLines;
        return false;
    }

    // pid
    fieldstart = tab + 1;
    tab = strchr(fieldstart, '\t');
    if (tab == NULL || tab == fieldstart) {
        LOG(spam, "bad logline no 3. tab: %.*s",
            llength, linestart);
        return false;
    }
    int pid = strtol(fieldstart, &eod, 10);
    // not checked - pid may not be a number after all

    // service
    fieldstart = tab + 1;
    tab = strchr(fieldstart, '\t');
    if (tab == NULL) {
        LOG(spam, "bad logline no 4. tab: %.*s",
            llength, linestart);
        ++_badLines;
        return false;
    }
    if (tab == fieldstart) {
        LOG(spam, "empty service in logline: %.*s",
            llength, linestart);
    }
    string service(fieldstart, tab-fieldstart);

    // component
    fieldstart = tab + 1;
    tab = strchr(fieldstart, '\t');
    if (tab == NULL || tab == fieldstart) {
        LOG(spam, "bad logline no 5. tab: %.*s",
            llength, linestart);
        ++_badLines;
        return false;
    }
    string component(fieldstart, tab-fieldstart);

    // level
    fieldstart = tab + 1;
    tab = strchr(fieldstart, '\t');
    if (tab == NULL || tab == fieldstart) {
        LOG(spam, "bad logline no 6. tab: %.*s",
            llength, linestart);
        ++_badLines;
        return false;
    }
    string level(fieldstart, tab-fieldstart);
    LogLevel l = _levelparser.parseLevel(level.c_str());

    // rest is freeform message, must be on this line:
    if (tab > lineend) {
        LOG(spam, "bad logline last tab after end: %.*s",
            llength, linestart);
        ++_badLines;
        return false;
    }

    // Check overrides
    ForwardMap::iterator found = _forwardMap.find(l);
    if (found != _forwardMap.end()) {
        return found->second;
    }

    Service *svcp = knownServices.getService(service.c_str());
    Component *cp = svcp->getComponent(component.c_str());
    cp->remember(logtime, pid);
    bool retval = cp->shouldForward(l);
    return retval;
}

LogLevel
LevelParser::parseLevel(const char *level)
{
    using ns_log::Logger;

    LogLevel l = Logger::parseLevel(level);
    if (l >= 0 && l <= Logger::NUM_LOGLEVELS) {
        return l;
    }
    if (! _seenLevelMap[level]) {
        LOG(warning, "unknown level '%s'", level);
        _seenLevelMap.set(level, true);
    }
    return Logger::fatal;
}

} // namespace
