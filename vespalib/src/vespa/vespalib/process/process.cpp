// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process.h"
#include "pipe.h"
#include "close_all_files.h"

#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/require.h>

#include <sys/types.h>
#include <sys/wait.h>

#include <csignal>
#include <unistd.h>
#include <fcntl.h>

namespace vespalib {

Process::Process(const vespalib::string &cmd, bool capture_stderr)
  : _pid(-1),
    _in(),
    _out(),
    _in_buf(4_Ki),
    _out_buf(4_Ki),
    _eof(false)
{
    Pipe pipe_in = Pipe::create();
    Pipe pipe_out = Pipe::create();
    REQUIRE(pipe_in.valid() && pipe_out.valid());
    pid_t pid = fork();
    REQUIRE(pid != -1);
    if (pid == 0) {
        dup2(pipe_in.read_end.fd(), STDIN_FILENO);
        dup2(pipe_out.write_end.fd(), STDOUT_FILENO);
        if (capture_stderr) {
            dup2(pipe_out.write_end.fd(), STDERR_FILENO);
        } else {
            int dev_null = open("/dev/null", O_WRONLY);
            dup2(dev_null, STDERR_FILENO);
            ::close(dev_null);
        }
        close_all_files();
        const char *sh_args[4];
        sh_args[0] = "sh";
        sh_args[1] = "-c";
        sh_args[2] = cmd.c_str();
        sh_args[3] = nullptr;
        execv("/bin/sh", const_cast<char * const *>(sh_args));
        abort();
    } else {
        _pid = pid;
        pipe_in.read_end.reset();
        pipe_out.write_end.reset();
        _in.reset(pipe_in.write_end.release());
        _out.reset(pipe_out.read_end.release());
    }
}

Memory
Process::obtain()
{
    if ((_out_buf.obtain().size == 0) && !_eof) {
        WritableMemory buf = _out_buf.reserve(4_Ki);
        ssize_t res = read(_out.fd(), buf.data, buf.size);
        while ((res == -1) && (errno == EINTR)) {
            res = read(_out.fd(), buf.data, buf.size);
        }
        REQUIRE(res >= 0);
        if (res > 0) {
            _out_buf.commit(res);
        } else {
            _eof = true;
        }
    }
    return _out_buf.obtain();
}

Input &
Process::evict(size_t bytes)
{
    _out_buf.evict(bytes);
    return *this;
}

WritableMemory
Process::reserve(size_t bytes)
{
    return _in_buf.reserve(bytes);
}

Output &
Process::commit(size_t bytes)
{
    _in_buf.commit(bytes);
    Memory buf = _in_buf.obtain();
    while (buf.size > 0) {
        ssize_t res = write(_in.fd(), buf.data, buf.size);
        while ((res == -1) && (errno == EINTR)) {
            res = write(_in.fd(), buf.data, buf.size);
        }
        REQUIRE(res > 0);
        _in_buf.evict(res);
        buf = _in_buf.obtain();
    }
    return *this;
}

vespalib::string
Process::read_line() {
    vespalib::string line;
    for (auto mem = obtain(); (mem.size > 0); mem = obtain()) {
        for (size_t i = 0; i < mem.size; ++i) {
            if (mem.data[i] == '\n') {
                evict(i + 1);
                return line;
            } else {
                line.push_back(mem.data[i]);
            }
        }
        evict(mem.size);
    }
    return line;
}

int
Process::join()
{
    pid_t res;
    int status;
    do {
        res = waitpid(_pid, &status, 0);
    } while ((res == -1) && (errno == EINTR));
    REQUIRE_EQ(res, _pid);
    _pid = -1; // make invalid
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    return (0x80000000 | status);
}

Process::~Process()
{
    if (valid()) {
        kill(_pid, SIGKILL);
        join();
    }
}

bool
Process::run(const vespalib::string &cmd, vespalib::string &output)
{
    Process proc(cmd);
    proc.close();
    for (auto mem = proc.obtain(); mem.size > 0; mem = proc.obtain()) {
        output.append(mem.data, mem.size);
        proc.evict(mem.size);
    }
    if (!output.empty() && (output.find('\n') == (output.size() - 1))) {
        output.pop_back();
    }
    return (proc.join() == 0);
}

bool
Process::run(const vespalib::string &cmd)
{
    vespalib::string ignore_output;
    return run(cmd, ignore_output);
}

} // namespace vespalib
