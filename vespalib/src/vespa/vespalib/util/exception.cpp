// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exception.h"

#ifdef VESPALIB_EXCEPTION_USEBACKTRACES
#include <vespa/vespalib/util/backtrace.h>
#endif

namespace vespalib {

ExceptionPtr::ExceptionPtr()
    : _ref(nullptr)
{
}

ExceptionPtr::ExceptionPtr(const Exception &e)
    : _ref(e.clone())
{
}

ExceptionPtr::ExceptionPtr(const ExceptionPtr &rhs)
    : _ref(rhs._ref != nullptr ? rhs._ref->clone() : nullptr)
{
}

ExceptionPtr &
ExceptionPtr::operator=(const Exception &rhs)
{
    ExceptionPtr tmp(rhs);
    swap(tmp);
    return *this;
}

ExceptionPtr &
ExceptionPtr::operator=(const ExceptionPtr &rhs)
{
    ExceptionPtr tmp(rhs);
    swap(tmp);
    return *this;
}

void
ExceptionPtr::swap(ExceptionPtr &other)
{
    std::swap(_ref, other._ref);
}

ExceptionPtr::~ExceptionPtr()
{
    delete _ref;
}

void
swap(ExceptionPtr &a, ExceptionPtr &b)
{
    a.swap(b);
}

//-----------------------------------------------------------------------------

Exception::Exception(stringref msg, stringref location, int skipStack)
    : _what(),
      _msg(msg),
      _location(location),
      _stackframes(getStackTraceFrames(_stack, STACK_FRAME_BUFFER_SIZE)),
      _skipStack(skipStack),
      _cause()
{
}

Exception::Exception(stringref msg, const Exception &cause, stringref location, int skipStack)
    : _what(),
      _msg(msg),
      _location(location),
      _stackframes(getStackTraceFrames(_stack, STACK_FRAME_BUFFER_SIZE)),
      _skipStack(skipStack),
      _cause(cause)
{}

Exception::Exception(const Exception &) = default;
Exception & Exception::operator = (const Exception &) = default;
Exception::Exception(Exception &&) noexcept = default;
Exception & Exception::operator = (Exception &&) noexcept = default;
Exception::~Exception() = default;

const char *
Exception::what() const noexcept
{
    if (_what.empty()) {
        _what.append(toString());
        for (const Exception *next = getCause();
             next != nullptr; next = next->getCause())
        {
            _what.append("\n--> Caused by: ");
            _what.append(next->toString());
        }
    }
    return _what.c_str();
}

const char *
Exception::getName() const
{
    return "Exception";
}

Exception *
Exception::clone() const
{
    return new Exception(*this);
}

void
Exception::throwSelf() const
{
    throw Exception(*this);
}

string
Exception::toString() const
{
    string str;
    str.append(getName());
    str.append(": ");
    str.append(_msg);
    if (!_location.empty()) {
        str.append(" at ");
        str.append(_location);
    }
    if (_stackframes > 0) {
        str.append("\n");
        str.append(getStackTrace(_skipStack, _stack, _stackframes));
    }
    return str;
}

} // namespace vespalib
