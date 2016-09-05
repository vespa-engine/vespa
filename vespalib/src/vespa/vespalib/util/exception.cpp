// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/exception.h>
#include <algorithm>
#include <vespa/fastos/backtrace.h>
#include <mutex>

#ifdef VESPALIB_EXCEPTION_USEBACKTRACES
#include <vespa/vespalib/util/backtrace.h>
#endif

#include <vespa/log/log.h>
LOG_SETUP(".vespa.exception");

namespace vespalib {

ExceptionPtr::ExceptionPtr()
    : _ref(NULL)
{
}


ExceptionPtr::ExceptionPtr(const Exception &e)
    : _ref(e.clone())
{
}


ExceptionPtr::ExceptionPtr(const ExceptionPtr &rhs)
    : _ref(rhs._ref != NULL ? rhs._ref->clone() : NULL)
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

Exception::Exception(const stringref &msg, const stringref &location,
                     int skipStack)
    : _what(),
      _msg(msg),
      _location(location),
      _stackframes(getStackTraceFrames(_stack, STACK_FRAME_BUFFER_SIZE)),
      _skipStack(skipStack),
      _cause()
{
}

Exception::Exception(const stringref &msg, const Exception &cause,
                     const stringref &location, int skipStack)
    : _what(),
      _msg(msg),
      _location(location),
      _stackframes(getStackTraceFrames(_stack, STACK_FRAME_BUFFER_SIZE)),
      _skipStack(skipStack),
      _cause(cause)
{
}

const char *
Exception::what() const throw()
{
    if (_what.empty()) {
        _what.append(toString());
        for (const Exception *next = getCause();
             next != NULL; next = next->getCause())
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


Exception::~Exception() throw()
{
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

namespace {

std::mutex _G_silence_mutex;
vespalib::string _G_what;

void silent_terminate() {
    std::lock_guard<std::mutex> guard(_G_silence_mutex);
    LOG(fatal, "Will exit with code 66 due to: %s", _G_what.c_str());
    exit(66);  //OR _exit() ?
}

}

SilenceUncaughtException::SilenceUncaughtException(const std::exception & e) :
    _oldTerminate(std::set_terminate(silent_terminate))
{
    std::lock_guard<std::mutex> guard(_G_silence_mutex);
    _G_what = e.what();
}

SilenceUncaughtException::~SilenceUncaughtException()
{
    std::set_terminate(_oldTerminate);
    std::lock_guard<std::mutex> guard(_G_silence_mutex);
    _G_what = "";
}

} // namespace vespalib
