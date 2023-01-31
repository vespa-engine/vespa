// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <fcntl.h>
#include <cerrno>
#include <unistd.h>
#include <csignal>

#include <sys/select.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/resource.h>
#include <sys/file.h>

#include <vespa/defaults.h>
#include <vespa/log/llparser.h>
#include "llreader.h"
#include <vespa/log/log.h>
#include <chrono>

LOG_SETUP("runserver");

// XXX should close all file descriptors,
// XXX but that makes logging go haywire

static volatile sig_atomic_t gotstopsig = 0;
static volatile sig_atomic_t lastsig = 0;
static volatile sig_atomic_t unhandledsig = 0;

extern "C" {
void termsig(int sig) {
    lastsig = sig;
    gotstopsig = 1;
    unhandledsig = 1;
}
}

namespace {

using namespace std::chrono;

time_t steady_time() {
    return duration_cast<seconds>(steady_clock::now().time_since_epoch()).count();
}

bool whole_seconds(int cnt, int secs) {
    cnt %= (secs * 10);
    return cnt == 0;
}

} // namespace

class PidFile
{
private:
    std::string _pidfile;
    int _fd;
    PidFile(const PidFile&);
    PidFile& operator= (const PidFile&);
public:
    PidFile(const char *pidfile) : _pidfile(pidfile), _fd(-1) {}
    ~PidFile() { if (_fd >= 0) close(_fd); }
    int readPid();
    void writePid();
    bool writeOpen();
    bool anotherRunning();
    bool canStealLock();
    void cleanUp();
};

void
PidFile::cleanUp()
{
    if (!anotherRunning()) remove(_pidfile.c_str());
    if (_fd >= 0) close(_fd);
    _fd = -1;
}

bool
PidFile::writeOpen()
{
    if (_fd >= 0) close(_fd);
    int flags = O_CREAT | O_WRONLY | O_NONBLOCK;
    _fd = open(_pidfile.c_str(), flags, 0644);
    if (_fd < 0) {
        fprintf(stderr, "could not create pidfile %s: %s\n", _pidfile.c_str(),
                strerror(errno));
        return false;
    }
    if (flock(_fd, LOCK_EX | LOCK_NB) != 0) {
        fprintf(stderr, "could not lock pidfile %s: %s\n", _pidfile.c_str(),
                strerror(errno));
        close(_fd);
        _fd = -1;
        return false;
    }
    fcntl(_fd, F_SETFD, FD_CLOEXEC);
    return true;
}

void
PidFile::writePid()
{
    if (_fd < 0) abort();
    int didtruncate = ftruncate(_fd, (off_t)0);
    if (didtruncate != 0) {
        fprintf(stderr, "could not truncate pid file %s: %s\n",
                _pidfile.c_str(), strerror(errno));
        std::_Exit(1);
    }
    char buf[100];
    snprintf(buf, sizeof(buf), "%d\n", getpid());
    int l = strlen(buf);
    ssize_t didw = write(_fd, buf, l);
    if (didw != l) {
        fprintf(stderr, "could not write pid to %s: %s\n",
                _pidfile.c_str(), strerror(errno));
        std::_Exit(1);
    }
    LOG(debug, "wrote '%s' to %s (fd %d)", buf, _pidfile.c_str(), _fd);
}

int
PidFile::readPid()
{
    FILE *pf = fopen(_pidfile.c_str(), "r");
    if (pf == NULL) return 0;
    char buf[100];
    strcpy(buf, "0");
    char *fgetsres = fgets(buf, 100, pf);
    fclose(pf);
    return ((fgetsres != nullptr) ? atoi(buf) : 0);
}

bool
PidFile::anotherRunning()
{
    int pid = readPid();
    if (pid < 1 || pid == getpid()) {
        // no valid pid, or my own pid
        return false;
    }
    if (canStealLock()) {
        return false;
    }
    return (kill(pid, 0) == 0 || errno == EPERM);
}

bool
PidFile::canStealLock()
{
    int flags = O_WRONLY | O_NONBLOCK;
    int desc = open(_pidfile.c_str(), flags, 0644);
    if (desc < 0) {
        return false;
    }
    if (flock(desc, LOCK_EX | LOCK_NB) != 0) {
        close(desc);
        return false;
    }
    flock(desc, LOCK_UN | LOCK_NB);
    close(desc);
    return true;
}

using namespace ns_log;

int loop(const char *svc, char * const * run)
{
    int pstdout[2];
    int pstderr[2];

    if (pipe(pstdout) < 0 || pipe(pstderr) < 0) {
        LOG(error, "pipe: %s", strerror(errno));
        std::_Exit(1);
    }
    LOG(debug, "stdout pipe %d <- %d; stderr pipe %d <- %d",
        pstdout[0], pstdout[1],
        pstderr[0], pstderr[1]);

    int high = 1 + pstdout[0] + pstderr[0];

    pid_t child = fork();

    if (child == 0) {
        // I am the child process
        dup2(pstdout[1], 1);
        dup2(pstderr[1], 2);
        close(pstdout[0]);
        close(pstderr[0]);
        close(pstdout[1]);
        close(pstderr[1]);
        execvp(run[0], run);
        LOG(error, "exec %s: %s", run[0], strerror(errno));
        std::_Exit(1);
    }
    if (child < 0) {
        LOG(error, "fork(): %s", strerror(errno));
        std::_Exit(1);
    }
    // I am the parent process

    LOG(debug, "started %s (pid %d)", run[0], (int)child);
    std::string torun = run[0];
    for (char * const *arg = (run + 1); *arg != NULL; ++arg) {
        torun += " ";
        torun += *arg;
    }

    {
        torun += " (pid ";
        char buf[20];
        snprintf(buf, sizeof(buf), "%d", (int)child);
        torun += buf;
        torun += ")";
    }
    EV_STARTING(torun.c_str());

    close(pstdout[1]);
    close(pstderr[1]);

    LLParser outvia;
    LLParser errvia;
    outvia.setDefaultLevel(Logger::info);
    errvia.setDefaultLevel(Logger::warning);

    outvia.setService(svc);
    errvia.setService(svc);
    outvia.setComponent("stdout");
    errvia.setComponent("stderr");
    outvia.setPid(child);
    errvia.setPid(child);

    InputBuf outReader(pstdout[0]);
    InputBuf errReader(pstderr[0]);

    bool outeof = false;
    bool erreof = false;

    int wstat = 0;

    while (child || !outeof || !erreof) {
        struct timeval timeout;

        timeout.tv_sec = 0;
        timeout.tv_usec = 100000; // == 100 ms == 1/10 s

        fd_set pipes;

        FD_ZERO(&pipes);
        if (!outeof) FD_SET(pstdout[0], &pipes);
        if (!erreof) FD_SET(pstderr[0], &pipes);

        int n = select(high, &pipes, NULL, NULL, &timeout);
        if (n > 0) {
            if (FD_ISSET(pstdout[0], &pipes)) {
                LOG(debug, "out reader has input");
                if (outReader.blockRead()) {
                    while (outReader.hasInput()) {
                        LOG(debug, "process out reader input");
                        outReader.doInput(outvia);
                    }
                } else {
                    LOG(debug, "eof on stdout");
                    outeof = true; // EOF on stdout
                    close(pstdout[0]);
                }
            }
            if (FD_ISSET(pstderr[0], &pipes)) {
                LOG(debug, "err reader has input");
                if (errReader.blockRead()) {
                    while (errReader.hasInput()) {
                        LOG(debug, "process err reader input");
                        errReader.doInput(errvia);
                    }
                } else {
                    LOG(debug, "eof on stderr");
                    erreof = true; // EOF on stderr
                    close(pstderr[0]);
                }
            }
        }

        if (child != 0) {
            int cpid = waitpid(child, &wstat, WNOHANG);
            if (cpid == child) {
                if (WIFSTOPPED(wstat)) {
                    LOG(info, "child %d stopped, waiting for it to continue",
                        cpid);
                } else if (WIFEXITED(wstat)) {
                    // child terminated
                    LOG(debug, "child %d exit status: %d", cpid,
                        (int)WEXITSTATUS(wstat));
                    EV_STOPPED(torun.c_str(), (int)child, (int)WEXITSTATUS(wstat));
                    child = 0;
                } else if (WIFSIGNALED(wstat)) {
                    if (WTERMSIG(wstat) != lastsig) {
                        LOG(warning, "child died from signal: %d", WTERMSIG(wstat));
                        if (WCOREDUMP(wstat)) {
                            LOG(info, "child %d dumped core", cpid);
                        }
                    }
                    child = 0;
                } else {
                    LOG(error, "unexpected status %d from waidpit", wstat);
                    abort();
                }
            } else if (cpid < 0) {
                LOG(error, "waitpid: %s", strerror(errno));
                abort();
            } else if (cpid != 0) {
                LOG(warning, "unexpected status %d for pid %d",
                    wstat, cpid);
                abort();
            }
        }
        if (unhandledsig && child != 0) {
            LOG(debug, "got signal %d, sending to pid %d",
                (int)lastsig, (int)child);
            char why[32];
            snprintf(why, sizeof(why), "got signal %d", (int)lastsig);
            EV_STOPPING(torun.c_str(), why);
            kill(child, lastsig);
            unhandledsig = 0;
        }
    }
    if (WIFSIGNALED(wstat))
        return WTERMSIG(wstat);
    return WEXITSTATUS(wstat);
}

int usage(char *prog, int es)
{
    fprintf(stderr, "Usage: %s\n"
            "       [-s service] [-r restartinterval] [-p pidfile]"
            " program [args ...]\n"
            "or:    [-p pidfile] [-k killcmd] -S\n", prog);
    return es;
}

int main(int argc, char *argv[])
{
    bool doStop = false;
    bool checkWouldRun = false;
    int restart = 0;
    const char *service = "runserver";
    const char *pidfile = "vespa-runserver.pid"; // XXX bad default?
    const char *killcmd = NULL;

    signal(SIGQUIT, SIG_IGN);

    int ch;
    while ((ch = getopt(argc, argv, "k:s:r:p:ShW")) != -1) {
        switch (ch) {
        case 's':
            service = optarg;
            break;
        case 'r':
            restart = atoi(optarg);
            break;
        case 'p':
            pidfile = optarg;
            break;
        case 'S':
            doStop = true;
            break;
        case 'k':
            killcmd = optarg;
            break;
        case 'W':
            checkWouldRun = true;
            break;
        default:
            return usage(argv[0], ch != 'h');
        }
    }

    const char *envROOT = getenv("ROOT");
    if (envROOT == NULL || envROOT[0] == '\0') {
        envROOT = vespa::Defaults::vespaHome();
        setenv("ROOT", envROOT, 1);
    }
    if (chdir(envROOT) != 0) {
        fprintf(stderr, "Cannot chdir to %s: %s\n", envROOT, strerror(errno));
        return 1;
    }

    PidFile mypf(pidfile);
    if (checkWouldRun) {
        if (mypf.anotherRunning()) {
            fprintf(stderr, "%s already running with pid %d\n", service, mypf.readPid());
            return 1;
        } else {
            return 0;
        }
    }
    if (doStop) {
        if (mypf.anotherRunning()) {
            int pid = mypf.readPid();
            if (killcmd != NULL) {
                fprintf(stdout, "%s was running with pid %d, running '%s' to stop it\n",
                        service, pid, killcmd);
                if (system(killcmd) != 0) {
                    fprintf(stderr, "WARNING: stop command '%s' had some problem\n", killcmd);
                }
                fflush(stdout);
            } else {
                fprintf(stdout, "%s was running with pid %d, sending SIGTERM\n",
                    service, pid);
                if (kill(pid, SIGTERM) != 0) {
                    fprintf(stderr, "could not signal %d: %s\n", pid,
                            strerror(errno));
                    killpg(pid, SIGTERM);
                    return 1;
                }
            }
            fprintf(stdout, "Waiting for exit (up to 15 minutes)\n");
            fflush(stdout);
            const int one_day = 24 * 60 * 60 * 10;
            const int twelve_minutes = 12 * 60 * 10;
            const int fifteen_minutes = 15 * 60 * 10;
            for (int cnt(0); cnt < one_day; cnt++) {
                usleep(100000); // wait 0.1 seconds
                if ((cnt < twelve_minutes) && (kill(pid, 0) != 0)) {
                    if (killpg(pid, SIGTERM) == 0) {
                        fprintf(stdout, " %s exited, terminating strays in its progress groups\n", service);
                        fflush(stdout);
                    }
                    cnt = twelve_minutes;
                }
                if ((cnt > twelve_minutes) && whole_seconds(cnt, 10)) {
                    fprintf(stdout, " %s or its children not stopping: sending SIGTERM to process group %d\n",
                            service, pid);
                    killpg(pid, SIGTERM);
                    fflush(stdout);
                }
                if (killpg(pid, 0) == 0) {
                    if (cnt%10 == 0) {
                        fprintf(stdout, ".");
                        fflush(stdout);
                    }
                } else {
                    fprintf(stdout, " DONE\n");
                    fflush(stdout);
                    break;
                }
                if ((cnt >= fifteen_minutes) && whole_seconds(cnt, 5)) {
                    printf(" giving up, sending KILL signal\n");
                    killpg(pid, SIGKILL);
                    fflush(stdout);
                }
            }
        } else {
            fprintf(stdout, "%s not running according to %s\n",
                    service, pidfile);
        }
        mypf.cleanUp();
        return 0;
    }
    if (optind >= argc || killcmd != NULL) {
        return usage(argv[0], 1);
    }

    if (mypf.anotherRunning()) {
        fprintf(stderr, "runserver already running with pid %d\n",
                mypf.readPid());
        return 0;
    }

    if (!mypf.writeOpen()) {
        perror(pidfile);
        return 1;
    }

    pid_t rsp = fork();
    if (rsp == 0) {
        close(0);
        if (open("/dev/null", O_RDONLY) != 0) {
            perror("open /dev/null for reading failed");
            std::_Exit(1);
        }
        close(1);
        if (open("/dev/null", O_WRONLY) != 1) {
            perror("open /dev/null for writing failed");
            std::_Exit(1);
        }
        dup2(1, 2);
        if (setsid() < 0) {
            perror("setsid");
            std::_Exit(1);
        }
        struct sigaction act;
        struct sigaction oact;

        memset(&act, 0, sizeof(act));

        act.sa_handler = termsig;

        sigaction(SIGINT, &act, &oact);
        sigaction(SIGTERM, &act, &oact);

        int stat = 0;
        try {
            mypf.writePid();
            do {
                time_t laststart = steady_time();
                stat = loop(service, argv+optind);
                if (restart > 0 && !gotstopsig) {
                    int wt = restart + laststart - steady_time();
                    if (wt < 0) wt = 0;
                    LOG(info, "will restart in %d seconds", wt);
                }
                while (!gotstopsig && steady_time() - laststart < restart) {
                    sleep(1);
                }
            } while (!gotstopsig && restart > 0);
        } catch (MsgException& ex) {
            LOG(error, "exception: '%s'", ex.what());
            return 1;
        }
        if (restart > 0) {
            LOG(debug, "final exit status: %d", stat);
        }
        mypf.cleanUp();
        return stat;
    }
    if (rsp < 0) {
        perror("fork");
        return 1;
    }
    printf("runserver(%s) running with pid: %d\n", service, rsp);
    return 0;
}
