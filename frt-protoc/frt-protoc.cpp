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
        fprintf(stderr, "try write %d - data '%s'\n", size, src);
        if (target->Next(&data, &size)) {
            if (size == 0) continue;
            if (size >= left) {
                fprintf(stderr, "last with size %d - data '%s'\n", size, src);
                memcpy(data, src, left);
                char * buf = static_cast<char *>(data);
                buf[left - 1] = '\n';
                if (size > left) {
                    target->BackUp(size - left);
                }
                left = 0;
            } else {
                fprintf(stderr, "next with size %d - data '%.*s'\n", size, size, src);
                memcpy(data, src, size);
                left -= size;
                src += size;
            }
        } else {
            perror("target->Next() returned false");
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
    const auto & name = file->name();
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
    std::string filename = "frt_" + name + "_proto_api.h";
    auto api_header = generator_context->Open(filename);
    WL(api_header, "// API header for protobuf file "+name);
    filename = "frt_" + name + "_proto_client.h";
    auto cli_header = generator_context->Open(filename);
    WL(cli_header, "// Client header for protobuf file "+name);
    filename = "frt_" + name + "_proto_client.cpp";
    auto cli_cpp = generator_context->Open(filename);
    WL(cli_cpp, "// Client implementation for protobuf file "+name);
    return true;
}
