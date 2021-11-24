// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <cstdlib>
#include <cstring>
#include <ctype.h>
#include <cstdio>
#include <sys/mman.h>
#include <errno.h>
#include <unistd.h>
#include <memory>

#include "log.h"
LOG_SETUP_INDIRECT(".log.control", "$Id$");
#undef LOG
#define LOG LOG_INDIRECT

#include "control-file.h"
#include "internal.h"
#include "component.h"

namespace ns_log {

ControlFile::ControlFile(const char *file, Mode mode)
    : _fileBacking(file,
                   O_NOCTTY | (  (mode == READONLY) ? O_RDONLY
                               : (mode == READWRITE) ? O_RDWR
                               : (O_RDWR | O_CREAT))),
      _fileSize(0),
      _mode(mode),
      _fileName(file),
      _prefix(0),
      _mapBase(0),
      _mappedSize(0),
      _firstComponent(0)
{
    _fileBacking.lock(mode != READONLY);
    ensureHeader(); // Create the header if it doesn't exist
    ensureMapping(); // mmap the file
    _fileBacking.unlock();
}

ControlFile::~ControlFile()
{
    freeMapping();
}

void
ControlFile::ensureHeader()
{
    // Make sure the file has a valid header. If it doesn't, or is
    // empty, truncate it and write a new fresh header.
    static const char fileHeader[] = "Vespa log control file version 1\n";
    const int fd = _fileBacking.fd();
    char buf[sizeof fileHeader];

    int wantsLen = strlen(fileHeader);
    int len = read(fd, &buf, wantsLen);
    if (len != wantsLen || memcmp(fileHeader, buf, wantsLen) != 0) {
        if (ftruncate(fd, 0) != 0) {
            perror("log::ControlFile ftruncate failed");
        }
        lseek(fd, 0, SEEK_SET);
        ssize_t nbw = write(fd, fileHeader, wantsLen);
        if (nbw != wantsLen) {
            perror("log::ControlFile write(A) failed");
        }

        char spaces[_maxPrefix + 3];
        memset(spaces, ' ', sizeof spaces);
        spaces[sizeof(spaces) - 1] = '\0';

        char buf2[sizeof(spaces) + 100];
        snprintf(buf2, sizeof buf2, "Prefix: \n%s\n", spaces);
        wantsLen = strlen(buf2);
        nbw = write(fd, buf2, wantsLen);
        if (nbw != wantsLen) {
            perror("log::ControlFile write(B) failed");
        }

    }
}

void
ControlFile::flush()
{
    if (_mapBase != NULL) {
        if (msync(_mapBase, 0, MS_SYNC) != 0) {
            LOG(warning, "msync of log control file failed: %s",
                strerror(errno));
        }
    }
}

void
ControlFile::ensureMapping()
{
    // If the file is not mmaped yet, first do a huge anonymous mmap
    // so that we never have to change the address, then mmap the
    // start of the file.
    if (!_mapBase) {
        int prot = PROT_READ;
        int flags = MAP_PRIVATE | MAP_ANON;
        int fd = -1;
        size_t length = pageAlign(_maxMapSize + 1);

        void *addr = mmap(NULL, length, prot, flags, fd, 0);
        if (!addr) {
            throwInvalid("Failed to get anonymous memory for control file: %s",
                         strerror(errno));
        }
        _mapBase = static_cast<char *>(addr);
        extendMapping();

        char *s = strstr(_mapBase, "Prefix: ");
        if (!s) {
            throwInvalid("Bad format of mapped file. bleh.");
        }
        _prefix = s + strlen("Prefix: ");
        _firstComponent = _mapBase + _maxPrefix + 25;
        _firstComponent = strchr(_firstComponent, '\n') + 1;
    }
}

void
ControlFile::freeMapping()
{
    // If the file is mmapped, release all resource used
    if (_mapBase && (munmap(_mapBase, pageAlign(_maxMapSize + 1)) < 0)) {
        LOG(warning, "munmapping of loglevel settings failed: %s",
            strerror(errno));
    }
    _mapBase = NULL;
}

unsigned int
ControlFile::pageAlign(unsigned int len)
{
    static const int pageMask = getpagesize() - 1;
    return (len + pageMask) & ~pageMask;
}

char *
ControlFile::nextNewline(char *addr)
{
    if (addr < _mapBase) return NULL;
    char *end = _mapBase + _fileSize;
    while (addr < end) {
        if (*addr == '\n') return addr;
        ++addr;
    }
    return NULL;
}

char *
ControlFile::alignLevels(char *addr)
{
    unsigned long x = reinterpret_cast<unsigned long>(addr);
    x = (x + 3) & ~3;
    return reinterpret_cast<char *>(x);
}

bool
ControlFile::extendMapping()
{
    int fileLen = _fileBacking.size();

    if (fileLen == -1) {
        _fileBacking.unlock();
        LOG(error, "Cannot get file size of '%s': %s", _fileName.c_str(),
            strerror(errno));
        return false;
    }

    if (fileLen >= _maxMapSize) {
        _fileBacking.unlock();
        LOG(error, "Log control file is too big at %d bytes (max "
            "size is %d). Ignoring it for further log components.",
            fileLen, _maxMapSize - 1);
        return false;
    }

    off_t size = pageAlign(fileLen);

    int prot = PROT_READ | (_mode == READONLY ? 0 : PROT_WRITE);
    int flags = MAP_FIXED | MAP_SHARED;
    int fd = _fileBacking.fd();
    if (mmap(_mapBase, size, prot, flags, fd, 0) != _mapBase) {
        _fileBacking.unlock();
        _mappedSize = -1;
        LOG(error, "failed to mmap lock file: %s", strerror(errno));
        return false;
    }
    _mappedSize = size;
    _fileSize = fileLen;

    return true;
}

void
ControlFile::setPrefix(const char *prefix)
{
    if (prefix && !hasPrefix() && _prefix) {
        char buf[_maxPrefix + 1];
        sprintf(buf, "%.*s\n", _maxPrefix - 1, prefix);
        memcpy(_prefix, buf, strlen(buf));
        msync(_mapBase, pageAlign(1), MS_ASYNC | MS_INVALIDATE);
    }
}


unsigned int *
ControlFile::getLevels(const char *name)
{
    // these comments are all wrong...

    // See if there already is some info for name. If not,
    // lock the file, stat it, remap if necessary, scan again,
    // and if still not there, append to it. Return pointer to mapped area.

    // ### call getComponent() instead,
    // ### if it does not exist, create it and return the thing
    // ### get default value from pre-existing levels, or from
    // ### the default level if nothing exists.
    // ### (if default level does not exist, create it?)

    _fileBacking.lock(_mode != READONLY);
    static const char *padSpaces = "   "; // 3 spaces
    char buf[2000];
    if (strcmp(name, "") == 0) {
        name = "default";
    }
    // Leave space for 3 spaces and a full levels string (200 bytes should
    // be enough)
    snprintf(buf, sizeof buf - 200, "\n%s: ", name);

    char *levels = strstr(_mapBase, buf);
    if (levels) {
        _fileBacking.unlock();
        char *addr = levels + strlen(buf);
        addr = alignLevels(addr);
        return reinterpret_cast<unsigned int *>(addr);
    }

    char *inheritLevels =  reinterpret_cast<char *>(defaultLevels());
    const char *chop = strrchr(name, '.');
    if (chop != NULL) {
        char shorterName[2000];
        strncpy(shorterName, name, chop - name);
        shorterName[chop-name] = '\0';
        unsigned int *inherit = getLevels(shorterName);
        if (inherit != NULL) {
            inheritLevels =  reinterpret_cast<char *>(inherit);
        }
    }

    //  Append whatever is in buf, excluding the initial newline, and
    //  up to 3 more spaces to get the entire file length to be aligned.
    int oldFileLength = _fileBacking.size();
    char *appendedString = buf + 1;
    int newLength = oldFileLength + strlen(appendedString);
    unsigned int padding = static_cast<unsigned int>(-newLength) & 3u;
    strcat(appendedString, &padSpaces[3 - padding]);
    int prefix_len = strlen(appendedString);

#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wstringop-truncation"
#endif
    strncat(appendedString, inheritLevels, Logger::NUM_LOGLEVELS*sizeof(int));
#pragma GCC diagnostic pop
    strcat(appendedString, "\n");

    int len = strlen(appendedString);
    int fd = open(_fileName.c_str(), O_WRONLY | O_APPEND);
    int wlen = write(fd, appendedString, len);
    oldFileLength = lseek(fd, (off_t)0, SEEK_CUR) - wlen;
    close(fd);
    if (wlen != len) {
        _fileBacking.unlock();
        LOG(error, "Writing to control file '%s' fails (%d/%d bytes): %s",
            _fileName.c_str(), wlen, len, strerror(errno));
        return reinterpret_cast<unsigned int *>(inheritLevels);
    } else {
        _fileSize = _fileBacking.size();
    }

    if (_fileSize > _mappedSize) {
        if (!extendMapping()) {
            _fileBacking.unlock(); // just for sure
            LOG(error, "Failed to extend mapping of '%s', losing runtime "
                "configurability of component '%s'", _fileName.c_str(), name);
            return defaultLevels();
        }
    }
    char *baseAddr = _mapBase + oldFileLength + prefix_len;
    _fileBacking.unlock();
    return reinterpret_cast<unsigned int *>(baseAddr);
}


unsigned int *
ControlFile::defaultLevels()
{
    static unsigned int levels[Logger::NUM_LOGLEVELS + 1];
    if (levels[0] == 0) {
        const char *env = getenv("VESPA_LOG_LEVEL");
        if (!env) {
            env = "all -debug -spam";
        }
        makeLogLevelArray(levels, sizeof levels, env);
        levels[Logger::NUM_LOGLEVELS] = 0;
    }
    return levels;
}


unsigned int
ControlFile::findOnOffStatus(Logger::LogLevel level, const char *levelsString)
{
    const char *name = Logger::levelName(level);
    if (hasWord(name, levelsString)
        || (!hasNegWord(name, levelsString)
            && hasWord("all", levelsString))) {
        return CHARS_TO_UINT(' ', ' ', 'O', 'N');
    } else {
        return CHARS_TO_UINT(' ', 'O', 'F', 'F');
    }
}

void
ControlFile::makeLogLevelArray(unsigned int *levels, unsigned int size,
                               const char *env)
{
    int n;
    for (n = 0; n < Logger::NUM_LOGLEVELS
             && (n * sizeof levels[0] < size); ++n) {
        levels[n] = findOnOffStatus(static_cast<Logger::LogLevel>(n), env);
    }
}

bool
ControlFile::hasWord(const char *word, const char *haystack)
{
    int len = strlen(word);
    const char *start = strstr(haystack, word);
    const char *end = start + len;
    if (!start)
        return false;

    return ((start == haystack) || isspace(start[-1]))
                    && (!*end || isspace(*end));
}

bool
ControlFile::hasNegWord(const char *word, const char *haystack)
{
    int len = strlen(word);
    const char *start = strstr(haystack, word);
    const char *end = start + len;
    if (!start || start == haystack)
        return false;
    return start[-1] == '-' && (!*end || isspace(*end));
}

void
ControlFile::ensureComponent(const char *pattern)
{
    // Make sure at least one entry exists matching a pattern, if not,
    // create it. Wildcard patterns cannot be created though!
    ComponentIterator iter(getComponentIterator());
    bool wasSeen = false;
    Component *c;
    while ((c = iter.next()) != NULL) {
        std::unique_ptr<Component> component(c);
        if (c->matches(pattern)) {
            wasSeen = true;
            break;
        }
    }
    if (!wasSeen) {
        (void) getLevels(pattern); // Creates it ((### ugly ###))
    }
}

bool
ControlFile::makeName(const char *service, char *buf, int bufLen)
{
    static const char *file = getenv("VESPA_LOG_CONTROL_FILE");
    static const char *dir = getenv("VESPA_LOG_CONTROL_DIR");

    bool result;

    if (file) {
        // mostly used for manual testing, so allow this even
        // with empty service name
        result = (snprintf(buf, bufLen, "%s", file) < bufLen);
    } else if (dir) {
        // We can't make control files for empty services here
        if (!*service || strcmp(service, "-") == 0) {
            return false;
        }
        if (strchr(service, '/') != NULL) {
            LOG(debug, "service name '%s' should not contain /", service);
            return false;
        }
        int req = snprintf(buf, bufLen, "%s/%s.logcontrol", dir, service);
        result = (req < bufLen);
    } else {
        result = false;
    }
    return result;
}



ComponentIterator
ControlFile::getComponentIterator()
{
    return ComponentIterator(this);
}

ComponentIterator::ComponentIterator(const ComponentIterator& ci)
    : _cf(ci._cf),
      _next(ci._next)
{
}

ComponentIterator::ComponentIterator(ControlFile *cf)
    : _cf(cf),
      _next(cf->_firstComponent)
{
}

Component *
ComponentIterator::next()
{
    Component *ret = NULL;
    if (_next) {
        char *nn = _cf->nextNewline(_next);
        if (nn) {
            ret = new Component(_next);
            if (nn == ret->endPointer()) {
                _next = nn + 1;
            } else {
                LOG(warning, "mismatch between component size and line size, aborting ComponentIterator loop");
                delete ret;
                ret = NULL;
                _next = NULL;
            }
        } else {
            _next = NULL;
        }
    }
    return ret;
}

} // end namespace ns_log
