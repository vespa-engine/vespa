// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/child_process.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <vespa/vespalib/io/mapped_file_input.h>

using namespace vespalib;

std::string readFile(const std::string &filename) {
    TEST_STATE(filename.c_str());
    MappedFileInput file(filename);
    ASSERT_TRUE(file.valid());
    Memory data = file.get();
    return std::string(data.data, data.size);
}

std::string runCommand(const std::string &cmd) {
    std::string out;
    ASSERT_TRUE(ChildProcess::run(cmd.c_str(), out));
    return out;
}

void insertExample(const std::string &name, const std::string &src_dir) {
    std::string str = runCommand(make_string("%s/make_example.sh %s", src_dir.c_str(),
                                             name.c_str()));
    fprintf(stdout, "%s", str.c_str());
}

void insertSource(const std::string &name, const std::string &src_dir) {
    std::string str = runCommand(make_string("%s/make_source.sh %s", src_dir.c_str(),
                                             name.c_str()));
    fprintf(stdout, "%s", str.c_str());
}

void insertFile(const std::string &name, const std::string &src_dir) {
    std::string str = readFile(src_dir + "/" + name);
    fprintf(stdout, "%s", str.c_str());
}

TEST_MAIN_WITH_PROCESS_PROXY() {
    std::string pre("[insert:");
    std::string example("example:");
    std::string source("source:");
    std::string file("file:");
    std::string post("]\n");

    size_t pos = 0;
    size_t end = 0;
    size_t cursor = 0;
    std::string input = readFile(TEST_PATH("tutorial_source.html"));
    while ((pos = input.find(pre, cursor)) < input.size() &&
           (end = input.find(post, pos)) < input.size())
    {
        fprintf(stdout, "%.*s", (int)(pos - cursor), (input.data() + cursor));
        pos += pre.size();
        if (input.find(example, pos) == pos) {
            pos += example.size();
            insertExample(std::string((input.data() + pos), (end - pos)), TEST_PATH(""));
        } else if (input.find(source, pos) == pos) {
            pos += source.size();
            insertSource(std::string((input.data() + pos), (end - pos)), TEST_PATH(""));
        } else if (input.find(file, pos) == pos) {
            pos += file.size();
            insertFile(std::string((input.data() + pos), (end - pos)), TEST_PATH(""));
        } else {
            std::string str((input.data() + pos), (end - pos));
            TEST_FATAL(make_string("invalid directive >%s<", str.c_str()).c_str());
        }
        cursor = end + post.size();
    }
    fprintf(stdout, "%.*s", (int)(input.size() - cursor), (input.data() + cursor));
}
