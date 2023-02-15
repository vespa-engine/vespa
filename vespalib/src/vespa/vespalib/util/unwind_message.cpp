// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unwind_message.h"
#include <exception>

namespace vespalib {

UnwindMessage::UnwindMessage(const vespalib::string &msg)
    : _num_active(std::uncaught_exceptions()),
      _message(msg)
{
}

UnwindMessage::UnwindMessage(UnwindMessage &&rhs) noexcept
    : _num_active(std::uncaught_exceptions()),
      _message(rhs._message)
{
    rhs._message.clear();
}

UnwindMessage::~UnwindMessage() {
    if ((std::uncaught_exceptions() != _num_active) && !_message.empty()) {
        fprintf(stderr, "%s\n", _message.c_str());
    }
}

UnwindMessage unwind_msg(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    vespalib::string msg = make_string_va(fmt, ap);
    va_end(ap);
    return {msg};
}

} // namespace
