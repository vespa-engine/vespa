// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

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
    UnwindMessage(UnwindMessage &&rhs);
    UnwindMessage(const UnwindMessage &) = delete;
    UnwindMessage &operator=(const UnwindMessage &) = delete;
    UnwindMessage &operator=(UnwindMessage &&) = delete;
    ~UnwindMessage();
};

extern UnwindMessage unwind_msg(const char *fmt, ...)
#ifdef __GNUC__
        // Add printf format checks with gcc
        __attribute__ ((format (printf,1,2)))
#endif
    ;

} // namespace
