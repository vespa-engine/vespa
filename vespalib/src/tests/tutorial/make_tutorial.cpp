// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/slaveproc.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

using namespace vespalib;

std::string readFile(const std::string &filename) {
    TEST_STATE(filename.c_str());
    std::string ret;
    struct stat info;
    int fd = open(filename.c_str(), O_RDONLY);
    ASSERT_TRUE(fd >= 0 && fstat(fd, &info) == 0);
    char *data = (char*)(mmap(0, info.st_size, PROT_READ, MAP_SHARED, fd, 0));
    ASSERT_NOT_EQUAL(data, MAP_FAILED);
    ret = std::string(data, info.st_size);
    munmap(data, info.st_size);
    close(fd);
    return ret;
}

std::string runCommand(const std::string &cmd) {
    std::string out;
    ASSERT_TRUE(SlaveProc::run(cmd.c_str(), out));
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
    const std::string src_dir = getenv("SOURCE_DIRECTORY") ? getenv("SOURCE_DIRECTORY") : ".";

    std::string pre("[insert:");
    std::string example("example:");
    std::string source("source:");
    std::string file("file:");
    std::string post("]\n");

    size_t pos = 0;
    size_t end = 0;
    size_t cursor = 0;
    std::string input = readFile(src_dir + "/tutorial_source.html");
    while ((pos = input.find(pre, cursor)) < input.size() &&
           (end = input.find(post, pos)) < input.size())
    {
        fprintf(stdout, "%.*s", (int)(pos - cursor), (input.data() + cursor));
        pos += pre.size();
        if (input.find(example, pos) == pos) {
            pos += example.size();
            insertExample(std::string((input.data() + pos), (end - pos)), src_dir);
        } else if (input.find(source, pos) == pos) {
            pos += source.size();
            insertSource(std::string((input.data() + pos), (end - pos)), src_dir);
        } else if (input.find(file, pos) == pos) {
            pos += file.size();
            insertFile(std::string((input.data() + pos), (end - pos)), src_dir);
        } else {
            std::string str((input.data() + pos), (end - pos));
            TEST_FATAL(make_string("invalid directive >%s<", str.c_str()).c_str());
        }
        cursor = end + post.size();
    }
    fprintf(stdout, "%.*s", (int)(input.size() - cursor), (input.data() + cursor));
}
