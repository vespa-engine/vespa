// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/memory.h>
#include <vespa/vespalib/data/writable_memory.h>
#include <vespa/vespalib/data/input.h>
#include <vespa/vespalib/data/output.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/process/process.h>
#include <functional>

namespace vespalib::eval::test {

/**
 * Simple adapter making stdin act as an Input.
 **/
class StdIn : public Input {
private:
    bool _eof = false;
    SimpleBuffer _input;
public:
    ~StdIn() {}
    Memory obtain() override;
    Input &evict(size_t bytes) override;
};

/**
 * Simple adapter making stdout act as an Output.
 **/
class StdOut : public Output {
private:
    SimpleBuffer _output;
public:
    ~StdOut() {}
    WritableMemory reserve(size_t bytes) override;
    Output &commit(size_t bytes) override;
};

/**
 * A command run as a child process that acts as a server reading json
 * from stdin and writing json to stdout.
 **/
class ServerCmd {
private:
    Process _child;
    vespalib::string _basename;
    bool _closed;
    bool _exited;
    int _exit_code;

    void maybe_close();
    void maybe_exit();

    void dump_string(const char *prefix, const vespalib::string &str);
    void dump_message(const char *prefix, const Slime &slime);

public:
    struct capture_stderr_tag{};
    ServerCmd(vespalib::string cmd);
    ServerCmd(vespalib::string cmd, capture_stderr_tag);
    ~ServerCmd();
    Slime invoke(const Slime &req);
    vespalib::string write_then_read_all(const vespalib::string &input);
    int shutdown();
};

/**
 * Read one line at a time from an input
 **/
class LineReader {
private:
    Input &_input;
public:
    LineReader(Input &input) : _input(input) {}
    bool read_line(vespalib::string &line);
};

/**
 * Skip whitespaces from the input and return true if eof was reached.
 **/
bool look_for_eof(Input &input);

/**
 * Read from the input until eof is reached (data is discarded).
 **/
void read_until_eof(Input &input);

/**
 * Write a slime structure as compact json with a trailing newline.
 **/
void write_compact(const Slime &slime, Output &out);

/**
 * Write tests to the given output. Will write a minimal summary when
 * destructed. The current test will be flushed to the output when a
 * new test is created or right before writing the summary. The
 * 'create' function will return an object. A test may be any object
 * containing at least one field, but a test may not contain the
 * 'num_tests' field (to avoid confusion with the trailing summary)
 **/
class TestWriter {
private:
    Output &_out;
    Slime   _test;
    size_t  _num_tests;
    void maybe_write_test();
public:
    TestWriter(Output &output);
    slime::Cursor &create();
    ~TestWriter();
};

/**
 * Reads all tests from 'in' as well as the trailing summary. The
 * provided 'handle_test' function will be called for each test and
 * the 'handle_summary' function will be called once at the end. This
 * function also does some minor consistency checking.
 **/
void for_each_test(Input &in,
                   const std::function<void(Slime&)> &handle_test,
                   const std::function<void(Slime&)> &handle_summary);

} // namespace vespalib::eval::test
