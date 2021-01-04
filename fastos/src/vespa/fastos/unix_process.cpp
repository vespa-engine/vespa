// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "process.h"
#include "unix_ipc.h"
#include "ringbuffer.h"
#include <vector>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/wait.h>
#ifndef __linux__
#include <signal.h>
#endif
#include <thread>

#ifndef AF_LOCAL
#define AF_LOCAL        AF_UNIX
#endif


extern "C"
{
extern char **environ;
}


#ifndef FASTOS_HAVE_ACCRIGHTSLEN

#ifndef ALIGN
#define ALIGN(x) (((x) + sizeof(int) - 1) & ~(sizeof(int) - 1))
#endif

#ifndef CMSG_SPACE
#define CMSG_SPACE(l) (ALIGN(sizeof(struct cmsghdr)) + ALIGN(l))
#endif

#ifndef CMSG_LEN
#define CMSG_LEN(l)(ALIGN(sizeof(struct cmsghdr)) + (l))
#endif

#endif

using namespace std::chrono_literals;
using namespace std::chrono;

static pid_t safe_fork ()
{
    pid_t pid;
    int retry = 1;
    while((pid = fork()) == -1 && errno == EAGAIN) {
        sleep(retry);
        if (retry < 4) retry *= 2;
    }
    return pid;
}

static int
normalizedWaitStatus(int status)
{
    if (WIFEXITED(status))
        return WEXITSTATUS(status);
    else
        return (0x80000000 | status);
}


// The actual process launched in the proxy process
class FastOS_UNIX_RealProcess
{
private:
    FastOS_UNIX_RealProcess(const FastOS_UNIX_RealProcess&);
    FastOS_UNIX_RealProcess& operator=(const FastOS_UNIX_RealProcess&);

public:
    enum
    {
        STREAM_STDIN  = (1 << 0),
        STREAM_STDOUT = (1 << 1),
        STREAM_STDERR = (1 << 2),
        EXEC_SHELL    = (1 << 3)
    };

private:
    pid_t _pid;
    bool _died;
    bool _terse;        // Set if using direct fork (bypassing proxy process)
    int _streamMask;

    int _stdinDes[2];
    int _stdoutDes[2];
    int _stderrDes[2];
    int _ipcSockPair[2];
    int _handshakeDes[2];
    std::string _runDir;
    std::string _stdoutRedirName;
    std::string _stderrRedirName;
    const char *_path;
    std::vector<char> _pathProgBuf;

    void CloseDescriptor(int fd);
    void CloseAndResetDescriptor(int *fd);
    void CloseDescriptors();

public:
    void SetRunDir(const char * runDir) { _runDir = runDir; }
    int GetIPCDescriptor() const { return _ipcSockPair[0]; }
    int GetStdinDescriptor() const { return _stdinDes[1]; }
    int GetStdoutDescriptor() const { return _stdoutDes[0]; }
    int GetStderrDescriptor() const { return _stderrDes[0]; }
    int HandoverIPCDescriptor() {
        int ret = _ipcSockPair[0];
        _ipcSockPair[0] = -1;
        return ret;
    }

    int HandoverStdinDescriptor() {
        int ret = _stdinDes[1];
        _stdinDes[1] = -1;
        return ret;
    }

    int HandoverStdoutDescriptor() {
        int ret = _stdoutDes[0];
        _stdoutDes[0] = -1;
        return ret;
    }

    int HandoverStderrDescriptor() {
        int ret = _stderrDes[0];
        _stderrDes[0] = -1;
        return ret;
    }

    void CloseIPCDescriptor();
    void CloseStdinDescriptor();
    void CloseStdoutDescriptor();
    void CloseStderrDescriptor();

    FastOS_UNIX_RealProcess *_prev, *_next;

    FastOS_UNIX_RealProcess (int streamMask);
    ~FastOS_UNIX_RealProcess();
    pid_t GetProcessID() const { return _pid; }

    bool IsStdinPiped() const {
        return (_streamMask & STREAM_STDIN ) != 0;
    }

    bool IsStdoutPiped() const {
        return (_streamMask & STREAM_STDOUT) != 0;
    }

    bool IsStderrPiped() const {
        return (_streamMask & STREAM_STDERR) != 0;
    }

    bool IsUsingShell() const {
        return (_streamMask & EXEC_SHELL) != 0;
    }

    void SetStdoutRedirName(const char *stdoutRedirName) {
        _stdoutRedirName = stdoutRedirName;
    }

    void SetStderrRedirName(const char *stderrRedirName) {
        _stderrRedirName = stderrRedirName;
    }

    void PrepareExecVPE (const char *prog);

    void
    ExecVPE (const char *prog,
             char *const args[],
             char *const env[]);

    static bool IsWhiteSpace (char c);

    static const char *
    NextArgument (const char *p,
                  const char **endArg,
                  int *length = nullptr);

    static int CountArguments (const char *commandLine);

    void
    RedirOut(const std::string & filename,
             int targetfd,
             int exitCodeOnFailure);

    bool
    ForkAndExec(const char *command,
                char **environmentVariables,
                FastOS_UNIX_Process *process,
                FastOS_UNIX_ProcessStarter *processStarter);

    bool Setup();
    pid_t GetProcessId() const { return _pid; }
    void SetTerse() { _terse = true; }
    ssize_t HandshakeRead(void *buf, size_t len);
    void HandshakeWrite(int val);
};


void
FastOS_UNIX_RealProcess::CloseDescriptor(int fd)
{
    close(fd);
}


void
FastOS_UNIX_RealProcess::CloseAndResetDescriptor(int *fd)
{
    if (*fd == -1)
        return;
    CloseDescriptor(*fd);
    *fd = -1;
}


void
FastOS_UNIX_RealProcess::CloseDescriptors()
{
    CloseAndResetDescriptor(&_stdinDes[0]);
    CloseAndResetDescriptor(&_stdinDes[1]);
    CloseAndResetDescriptor(&_stdoutDes[0]);
    CloseAndResetDescriptor(&_stdoutDes[1]);
    CloseAndResetDescriptor(&_stderrDes[0]);
    CloseAndResetDescriptor(&_stderrDes[1]);
    CloseAndResetDescriptor(&_ipcSockPair[0]);
    CloseAndResetDescriptor(&_ipcSockPair[1]);
    CloseAndResetDescriptor(&_handshakeDes[0]);
    CloseAndResetDescriptor(&_handshakeDes[1]);
}


void
FastOS_UNIX_RealProcess::CloseIPCDescriptor()
{
    CloseAndResetDescriptor(&_ipcSockPair[0]);
}


void
FastOS_UNIX_RealProcess::CloseStdinDescriptor()
{
    CloseAndResetDescriptor(&_stdinDes[1]);
}


void
FastOS_UNIX_RealProcess::CloseStdoutDescriptor()
{
    CloseAndResetDescriptor(&_stdoutDes[0]);
}


void
FastOS_UNIX_RealProcess::CloseStderrDescriptor()
{
    CloseAndResetDescriptor(&_stderrDes[0]);
}


FastOS_UNIX_RealProcess::FastOS_UNIX_RealProcess(int streamMask)
    : _pid(-1),
      _died(false),
      _terse(false),
      _streamMask(streamMask),
      _runDir(),
      _stdoutRedirName(),
      _stderrRedirName(),
      _path(nullptr),
      _pathProgBuf(),
      _prev(nullptr),
      _next(nullptr)
{
    _stdinDes[0] = _stdinDes[1] = -1;
    _stdoutDes[0] = _stdoutDes[1] = -1;
    _stderrDes[0] = _stderrDes[1] = -1;
    _ipcSockPair[0] = _ipcSockPair[1] = -1;
    _handshakeDes[0] = _handshakeDes[1] = -1;
}


FastOS_UNIX_RealProcess::~FastOS_UNIX_RealProcess()
{
    CloseDescriptors();
}


void
FastOS_UNIX_RealProcess::PrepareExecVPE(const char *prog)
{
    const char *path = nullptr;

    char defaultPath[] = ":/usr/ucb:/bin:/usr/bin";

    if (strchr(prog, '/') != nullptr) {
        path = "";
    } else {
        path = getenv("PATH");
        if (path == nullptr) path = defaultPath;
    }
    _path = path;
    _pathProgBuf.resize(strlen(prog) + 1 + strlen(path) + 1);
}


void
FastOS_UNIX_RealProcess::ExecVPE (const char *prog,
                                  char *const args[],
                                  char *const env[])
{

    char *fullPath = &_pathProgBuf[0];
    const char *path = _path;

    for(;;)
    {
        char *p;
        for (p = fullPath; (*path != '\0') && (*path != ':'); path++)
            *p++ = *path;

        if (p > fullPath) *p++ = '/';

        strcpy(p, prog);
        //         fprintf(stdout, "Attempting execve [%s]\n", fullPath);
        //         fflush(stdout);
        execve(fullPath, args, env);

        if ((errno == ENOEXEC) ||
            (errno == ENOMEM)  ||
            (errno == E2BIG)   ||
            (errno == ETXTBSY))
            break;

        if (*path == '\0') break;
        path++;
    }
}


bool
FastOS_UNIX_RealProcess::IsWhiteSpace (char c)
{
    return (c == ' ' || c == '\t');
}


const char *
FastOS_UNIX_RealProcess::NextArgument (const char *p,
                                       const char **endArg,
                                       int *length)
{
    while(*p != '\0')
    {
        if (!IsWhiteSpace(*p)) {
            char quoteChar = '\0';
            if ((*p == '\'') || (*p == '"')) {
                quoteChar = *p;
                p++;
            }

            const char *nextArg = p;

            // Find the end of the argument.
            for(;;)
            {
                if (*p == '\0') {
                    if (length != nullptr)
                        *length = p - nextArg;
                    break;
                }

                if (quoteChar != '\0') {
                    if (*p == quoteChar) {
                        if (length != nullptr)
                            *length = p - nextArg;
                        p++;
                        break;
                    }
                }
                else
                {
                    if (IsWhiteSpace(*p)) {
                        if (length != nullptr)
                            *length = p - nextArg;
                        break;
                    }
                }
                p++;
            }

            *endArg = p;
            return nextArg;
        }
        p++;
    }
    return nullptr;
}


int
FastOS_UNIX_RealProcess::CountArguments (const char *commandLine)
{
    int numArgs = 0;
    const char *nextArg = commandLine;
    while(NextArgument(nextArg, &nextArg))
        numArgs++;

    return numArgs;
}


void
FastOS_UNIX_RealProcess::RedirOut(const std::string & filename,
                                  int targetfd,
                                  int exitCodeOnFailure)
{
    if (filename.empty() || filename[0] != '>')
        return;

    int newfd;
    if (filename[1] == '>') {
        newfd = open(&filename[2],
                     O_WRONLY | O_CREAT | O_APPEND,
                     0666);
        if (newfd < 0) {
            if (!_terse) {
                fprintf(stderr,
                        "ERROR: Could not open %s for append: %s\n",
                        &filename[2],
                        strerror(errno));
                fflush(stderr);
            }
            _exit(exitCodeOnFailure);
        }
    } else {
        newfd = open(&filename[1],
                     O_WRONLY | O_CREAT | O_TRUNC,
                     0666);
        if (newfd < 0) {
            if (!_terse) {
                fprintf(stderr,
                        "ERROR: Could not open %s for write: %s\n",
                        &filename[1],
                        strerror(errno));
                fflush(stderr);
            }
            _exit(exitCodeOnFailure);
        }
    }
    if (newfd != targetfd) {
        dup2(newfd, targetfd);
        CloseDescriptor(newfd);
    }
}


bool
FastOS_UNIX_RealProcess::
ForkAndExec(const char *command,
            char **environmentVariables,
            FastOS_UNIX_Process *process,
            FastOS_UNIX_ProcessStarter *processStarter)
{
    bool rc = false;

    pid_t starterPid = getpid();
    pid_t starterPPid = getppid();

    sprintf(environmentVariables[0], "%s=%d,%d,%d",
            "FASTOS_IPC_PARENT",
            int(starterPid), int(starterPPid), _ipcSockPair[1]);


    int numArguments = 0;
    char **execArgs = nullptr;

    if (!IsUsingShell()) {
        numArguments = CountArguments(command);
        if (numArguments > 0) {
            execArgs = new char *[numArguments + 1];
            const char *nextArg = command;

            for(int i=0; ; i++) {
                int length;
                const char *arg = NextArgument(nextArg, &nextArg,
                                               &length);

                if (arg == nullptr) {
                    // printf("ARG nullptr\n");
                    execArgs[i] = nullptr;
                    break;
                }
                // printf("argLen = %d\n", length);
                execArgs[i] = new char[length + 1];
                memcpy(execArgs[i], arg, length);
                execArgs[i][length] = '\0';
                // printf("arg %d: [%s]\n", i, execArgs[i]);
            }
            PrepareExecVPE(execArgs[0]);
        }
    }
    if (process == nullptr) {
        processStarter->CloseProxyDescs(IsStdinPiped() ? _stdinDes[0] : -1,
                                        IsStdoutPiped() ? _stdoutDes[1] : -1,
                                        IsStderrPiped() ? _stderrDes[1] : -1,
                                        _ipcSockPair[1],
                                        _handshakeDes[0],
                                        _handshakeDes[1]);
    }
    _pid = safe_fork();
    if (_pid == static_cast<pid_t>(0)) {
        // Fork success, child side.
        if (IsStdinPiped() && _stdinDes[0] != STDIN_FILENO) {
            dup2(_stdinDes[0], STDIN_FILENO);
            CloseDescriptor(_stdinDes[0]);
        }
        _stdinDes[0] = -1;
        if (IsStdoutPiped() && _stdoutDes[1] != STDOUT_FILENO) {
            dup2(_stdoutDes[1], STDOUT_FILENO);
            CloseDescriptor(_stdoutDes[1]);
        }
        _stdoutDes[1] = -1;
        if (IsStderrPiped() && _stderrDes[1] != STDERR_FILENO) {
            dup2(_stderrDes[1], STDERR_FILENO);
            CloseDescriptor(_stderrDes[1]);
        }
        _stderrDes[1] = -1;
        // FIX! Check error codes for dup2, and do _exit(127) if trouble

        if ( ! _runDir.empty()) {
            if (chdir(_runDir.c_str())) {
                if (!_terse) {
                    fprintf(stderr,
                            "ERROR: Could not chdir to %s: %s\n",
                            _runDir.c_str(),
                            strerror(errno));
                    fflush(stderr);
                }
                _exit(126);
            }
        }
        RedirOut(_stdoutRedirName.c_str(), STDOUT_FILENO, 124);
        RedirOut(_stderrRedirName.c_str(), STDERR_FILENO, 125);

        CloseDescriptor(_handshakeDes[0]);
        _handshakeDes[0] = -1;
        if (process != nullptr) {
            int fdlimit = sysconf(_SC_OPEN_MAX);
            // Close everything else
            //         printf("fdlimit = %d\n", fdlimit);
            for(int fd = STDERR_FILENO + 1; fd < fdlimit; fd++)
            {
                if (fd != _ipcSockPair[1] &&
                    fd != _handshakeDes[1])
                    CloseDescriptor(fd);
            }
        } else {
            processStarter->CloseProxiedChildDescs();
        }
        if (fcntl(_handshakeDes[1], F_SETFD, FD_CLOEXEC) != 0) _exit(127);

        HandshakeWrite(0);

        //         printf("exev(p)e [%s]\n", command);
        if (IsUsingShell()) {
            const char *shExecArgs[4];

            shExecArgs[0] = "sh";
            shExecArgs[1] = "-c";
            shExecArgs[2] = command;
            shExecArgs[3] = nullptr;
            execve("/bin/sh",
                   const_cast<char *const *>
                   (reinterpret_cast<const char *const *>
                    (shExecArgs)),
                   environmentVariables);
            int error = errno;
            if (!_terse) {
                fprintf(stderr,
                        "ERROR: Could not execv /bin/sh -c '%s': %s\n",
                        command,
                        strerror(error));
                fflush(stderr);
            }
            HandshakeWrite(error);
        }
        else
        {
            if (numArguments > 0) {
                // printf("Command: [%s]\n", execArgs[0]);
                ExecVPE(execArgs[0],
                        static_cast<char *const *>(execArgs),
                        environmentVariables);
                int error = errno;
                if (!_terse) {
                    fprintf(stderr,
                            "ERROR: Could not execve %s with "
                            "path search: %s\n",
                            execArgs[0],
                            strerror(error));
                    fflush(stderr);
                }
                HandshakeWrite(error);
            }
        }
        _exit(127);  // If execve fails, we'll get it here
    }
    else if(_pid != static_cast<pid_t>(-1))
    {
        /* Fork success, parent side */

        // Close unused file descriptors
        if (IsStdinPiped()) {
            CloseAndResetDescriptor(&_stdinDes[0]);
        }
        if (IsStdoutPiped()) {
            CloseAndResetDescriptor(&_stdoutDes[1]);
        }
        if (IsStderrPiped()) {
            CloseAndResetDescriptor(&_stderrDes[1]);
        }

        CloseAndResetDescriptor(&_ipcSockPair[1]);

        CloseAndResetDescriptor(&_handshakeDes[1]);

        int flags = fcntl(_handshakeDes[0], F_GETFL, 0);
        if (flags != -1) {
            flags &= ~O_NONBLOCK;
            fcntl(_handshakeDes[0], F_SETFL, flags);
        }
        int phase1res = 0;
        ssize_t rgot = HandshakeRead(&phase1res, sizeof(int));
        bool wasError = false;
        int error = 0;
        if (static_cast<size_t>(rgot) != sizeof(int)) wasError = true;
        else if (phase1res != 0) {
            wasError = true;
            error = phase1res;
        } else {
            int phase2res = 0;
            rgot = HandshakeRead(&phase2res, sizeof(int));
            if (rgot >= 1) {
                if (static_cast<size_t>(rgot) >= sizeof(int))
                    error = phase2res;
                wasError = true;
            }
        }

        if (wasError) {
            int status = 0;
            CloseDescriptors();
            pid_t wpid = waitpid(_pid, &status, 0);
            if (wpid <= 0) {
                fprintf(stderr, "ERROR: Could not start process %s\n", command);
            } else if (WIFEXITED(status)) {
                status = WEXITSTATUS(status);
                switch (status) {
                case 124:
                    if ( ! _stdoutRedirName.empty() &&
                        _stdoutRedirName[0] == '>') {
                        if (_stdoutRedirName[1] == '>')
                            fprintf(stderr, "ERROR: Could not open %s for append", &_stdoutRedirName[2]);
                        else
                            fprintf(stderr, "ERROR: Could not open %s for write", &_stdoutRedirName[1]);
                    }
                    break;
                case 125:
                    if ( ! _stderrRedirName.empty() &&
                        _stderrRedirName[0] == '>') {
                        if (_stderrRedirName[1] == '>')
                            fprintf(stderr, "ERROR: Could not open %s for append", &_stderrRedirName[2]);
                        else
                            fprintf(stderr, "ERROR: Could not open %s for write", &_stderrRedirName[1]);
                    }
                    break;
                case 126:
                    if ( ! _runDir.empty()) {
                        fprintf(stderr, "ERROR: Could not chdir to %s\n", _runDir.c_str());
                    }
                    break;
                case 127:
                    if (error != 0) {
                        std::error_code ec(error, std::system_category());
                        fprintf(stderr, "ERROR: Could not execve %s: %s\n", command, ec.message().c_str());
                    } else
                        fprintf(stderr, "ERROR: Could not execve %s\n", command);
                    break;
                default:
                    fprintf(stderr, "ERROR: Could not start process %s\n", command);
                    break;
                }
            } else {
                fprintf(stderr, "ERROR: Could not start process %s\n", command);
            }
            fflush(stderr);
        } else {
            rc = true;
        }
    }
    if (execArgs != nullptr) {
        char **arg = execArgs;
        while (*arg != nullptr) {
            delete [] *arg;
            arg++;
        }
        delete [] execArgs;
    }

    return rc;
}


void
FastOS_UNIX_RealProcess::HandshakeWrite(int val)
{
    if (_handshakeDes[1] == -1)
        return;
    const void *wbuf = &val;
    size_t residue = sizeof(val);
    for (;;) {
        /*
         * XXX: Might need to use syscall(SYS_write....) to avoid
         * thread library interference.
         */
        ssize_t wgot = write(_handshakeDes[1], wbuf, residue);
        if (wgot < 0 && errno == EINTR)
            continue;
        if (wgot <= 0)
            break;
        wbuf = static_cast<const char *>(wbuf) + wgot;
        residue -= wgot;
        if (residue == 0)
            break;
    }
}


ssize_t
FastOS_UNIX_RealProcess::HandshakeRead(void *buf, size_t len)
{
    if (_handshakeDes[0] == -1)
        return 0;
    size_t residue = len;
    ssize_t rgot = 0;
    void *rbuf = buf;
    for (;;) {
        rgot = read(_handshakeDes[0], rbuf, residue);
        if (rgot < 0 && errno == EINTR)
            continue;
        if (rgot <= 0)
            break;
        rbuf = static_cast<char *>(rbuf) + rgot;
        residue -= rgot;
        if (residue == 0)
            break;
    }
    return (residue == len) ? rgot : len - residue;
}


bool
FastOS_UNIX_RealProcess::Setup()
{
    bool rc = true;

    if (IsStdinPiped())  rc = rc && (pipe(_stdinDes) == 0);
    if (IsStdoutPiped()) rc = rc && (pipe(_stdoutDes) == 0);
    if (IsStderrPiped()) rc = rc && (pipe(_stderrDes) == 0);
    if (!IsUsingShell()) rc = rc && (socketpair(AF_LOCAL, SOCK_STREAM,
                                                0, _ipcSockPair) == 0);
    rc = rc && (pipe(_handshakeDes) == 0);
    return rc;
}


FastOS_UNIX_Process::
FastOS_UNIX_Process (const char *cmdLine, bool pipeStdin,
                     FastOS_ProcessRedirectListener *stdoutListener,
                     FastOS_ProcessRedirectListener *stderrListener,
                     int bufferSize) :
    FastOS_ProcessInterface(cmdLine, pipeStdin, stdoutListener,
                            stderrListener, bufferSize),
    _pid(0),
    _died(false),
    _returnCode(-1),
    _descriptor(),
    _runDir(),
    _stdoutRedirName(),
    _stderrRedirName(),
    _killed(false),
    _closing(nullptr)
{
    _descriptor[TYPE_IPC]._readBuffer.reset(new FastOS_RingBuffer(bufferSize));
    _descriptor[TYPE_IPC]._writeBuffer.reset(new FastOS_RingBuffer(bufferSize));

    if (stdoutListener != nullptr)
        _descriptor[TYPE_STDOUT]._readBuffer.reset(new FastOS_RingBuffer(bufferSize));
    if (stderrListener != nullptr)
        _descriptor[TYPE_STDERR]._readBuffer.reset(new FastOS_RingBuffer(bufferSize));

    {
        auto guard = _app->getProcessGuard();
        _app->AddChildProcess(this);
    }

    // App::AddToIPCComm() is performed when the process is started
}

FastOS_UNIX_Process::~FastOS_UNIX_Process ()
{
    Kill();             // Kill if not dead or detached.

    if ((GetDescriptorHandle(TYPE_IPC)._fd    != -1) ||
        (GetDescriptorHandle(TYPE_STDOUT)._fd != -1) ||
        (GetDescriptorHandle(TYPE_STDERR)._fd != -1))
    {
        // Let the IPC helper flush write queues and remove us from the
        // process list before we disappear.
        static_cast<FastOS_UNIX_Application *>(_app)->RemoveFromIPCComm(this);
    } else {
        // No IPC descriptor, do it ourselves
        auto guard = _app->getProcessGuard();
        _app->RemoveChildProcess(this);
    }

    for(int i=0; i<int(TYPE_COUNT); i++) {
        _descriptor[i]._readBuffer.reset();
        _descriptor[i]._writeBuffer.reset();
        CloseDescriptor(DescriptorType(i));
    }

    CloseListener(TYPE_STDOUT);
    CloseListener(TYPE_STDERR);
}

bool FastOS_UNIX_Process::CreateInternal (bool useShell)
{
    return GetProcessStarter()->CreateProcess(this, useShell,
                                              _pipeStdin,
                                              _stdoutListener != nullptr,
                                              _stderrListener != nullptr);
}

bool FastOS_UNIX_Process::WriteStdin (const void *data, size_t length)
{
    bool rc = false;
    DescriptorHandle &desc = GetDescriptorHandle(TYPE_STDIN);

    if (desc._fd != -1) {
        if (data == nullptr) {
            CloseDescriptor(TYPE_STDIN);
            rc = true;
        }
        else
        {
            int writerc = write(desc._fd, data, length);
            if (writerc < int(length))
                CloseDescriptor(TYPE_STDIN);
            else
                rc = true;
        }
    }

    return rc;
}

bool FastOS_UNIX_Process::Signal(int sig)
{
    bool rc = false;
    pid_t pid;

    auto guard = _app->getProcessGuard();
    pid = GetProcessId();
    if (pid == 0) {
        /* Do nothing */
    } else if (GetDeathFlag()) {
        rc = true;                    // The process is no longer around.
    } else if (kill(pid, sig) == 0) {
        if (sig == SIGKILL)
            _killed = true;
        rc = true;
    }
    return rc;
}

bool FastOS_UNIX_Process::Kill ()
{
    return Signal(SIGKILL);
}

bool FastOS_UNIX_Process::InternalWait (int *returnCode,
                                        int timeOutSeconds,
                                        bool *pollStillRunning)
{
    bool rc = GetProcessStarter()->Wait(this, timeOutSeconds,
                                        pollStillRunning);
    if (rc) {
        if (_killed)
            *returnCode = KILL_EXITCODE;
        else
            *returnCode = _returnCode;
    }

    return rc;
}

bool FastOS_UNIX_Process::Wait (int *returnCode, int timeOutSeconds)
{
    return InternalWait(returnCode, timeOutSeconds, nullptr);
}

bool FastOS_UNIX_Process::PollWait (int *returnCode, bool *stillRunning)
{
    return InternalWait(returnCode, -1, stillRunning);
}

int FastOS_UNIX_Process::BuildStreamMask (bool useShell)
{
    int streamMask = 0;

    if (_pipeStdin)      streamMask |= FastOS_UNIX_RealProcess::STREAM_STDIN;
    if (_stdoutListener) streamMask |= FastOS_UNIX_RealProcess::STREAM_STDOUT;
    if (_stderrListener) streamMask |= FastOS_UNIX_RealProcess::STREAM_STDERR;
    if (useShell)        streamMask |= FastOS_UNIX_RealProcess::EXEC_SHELL;

    return streamMask;
}

void FastOS_UNIX_ProcessStarter::
ReadBytes(int fd, void *buffer, int bytes)
{

    uint8_t *writePtr = static_cast<uint8_t *>(buffer);
    int remaining = bytes;

    while(remaining > 0)
    {
        int bytesRead;
        do
        {
            bytesRead = read(fd, writePtr, remaining);
        } while(bytesRead < 0 && (errno == EINTR));

        if (bytesRead < 0) {
            //perror("FATAL: FastOS_UNIX_ProcessStarter read");
            std::_Exit(1);
        }
        else if(bytesRead == 0)
        {
            //fprintf(stderr, "FATAL: FastOS_UNIX_RealProcessStart read 0\n");
            std::_Exit(1);
        }

        writePtr += bytesRead;
        remaining -= bytesRead;
    }
}

void FastOS_UNIX_ProcessStarter::
WriteBytes(int fd, const void *buffer, int bytes, bool ignoreFailure)
{
    const uint8_t *readPtr = static_cast<const uint8_t *>(buffer);
    int remaining = bytes;

    while(remaining > 0)
    {
        int bytesWritten;
        do {
            bytesWritten = write(fd, readPtr, remaining);
        } while (bytesWritten < 0 && (errno == EINTR));

        if (bytesWritten < 0) {
            if (ignoreFailure)
                return;
            //perror("FATAL: FastOS_UNIX_ProcessStarter write");
            std::_Exit(1);
        } else if (bytesWritten == 0) {
            if (ignoreFailure)
                return;
            //fprintf(stderr, "FATAL: FastOS_UNIX_RealProcessStart write 0\n");
            std::_Exit(1);
        }

        readPtr += bytesWritten;
        remaining -= bytesWritten;
    }
}

int FastOS_UNIX_ProcessStarter::ReadInt (int fd)
{
    int intStorage;
    ReadBytes(fd, &intStorage, sizeof(int));
    return intStorage;
}

void FastOS_UNIX_ProcessStarter::WriteInt (int fd, int integer,
                                           bool ignoreFailure)
{
    int intStorage = integer;
    WriteBytes(fd, &intStorage, sizeof(int), ignoreFailure);
}

void FastOS_UNIX_ProcessStarter::
AddChildProcess (FastOS_UNIX_RealProcess *node)
{
    node->_prev = nullptr;
    node->_next = _processList;

    if (_processList != nullptr)
        _processList->_prev = node;
    _processList = node;
}

void FastOS_UNIX_ProcessStarter::
RemoveChildProcess (FastOS_UNIX_RealProcess *node)
{
    if (node->_prev)
        node->_prev->_next = node->_next;
    else
        _processList = node->_next;

    if (node->_next) {
        node->_next->_prev = node->_prev;
        node->_next = nullptr;
    }

    if (node->_prev != nullptr)
        node->_prev = nullptr;
}

bool FastOS_UNIX_ProcessStarter::SendFileDescriptor (int fd)
{
    //   printf("SENDFILEDESCRIPTOR\n");
    bool rc = false;

    struct msghdr msg;
    struct iovec iov;

    memset(&msg, 0, sizeof(msg));
    memset(&iov, 0, sizeof(iov));

#ifndef FASTOS_HAVE_ACCRIGHTSLEN
    union
    {
        struct cmsghdr cm;
        char control[CMSG_SPACE(sizeof(int))];
    } control_un;
    struct cmsghdr *cmptr;

    memset(&control_un, 0, sizeof(control_un));
    msg.msg_control = control_un.control;
    msg.msg_controllen = sizeof(control_un.control);

    cmptr = CMSG_FIRSTHDR(&msg);
    cmptr->cmsg_len = CMSG_LEN(sizeof(int));
    cmptr->cmsg_level = SOL_SOCKET;
    cmptr->cmsg_type = SCM_RIGHTS;
    memcpy(CMSG_DATA(cmptr), &fd, sizeof(int));
#else
    msg.msg_accrights = static_cast<caddr_t>(&fd);
    msg.msg_accrightslen = sizeof(int);
#endif

    msg.msg_name = nullptr;
    msg.msg_namelen = 0;

    char dummyData = '\0';
    iov.iov_base = &dummyData;
    iov.iov_len = 1;
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    int sendmsgrc = sendmsg(_starterSocketDescr, &msg, 0);
    //   printf("sendmsg = %d\n", sendmsgrc);
    if (sendmsgrc < 0)
        perror("sendmsg");
    else
        rc = true;

    return rc;
}

void FastOS_UNIX_ProcessStarter::StarterDoWait ()
{
    //            printf("WAIT FOR PROCESSES\n");

    pid_t pid;
    int status;

    pid_t deadProcesses[MAX_PROCESSES_PER_WAIT];
    int returnCodes[MAX_PROCESSES_PER_WAIT];
    int numDeadProcesses = 0;

    while((pid = waitpid(-1, &status, WNOHANG)) > 0)
    {
        //               printf("Child %d has died\n", pid);
        bool foundProcess = false;

        FastOS_UNIX_RealProcess *process, *next;
        for(process = FastOS_UNIX_ProcessStarter::_processList;
            process != nullptr; process = next)
        {

            // Need to do this here since we are deleting entries
            next = process->_next;

            if (process->GetProcessId() == pid) {
                foundProcess = true;
                RemoveChildProcess(process);
                delete process;
                break;
            }
        }

        if (!foundProcess && !_hasDetachedProcess)
            printf("*** Strange... We don't know about pid %d\n", int(pid));

        if (!foundProcess)
            continue;           /* Don't report death of detached processes */

        deadProcesses[numDeadProcesses] = pid;

        returnCodes[numDeadProcesses] = normalizedWaitStatus(status);

        numDeadProcesses++;
        if (numDeadProcesses == MAX_PROCESSES_PER_WAIT)
            break;
    }

    WriteBytes(_starterSocket, &numDeadProcesses, sizeof(int));
    for(int i=0; i<numDeadProcesses; i++)
    {
        WriteBytes(_starterSocket, &deadProcesses[i], sizeof(pid_t));
        WriteBytes(_starterSocket, &returnCodes[i], sizeof(int));
    }
}

void FastOS_UNIX_ProcessStarter::StarterDoCreateProcess ()
{
    int stringLength = ReadInt(_starterSocket);
    char cmdLine[stringLength];

    ReadBytes(_starterSocket, cmdLine, stringLength);
    int streamMask = ReadInt(_starterSocket);
    char **environmentVariables = ReceiveEnvironmentVariables();

    FastOS_UNIX_RealProcess *process = new FastOS_UNIX_RealProcess(streamMask);

    bool rc=false;

    int runDirLength = ReadInt(_starterSocket);

    if (runDirLength > 0) {
        char runDir[runDirLength];
        ReadBytes(_starterSocket, runDir, runDirLength);
        process->SetRunDir(runDir);
    }

    int stdoutRedirNameLen = ReadInt(_starterSocket);
    if (stdoutRedirNameLen > 0) {
        char stdoutRedirName[stdoutRedirNameLen];
        ReadBytes(_starterSocket, stdoutRedirName, stdoutRedirNameLen);
        process->SetStdoutRedirName(stdoutRedirName);
    }

    int stderrRedirNameLen = ReadInt(_starterSocket);
    if (stderrRedirNameLen > 0) {
        char stderrRedirName[stderrRedirNameLen];
        ReadBytes(_starterSocket, stderrRedirName, stderrRedirNameLen);
        process->SetStderrRedirName(stderrRedirName);
    }

    if (process->Setup()) {
        WriteInt(_starterSocket, CODE_SUCCESS);

        // Send IPC descriptor if the shell is not used
        if (process->IsUsingShell())
            rc = true;
        else {
            if (SendFileDescriptor(process->GetIPCDescriptor())) {
                process->CloseIPCDescriptor();
                WriteInt(_starterSocket, CODE_SUCCESS);
                if (ReadInt(_starterSocket) == CODE_SUCCESS)
                    rc = true;
            } else {
                WriteInt(_starterSocket, CODE_FAILURE);
            }
        }

        if (rc) {
            rc = false;

            if (!process->IsStdinPiped()) {
                rc = true;
            } else {
                if (SendFileDescriptor(process->GetStdinDescriptor())) {
                    process->CloseStdinDescriptor();
                    WriteInt(_starterSocket, CODE_SUCCESS);
                    if (ReadInt(_starterSocket) == CODE_SUCCESS)
                        rc = true;
                } else {
                    WriteInt(_starterSocket, CODE_FAILURE);
                }
            }
        }

        if (rc) {
            rc = false;

            if (!process->IsStdoutPiped()) {
                rc = true;
            } else {
                if (SendFileDescriptor(process->GetStdoutDescriptor())) {
                    process->CloseStdoutDescriptor();
                    WriteInt(_starterSocket, CODE_SUCCESS);
                    if (ReadInt(_starterSocket) == CODE_SUCCESS) {
                        rc = true;
                    }
                } else {
                    WriteInt(_starterSocket, CODE_FAILURE);
                }
            }
        }

        if (rc) {
            rc = false;

            if (!process->IsStderrPiped()) {
                rc = true;
            } else {
                if (SendFileDescriptor(process->GetStderrDescriptor())) {
                    process->CloseStderrDescriptor();
                    WriteInt(_starterSocket, CODE_SUCCESS);
                    if (ReadInt(_starterSocket) == CODE_SUCCESS)
                        rc = true;
                } else {
                    WriteInt(_starterSocket, CODE_FAILURE);
                }
            }
        }

        if (rc) {
            rc = false;

            pid_t processId = -1;
            if (process->ForkAndExec(cmdLine,
                                     environmentVariables,
                                     nullptr,
                                     this))
            {
                processId = process->GetProcessID();
                AddChildProcess(process);
                rc = true;
            }
            WriteBytes(_starterSocket, &processId, sizeof(pid_t));
        }
    } else {
        WriteInt(_starterSocket, CODE_FAILURE);
    }


    if (!rc) delete process;

    char **pe = environmentVariables;
    while(*pe != nullptr) {
        delete [] *pe++;
    }
    delete [] environmentVariables;
}

void FastOS_UNIX_ProcessStarter::Run ()
{
    for(;;)
    {
        // Receive commands from main process
        int command = ReadInt(_starterSocket);

        switch(command)
        {
        case CODE_WAIT:       StarterDoWait();           break;
        case CODE_NEWPROCESS: StarterDoCreateProcess();  break;
        case CODE_EXIT:       _exit(2);
        }
    }
}

bool FastOS_UNIX_ProcessStarter::CreateSocketPairs ()
{
    bool rc = false;
    int fileDescriptors[2];
    if (socketpair(AF_LOCAL, SOCK_STREAM, 0, fileDescriptors) == 0) {
        _starterSocket = fileDescriptors[0];
        _mainSocket = fileDescriptors[1];

        // We want to use a separate pair of sockets for passing
        // file descriptors, as errors sending file descriptors
        // shouldn't intefere with handshaking of the main <-> starter
        // process protocol.
        if (socketpair(AF_LOCAL, SOCK_STREAM, 0, fileDescriptors) == 0) {
            _starterSocketDescr = fileDescriptors[0];
            _mainSocketDescr = fileDescriptors[1];
            rc = true;
        } else {
            std::error_code ec(errno, std::system_category());
            fprintf(stderr, "socketpair() failed: %s\n", ec.message().c_str());
        }
    } else {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "socketpair() failed: %s\n", ec.message().c_str());
    }
    return rc;
}

FastOS_UNIX_ProcessStarter::FastOS_UNIX_ProcessStarter (FastOS_ApplicationInterface *app)
    : _app(app),
      _processList(nullptr),
      _pid(-1),
      _starterSocket(-1),
      _mainSocket(-1),
      _starterSocketDescr(-1),
      _mainSocketDescr(-1),
      _hasProxiedChildren(false),
      _closedProxyProcessFiles(false),
      _hasDetachedProcess(false),
      _hasDirectChildren(false)
{
}

FastOS_UNIX_ProcessStarter::~FastOS_UNIX_ProcessStarter ()
{
    if(_starterSocket != -1)
        close(_starterSocket);
    if(_mainSocket != -1)
        close(_mainSocket);
}

bool FastOS_UNIX_ProcessStarter::Start ()
{
    bool rc = false;

    if (CreateSocketPairs()) {
        pid_t pid = safe_fork();
        if (pid != -1) {
            if (pid == 0) {  // Child
                close(_mainSocket);       // Close unused end of pipes
                close(_mainSocketDescr);
                _mainSocket = -1;
                _mainSocketDescr = -1;
                Run(); // Never returns
            } else {          // Parent
                _pid = pid;
                close(_starterSocket);    // Close unused end of pipes
                close(_starterSocketDescr);
                _starterSocket = -1;
                _starterSocketDescr = -1;
                rc = true;
            }
        } else {
            std::error_code ec(errno, std::system_category());
            fprintf(stderr, "could not fork(): %s\n", ec.message().c_str());
        }
    } else {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "could not CreateSocketPairs: %s\n", ec.message().c_str());
    }
    return rc;
}

void FastOS_UNIX_ProcessStarter::Stop ()
{
    // Ignore failure (if it already died of SIGINT, etc..)
    WriteInt(_mainSocket, CODE_EXIT, true);

    int result = -1;
    waitpid(_pid, &result, 0);
}

char ** FastOS_UNIX_ProcessStarter::ReceiveEnvironmentVariables ()
{
    int numEnvVars = ReadInt(_starterSocket);

    //   printf("Receiving %d environment variables\n", numEnvVars);
    char **myEnvironment = new char *[numEnvVars + 2];

    // Reserve the first entry for the IPC parent variable
    myEnvironment[0] = new char [1024];

    int fillIndex=1;
    for(int i=0; i<numEnvVars; i++)
    {
        int envBytes = ReadInt(_starterSocket);
        myEnvironment[fillIndex] = new char [envBytes];
        ReadBytes(_starterSocket, myEnvironment[fillIndex], envBytes);
        //      printf("Received [%s]\n", myEnvironment[fillIndex]);

        if (strlen(myEnvironment[fillIndex]) == 0 ||
            strncmp(myEnvironment[fillIndex], "FASTOS_IPC_PARENT=", 18) == 0)
            delete [] myEnvironment[fillIndex];
        else
            fillIndex++;
    }
    myEnvironment[fillIndex] = nullptr;

    return myEnvironment;
}

void
FastOS_UNIX_ProcessStarter::CloseProxiedChildDescs()
{
    if (_starterSocket >= 0)      close(_starterSocket);
    if (_starterSocketDescr >= 0) close(_starterSocketDescr);
}


void
FastOS_UNIX_ProcessStarter::CloseProxyDescs(int stdinPipedDes, int stdoutPipedDes, int stderrPipedDes,
                                            int ipcDes, int handshakeDes0, int handshakeDes1)
{
    return;
    if (_closedProxyProcessFiles)
        return;
    int fdlimit = sysconf(_SC_OPEN_MAX);
    for(int fd = STDERR_FILENO + 1; fd < fdlimit; fd++)
    {
        if (fd != stdinPipedDes &&
            fd != stdoutPipedDes &&
            fd != stderrPipedDes &&
            fd != ipcDes &&
            fd != handshakeDes0 &&
            fd != handshakeDes1 &&
            fd != _starterSocket &&
            fd != _starterSocketDescr)
            close(fd);
    }
    _closedProxyProcessFiles = true;
}

char **
FastOS_UNIX_ProcessStarter::CopyEnvironmentVariables()
{
    char **env = environ;
    while (*env != nullptr)
        env++;
    int numEnvVars = env - environ;
    char **newEnv = new char *[numEnvVars + 2];
    newEnv[0] = new char[1024];

    int fillIdx = 1;
    env = environ;
    while (*env != nullptr) {
        size_t len = strlen(*env);
        if (len > 0 &&
            strncmp(*env, "FASTOS_IPC_PARENT=", 18) != 0) {
            newEnv[fillIdx] = new char[len + 1];
            memcpy(newEnv[fillIdx], *env, len + 1);
            fillIdx++;
        }
        env++;
    }
    newEnv[fillIdx] = nullptr;
    return newEnv;
}


void
FastOS_UNIX_ProcessStarter::FreeEnvironmentVariables(char **env)
{
    char **p = env;
    while (*p != nullptr) {
        delete [] *p;
        p++;
    }
    delete [] env;
}


bool
FastOS_UNIX_ProcessStarter::
CreateProcess (FastOS_UNIX_Process *process,
               bool useShell,
               bool pipeStdin,
               bool pipeStdout,
               bool pipeStderr)
{
    bool rc = false;

    const char *cmdLine = process->GetCommandLine();

    auto guard = _app->getProcessGuard();

    _hasDirectChildren = true;
    FastOS_UNIX_RealProcess *rprocess = new FastOS_UNIX_RealProcess(process->BuildStreamMask(useShell));
    const char *runDir = process->GetRunDir();
    if (runDir != nullptr) {
        rprocess->SetRunDir(runDir);            // Handover
    }
    const char *stdoutRedirName = process->GetStdoutRedirName();
    if (stdoutRedirName != nullptr) {
        rprocess->SetStdoutRedirName(stdoutRedirName);
    }
    const char *stderrRedirName = process->GetStderrRedirName();
    if (stderrRedirName != nullptr) {
        rprocess->SetStderrRedirName(stderrRedirName);
    }
    char **env = CopyEnvironmentVariables();
    rprocess->SetTerse();
    rprocess->Setup();
    if (!useShell)
        process->SetDescriptor(FastOS_UNIX_Process::TYPE_IPC, rprocess->HandoverIPCDescriptor());
    if (pipeStdin)
        process->SetDescriptor(FastOS_UNIX_Process::TYPE_STDIN, rprocess->HandoverStdinDescriptor());
    if (pipeStdout)
        process->SetDescriptor(FastOS_UNIX_Process::TYPE_STDOUT, rprocess->HandoverStdoutDescriptor());
    if (pipeStderr)
        process->SetDescriptor(FastOS_UNIX_Process::TYPE_STDERR, rprocess->HandoverStderrDescriptor());
    pid_t processId = -1;
    if (rprocess->ForkAndExec(cmdLine, env, process, this)) {
        processId = rprocess->GetProcessID();
    }
    if (processId != -1) {
        process->SetProcessId(static_cast<unsigned int>(processId));
        if (!useShell || pipeStdout || pipeStderr)
            static_cast<FastOS_UNIX_Application *>(_app)->AddToIPCComm(process);
        rc = true;
    } else {
        fprintf(stderr, "Forkandexec %s failed\n", cmdLine);
    }
    guard.unlock();
    delete rprocess;
    FreeEnvironmentVariables(env);
    return rc;
}


void
FastOS_UNIX_ProcessStarter::PollReapDirectChildren()
{
    int status;
    pid_t pid;

    for (;;) {
        pid = waitpid(-1, &status, WNOHANG);
        if (pid <= 0)
            break;

        FastOS_ProcessInterface *node;
        for(node = _app->GetProcessList();
            node != nullptr; node = node->_next)
        {
            FastOS_UNIX_Process *xproc = static_cast<FastOS_UNIX_Process *>(node);

            if (xproc->GetProcessId() == static_cast<unsigned int>(pid))
                xproc->DeathNotification(normalizedWaitStatus(status));
        }
    }
}


void
FastOS_UNIX_ProcessStarter::PollReapProxiedChildren()
{
    // Ask our process starter to report dead processes
    WriteInt(_mainSocket, FastOS_UNIX_ProcessStarter::CODE_WAIT);

    int numDeadProcesses;
    ReadBytes(_mainSocket, &numDeadProcesses, sizeof(int));

    for(int i=0; i<numDeadProcesses; i++)
    {
        pid_t deadProcess;
        int returnCode;

        ReadBytes(_mainSocket, &deadProcess, sizeof(pid_t));
        ReadBytes(_mainSocket, &returnCode, sizeof(int));

        FastOS_ProcessInterface *node;
        for(node = _app->GetProcessList(); node != nullptr; node = node->_next)
        {
            FastOS_UNIX_Process *xproc = static_cast<FastOS_UNIX_Process *>(node);

            if (xproc->GetProcessId() == static_cast<unsigned int>(deadProcess))
            {
                xproc->DeathNotification(returnCode);
            }
        }
    }
}


bool
FastOS_UNIX_ProcessStarter::Wait(FastOS_UNIX_Process *process,
                                 int timeOutSeconds,
                                 bool *pollStillRunning)
{
    bool rc = true;

    bool timeOutKillAttempted = false;

    steady_clock::time_point start = steady_clock::now();

    if (pollStillRunning != nullptr)
        *pollStillRunning = true;

    for (;;) {
        {
            auto guard = process->_app->getProcessGuard();

            if (_hasDirectChildren)  PollReapDirectChildren();

            if (_hasProxiedChildren) PollReapProxiedChildren();
        }

        if (process->GetDeathFlag()) {
            if (pollStillRunning != nullptr)
                *pollStillRunning = false;
            break;
        }

        if (pollStillRunning != nullptr)
            break;

        if ((timeOutSeconds != -1) && !timeOutKillAttempted) {

            if ((steady_clock::now() - start) >= seconds(timeOutSeconds)) {
                process->Kill();
                timeOutKillAttempted = true;
            }
        }

        std::this_thread::sleep_for(100ms);
    }

    return rc;
}

FastOS_UNIX_Process::DescriptorHandle::DescriptorHandle()
    : _fd(-1),
      _wantRead(false),
      _wantWrite(false),
      _canRead(false),
      _canWrite(false),
      _pollIdx(-1),
      _readBuffer(),
      _writeBuffer()
{
}
FastOS_UNIX_Process::DescriptorHandle::~DescriptorHandle() = default;
void
FastOS_UNIX_Process::DescriptorHandle::CloseHandle()
{
    _wantRead = false;
    _wantWrite = false;
    _canRead = false;
    _canWrite = false;
    _pollIdx = -1;
    if (_fd != -1) {
        close(_fd);
        _fd = -1;
    }
    if (_readBuffer.get() != nullptr)
        _readBuffer->Close();
    if (_writeBuffer.get() != nullptr)
        _writeBuffer->Close();
}
