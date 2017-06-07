// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Class definitions for FastOS_UNIX_Process.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once

#include <vespa/fastos/process.h>
#include <vespa/fastos/app.h>
#include <string>
#include <memory>

class FastOS_BoolCond;
class FastOS_UNIX_RealProcess;

#include <vespa/fastos/ringbuffer.h>

class FastOS_UNIX_Process : public FastOS_ProcessInterface
{
private:
    FastOS_UNIX_Process(const FastOS_UNIX_Process&);
    FastOS_UNIX_Process& operator=(const FastOS_UNIX_Process&);

    unsigned int _pid;
    bool _died;
    bool _directChild;
    bool _keepOpenFilesIfDirectChild;
    int _returnCode;
public:
    class DescriptorHandle
    {
    private:
        DescriptorHandle(const DescriptorHandle &);
        DescriptorHandle& operator=(const DescriptorHandle &);

    public:
        int _fd;
        bool _wantRead;
        bool _wantWrite;
        bool _canRead;
        bool _canWrite;
        int _pollIdx;
        std::unique_ptr<FastOS_RingBuffer> _readBuffer;
        std::unique_ptr<FastOS_RingBuffer> _writeBuffer;
        DescriptorHandle(void)
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
        ~DescriptorHandle() { }
        void CloseHandle(void)
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
            if (_readBuffer.get() != NULL)
                _readBuffer->Close();
            if (_writeBuffer.get() != NULL)
                _writeBuffer->Close();
        }
        void CloseHandleDirectChild(void)
        {
            if (_fd != -1) {
                close(_fd);
                _fd = -1;
            }
        }
    };
private:
    DescriptorHandle _descriptor[4];

    std::string _runDir;
    std::string _stdoutRedirName;
    std::string _stderrRedirName;
    bool _killed;

    FastOS_UNIX_ProcessStarter *GetProcessStarter ()
    {
        return static_cast<FastOS_UNIX_Application *>(_app)->
            GetProcessStarter();
    }

    bool InternalWait (int *returnCode, int timeOutSeconds,
                       bool *pollStillRunning);
public:
    enum DescriptorType
    {
        TYPE_STDOUT,
        TYPE_STDERR,
        TYPE_IPC,
        TYPE_STDIN,
        TYPE_COUNT
    };

    enum Constants
    {
        TYPE_READCOUNT = 3
    };
    FastOS_BoolCond *_closing;
    FastOS_ProcessRedirectListener *GetListener (DescriptorType type)
    {
        if(type == TYPE_STDOUT)
            return _stdoutListener;
        else if(type == TYPE_STDERR)
            return _stderrListener;

        return NULL;
    }

    void CloseListener (DescriptorType type)
    {
        if(type == TYPE_STDOUT)
        {
            if(_stdoutListener != NULL)
            {
                _stdoutListener->OnReceiveData(NULL, 0);
                _stdoutListener = NULL;
            }
        }
        else if(type == TYPE_STDERR)
        {
            if(_stderrListener != NULL)
            {
                _stderrListener->OnReceiveData(NULL, 0);
                _stderrListener = NULL;
            }
        }
    }

    FastOS_UNIX_Process (const char *cmdLine, bool pipeStdin = false,
                         FastOS_ProcessRedirectListener *stdoutListener = NULL,
                         FastOS_ProcessRedirectListener *stderrListener = NULL,
                         int bufferSize = 65535);
    ~FastOS_UNIX_Process ();
    bool CreateInternal (bool useShell);
    bool Create () override { return CreateInternal(false); }
    bool CreateWithShell () override  { return CreateInternal(true); }
    bool WriteStdin (const void *data, size_t length) override;
    bool Signal(int sig);
    bool Kill () override;
    bool WrapperKill () override;
    bool Wait (int *returnCode, int timeOutSeconds = -1) override;
    bool PollWait (int *returnCode, bool *stillRunning) override;
    bool Detach() override;
    void SetProcessId (unsigned int pid) { _pid = pid; }
    unsigned int GetProcessId() override { return _pid; }
    bool SendIPCMessage (const void *data, size_t length) override;
    void DeathNotification (int returnCode)
    {
        _returnCode = returnCode;
        _died = true;
    }
    bool GetDeathFlag () { return _died; }
    int BuildStreamMask (bool useShell);

    void CloseDescriptor (DescriptorType type)
    {
        _descriptor[type].CloseHandle();
    }

    void CloseDescriptorDirectChild(DescriptorType type)
    {
        _descriptor[type].CloseHandleDirectChild();
    }

    void CloseDescriptorsDirectChild(void)
    {
        CloseDescriptorDirectChild(TYPE_STDOUT);
        CloseDescriptorDirectChild(TYPE_STDERR);
        CloseDescriptorDirectChild(TYPE_IPC);
        CloseDescriptorDirectChild(TYPE_STDIN);
    }

    void SetDescriptor (DescriptorType type,
                        int descriptor)
    {
        _descriptor[type]._fd = descriptor;
    }

    DescriptorHandle &GetDescriptorHandle(DescriptorType type)
    {
        return _descriptor[type];
    }

    FastOS_ProcessRedirectListener *GetStdoutListener ()
    {
        return _stdoutListener;
    }
    FastOS_ProcessRedirectListener *GetStderrListener ()
    {
        return _stderrListener;
    }
    bool GetKillFlag () {return _killed; }
    bool GetDirectChild() const override { return _directChild; }

    bool SetDirectChild() override {
        _directChild = true;
        return true;
    }

    bool GetKeepOpenFilesIfDirectChild() const override { return _keepOpenFilesIfDirectChild; }

    bool SetKeepOpenFilesIfDirectChild() override {
        _keepOpenFilesIfDirectChild = true;
        return true;
    }

    void SetRunDir(const char *runDir);
    void SetStdoutRedirName(const char *stdoutRedirName);
    void SetStderrRedirName(const char *stderrRedirName);
    const char *GetRunDir(void) const { return _runDir.c_str(); }
    const char *GetStdoutRedirName(void) const { return _stdoutRedirName.c_str(); }
    const char *GetStderrRedirName(void) const { return _stderrRedirName.c_str(); }
};


class FastOS_UNIX_RealProcess;
class FastOS_UNIX_ProcessStarter
{
private:
    FastOS_UNIX_ProcessStarter(const FastOS_UNIX_ProcessStarter&);
    FastOS_UNIX_ProcessStarter& operator=(const FastOS_UNIX_ProcessStarter&);

public:

    enum Constants
    {
        CODE_EXIT,
        CODE_NEWPROCESS,
        CODE_WAIT,
        CODE_DETACH,

        CODE_SUCCESS,
        CODE_FAILURE,

        MAX_PROCESSES_PER_WAIT = 50,

        CONSTEND
    };

protected:
    FastOS_ApplicationInterface *_app;
    static void ReadBytes(int fd, void *buffer, int bytes);
    static void WriteBytes(int fd, const void *buffer,
                           int bytes, bool ignoreFailure = false);
    static int ReadInt (int fd);
    static void WriteInt (int fd, int integer, bool ignoreFailure = false);

    FastOS_UNIX_RealProcess *_processList;

    pid_t _pid;
    int _starterSocket;
    int _mainSocket;
    int _starterSocketDescr;
    int _mainSocketDescr;
    bool _hasProxiedChildren;
    bool _closedProxyProcessFiles;
    bool _hasDetachedProcess;
    bool _hasDirectChildren;

    void StarterDoWait ();
    void StarterDoDetachProcess ();
    void StarterDoCreateProcess ();

    bool SendFileDescriptor (int fd);
    int ReadFileDescriptor ();

    char **ReceiveEnvironmentVariables ();
    void SendEnvironmentVariables ();

    bool CreateSocketPairs ();
    void Run ();

    void AddChildProcess (FastOS_UNIX_RealProcess *node);
    void RemoveChildProcess (FastOS_UNIX_RealProcess *node);

    bool ReceiveFileDescriptor (bool use,
                                FastOS_UNIX_Process::DescriptorType type,
                                FastOS_UNIX_Process *xproc);
    void PollReapProxiedChildren(void);
    char **CopyEnvironmentVariables(void);
    void FreeEnvironmentVariables(char **env);
    void PollReapDirectChildren(void);

public:
    FastOS_UNIX_ProcessStarter (FastOS_ApplicationInterface *app)
        : _app(app),
          _processList(NULL),
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

    ~FastOS_UNIX_ProcessStarter ()
    {
        if(_starterSocket != -1)
            close(_starterSocket);
        if(_mainSocket != -1)
            close(_mainSocket);
    }

    bool Start ();
    void Stop ();
    void CloseProxiedChildDescs(void);
    void CloseProxyDescs(int stdinPipedDes,
                         int stdoutPipedDes,
                         int stderrPipedDes,
                         int ipcDes,
                         int handshakeDes0,
                         int handshakeDes1);

    bool CreateProcess (FastOS_UNIX_Process *process, bool useShell,
                        bool pipeStdin, bool pipeStdout, bool pipeStderr);
    bool Wait (FastOS_UNIX_Process *process, int timeOutSeconds,
               bool *pollStillRunning);
    bool Detach(FastOS_UNIX_Process *process);
};


