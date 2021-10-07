// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/process.h>
#ifndef FASTOS_NO_THREADS
#include <string>
#include <queue>
#include <condition_variable>

namespace vespalib::child_process { class Timer; }

namespace vespalib {
/**
 * @brief Child Process utility class for running external programs
 *
 * Designed for use in unit tests and other places
 * where you need to run, control and communicate with
 * some external program.
 **/
class ChildProcess
{
private:
    class Reader : public FastOS_ProcessRedirectListener
    {
    private:
        std::mutex              _lock;
        std::condition_variable _cond;
        std::queue<std::string> _queue;
        std::string             _data;
        bool                    _gotEOF;
        int                     _waitCnt;
        bool                    _readEOF;

        void OnReceiveData(const void *data, size_t length) override;
        bool hasData();
        bool waitForData(child_process::Timer &timer, std::unique_lock<std::mutex> &lock);
        void updateEOF();

    public:
        Reader();
        ~Reader() override;

        uint32_t read(char *buf, uint32_t len, int msTimeout);
        bool readLine(std::string &line, int msTimeout);
        bool eof() const { return _readEOF; };
    };

    Reader         _reader;
    FastOS_Process _proc;
    bool           _running;
    bool           _failed;
    int            _exitCode;

    void checkProc();

public:
    ChildProcess(const ChildProcess &) = delete;
    ChildProcess &operator=(const ChildProcess &) = delete;
    
    /**
     * @brief Run a child process
     *
     * Starts a process running the given command
     * @param cmd A shell command line to run
     **/
    explicit ChildProcess(const char *cmd);

    /** @brief destructor doing cleanup if needed */
    ~ChildProcess();

    /**
     * @return process id
     **/
    pid_t getPid() { return _proc.GetProcessId(); }

    /**
     * @brief send data as input to the running process
     *
     * The given data will be sent so it becomes
     * available on the running process's standard input.
     *
     * @param buf the data containing len bytes to be sent
     * @param len the number of bytes to send
     * @return true if successful
     **/
    bool write(const char *buf, uint32_t len);

    /**
     * @brief close the running process's standard input
     *
     * when running a program that consumes stdin,
     * make sure to call this method to signal
     * that it can finish.
     * @return true if successful
     **/
    bool close();

    /**
     * @brief read program output
     *
     * If the running program writes data to its standard output, you
     * can and should get the data with read() or readLine() calls.
     *
     * @param buf pointer where data is stored
     * @param len number of bytes to try to read
     * @param msTimeout number of milliseconds to wait for data
     **/
    uint32_t read(char *buf, uint32_t len, int msTimeout = 10000);

    /**
     * @brief read a line of program output
     *
     * See read().
     * @param line reference to a string where a line of program output will be stored
     * @param msTimeout number of milliseconds to wait for data
     * @return true if successful
     **/
    bool readLine(std::string &line, int msTimeout = 10000);

    /**
     * @brief check if the program has finished writing output
     * @return true if standard output from the process is closed
     **/
    bool eof() const { return _reader.eof(); }

    /**
     * @brief wait for the program to exit
     * @param msTimeout milliseconds to wait; the default (-1) will wait forever
     * @return true if the program exited, false on timeout
     **/
    bool wait(int msTimeout = -1);

    /** @brief check if the program is still running */
    bool running();

    /**
     * @brief get the exit code of the started program.
     *
     * Will return the stored exitcode.
     * It assumes that the command is done.
     * @return exit code.
     **/
    int getExitCode();

    /**
     * @brief check if program failed
     *
     * Check if we failed to run the program or the program finished with
     * a non-zero exit status.
     * @return true if something went wrong
     **/
    bool failed();

    /**
     * @brief run a command
     *
     * Utility function that runs the given command, sends input, and
     * loops reading output until the program finishes or the timeout
     * expires.
     * Any final terminating newline will be erased from output,
     * just like for shell backticks.
     *
     * @param input The input the program will receive
     * @param cmd The command to run
     * @param output Any output will be appended to this string
     * @param msTimeout milliseconds timeout; -1 means wait forever for program to finish
     **/
    static bool run(const std::string &input, const char *cmd,
                    std::string &output, int msTimeout = -1);
    /**
     * @brief run a command
     *
     * Utility function that runs the given command with no input, and
     * loops reading output until the program finishes or the timeout
     * expires.
     * Any final terminating newline will be erased from output,
     * just like for shell backticks.
     *
     * @param cmd The command to run
     * @param output Any output will be appended to this string
     * @param msTimeout milliseconds timeout; -1 means wait forever for program to finish
     **/
    static bool run(const char *cmd, std::string &output, int msTimeout = -1);

    /**
     * @brief run a command
     *
     * Utility function that runs the given command with no input, and
     * loops reading output until the program finishes or the timeout
     * expires.  Output (if any) is ignored.
     *
     * @param cmd The command to run
     * @param msTimeout milliseconds timeout; -1 means wait forever for program to finish
     **/
    static bool run(const char *cmd, int msTimeout = -1);
};

} // namespace vespalib

#endif // FASTOS_NO_THREADS
