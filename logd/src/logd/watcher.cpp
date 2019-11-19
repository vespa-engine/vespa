// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config_subscriber.h"
#include "exceptions.h"
#include "forwarder.h"
#include "watcher.h"
#include <vespa/log/log.h>
#include <vespa/vespalib/util/sig_catch.h>
#include <vespa/fastos/time.h>
#include <fcntl.h>
#include <glob.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <unistd.h>

LOG_SETUP("");

namespace logdemon {
namespace {

// wait until 1 second has passed since "start"
void snooze(const struct timeval &start)
{
    struct timeval sincestart;
    gettimeofday(&sincestart, 0);
    // compute time elapsed since start:
    sincestart.tv_sec  -= start.tv_sec;
    sincestart.tv_usec -= start.tv_usec;

    // how many microseconds to wait:
    long wait_usecs = (1000000 - sincestart.tv_usec);
    wait_usecs     -= (1000000 * sincestart.tv_sec);

    if (wait_usecs <= 0) {
        // already used enough time, no sleep
        return;
    }

    struct timespec tsp;
    tsp.tv_sec  = (wait_usecs / 1000000);
    tsp.tv_nsec = (wait_usecs % 1000000) * 1000;

    if (nanosleep(&tsp, nullptr) != 0 && errno != EINTR) {
        LOG(error, "nanosleep %ld s %ld ns failed: %s",
            (long)tsp.tv_sec, (long)tsp.tv_nsec, strerror(errno));
        throw SomethingBad("nanosleep failed");
    }
}

int elapsed(struct timeval &start) {
    struct timeval now;
    gettimeofday(&now, 0);
    int diffsecs = now.tv_sec - start.tv_sec;
    if (now.tv_usec < start.tv_usec) {
        --diffsecs;
    }
    return diffsecs;
}

constexpr size_t G_BUFSIZE = 1024*1024;
} // namespace logdemon::<unnamed>


Watcher::Watcher(ConfigSubscriber &cfs, Forwarder &fw)
    : _buffer(G_BUFSIZE),
      _confsubscriber(cfs),
      _forwarder(fw),
      _wfd(-1)
{
}

Watcher::~Watcher()
{
    if (_wfd >= 0) {
        LOG(debug, "~Watcher closing %d", _wfd);
        close(_wfd);
    }
}


struct donecache {
    donecache() : st_dev(0), st_ino(0), offset(0), valid(false) { memset(pad, 0, sizeof(pad)); }
    dev_t st_dev; /* device */
    ino_t st_ino; /* inode number */
    off_t offset;
    bool  valid;
    char pad[7];
};

class StateSaver {
public:
    StateSaver();
    ~StateSaver();
    void saveState(const donecache&);
    bool loadState(donecache&);
private:
    int _savefd;
};

void StateSaver::saveState(const donecache& already)
{
    if (_savefd < 0) {
        // cannot save state
        return;
    }
    off_t origin = 0;
    lseek(_savefd, origin, SEEK_SET);
    if (write(_savefd, &already, sizeof(already)) != sizeof(already)) {
        LOG(error, "error writing to donecachefile: %s", strerror(errno));
        close(_savefd);
        _savefd = -1;
    }
}

bool
StateSaver::loadState(donecache& already)
{
    if (_savefd >= 0 &&
        read(_savefd, &already, sizeof(already)) == sizeof(already))
    {
        return true;
    } else {
        return false;
    }
}

StateSaver::StateSaver() : _savefd(-1)
{
    _savefd = open("var/db/vespa/logd.donestate", O_RDWR|O_CREAT, 0664);
    if (_savefd < 0) {
        LOG(warning, "could not open var/db/vespa/logd.donestate: %s", strerror(errno));
    }
}

StateSaver::~StateSaver()
{
    LOG(debug, "~StateSaver closing %d", _savefd);
    if (_savefd >= 0) {
        close(_savefd);
    }
}
        
void
Watcher::watchfile()
{
    struct donecache already;

    char *target = getenv("VESPA_LOG_TARGET");
    if (target == nullptr || strncmp(target, "file:", 5) != 0) {
        LOG(error, "expected VESPA_LOG_TARGET (%s) to be a file: target", target);
        throw SomethingBad("bad log target");
    }
    const char *filename = target+5;

    if (strlen(filename) + 50 > FILENAME_MAX) {
        LOG(error, "too long filename '%s'", filename);
        throw SomethingBad("too long filename in watchfile");
    }

    StateSaver dcf;
    if (dcf.loadState(already)) {
        already.valid = true;
    }

    vespalib::SigCatch catcher;
    int sleepcount = 0;
    time_t created = 0;

 again:
    // XXX should close and/or check _wfd first ?
    _wfd = open(filename, O_RDONLY|O_CREAT, 0664);
    if (_wfd < 0) {
        LOG(error, "open(%s) failed: %s", filename, strerror(errno));
        throw SomethingBad("could not create or open logfile");
    }

    bool rotate = false;
    struct timeval rotStart;
    off_t offset = 0;

    while (1) {
        struct stat sb;
        if (fstat(_wfd, &sb) != 0) {
            LOG(error, "fstat(%s) failed: %s", filename, strerror(errno));
            throw SomethingBad("fstat failed");
        }
        if (created == 0) {
            created = sb.st_ctime;
        }
        if (already.valid) {
            if (sb.st_dev == already.st_dev &&
                sb.st_ino == already.st_ino &&
                sb.st_size >= already.offset)
            {
                offset = already.offset;
            }
            // only update offset from cache once:
            already.valid = false;
        }

        if (sb.st_size < offset) {
            // this is bad, maybe somebody else truncated the file
            LOG(error, "file mysteriously shrunk %d -> %d", (int)offset, (int)sb.st_size);
            return;
        }

        struct timeval tickStart;
        gettimeofday(&tickStart, 0);

        if (sb.st_size > offset) {
            lseek(_wfd, offset, SEEK_SET);
            char *buffer = getBuf();
            ssize_t rsize = read(_wfd, buffer, (getBufSize() - 1));
            if (rsize > 0) {
                buffer[rsize] = '\0';
                char *l = buffer;
                char *nnl = (char *)memchr(buffer, '\n', rsize);
                if (nnl == nullptr && rsize == (getBufSize() - 1)) {
                    LOG(error, "no newline in %ld bytes, skipping", static_cast<long>(rsize));
                    offset += rsize;
                }
                while (nnl != nullptr && elapsed(tickStart) < 1) {
                    ++nnl;
                    _forwarder.forwardLine(std::string_view(l, (nnl - l) - 1));
                    ssize_t wsize = nnl - l;
                    offset += wsize;
                    l = nnl;
                    nnl = strchr(l, '\n');
                }
            } else {
                LOG(error, "could not read from %s: %s", filename, strerror(errno));
                throw SomethingBad("read failed");
            }
        }

        already.offset = offset;
        already.st_dev = sb.st_dev;
        already.st_ino = sb.st_ino;

        time_t now = fastos::time();
        bool wantrotate = (now > created + _confsubscriber.getRotateAge())
                          || (sb.st_size > _confsubscriber.getRotateSize());

        if (rotate) {
            int rotTime = elapsed(rotStart);
            if (rotTime > 59 || (sb.st_size == offset && rotTime > 4)) {
                removeOldLogs(filename);
                if (sb.st_size != offset) {
                    LOG(warning, "logfile rotation incomplete after %d s (dropping %" PRIu64 " bytes)",
                        rotTime, static_cast<uint64_t>(sb.st_size - offset));
                } else {
                    LOG(debug, "logfile rotation complete after %d s", rotTime);
                }
                created = now;
                rotate = false;
                close(_wfd);
                goto again;
            }
        } else if (stat(filename, &sb) != 0
            || sb.st_dev != already.st_dev
            || sb.st_ino != already.st_ino)
        {
            LOG(warning, "logfile rotated away underneath");
            created = now;
            close(_wfd);
            goto again;
        } else if (wantrotate) {
            rotate = true;
            gettimeofday(&rotStart, 0);
            LOG(debug, "preparing to rotate logfile, old logfile size %d, age %d seconds",
                (int)offset, (int)(now-created));
            char newfn[FILENAME_MAX];
            int l = strlen(filename);
            strcpy(newfn, filename);
            struct tm *nowtm = gmtime(&now);
            if (strftime(newfn+l, FILENAME_MAX-l-1, "-%Y-%m-%d.%H-%M-%S", nowtm) < 10)
            {
                LOG(error, "could not strftime");
                throw SomethingBad("strftime failed");
            }

            if (rename(filename, newfn) != 0) {
                LOG(error, "could not rename logfile %s -> %s: %s", filename, newfn, strerror(errno));
                throw SomethingBad("rename failed");
            } else {
                LOG(debug, "old logfile name: %s", newfn);
            }
        }

        _forwarder.flush();
        dcf.saveState(already);

        if (_confsubscriber.checkAvailable()) {
            LOG(debug, "new config available, doing reconfigure");
            return;
        }

        if (catcher.receivedStopSignal()) {
            throw SigTermException("caught signal");
        }
        snooze(tickStart);
        if (catcher.receivedStopSignal()) {
            throw SigTermException("caught signal");
        }
        if (++sleepcount > 99) {
            if (_forwarder.badLines()) {
                LOG(info, "seen %d bad loglines in %d iterations", _forwarder.badLines(), sleepcount);
                _forwarder.resetBadLines();
                sleepcount=0;
            }
        }
    }
}

static int globerrfunc(const char *path, int errno_was)
{
    LOG(warning, "glob %s: %s", path, strerror(errno_was));
    return 0;
}

void
Watcher::removeOldLogs(const char *prefix)
{
    const char suffix[] = "-*-*-*.*-*-*";
    char pattern[FILENAME_MAX];
    int l = strlen(prefix) + sizeof(suffix) + 20;
    if (l > FILENAME_MAX) {
        LOG(error, "too long filename prefix in removeOldLog()");
        return;
    }

    strcpy(pattern, prefix);
    strcat(pattern, suffix);

    glob_t myglob;
    myglob.gl_pathc = 0;
    myglob.gl_offs = 0;
    myglob.gl_flags = 0;
    myglob.gl_pathv = nullptr;

    off_t totalsize = 0;

    int globresult = glob(pattern, 0, &globerrfunc, &myglob);
    if (globresult == 0) {
        for (int i = 0; i < (int)myglob.gl_pathc; i++) {
            const char *fname = myglob.gl_pathv[myglob.gl_pathc-i-1];

            struct stat sb;
            if (stat(fname, &sb) != 0) {
                LOG(warning, "cannot stat %s: %s", fname, strerror(errno));
                continue;
            }
            if (S_ISREG(sb.st_mode)) {
                if (sb.st_mtime +
                    _confsubscriber.getRemoveAge() * 86400 < fastos::time())
                {
                    LOG(info, "removing %s, too old (%f days)", fname,
                        (double)(fastos::time()-sb.st_mtime)/86400.0);

                    if (unlink(fname) != 0) {
                        LOG(warning, "cannot remove %s: %s",
                            fname, strerror(errno));
                    }
                    continue;
                }
                totalsize += sb.st_size;
                if (totalsize > (_confsubscriber.getRemoveMegabytes() * 1048576LL))
                {
                    LOG(info, "removing %s, total size (%" PRId64 ") too big", fname, static_cast<int64_t>(totalsize));
                    if (unlink(fname) != 0) {
                        LOG(warning, "cannot remove %s: %s", fname, strerror(errno));
                    }
                }
            } else {
                LOG(warning, "not a regular file: %s", fname);
            }
        }
    } else if (globresult == GLOB_NOMATCH) {
        LOG(info, "no old logfiles matching %s", pattern);
    } else {
        LOG(warning, "glob %s failed: %d", pattern, globresult);
    }
    globfree(&myglob);
}

} // namespace
