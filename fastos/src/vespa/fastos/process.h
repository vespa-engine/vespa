// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definitions for FastOS_ProcessInterface and
 * FastOS_ProcessRedirectListener.
 *
 * @author  Oivind H. Danielsen
 */

#pragma once

#include "types.h"
#include <cstddef>

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
     * When the pipe closes, the method is invoked with data = nullptr
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

    char *_cmdLine;
    bool _pipeStdin;

    FastOS_ProcessRedirectListener *_stdoutListener;
    FastOS_ProcessRedirectListener *_stderrListener;

    int _bufferSize;
public:
    FastOS_ProcessInterface *_next, *_prev;
    static FastOS_ApplicationInterface *_app;

    enum Constants
    {
        KILL_EXITCODE = 65535,		/* Process killed or failed */
        CONSTEND
    };

    /**
     * Constructor. Does not start the process, use @ref Create or
     * @ref CreateWithShell to actually start the process.
     * @param  cmdLine           Command line
     * @param  pipeStdin         set to true in order to redirect stdin
     * @param  stdoutListener    non-nullptr to redirect stdout
     * @param  stderrListener    non-nullptr to redirect stderr
     * @param  bufferSize        Size of redirect buffers
     */
    FastOS_ProcessInterface (const char *cmdLine,
                             bool pipeStdin = false,
                             FastOS_ProcessRedirectListener *stdoutListener = nullptr,
                             FastOS_ProcessRedirectListener *stderrListener = nullptr,
                             int bufferSize = 65535);

    /**
     * Destructor.
     * If @ref Wait has not been called yet, it is called here.
     */
    virtual ~FastOS_ProcessInterface ();

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
     * invoke @ref WriteStdin with data=nullptr. If the input stream
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
     * Get process identification number.
     * @return                   Process id
     */
    virtual unsigned int GetProcessId() = 0;

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

