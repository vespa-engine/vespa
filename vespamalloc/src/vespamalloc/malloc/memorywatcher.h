// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdio.h>
#include <csignal>
#include <limits.h>
#include <sys/stat.h>
#include <ctype.h>
#include <fcntl.h>
#include <vespamalloc/malloc/malloc.h>
#include <vespamalloc/util/callstack.h>

namespace vespamalloc {


template <typename T, typename S>
class MemoryWatcher : public MemoryManager<T, S>
{
public:
    MemoryWatcher(int infoAtEnd, size_t prAllocAtStart) __attribute__((noinline));
    virtual ~MemoryWatcher() __attribute__((noinline));
private:
    void installMonitor();
    int     getDumpSignal() const { return _params[Params::dumpsignal].valueAsLong(); }
    static int getReconfigSignal() { return SIGHUP; }
    bool activateLogFile(const char *logfile);
    void activateOptions();
    void getOptions() __attribute__ ((noinline));
    void parseOptions(char * options) __attribute__ ((noinline));
    virtual void signalHandler(int signum, siginfo_t *sig, void * arg);
    static MemoryWatcher<T, S> * _manager;
    static void ssignalHandler(int signum, siginfo_t *info, void * arg);
    static MemoryWatcher<T, S> *manager() { return _manager; }
    bool signal(int signum) __attribute__ ((noinline));
    class NameValuePair {
    public:
        NameValuePair() : _valueName("") { _value[0] = '\0'; }
        NameValuePair(const char *vName, const char *v)
            : _valueName(vName)
        {
            value(v);
        }
        const char * valueName()   const { return _valueName; }
        const char * value()       const { return _value; }
        void value(const char * v) __attribute__((noinline));
        long valueAsLong()         const __attribute__((noinline)) { return strtol(_value, NULL, 0); }
        void info(FILE * os) __attribute__ ((noinline)) {
            fprintf(os, "%s = %s %ld", valueName(), value(), valueAsLong());
        }
    private:
        const char * _valueName;
        char         _value[256];
    };
    class Params {
    public:
        enum {
            alwaysreuselimit = 0,
            threadcachelimit,
            logfile,
            sigprof_loglevel,
            atend_loglevel,
            pralloc_loglimit,
            atnomem_loglevel,
            atdoubledelete_loglevel,
            atinvalid_loglevel,
            bigsegment_loglevel,
            bigsegment_limit,
            bigsegment_increment,
            allocs2show,
            bigblocklimit,
            fillvalue,
            dumpsignal,
            numberofentries  // Must be the last one
        };
        Params() __attribute__ ((noinline));
        ~Params() __attribute__ ((noinline));
        NameValuePair & operator[] (unsigned index)             { return _params[index]; }
        const NameValuePair & operator[] (unsigned index) const { return _params[index]; }
        bool update(const char *vName, const char *v) {
            int index(find(vName));
            if (index >= 0) {
                _params[index].value(v);
            }
            return (index >= 0);
        }
        bool getAsChar(const char *vName, const char * & v) {
            int index(find(vName));
            if (index >= 0) {
                v = _params[index].value();
            }
            return (index >= 0);
        }
        bool getAsLong(const char *vName, long & v) {
            int index(find(vName));
            if (index >= 0) {
                v = _params[index].valueAsLong();
            }
            return (index >= 0);
        }
        void info(FILE * os) {
            for (size_t i=0; i < NELEMS(_params); i++) {
                fprintf(os, "%2ld ", i);
                _params[i].info(os);
                fprintf(os, "\n");
            }
        }
    private:
        int find(const char *vName) __attribute__ ((noinline));
        NameValuePair _params[numberofentries];
    };
    FILE * _logFile;
    int    _infoAtAbort;
    int    _infoAtNOMEM;

    Params _params;
    struct sigaction _oldSig;
};

template <typename T, typename S>
MemoryWatcher<T, S>::Params::Params()
{
    _params[       alwaysreuselimit] = NameValuePair("alwaysreuselimit", "0x200000"); // 2M for allignment with hugepage size.
    _params[       threadcachelimit] = NameValuePair("threadcachelimit", "0x10000");  // 64K
    _params[                logfile] = NameValuePair("logfile", "stderr");
    _params[       sigprof_loglevel] = NameValuePair("sigprof_loglevel", "1");
    _params[         atend_loglevel] = NameValuePair("atend_loglevel", "1");
    _params[       pralloc_loglimit] = NameValuePair("pralloc_loglimit", "0x2000000");
    _params[       atnomem_loglevel] = NameValuePair("atnomem_loglevel", "1");
    _params[atdoubledelete_loglevel] = NameValuePair("atdoubledelete_loglevel", "1");
    _params[     atinvalid_loglevel] = NameValuePair("atinvalid_loglevel", "1");
    _params[    bigsegment_loglevel] = NameValuePair("bigsegment_loglevel", "1");
    _params[       bigsegment_limit] = NameValuePair("bigsegment_limit", "0x1000000000");  // 64GM
    _params[   bigsegment_increment] = NameValuePair("bigsegment_increment", "0x100000000"); //4GM
    _params[            allocs2show] = NameValuePair("allocs2show", "8");
    _params[          bigblocklimit] = NameValuePair("bigblocklimit", "0x80000000"); // 8M
    _params[              fillvalue] = NameValuePair("fillvalue", "0xa8"); // Means NO fill.
    _params[             dumpsignal] = NameValuePair("dumpsignal", "27"); // SIGPROF
}

template <typename T, typename S>
MemoryWatcher<T, S>::Params::~Params()
{
}

template <typename T, typename S>
int MemoryWatcher<T, S>::Params::find(const char *vName)
{
    int index(-1);
    for (size_t i=0; (index < 0) && (i < NELEMS(_params)); i++) {
        if (strcmp(vName, _params[i].valueName()) == 0) {
            index = i;
        }
    }
    return index;
}

template <typename T, typename S>
void MemoryWatcher<T, S>::NameValuePair::value(const char * v) {
    strncpy(_value, v, sizeof(_value)-1);
    _value[sizeof(_value)-1] = '\0';
}

template <typename T, typename S>
MemoryWatcher<T, S>::MemoryWatcher(int infoAtEnd, size_t prAllocAtStart) :
    MemoryManager<T, S>(prAllocAtStart),
    _logFile(stderr),
    _infoAtAbort(-1),
    _infoAtNOMEM(1)
{
    _manager = this;
    char tmp[16];
    sprintf(tmp, "%d", infoAtEnd);
    _params[Params::atend_loglevel].value(tmp);
    installMonitor();
}

template <typename T, typename S>
void MemoryWatcher<T, S>::installMonitor()
{
    getOptions();

    signal(getDumpSignal());
    signal(getReconfigSignal());
}

template <typename T, typename S>
bool MemoryWatcher<T, S>::activateLogFile(const char *logfile)
{
    FILE * oldFp(_logFile);
    if (strcmp(logfile, "stderr") == 0) {
        _logFile = stderr;
    } else if (strcmp(logfile, "stdout") == 0) {
        _logFile = stdout;
    } else {
        char logFileName[1024];
        snprintf(logFileName, sizeof(logFileName), "%s.%d", logfile, getpid());
        _logFile = fopen(logFileName, "a");
    }
    if ((oldFp != stderr) && (oldFp != stdout)) {
        fclose(oldFp);
    }
    return (_logFile != NULL);
}

template <typename T, typename S>
void MemoryWatcher<T, S>::activateOptions()
{
    activateLogFile(_params[Params::logfile].value());
    T::dumpFile(_logFile);
    this->setupSegmentLog(_params[Params::atnomem_loglevel].valueAsLong(),
                    _params[Params::bigsegment_loglevel].valueAsLong(),
                    _params[Params::bigsegment_limit].valueAsLong(),
                    _params[Params::bigsegment_increment].valueAsLong(),
                    _params[Params::allocs2show].valueAsLong());
    this->setupLog(_params[Params::atdoubledelete_loglevel].valueAsLong(),
                   _params[Params::atinvalid_loglevel].valueAsLong(),
                   _params[Params::pralloc_loglimit].valueAsLong());
    this->setParams(_params[Params::alwaysreuselimit].valueAsLong(),
                    _params[Params::threadcachelimit].valueAsLong());
    T::bigBlockLimit(_params[Params::bigblocklimit].valueAsLong());
    T::setFill(_params[Params::fillvalue].valueAsLong());

}

namespace {

const char *vespaHomeConf(char pathName[])
{
    const char *home = "/opt/vespa";
    const char *conf = "/etc/vespamalloc.conf";
    const char *env = getenv("VESPA_HOME");
    if (env != NULL) {
        home = env;
    }
    assert((strlen(home)  + strlen(conf) + 1) < PATH_MAX);
    strcpy(pathName, home);
    strcat(pathName, conf);
    pathName[PATH_MAX - 1] = '\0';
    return pathName;
}

} // namespace <unnamed>

template <typename T, typename S>
void MemoryWatcher<T, S>::getOptions()
{
    char homeConf[PATH_MAX];
    const char * searchOrder[3] = {
        "vespamalloc.conf",
        vespaHomeConf(homeConf),
        "/etc/vespamalloc.conf"
    };
    struct stat st;
    int retval(-1);
    unsigned index(0);
    for (unsigned i=0; (retval == -1) && (i < NELEMS(searchOrder)); i++) {
        retval = stat(searchOrder[i], & st);
        index = i;
    }
    if (retval == 0) {
        int fd = open(searchOrder[index], O_RDONLY);
        char buffer[4096];
        assert(st.st_size+1 < int(sizeof(buffer)));
        retval = read(fd, buffer, st.st_size);
        if (retval == st.st_size) {
            buffer[st.st_size] = 0;
            parseOptions(buffer);
            activateOptions();
        }
        close (fd);
    }
}

template <typename T, typename S>
void MemoryWatcher<T, S>::parseOptions(char * options)
{
    bool isComment(false);
    const char ignore('\0');
    const char *valueName(NULL);
    const char *value(NULL);
    bool isWhite(true);
    for(char *p=options; *p; p++) {
        char c(*p);
        if (c == '\n') {
            if ((valueName != NULL) && (value != NULL)) {
                if (_params.update(valueName, value) == false) {
                    fprintf(stderr, "Invalid parameter %s", valueName);
                }
            }
            isComment = false;
            isWhite = true;
            valueName = NULL;
            value = NULL;
        } else if (isComment) {
            *p = ignore;
        } else if (c == '#') {
            isComment = true;
            *p = ignore;
        } else {
            if (isWhite) {
                if (!isspace(c)) {
                    if (valueName == NULL) {
                        valueName = p;
                    } else {
                        value = p;
                    }
                    isWhite = false;
                } else {
                    *p = ignore;
                }
            } else {
                if (isspace(c)) {
                    isWhite = true;
                    *p = ignore;
                }
            }
        }
    }
}

template <typename T, typename S>
MemoryWatcher<T, S>::~MemoryWatcher() {
    int infoAtEnd(_params[Params::atend_loglevel].valueAsLong());
    if (infoAtEnd >= 0) {
        this->info(_logFile, infoAtEnd);
    }
    fclose(_logFile);
}

template <typename T, typename S>
void MemoryWatcher<T, S>::signalHandler(int signum, siginfo_t * sig, void *  arg)
{
    if (_params[Params::sigprof_loglevel].valueAsLong() > 1) {
        fprintf(_logFile, "SignalHandler %d caught\n", signum);
    }
    if (signum == getDumpSignal()) {
        this->info(_logFile, _params[Params::sigprof_loglevel].valueAsLong());
    } else if (signum == getReconfigSignal()) {
        getOptions();
        if (_params[Params::sigprof_loglevel].valueAsLong() > 1) {
            _params.info(_logFile);
        }
    }
    if (_params[Params::sigprof_loglevel].valueAsLong() > 1) {
        fprintf(_logFile, "SignalHandler %d done\n", signum);
    }
    if ((_oldSig.sa_handler != SIG_IGN) && (_oldSig.sa_handler != SIG_DFL) && (_oldSig.sa_handler != NULL)) {
        (_oldSig.sa_sigaction)(signum, sig, arg);
    }
}

template <typename T, typename S>
void MemoryWatcher<T, S>::ssignalHandler(int signum, siginfo_t *info, void * arg)
{
    if (_manager) {
        _manager->signalHandler(signum, info, arg);
    } else {
        fprintf(stderr, "Manager not initialized when signal arrives");
    }
}

template <typename T, typename S>
bool MemoryWatcher<T, S>::signal(int signum)
{
    bool retval(true);
    struct sigaction sig;
    sig.sa_sigaction = ssignalHandler;
    sigemptyset(& sig.sa_mask);
    sig.sa_flags = SA_SIGINFO;
    if (!(retval = (sigaction(signum, &sig, &_oldSig) == 0))) {
        fprintf(stderr, "Signal handler for %d FAILED to install!\n", signum);
    }
    return retval;
}

template <typename T, typename S>
MemoryWatcher<T, S> * MemoryWatcher<T, S>::_manager = NULL;

} // namespace vespamalloc

