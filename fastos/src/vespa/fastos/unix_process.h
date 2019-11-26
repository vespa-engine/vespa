// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Class definitions for FastOS_UNIX_Process.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once

#include "process.h"
#include "app.h"
#include <string>
#include <memory>
#include <future>

class FastOS_UNIX_RealProcess;
class FastOS_RingBuffer;

class FastOS_UNIX_Process : public FastOS_ProcessInterface
{
private:
    FastOS_UNIX_Process(const FastOS_UNIX_Process&);
    FastOS_UNIX_Process& operator=(const FastOS_UNIX_Process&);

    unsigned int _pid;
    bool _died;
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
        DescriptorHandle();
        ~DescriptorHandle();
        void CloseHandle();
    };
private:
    DescriptorHandle _descriptor[4];

    std::string _runDir;
    std::string _stdoutRedirName;
    std::string _stderrRedirName;
    bool _killed;

    FastOS_UNIX_ProcessStarter *GetProcessStarter () {
        return static_cast<FastOS_UNIX_Application *>(_app)->GetProcessStarter();
    }

    bool InternalWait (int *returnCode, int timeOutSeconds, bool *pollStillRunning);
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
    std::unique_ptr<std::promise<void>> _closing;
    FastOS_ProcessRedirectListener *GetListener (DescriptorType type)
    {
        if(type == TYPE_STDOUT)
            return _stdoutListener;
        else if(type == TYPE_STDERR)
            return _stderrListener;

        return nullptr;
    }

    void CloseListener (DescriptorType type)
    {
        if(type == TYPE_STDOUT)
        {
            if(_stdoutListener != nullptr)
            {
                _stdoutListener->OnReceiveData(nullptr, 0);
                _stdoutListener = nullptr;
            }
        }
        else if(type == TYPE_STDERR)
        {
            if(_stderrListener != nullptr)
            {
                _stderrListener->OnReceiveData(nullptr, 0);
                _stderrListener = nullptr;
            }
        }
    }

    FastOS_UNIX_Process (const char *cmdLine, bool pipeStdin = false,
                         FastOS_ProcessRedirectListener *stdoutListener = nullptr,
                         FastOS_ProcessRedirectListener *stderrListener = nullptr,
                         int bufferSize = 65535);
    ~FastOS_UNIX_Process ();
    bool CreateInternal (bool useShell);
    bool Create () override { return CreateInternal(false); }
    bool CreateWithShell () override  { return CreateInternal(true); }
    bool WriteStdin (const void *data, size_t length) override;
    bool Signal(int sig);
    bool Kill () override;
    bool Wait (int *returnCode, int timeOutSeconds = -1) override;
    bool PollWait (int *returnCode, bool *stillRunning) override;
    void SetProcessId (unsigned int pid) { _pid = pid; }
    unsigned int GetProcessId() override { return _pid; }
    void DeathNotification (int returnCode) {
        _returnCode = returnCode;
        _died = true;
    }
    bool GetDeathFlag () { return _died; }
    int BuildStreamMask (bool useShell);

    void CloseDescriptor (DescriptorType type)
    {
        _descriptor[type].CloseHandle();
    }

    void SetDescriptor (DescriptorType type, int descriptor)
    {
        _descriptor[type]._fd = descriptor;
    }

    DescriptorHandle &GetDescriptorHandle(DescriptorType type)
    {
        return _descriptor[type];
    }

    bool GetKillFlag () {return _killed; }

    const char *GetRunDir() const { return _runDir.c_str(); }
    const char *GetStdoutRedirName() const { return _stdoutRedirName.c_str(); }
    const char *GetStderrRedirName() const { return _stderrRedirName.c_str(); }
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
    void StarterDoCreateProcess ();

    bool SendFileDescriptor (int fd);

    char **ReceiveEnvironmentVariables ();

    bool CreateSocketPairs ();
    void Run ();

    void AddChildProcess (FastOS_UNIX_RealProcess *node);
    void RemoveChildProcess (FastOS_UNIX_RealProcess *node);

    void PollReapProxiedChildren();
    char **CopyEnvironmentVariables();
    static void FreeEnvironmentVariables(char **env);
    void PollReapDirectChildren();

public:
    FastOS_UNIX_ProcessStarter (FastOS_ApplicationInterface *app);
    ~FastOS_UNIX_ProcessStarter ();

    bool Start ();
    void Stop ();
    void CloseProxiedChildDescs();
    void CloseProxyDescs(int stdinPipedDes, int stdoutPipedDes, int stderrPipedDes,
                         int ipcDes, int handshakeDes0, int handshakeDes1);

    bool CreateProcess (FastOS_UNIX_Process *process, bool useShell,
                        bool pipeStdin, bool pipeStdout, bool pipeStderr);
    bool Wait (FastOS_UNIX_Process *process, int timeOutSeconds, bool *pollStillRunning);
};


