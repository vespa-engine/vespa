// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "addr_to_symbol.h"
#include <vespa/vespalib/util/classname.h>

#include <dlfcn.h>
#include <llvm/Object/ObjectFile.h>

using vespalib::demangle;
using llvm::object::ObjectFile;

namespace vespalib::eval {

namespace {

void my_local_test_symbol() {}

} // <unnamed>

vespalib::string addr_to_symbol(const void *addr) {
    if (addr == nullptr) {
        return {"<nullptr>"};
    }
    Dl_info info;
    memset(&info, 0, sizeof(info));
    if (dladdr(addr, &info) == 0) {
        // address not in any shared object
        return {"<invalid>"};
    }
    if (info.dli_sname != nullptr) {
        // address of global symbol
        return demangle(info.dli_sname);
    }
    // find addr offset into shared object
    uint64_t offset = ((const char *)addr) - ((const char *)info.dli_fbase);
    // use llvm to look up local symbols...
    auto file = ObjectFile::createObjectFile(info.dli_fname);
    if (!file) {
        return {"<object_error>"};
    }
    auto symbols = file.get().getBinary()->symbols();
    for (const auto &symbol: symbols) {
        auto sym_name = symbol.getName();
        auto sym_addr = symbol.getAddress();
        if (sym_name && sym_addr && (*sym_addr == offset)) {
            return demangle(sym_name->str().c_str());
        }
    }
    // could not resolve symbol
    return {"<unknown>"};
}

const void *get_addr_of_local_test_symbol() {
    return (const void *) my_local_test_symbol;
}

}
