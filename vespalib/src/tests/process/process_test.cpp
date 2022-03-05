// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/process/process.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::Input;
using vespalib::Output;
using vespalib::Process;
using vespalib::SimpleBuffer;
using vespalib::Slime;
using vespalib::slime::JsonFormat;

//-----------------------------------------------------------------------------

TEST(ProcessTest, simple_run_ignore_output) {
    EXPECT_TRUE(Process::run("echo foo"));
}

TEST(ProcessTest, simple_run_ignore_output_failure) {
    EXPECT_FALSE(Process::run("false"));
}

//-----------------------------------------------------------------------------

TEST(ProcessTest, simple_run) {
    vespalib::string out;
    EXPECT_TRUE(Process::run("/bin/echo -n foo", out));
    EXPECT_EQ(out, "foo");
}

TEST(ProcessTest, simple_run_failure) {
    vespalib::string out;
    EXPECT_FALSE(Process::run("/bin/echo -n foo; false", out));
    EXPECT_EQ(out, "foo");
}

TEST(ProcessTest, simple_run_strip_single_line_trailing_newline) {
    vespalib::string out;
    EXPECT_TRUE(Process::run("echo foo", out));
    EXPECT_EQ(out, "foo");
}

TEST(ProcessTest, simple_run_dont_strip_multi_line_output) {
    vespalib::string out;
    EXPECT_TRUE(Process::run("perl -e 'print \"foo\\n\\n\"'", out));
    EXPECT_EQ(out, "foo\n\n");
}

//-----------------------------------------------------------------------------

TEST(ProcessTest, proc_failure) {
    Process proc("false");
    EXPECT_EQ(proc.obtain().size, 0);
    EXPECT_NE(proc.join(), 0);
}

TEST(ProcessTest, proc_kill) {
    {
        Process proc("sleep 60");
        (void) proc;
    }
}

//-----------------------------------------------------------------------------

vespalib::string line1 = "this is a line";
vespalib::string line2 = "this is also a line";
vespalib::string line3 = "this is last line";

TEST(ProcessTest, read_line) {
    Process proc("cat");
    for (const vespalib::string &line: {std::cref(line1), std::cref(line2), std::cref(line3)}) {
        auto mem = proc.reserve(line.size() + 1);
        memcpy(mem.data, line.data(), line.size());
        mem.data[line.size()] = '\n';
        proc.commit(line.size() + 1);
        fprintf(stderr, "write: %s\n", line.c_str());
        auto res = proc.read_line();
        fprintf(stderr, "read: %s\n", line.c_str());
        EXPECT_EQ(res, line);
        EXPECT_FALSE(proc.eof());
    }
    proc.close();
    EXPECT_EQ(proc.read_line(), "");
    EXPECT_TRUE(proc.eof());
    EXPECT_EQ(proc.join(), 0);
}

TEST(ProcessTest, read_line_without_newline) {
    Process proc("cat");
    const auto &line = line3;
    auto mem = proc.reserve(line.size());
    memcpy(mem.data, line.data(), line.size());
    proc.commit(line.size());
    fprintf(stderr, "write: %s\n", line.c_str());
    proc.close(); // need eof to flush line
    auto res = proc.read_line();
    fprintf(stderr, "read: %s\n", line.c_str());
    EXPECT_EQ(res, line);
    EXPECT_TRUE(proc.eof());
    EXPECT_EQ(proc.join(), 0);
}

//-----------------------------------------------------------------------------

void write_slime(const Slime &slime, Output &out) {
    JsonFormat::encode(slime, out, true);
    out.reserve(1).data[0] = '\n';
    out.commit(1);
}

Slime read_slime(Input &input) {
    Slime slime;
    EXPECT_TRUE(JsonFormat::decode(input, slime));
    return slime;
}

vespalib::string to_json(const Slime &slime) {
    SimpleBuffer buf;
    JsonFormat::encode(slime, buf, true);
    return buf.get().make_string();
}

Slime from_json(const vespalib::string &json) {
    Slime slime;
    EXPECT_TRUE(JsonFormat::decode(json, slime));
    return slime;
}

Slime obj1 = from_json("[1,2,3]");
Slime obj2 = from_json("{a:1,b:2,c:3}");
Slime obj3 = from_json("{a:1,b:2,c:3,d:[1,2,3]}");

TEST(ProcessTest, read_write_test) {
    Process proc("cat");
    for (const Slime &obj: {std::cref(obj1), std::cref(obj2), std::cref(obj3)}) {
        write_slime(obj, proc);
        fprintf(stderr, "write: %s\n", to_json(obj).c_str());
        auto res = read_slime(proc);
        fprintf(stderr, "read: %s\n", to_json(res).c_str());
        EXPECT_EQ(res, obj);
    }
    proc.close();
    EXPECT_EQ(proc.join(), 0);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
