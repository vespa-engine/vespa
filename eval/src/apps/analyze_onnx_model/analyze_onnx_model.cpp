// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/onnx/onnx_wrapper.h>
#include <vespa/vespalib/util/guard.h>

using vespalib::FilePointer;
using namespace vespalib::eval;

bool read_line(FilePointer &file, vespalib::string &line) {
    char line_buffer[1024];
    char *res = fgets(line_buffer, sizeof(line_buffer), file.fp());
    if (res == nullptr) {
        line.clear();
        return false;
    }
    line = line_buffer;
    while (!line.empty() && isspace(line[line.size() - 1])) {
        line.pop_back();
    }
    return true;
}

void extract(const vespalib::string &str, const vespalib::string &prefix, vespalib::string &dst) {
    if (starts_with(str, prefix)) {
        size_t pos = prefix.size();
        while ((str.size() > pos) && isspace(str[pos])) {
            ++pos;
        }
        dst = str.substr(pos);
    }
}

void report_memory_usage(const vespalib::string &desc) {
    vespalib::string vm_size = "unknown";
    vespalib::string vm_rss = "unknown";
    vespalib::string line;
    FilePointer file(fopen("/proc/self/status", "r"));
    while (read_line(file, line)) {
        extract(line, "VmSize:", vm_size);
        extract(line, "VmRSS:", vm_rss);
    }
    fprintf(stderr, "vm_size: %s, vm_rss: %s (%s)\n", vm_size.c_str(), vm_rss.c_str(), desc.c_str());
}

int usage(const char *self) {
    fprintf(stderr, "usage: %s <onnx-model>\n", self);
    fprintf(stderr, "  load onnx model and report memory usage\n");
    return 1;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        return usage(argv[0]);
    }
    report_memory_usage("before loading model");
    Onnx onnx(argv[1], Onnx::Optimize::ENABLE);
    report_memory_usage("after loading model");
    return 0;
}
