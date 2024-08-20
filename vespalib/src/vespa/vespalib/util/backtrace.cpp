// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <execinfo.h>
#include <csignal>
#include <string>

namespace {

/**
 * Convert input line of form eg.
 *   ./exception_test(_ZN11Application5entryEiPPc+0x0) [0x1234]
 * to its demangled form
 *   ./exception_test(Application::entry(int, char**)+0x0) [0x1234]
 *
 * Assumes everything between '(' and '+' is a mangled function signature.
 *
 * @param line A single line from backtrace_symbols
 * @return The demangled line or the original line if demangling fails
 */
std::string
demangleBacktraceLine(const std::string& line)
{
    size_t symBegin = line.find_first_of('(');
    if (symBegin != std::string::npos) {
        size_t symEnd = line.find_first_of('+', symBegin);
        if (symEnd != std::string::npos) {
            std::string mangled = line.substr(symBegin + 1, symEnd - symBegin - 1);
            std::string demangled = vespalib::demangle(mangled.c_str());
            if ( ! demangled.empty()) {
                // Create string matching original backtrace line format,
                // except with demangled function signature
                std::string ret(line.c_str(), symBegin + 1);
                ret.append(demangled);
                ret.append(line.c_str() + symEnd);
                return ret;
            }
        }
    }
    // Return unchanged since we couldn't make sense of it
    return line;
}

}

int
vespalib::getStackTraceFrames(void** framesOut, int maxFrames) {
    return backtrace(framesOut, maxFrames);
}

std::string
vespalib::getStackTrace(int ignoreTop, void* const* stack, int size)
{
    asciistream ost;
    char** symbols = backtrace_symbols(stack, size);
    if (symbols) {
        ost << "Backtrace:";
        for (int i = ignoreTop; i < size; ++i) {
            ost << "\n  " << demangleBacktraceLine(symbols[i]);
        }
        free(symbols);
    }
    return ost.str();
}

std::string
vespalib::getStackTrace(int ignoreTop) {
    ignoreTop += 1;
    void* stack[25];
    int size = backtrace(stack, 25);
    return getStackTrace(ignoreTop, stack, size);
}
