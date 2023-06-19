// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <mutex>
#include <cerrno>

#include <vespa/log/log.h>
LOG_SETUP(".vespa.exceptions");

namespace vespalib {

VESPA_IMPLEMENT_EXCEPTION(UnsupportedOperationException, Exception);
VESPA_IMPLEMENT_EXCEPTION(IllegalArgumentException, Exception);
VESPA_IMPLEMENT_EXCEPTION(IllegalStateException, Exception);
VESPA_IMPLEMENT_EXCEPTION(OverflowException, Exception);
VESPA_IMPLEMENT_EXCEPTION(UnderflowException, Exception);
VESPA_IMPLEMENT_EXCEPTION(TimeoutException, Exception);
VESPA_IMPLEMENT_EXCEPTION(FatalException, Exception);
VESPA_IMPLEMENT_EXCEPTION(NetworkSetupFailureException, IllegalStateException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(PortListenException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(IoException);

//-----------------------------------------------------------------------------

namespace {

std::mutex _G_silence_mutex;
vespalib::string _G_what;

void silent_terminate() {
    std::lock_guard<std::mutex> guard(_G_silence_mutex);
    LOG(fatal, "Will exit with code 66 due to: %s", _G_what.c_str());
    std::_Exit(66);
}

}

const char *
ExceptionWithPayload::what() const noexcept {
    return _msg.c_str();
}

ExceptionWithPayload::ExceptionWithPayload(vespalib::stringref msg)
    : std::exception(),
      _msg(msg),
      _payload()
{ }
ExceptionWithPayload::ExceptionWithPayload(vespalib::stringref msg, Anything::UP payload)
    : std::exception(),
      _msg(msg),
      _payload(std::move(payload))
{ }
ExceptionWithPayload::ExceptionWithPayload(ExceptionWithPayload &&) noexcept = default;
ExceptionWithPayload & ExceptionWithPayload::operator = (ExceptionWithPayload &&) noexcept = default;
ExceptionWithPayload::~ExceptionWithPayload() = default;

SilenceUncaughtException::SilenceUncaughtException(const std::exception & e)
    : _oldTerminate(std::set_terminate(silent_terminate))
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

vespalib::string
PortListenException::make_message(int port, vespalib::stringref protocol,
                                  vespalib::stringref msg)
{
    return make_string("failed to listen on port %d with protocol %s%s%s",
                       port, vespalib::string(protocol).c_str(), msg.empty() ? "" : ": ",
                       vespalib::string(msg).c_str());
}

PortListenException::PortListenException(int port, vespalib::stringref protocol,
                                         vespalib::stringref msg,
                                         vespalib::stringref location, int skipStack)
    : Exception(make_message(port, protocol, msg), location, skipStack + 1),
      _port(port),
      _protocol(protocol)
{
}

PortListenException::PortListenException(int port, vespalib::stringref protocol,
                                         const Exception &cause,
                                         vespalib::stringref msg,
                                         vespalib::stringref location, int skipStack)
    : Exception(make_message(port, protocol, msg), cause, location, skipStack + 1),
      _port(port),
      _protocol(protocol)
{
}

PortListenException::PortListenException(PortListenException &&) noexcept = default;
PortListenException & PortListenException::operator = (PortListenException &&) noexcept = default;
PortListenException::PortListenException(const PortListenException &) = default;
PortListenException & PortListenException::operator = (const PortListenException &) = default;

PortListenException::~PortListenException() = default;

//-----------------------------------------------------------------------------

IoException::IoException(stringref msg, Type type,
                         stringref location, int skipStack)
    : Exception(createMessage(msg, type), location, skipStack+1),
      _type(type)
{
}

IoException::IoException(stringref msg, Type type,
                         const Exception& cause, stringref location,
                         int skipStack)
    : Exception(createMessage(msg, type), cause, location, skipStack+1),
      _type(type)
{
}

IoException::IoException(const IoException &) = default;
IoException::IoException(IoException &&) noexcept = default;
IoException & IoException::operator =(IoException &&) noexcept = default;
IoException::~IoException() = default;

string
IoException::createMessage(stringref msg, Type type)
{
    vespalib::asciistream ost;
    switch (type) {
        case UNSPECIFIED:            break;
        case ILLEGAL_PATH:           ost << "ILLEGAL PATH: "; break;
        case NO_PERMISSION:          ost << "NO PERMISSION: "; break;
        case DISK_PROBLEM:           ost << "DISK PROBLEM: "; break;
        case INTERNAL_FAILURE:       ost << "INTERNAL FAILURE: "; break;
        case NO_SPACE:               ost << "NO SPACE: "; break;
        case NOT_FOUND:              ost << "NOT FOUND: "; break;
        case CORRUPT_DATA:           ost << "CORRUPT DATA: "; break;
        case TOO_MANY_OPEN_FILES:    ost << "TOO MANY OPEN FILES: "; break;
        case DIRECTORY_HAVE_CONTENT: ost << "DIRECTORY HAVE CONTENT: "; break;
        case FILE_FULL:              ost << "FILE_FULL: "; break;
        case ALREADY_EXISTS:         ost << "ALREADY EXISTS: "; break;
        default:
            ost << "Unknown type(" << type << "): ";
    }
    ost << msg;
    return ost.str();
}

IoException::Type
IoException::getErrorType(int error) {
    switch (error) {
        case ENOENT:
            return IoException::NOT_FOUND;
            // These should be handled elsewhere and not get here..
        case EAGAIN:      // Non-block operation with no data
        case EINTR:       // Interrupted operation with no data
            return IoException::INTERNAL_FAILURE;
        case ENOTDIR:
        case ENAMETOOLONG:
        case ELOOP:
        case EISDIR:
        case EMLINK:
        case ENXIO:
            return IoException::ILLEGAL_PATH;
        case EACCES:
        case EPERM:
        case EROFS:
            return IoException::NO_PERMISSION;
        case EIO:             // An IO error occured
            return IoException::DISK_PROBLEM;
        case ENOSPC:          // No free space
        case EDQUOT:          // Quota filled
            return IoException::NO_SPACE;
        case EMFILE:
            return IoException::TOO_MANY_OPEN_FILES;
        case ENOTEMPTY:
            return IoException::DIRECTORY_HAVE_CONTENT;
        case EEXIST:          // File or directory already exists
            return IoException::ALREADY_EXISTS;
        case EBADF:           // File descriptor invalid
        case EOVERFLOW:       // Can't fit info in structure
        case EFBIG:           // Attempt to write beyond maximum size or
                              // file position is beyond end of file.
        case EPIPE:           // Write to pipe which doesn't lead anywhere.
        case ERANGE:          // Transfer request size out of stream range
        case EFAULT:
        case EINVAL:
        case ENOBUFS:         // Too few system resources
        default:
            return IoException::INTERNAL_FAILURE;
    }
}

template <typename T>
bool check_type(const std::exception &e) {
    return (dynamic_cast<const T *>(&e) != nullptr);
}

void rethrow_if_unsafe(const std::exception &e) {
    if (check_type<std::bad_alloc>(e) ||
        check_type<OOMException>(e) ||
        check_type<FatalException>(e))
    {
        throw;
    }
}

} // vespalib
