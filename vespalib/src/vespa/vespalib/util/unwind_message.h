// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "macro.h"
#include "stringfmt.h"

namespace vespalib {

/**
 * This class contains a message that will be printed to stderr if the
 * object is destructed due to stack unwinding caused by an exception.
 **/
class UnwindMessage {
private:
    int _num_active;
    vespalib::string _message;
public:
    UnwindMessage(const vespalib::string &msg);
    UnwindMessage(UnwindMessage &&rhs) noexcept ;
    UnwindMessage(const UnwindMessage &) = delete;
    UnwindMessage &operator=(const UnwindMessage &) = delete;
    UnwindMessage &operator=(UnwindMessage &&) = delete;
    ~UnwindMessage();
};

extern UnwindMessage unwind_msg(const char *fmt, ...) __attribute__ ((format (printf,1,2)));

// make an unwind message with a hopefully unique name on the stack
#define UNWIND_MSG(...) auto VESPA_CAT(unwindMessageOnLine, __LINE__) = unwind_msg(__VA_ARGS__)

// make an unwind message quoting a piece of code and then perform that code
#define UNWIND_DO(...) do { UNWIND_MSG("%s:%d: %s", __FILE__, __LINE__, VESPA_STRINGIZE(__VA_ARGS__)); __VA_ARGS__; } while(false)

} // namespace
