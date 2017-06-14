// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definitions for FastOS_ProcessInterface and
 * FastOS_ProcessRedirectListener.
 *
 * @author  Oivind H. Danielsen
 */

#pragma once

#include <vespa/fastos/types.h>

/**
 * This class serves as a sink for redirected (piped) output from
 * subclasses of @ref FastOS_ProcessInterface.
 */
class FastOS_ProcessRedirectListener
{
public:
    /**
     * This method is called when new data is available from the
     * process. Subclass this method to process the data.
     * You should assume that any thread can invoke this method.
     * For convenience the data buffer is always zero- terminated
     * (static_cast<uint8_t>(data[length]) = '\\0').
     * When the pipe closes, the method is invoked with data = NULL
     * and length = 0.
     * @param  data              Pointer to data
     * @param  length            Length of data block in bytes
     */
    virtual void OnReceiveData (const void *data, size_t length) = 0;

    virtual ~FastOS_ProcessRedirectListener () {}
};

class FastOS_ThreadPool;
class FastOS_ApplicationInterface;

/**
 * This class can start a process, redirect standard input, output
 * and error streams, kill process, wait for process to exit
 * and send IPC messages to the process.
 */
class FastOS_ProcessInterface
{
private:
    FastOS_ProcessInterface(const FastOS_ProcessInterface&);
    FastOS_ProcessInterface &operator=(const FastOS_ProcessInterface &);

protected:
    // Hack to achieve 64-bit alignment of
    // FastOS_ProcessInterface pointers (for Sparc)
    double _extradoublehackforalignment;

    char *_cmdLine;
    bool _pipeStdin;

    FastOS_ProcessRedirectListener *_stdoutListener;
    FastOS_ProcessRedirectListener *_stderrListener;

    int _bufferSize;

    FastOS_ThreadPool *GetThreadPool ();

public:
    FastOS_ProcessInterface *_next, *_prev;
    static FastOS_ApplicationInterface *_app;

    /**
     * Call this prior to opening files with fopen to avoid having
     * these file handles inherited by child processes. Remember
     * to call PostfopenNoInherit(x) after fopen. x is the return value
     * of PrefopenNoInherit.
     * @return              Internal data needed to cancel object inheritance.
     */
    static void *PrefopenNoInherit (void) {return NULL;}

    /**
     * Call this after opening files with fopen to avoid having
     * these files handles inherited by child processes. Remember
     * to call PrefopenNoInherit before fopen, and save its return
     * value to be used as a parameter of this method.
     * @param  inheritData    Data returned by PrefopenNoInherit
     * @return                Number of files processed (should be >= 1)
     */
    static int PostfopenNoInherit (void *inheritData)
    {
        (void) inheritData;
        return 1;
    }

    enum Constants
    {
        NOTFOUND_EXITCODE = 65533,		/* Process starter out of sync */
        DETACH_EXITCODE = 65534,		/* Process detached */
        KILL_EXITCODE = 65535,		/* Process killed or failed */
        CONSTEND
    };

    /**
     * Constructor. Does not start the process, use @ref Create or
     * @ref CreateWithShell to actually start the process.
     * @param  cmdLine           Command line
     * @param  pipeStdin         set to true in order to redirect stdin
     * @param  stdoutListener    non-NULL to redirect stdout
     * @param  stderrListener    non-NULL to redirect stderr
     * @param  bufferSize        Size of redirect buffers
     */
    FastOS_ProcessInterface (const char *cmdLine,
                             bool pipeStdin = false,
                             FastOS_ProcessRedirectListener *stdoutListener = NULL,
                             FastOS_ProcessRedirectListener *stderrListener = NULL,
                             int bufferSize = 65535) :
        _extradoublehackforalignment(0.0),
        _cmdLine(NULL),
        _pipeStdin(pipeStdin),
        _stdoutListener(stdoutListener),
        _stderrListener(stderrListener),
        _bufferSize(bufferSize),
        _next(NULL),
        _prev(NULL)
    {
        _cmdLine = strdup(cmdLine);
    }

    /**
     * Destructor.
     * If @ref Wait has not been called yet, it is called here.
     */
    virtual ~FastOS_ProcessInterface ()
    {
        free (_cmdLine);
    };

    /**
     * Create and start the process. If your command line includes
     * commands specific to the shell use @ref CreateWithShell instead.
     *
     * IPC communication currently only supports direct parent/child
     * relationships. If you launch a FastOS application through
     * the shell or some other script/process, the FastOS application
     * might not be a direct child of your process and IPC communication
     * will not work (the rest will work ok, though).
     *
     * This limitation might be removed in the future.
     * @return                   Boolean success / failure
     */
    virtual bool Create() = 0;

    /**
     * Create and start the process using the default OS shell
     * (UNIX: /bin/sh).
     *
     * IPC communication currently only supports direct parent/child
     * relationships. If you launch a FastOS application through
     * the shell or some other script/process, the FastOS application
     * might not be a direct child of your process and IPC communication
     * will not work (the rest will work ok, though).
     *
     * This limitation might be removed in the future.
     * @return                   Boolean success / failure
     */
    virtual bool CreateWithShell() = 0;

    /**
     * If you are redirecting the standard input stream of the process,
     * use this method to write data. To close the input stream,
     * invoke @ref WriteStdin with data=NULL. If the input stream
     * is not redirected, @ref WriteStdin will fail.
     * @param  data              Pointer to data
     * @param  length            Length of data block in bytes
     * @return                   Boolean success / failure
     */
    virtual bool WriteStdin (const void *data, size_t length) = 0;

    /**
     * Terminate the process. !!IMPORTANT LIMITATION!!: There is no guarantee
     * that child processes (of the process to be killed) will be killed
     * as well.
     * @return                   Boolean success / failure
     */
    virtual bool Kill () = 0;

    /**
     * Special case kill used in conjunction with wrapper processes
     * that use process groups to kill all descendant processes.
     * On UNIX, this sends SIGTERM.
     * Only use this method with wrapper processes.
     * @return                    Boolean success / failure
     */
    virtual bool WrapperKill () = 0;

    /**
     * Wait for the process to finish / terminate. This is called
     * automatically by the destructor, but it is recommended that
     * it is called as early as possible to free up resources.
     * @param  returnCode        Pointer to int which will receive
     *                           the process return code.
     * @param  timeOutSeconds    Number of seconds to wait before
     *                           the process is violently killed.
     *                           -1 = infinite wait / no timeout
     * @return                   Boolean success / failure
     */
    virtual bool Wait (int *returnCode, int timeOutSeconds = -1) = 0;

    /**
     * Poll version of @ref Wait.
     * This is basically @ref Wait with a timeout of 0 seconds.
     * The process is not killed if the "timeout" expires.
     * A boolean value, stillRunning, is set to indicate whether
     * the process is still running or not.
     * There is no need to invoke @ref Wait if @ref PollWait
     * indicates that the process is finished.
     * @param returnCode         Pointer to int which will receive
     *                           the process return code.
     * @param stillRunning       Pointer to boolean value which will
     *                           be set to indicate whether the
     *                           process is still running or not.
     * @return                   Boolean success / failure
     */
    virtual bool PollWait (int *returnCode, bool *stillRunning) = 0;

    /**
     * Detach a process, allowing it to exist beyond parent.
     * Only implemented on UNIX platforms.
     *
     * @return                   Boolean success / failure
     */
    virtual bool Detach(void) { return false; }

    /**
     * Check if child is a direct child or a proxied child.
     *
     * @return			Boolean true = direct, false = proxied
     */
    virtual bool GetDirectChild(void) const { return true; }

    /**
     * Specify that child should be a direct child.
     *
     * @return			Boolean success / failure
     */
    virtual bool SetDirectChild(void) { return true; }

    /**
     * Check if child inherits open file descriptors if it's a direct child.
     *
     * @return			Boolean true = inherit, false = close
     */
    virtual bool GetKeepOpenFilesIfDirectChild(void) const { return false; }

    /**
     * Specify that child should inherit open file descriptors from parent
     * if it's a direct child.  This reduces the cost of starting direct
     * children.
     *
     * @return			Boolean true = inherit, false = close
     */
    virtual bool SetKeepOpenFilesIfDirectChild(void) { return false; }

    /**
     * Get process identification number.
     * @return                   Process id
     */
    virtual unsigned int GetProcessId() = 0;

    /**
     * Send IPC message to process.
     * @param  data              Pointer to data
     * @param  length            Length of data block in bytes
     * @return                   Boolean success / failure
     */
    virtual bool SendIPCMessage (const void *data, size_t length) = 0;

    /**
     * Get command line string.
     * @return                   Command line string
     */
    const char *GetCommandLine ()
    {
        return _cmdLine;
    }
};

#include <vespa/fastos/unix_process.h>
typedef FastOS_UNIX_Process FASTOS_PREFIX(Process);

