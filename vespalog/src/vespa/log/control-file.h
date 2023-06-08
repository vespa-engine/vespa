// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "log.h"
#include "lock.h"
#include <string>

namespace ns_log {

class Component;
class ComponentIterator;

class ControlFile {
    friend class ComponentIterator;
    friend class Component;
public:
    enum Mode { READONLY, READWRITE, CREATE };
private:
    Lock _fileBacking;
    int _fileSize;
    enum Mode _mode;
    std::string _fileName;
    void ensureHeader();
    bool hasPrefix() { return (_prefix != NULL &&
                               _prefix[0] != '\0' &&
                               _prefix[0] != ' ' &&
                               _prefix[0] != '\n'); }
    void ensureMapping();
    void freeMapping();
    bool extendMapping();
    static unsigned int pageAlign(unsigned int len);

    char *_prefix;
    char *_mapBase;
    int _mappedSize;
    char *_firstComponent;

    char *nextNewline(char *p);

    static const int _maxMapSize = 200000;
    static const int _maxPrefix = 64;

    ControlFile(const ControlFile &);
    ControlFile& operator = (const ControlFile &);

    static bool hasWord(const char *word, const char *haystack);
    static bool hasNegWord(const char *word, const char *haystack);

    static void makeLogLevelArray(unsigned int *levels, unsigned int size,
                                  const char *env);
    static char *alignLevels(char *addr);
    static unsigned int findOnOffStatus(Logger::LogLevel level,
                                        const char *levelsString);

public:
    ComponentIterator getComponentIterator();
    explicit ControlFile(const char *file, enum Mode);
    void setPrefix(const char *prefix);
    ~ControlFile();
    unsigned int *getLevels(const char *name);
    void ensureComponent(const char *pattern);

    static unsigned int *defaultLevels() __attribute__((noinline));

    // make sure in-memory changes are synchronized to disk
    void flush();

    // Construct the name of the control file for <serviceName>
    // returns true if successful, false if not.
    // Makes the name into <buf> which is of length <bufLen>.
    static bool makeName(const char *serviceName, char *buf, int bufLen);
};

class ComponentIterator {
private:
    ControlFile *_cf;
    char *_next;

    ComponentIterator& operator = (const ComponentIterator &);

public:
    ComponentIterator(const ComponentIterator &ci);
    ComponentIterator(ControlFile *cf);
    ~ComponentIterator() {}
    Component *next();
};

} // end namespace ns_log

