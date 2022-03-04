// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/guard.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/data/input.h>
#include <vespa/vespalib/data/output.h>
#include <vespa/vespalib/data/smart_buffer.h>

#include <unistd.h>

namespace vespalib {

/**
 * A simple low-level class enabling you to start a process by running
 * a command in the shell. Use 'close' to close the stdin pipe from
 * the outside. Use 'join' to wait for process completion and exit
 * status. The destructor will use SIGKILL to stop the process if it
 * was not joined. The Process class implements the Input/Output
 * interfaces to interact with stdout/stdin. If stderr is captured, it
 * is merged with stdout.
 *
 * This class is primarily intended for use in tests. It has liberal
 * REQUIRE usage and will crash when something is not right.
 **/
class Process : public Output, public Input
{
private:
    pid_t _pid;
    FileDescriptor _in;
    FileDescriptor _out;
    SmartBuffer _in_buf;
    SmartBuffer _out_buf;
    bool _eof;

public:
    Process(const vespalib::string &cmd, bool capture_stderr = false);
    pid_t pid() const { return _pid; }
    bool valid() const { return (_pid > 0); }
    void close() { _in.reset(); }
    Memory obtain() override;                      // Input (stdout)
    Input &evict(size_t bytes) override;           // Input (stdout)
    WritableMemory reserve(size_t bytes) override; // Output (stdin)
    Output &commit(size_t bytes) override;         // Output (stdin)
    vespalib::string read_line();
    bool eof() const { return _eof; }
    int join();
    ~Process();

    static bool run(const vespalib::string &cmd, vespalib::string &output);
    static bool run(const vespalib::string &cmd);
};

} // namespace vespalib
