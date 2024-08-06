// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <dlfcn.h>
#include <cctype>
#include <vespamalloc/util/callstack.h>
#include <string>
#include <cxxabi.h>

namespace vespamalloc {

namespace {

std::string
demangle(const char *native) {
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


std::string
dlAddr(const void *func) {
    static std::string _unknown = "UNKNOWN";
    Dl_info info;
    int ret = dladdr(func, &info);
    if (ret != 0) {
        return demangle(info.dli_sname);
    }
    return _unknown;
}

}

namespace {
void
verifyAndCopy(const void *addr, char *v, size_t sz) {
    size_t pos(0);
    std::string sym = dlAddr(addr);
    for (; (pos < sym.size()) && (pos < sz - 1); pos++) {
        char c(sym[pos]);
        v[pos] = std::isprint(static_cast<unsigned char>(c)) ? c : '.';
    }
    v[pos] = '\0';
}

}

void
StackReturnEntry::info(FILE * os) const
{
    char tmp[0x400];
    verifyAndCopy(_return, tmp, sizeof(tmp));
    fprintf(os, "%s(%p)", tmp, _return);
}

asciistream &
operator << (asciistream & os, const StackReturnEntry & v)
{
    char tmp[0x100];
    char t[0x200];
    verifyAndCopy(v._return, tmp, sizeof(tmp));
    snprintf(t, sizeof(t), "%s(%p)", tmp, v._return);
    return os << t;
}

const void * StackEntry::_stopAddr = nullptr;

size_t
StackEntry::fillStack(StackEntry *stack, size_t nelems)
{
    // GNU extension: Variable-length automatic array
    void * retAddr[nelems];
    int sz = backtrace(retAddr, nelems);
    if ((sz > 0) && (size_t(sz) <= nelems)) {
        for(int i(1); i < sz; i++) {
            StackEntry entry(retAddr[i], nullptr);
            if (entry.valid()) {
                stack[i-1] = entry;
            } else {
                sz = i;
            }
        }
        sz -= 1;  // Do not count self
    } else {
        sz = 0;
    }
    if (size_t(sz) < nelems) {
        stack[sz] = StackEntry();
    }
    return sz;
}

}
