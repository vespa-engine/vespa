// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

/* This is a virtual base class for all log targets.
   A log target has a constructor with the log target in string form,
   and a write() method to write log messages. that's it. */

namespace ns_log {

class LogTarget {
private:
    char _name[256];
    LogTarget();
    LogTarget& operator =(const LogTarget &);
    LogTarget(const LogTarget&);

public:
    LogTarget(const char *name);
    virtual int write(const char *buf, int bufLen) = 0;
    virtual ~LogTarget();
    static LogTarget *makeTarget(const char *target);
    static LogTarget *defaultTarget();
    virtual const char *name() const { return _name; }
    virtual bool makeHumanReadable() const { return false; }
};

} // end namespace ns_log

