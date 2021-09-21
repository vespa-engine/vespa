#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/compiler/code_generator.h>
#include <google/protobuf/io/zero_copy_stream.h>
#include <google/protobuf/descriptor.h>

using namespace google::protobuf;
using namespace google::protobuf::compiler;

class MyGen : public CodeGenerator {
public:
    bool Generate(const FileDescriptor * file, const std::string & parameter,
              GeneratorContext * generator_context, std::string * error) const override;
    ~MyGen();
};

MyGen::~MyGen() = default;

int main(int argc, char* argv[]) {
    MyGen generator;
    return PluginMain(argc, argv, &generator);
}

bool write_line(io::ZeroCopyOutputStream *target, const std::string &line) {
    void *data = nullptr;
    const char *src = line.c_str();
    int left = line.size() + 1;
    while (left > 0) {
        int size = left;
        if (target->Next(&data, &size)) {
            if (size == 0) continue;
            if (size >= left) {
                memcpy(data, src, left);
                char * buf = static_cast<char *>(data);
                buf[left - 1] = '\n';
                if (size > left) {
                    target->BackUp(size - left);
                }
                left = 0;
            } else {
                memcpy(data, src, size);
                left -= size;
                src += size;
            }
        } else {
            perror("target->Next() returned false");
            return false;
        }
    }
    return true;
}

#define WL(target, line) do { \
    if (! write_line(target, line)) { \
        *error = name + ": Error writing output: "+strerror(errno); \
        return false; \
    } \
} while (0)

bool MyGen::Generate(const FileDescriptor * file, const std::string & parameter, 
                     GeneratorContext * generator_context, std::string * error) const
{
    if (file == nullptr) {
        *error = "No FileDescriptor";
        return false;
    }
    auto name = file->name();
    if (name.ends_with(".proto")) {
        name = name.substr(0, name.size() - 6);
    }
    if (! parameter.empty()) {
        *error = name + ": unknown command line parameter "+parameter;
        return false;
    }
    if (file->dependency_count() > 0
        || file->public_dependency_count() > 0
        || file->weak_dependency_count() > 0)
    {
        *error = name + ": Importing dependencies not supported";
        return false;
    }
    if (file->extension_count() > 0) {
        *error = name + ": Extensions not supported";
        return false;
    }
    if (file->is_placeholder()) {
        *error = name + ": Unexpected placeholder file";
        return false;
    }
    auto filename_ah = "frt_" + name + "_proto_api.h";
    auto api_header = generator_context->Open(filename_ah);
    WL(api_header, "// API header for protobuf file "+name);
    WL(api_header, "#pragma once");
    auto filename_ch = "frt_" + name + "_proto_client.h";
    auto cli_header = generator_context->Open(filename_ch);
    WL(cli_header, "// Client header for protobuf file "+name);
    WL(cli_header, "#pragma once");
    WL(cli_header, "#include \"" + filename_ah + "\"");
    auto filename_cc = "frt_" + name + "_proto_client.cpp";
    auto cli_cpp = generator_context->Open(filename_cc);
    WL(cli_cpp, "// Client implementation for protobuf file "+name);
    WL(cli_cpp, "#include \"" + filename_ch + "\"");
    // TODO: write code for services etc, traversing *file
    return true;
}
