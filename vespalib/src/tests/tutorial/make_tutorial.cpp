// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/process/process.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <vespa/vespalib/io/mapped_file_input.h>

using namespace vespalib;

vespalib::string readFile(const vespalib::string &filename) {
    TEST_STATE(filename.c_str());
    MappedFileInput file(filename);
    ASSERT_TRUE(file.valid());
    Memory data = file.get();
    return vespalib::string(data.data, data.size);
}

vespalib::string runCommand(const vespalib::string &cmd) {
    vespalib::string out;
    ASSERT_TRUE(Process::run(cmd, out));
    return out;
}

void insertExample(const vespalib::string &name, const vespalib::string &src_dir) {
    vespalib::string str = runCommand(make_string("%s/make_example.sh %s", src_dir.c_str(),
                                             name.c_str()));
    fprintf(stdout, "%s", str.c_str());
}

void insertSource(const vespalib::string &name, const vespalib::string &src_dir) {
    vespalib::string str = runCommand(make_string("%s/make_source.sh %s", src_dir.c_str(),
                                             name.c_str()));
    fprintf(stdout, "%s", str.c_str());
}

void insertFile(const vespalib::string &name, const vespalib::string &src_dir) {
    vespalib::string str = readFile(src_dir + "/" + name);
    fprintf(stdout, "%s", str.c_str());
}

TEST_MAIN() {
    vespalib::string pre("[insert:");
    vespalib::string example("example:");
    vespalib::string source("source:");
    vespalib::string file("file:");
    vespalib::string post("]\n");

    size_t pos = 0;
    size_t end = 0;
    size_t cursor = 0;
    vespalib::string input = readFile(TEST_PATH("tutorial_source.html"));
    while ((pos = input.find(pre, cursor)) < input.size() &&
           (end = input.find(post, pos)) < input.size())
    {
        fprintf(stdout, "%.*s", (int)(pos - cursor), (input.data() + cursor));
        pos += pre.size();
        if (input.find(example, pos) == pos) {
            pos += example.size();
            insertExample(vespalib::string((input.data() + pos), (end - pos)), TEST_PATH(""));
        } else if (input.find(source, pos) == pos) {
            pos += source.size();
            insertSource(vespalib::string((input.data() + pos), (end - pos)), TEST_PATH(""));
        } else if (input.find(file, pos) == pos) {
            pos += file.size();
            insertFile(vespalib::string((input.data() + pos), (end - pos)), TEST_PATH(""));
        } else {
            vespalib::string str((input.data() + pos), (end - pos));
            TEST_FATAL(make_string("invalid directive >%s<", str.c_str()).c_str());
        }
        cursor = end + post.size();
    }
    fprintf(stdout, "%.*s", (int)(input.size() - cursor), (input.data() + cursor));
}
