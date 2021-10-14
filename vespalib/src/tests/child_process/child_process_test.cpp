// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/child_process.h>

using vespalib::ChildProcess;

TEST("simple run, ignore output") {        
    EXPECT_TRUE(ChildProcess::run("echo foo"));
}

TEST("simple run, ignore output, failure") {
    EXPECT_TRUE(!ChildProcess::run("false"));
}

TEST("simple run, ignore output, timeout") {
    EXPECT_TRUE(!ChildProcess::run("exec sleep 60", 10));
}

TEST("simple run") {
    std::string out;
    EXPECT_TRUE(ChildProcess::run("/bin/echo -n foo", out));
    EXPECT_EQUAL(out, "foo");
}

TEST("simple run, strip single-line trailing newline") {
    std::string out;
    EXPECT_TRUE(ChildProcess::run("echo foo", out));
    EXPECT_EQUAL(out, "foo");
}

TEST("simple run, don't strip multi-line output") {
    std::string out;
    EXPECT_TRUE(ChildProcess::run("perl -e 'print \"foo\\n\\n\"'", out));
    EXPECT_EQUAL(out, "foo\n\n");
}

TEST("simple run with input") {
    std::string in = "bar";
    std::string out;
    EXPECT_TRUE(ChildProcess::run(in, "cat", out));
    EXPECT_EQUAL(out, "bar");
}

TEST("simple run with input, strip single-line trailing newline") {
    std::string in = "bar\n";
    std::string out;
    EXPECT_TRUE(ChildProcess::run(in, "cat", out));
    EXPECT_EQUAL(out, "bar");
}

TEST("simple run with input, don't strip multi-line output") {
    std::string in = "bar\n\n";
    std::string out;
    EXPECT_TRUE(ChildProcess::run(in, "cat", out));
    EXPECT_EQUAL("bar\n\n", out);
}

TEST_MT("simple run, partial output due to timeout", 2) {
    std::string out;
    std::vector<size_t> timeouts({150, 300, 3000, 6000, 60000});
    const char *my_cmd = "exec perl -e '$| = 1; print \"foo\\\n\"; sleep(600); print \"bar\\\n\"'";
    for (size_t timeout: timeouts) {
        fprintf(stderr, "... verifying partial output with%s input (timeout = %zu)\n",
                (thread_id == 0) ? "out" : "", timeout);
        if (thread_id == 0) {
            out.clear();
            EXPECT_TRUE(!ChildProcess::run(my_cmd, out, timeout));
        } else {
            out.clear();
            std::string in = "ignored\n";
            EXPECT_TRUE(!ChildProcess::run(in, my_cmd, out, timeout));
        }
        if (out == "foo") {
            break;
        }
    }
    EXPECT_EQUAL(out, "foo");
}

TEST("proc failure") {
    ChildProcess proc("false");
    // read with length 0 will wait for output
    EXPECT_TRUE(proc.read(NULL, 0) == 0);
    EXPECT_TRUE(proc.wait(60000));
    EXPECT_TRUE(!proc.running());
    EXPECT_TRUE(proc.failed());
}

TEST("basic read/write") {
    int x;
    int read;
    char buf[64];
    ChildProcess proc("cat");

    EXPECT_TRUE(proc.running());
    EXPECT_TRUE(!proc.failed());
    EXPECT_TRUE(proc.write("foo", 3));
    for (x = 0, read = 0; x < 10 && read < 3; ++x) {
        read += proc.read(buf + read, sizeof(buf) - read);
    }
    EXPECT_TRUE(read == 3 && memcmp(buf, "foo", 3) == 0);
    EXPECT_TRUE(proc.write("bar!", 4));
    for (x = 0, read = 0; x < 10 && read < 4; ++x) {
        read += proc.read(buf + read, sizeof(buf) - read);
    }
    EXPECT_TRUE(read == 4 && memcmp(buf, "bar!", 4) == 0);
    EXPECT_TRUE(!proc.eof());  // not eof yet
    EXPECT_TRUE(proc.close()); // close stdin
    EXPECT_TRUE(!proc.eof());  // eof not detected yet
    EXPECT_TRUE(proc.read(buf, sizeof(buf)) == 0);
    EXPECT_TRUE(proc.eof());
    EXPECT_TRUE(proc.read(buf, sizeof(buf)) == 0);
    EXPECT_TRUE(proc.wait(60000));
    EXPECT_TRUE(!proc.running());
    EXPECT_TRUE(!proc.failed());
}

TEST("continuos run, readLine") {
    std::string str;
    ChildProcess proc("cat");

    EXPECT_TRUE(proc.running());
    EXPECT_TRUE(!proc.failed());
    EXPECT_TRUE(proc.write("foo\n", 4));
    EXPECT_TRUE(proc.readLine(str));
    EXPECT_EQUAL(str, "foo");
    EXPECT_TRUE(proc.write("bar!\n", 5));
    EXPECT_TRUE(proc.readLine(str));
    EXPECT_EQUAL(str, "bar!");
    EXPECT_TRUE(!proc.eof());  // not eof yet
    EXPECT_TRUE(proc.close()); // close stdin
    EXPECT_TRUE(!proc.eof());  // eof not detected yet
    EXPECT_TRUE(!proc.readLine(str));
    EXPECT_EQUAL(str, "");
    EXPECT_TRUE(proc.eof());
    EXPECT_TRUE(!proc.readLine(str));
    EXPECT_EQUAL(str, "");
    EXPECT_TRUE(proc.wait(60000));
    EXPECT_TRUE(!proc.running());
    EXPECT_TRUE(!proc.failed());
}

TEST("readLine, eof flushes last line") {
    std::string str;
    ChildProcess proc("cat");

    EXPECT_TRUE(proc.running());
    EXPECT_TRUE(!proc.failed());
    EXPECT_TRUE(proc.write("foo\n", 4));
    EXPECT_TRUE(proc.readLine(str));
    EXPECT_EQUAL(str, "foo");
    EXPECT_TRUE(proc.write("bar!", 4));
    EXPECT_TRUE(!proc.eof());  // not eof yet
    EXPECT_TRUE(proc.close()); // close stdin
    EXPECT_TRUE(!proc.eof());  // eof not detected yet
    EXPECT_TRUE(proc.readLine(str));
    EXPECT_EQUAL(str, "bar!");
    EXPECT_TRUE(proc.eof());
    EXPECT_TRUE(!proc.readLine(str));
    EXPECT_EQUAL(str, "");
    EXPECT_TRUE(proc.wait(60000));
    EXPECT_TRUE(!proc.running());
    EXPECT_TRUE(!proc.failed());
}

TEST("long continuos run, readLine") {
    std::string in;
    std::string out;
    ChildProcess proc("cat");

    EXPECT_TRUE(proc.running());
    EXPECT_TRUE(!proc.failed());
    for (uint32_t i = 0; i < 10000; ++i) {
        char num[32];
        sprintf(num, "%d", i);
        in.assign("long continous run, line ");
        in.append(num).append("\n");
        EXPECT_TRUE(proc.write(in.data(), in.length()));
        in.erase(in.size() - 1, 1);
        EXPECT_TRUE(proc.readLine(out));
        EXPECT_EQUAL(in, out);
    }
    EXPECT_TRUE(proc.running());
    EXPECT_TRUE(!proc.failed());
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
