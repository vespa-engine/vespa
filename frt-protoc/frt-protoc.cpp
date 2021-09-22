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

void write_line(io::ZeroCopyOutputStream *target, const std::string &line) {
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
                return;
            } else {
                memcpy(data, src, size);
                left -= size;
                src += size;
            }
        } else {
            perror("target->Next() returned false");
            std::string message = "Error writing output: ";
            message += strerror(errno);
            throw message;
        }
    }
}

void my_generate(const std::string &name,
                 const FileDescriptor &file,
                 GeneratorContext &context)
{
    if (file.dependency_count() > 0
        || file.public_dependency_count() > 0
        || file.weak_dependency_count() > 0)
    {
        std::string message = "Importing dependencies not supported";
        throw message;
    }
    if (file.extension_count() > 0) {
        std::string message = "Extensions not supported";
        throw message;
    }
    if (file.is_placeholder()) {
        std::string message = "Unexpected placeholder file";
        throw message;
    }
    auto filename_ah = "frt_" + name + "_proto_api.h";
    auto api_header = context.Open(filename_ah);
    write_line(api_header, "// API header for protobuf file "+name);
    write_line(api_header, "#pragma once");

    auto filename_ch = "frt_" + name + "_proto_client.h";
    auto cli_header = context.Open(filename_ch);
    write_line(cli_header, "// Client header for protobuf file "+name);
    write_line(cli_header, "#pragma once");
    write_line(cli_header, "#include \"" + filename_ah + "\"");

    auto filename_cc = "frt_" + name + "_proto_client.cpp";
    auto cli_cpp = context.Open(filename_cc);
    write_line(cli_cpp, "// Client implementation for protobuf file "+name);
    write_line(cli_cpp, "#include \"" + filename_ch + "\"");

    auto filename_sh = "frt_" + name + "_proto_server.h";
    auto srv_header = context.Open(filename_sh);
    write_line(srv_header, "// Server header for protobuf file "+name);
    write_line(srv_header, "#pragma once");
    write_line(srv_header, "#include \"" + filename_ah + "\"");

    auto filename_sc = "frt_" + name + "_proto_server.cpp";
    auto srv_cpp = context.Open(filename_sc);
    write_line(srv_cpp, "// Server implementation for protobuf file "+name);
    write_line(srv_cpp, "#include \"" + filename_sh + "\"");
    // TODO: write code for services etc, traversing *file
}

bool MyGen::Generate(const FileDescriptor * file, const std::string & parameter, 
                     GeneratorContext * generator_context, std::string * error) const
{
    std::string name = "[unknown]";
    try {
        if (file == nullptr) {
            std::string m = "No FileDescriptor";
            throw m;
        }
        name = file->name();
        if (name.ends_with(".proto")) {
            name = name.substr(0, name.size() - 6);
        }
        if (generator_context == nullptr) {
            std::string m = "No GeneratorContext";
            throw m;
        }
        if (! parameter.empty()) {
            std::string m = "unknown command line parameter "+parameter;
            throw m;
        }
        my_generate(name, *file, *generator_context);
    } catch (const std::string &message) {
        *error = name + ": " + message;
        return false;
    }
    return true;
}
