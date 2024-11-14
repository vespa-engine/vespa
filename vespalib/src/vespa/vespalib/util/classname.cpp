// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/classname.h>
#include <cxxabi.h>
#include <cstdlib>

namespace vespalib {

std::string demangle(const char * native) {
    int status = 0;
    size_t size = 0;
    char *unmangled = abi::__cxa_demangle(native, nullptr, &size, &status);
    if (unmangled == nullptr) {
        return ""; // Demangling failed for some reason. TODO return `native` instead?
    }
    std::string result(unmangled);
    free(unmangled);
    return result;
}

}
