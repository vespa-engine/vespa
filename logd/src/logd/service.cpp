// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <time.h>
#include <sys/stat.h>
#include <assert.h>

#include <vespa/log/log.h>
#include <vespa/log/control-file.h>
LOG_SETUP("logdemon");
LOG_RCSID("$Id$");

#include "service.h"

namespace logdemon {

unsigned long Component::defFwd = (unsigned long)-1;


void
Component::doLogAtAll(LogLevel level)
{
    using ns_log::ControlFile;

    char lcfn[FILENAME_MAX];
    if (! ControlFile::makeName(_myservice, lcfn, FILENAME_MAX)) {
        LOG(debug, "no logcontrol file for service '%s'", _myservice);
        return;
    }
    try {
        ControlFile foo(lcfn, ControlFile::READWRITE);
        unsigned int *lstring = foo.getLevels(_logctlname);
        lstring[level] = CHARS_TO_UINT(' ', ' ', 'O', 'N');
    } catch (...) {
        LOG(debug, "exception changing logcontrol for %s", _myservice);
    }
}

void
Component::dontLogAtAll(LogLevel level)
{
    using ns_log::ControlFile;

    char lcfn[FILENAME_MAX];
    if (! ControlFile::makeName(_myservice, lcfn, FILENAME_MAX)) {
        LOG(debug, "no logcontrol file for service '%s'", _myservice);
        return;
    }
    try {
        ControlFile foo(lcfn, ControlFile::READWRITE);
        unsigned int *lstring = foo.getLevels(_logctlname);
        lstring[level] = CHARS_TO_UINT(' ', 'O', 'F', 'F');
    } catch (...) {
        LOG(debug, "exception changing logcontrol for %s", _myservice);
    }
}

bool
Component::shouldLogAtAll(LogLevel level)
{
    using ns_log::ControlFile;

    char lcfn[FILENAME_MAX];
    if (! ControlFile::makeName(_myservice, lcfn, FILENAME_MAX)) {
        LOG(spam, "no logcontrol file for service '%s'", _myservice);
        return true;
    }
    try {
        ControlFile foo(lcfn, ControlFile::READWRITE);
        unsigned int *lstring = foo.getLevels(_logctlname);
        if (lstring[level] == CHARS_TO_UINT(' ', ' ', 'O', 'N'))
            return true;
        else
            return false;
    } catch (...) {
        LOG(debug, "exception checking logcontrol for %s", _myservice);
    }
    return true;
}


Service::~Service()
{
    CompIter it = _components.iterator();
    while (it.valid()) {
        delete it.value();
        it.next();
    }
    free(_myname);
}

Services::~Services()
{
    ServIter it = _services.iterator();
    while (it.valid()) {
        delete it.value();
        it.next();
    }
}

void
Services::dumpState(int fildesc)
{
    using ns_log::Logger;

    ServIter sit = _services.iterator();
    while (sit.valid()) {
        Service *svc = sit.value();
        CompIter it = svc->_components.iterator();
        while (it.valid()) {
            Component *cmp = it.value();
            char buf[1024];
            int pos = snprintf(buf, 1024, "setstate %s %s ",
                               sit.key(), it.key());
            for (int i = 0; pos < 1000 && i < Logger::NUM_LOGLEVELS; i++) {
                LogLevel l = static_cast<LogLevel>(i);
                pos += snprintf(buf+pos, 1024-pos, "%s=%s,",
                                Logger::logLevelNames[i],
                                cmp->shouldForward(l) ? "forward" : "store");
            }
            if (pos < 1000) {
                buf[pos-1]='\n';
                write(fildesc, buf, pos);
            } else {
                LOG(warning, "buffer to small to dumpstate[%s, %s]",
                    sit.key(), it.key());
            }
            it.next();
        }
        sit.next();
    }
}


} // namespace
