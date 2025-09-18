// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <execinfo.h>
#include <csignal>
#include <cstdlib>
#include <string>

#if defined (__has_include)
#if __has_include(<unwind.h>)
#define VESPA_BACKTRACE_HAS_UNWIND_H
#include <unwind.h>
#endif
#endif

namespace vespalib {

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

#ifdef VESPA_BACKTRACE_HAS_UNWIND_H

struct UnwindState {
    // Could be done with just a current+end ptr pair, but this is more obvious
    void** frames_out = nullptr;
    size_t frames_written = 0;
    size_t frames_max = 0;
};

_Unwind_Reason_Code unwind_callback(_Unwind_Context* ctx, void* caller_arg) {
    auto* my_state = static_cast<UnwindState*>(caller_arg);
    // We do "top of stack" frame skipping on a higher level, and therefore don't
    // bother with that detail here.
    void* frame_addr = reinterpret_cast<void*>(_Unwind_GetIP(ctx));
    if (frame_addr == nullptr) {
        return _URC_END_OF_STACK;
    }
    my_state->frames_out[my_state->frames_written] = frame_addr;
    my_state->frames_written++;
    if (my_state->frames_written == my_state->frames_max) {
        return _URC_END_OF_STACK;
    }
    return _URC_NO_REASON;
}

#endif

} // anon ns

bool has_signal_safe_collect_stack_frames() noexcept {
#ifdef VESPA_BACKTRACE_HAS_UNWIND_H
    return true;
#else
    return false;
#endif
}

size_t signal_safe_collect_stack_frames(void** frames_out, size_t frames_max) {
#ifdef VESPA_BACKTRACE_HAS_UNWIND_H
    // The unwind callback must have room for at least 1 frame.
    if (frames_max == 0) {
        return 0;
    }
    UnwindState my_state{frames_out, 0, frames_max};
    _Unwind_Backtrace(unwind_callback, &my_state);
    return my_state.frames_written;
#else
    // No known async signal safe unwinding; bail out without doing anything.
    (void)frames_out;
    (void)frames_max;
    return 0;
#endif
}

int
getStackTraceFrames(void** framesOut, int maxFrames) {
    return backtrace(framesOut, maxFrames);
}

std::string
getStackTrace(int ignoreTop, void* const* stack, int size)
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
getStackTrace(int ignoreTop) {
    ignoreTop += 1;
    void* stack[25];
    int size = backtrace(stack, 25);
    return getStackTrace(ignoreTop, stack, size);
}

} // vespalib
