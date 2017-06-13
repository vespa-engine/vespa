// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ioevent.h"
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>

namespace storage {

namespace memfile {

IOEvent::IOEvent()
    : _state(Device::OK),
      _description(),
      _location(),
      _global(false),
      _timestamp(0)
{}

IOEvent::IOEvent(const IOEvent &) = default;
IOEvent & IOEvent::operator = (const IOEvent &) = default;

IOEvent::~IOEvent() {}

namespace {
    vespalib::string stripBacktrace(const vespalib::string& s) {
        vespalib::string::size_type pos = s.find("Backtrace:");
        if (pos == vespalib::string::npos) return s;
        while (pos > 0 && (s[pos - 1] == ' ' || s[pos - 1] == '\n')) {
            --pos;
        }
        return s.substr(0, pos);
    }
}

IOEvent::IOEvent(uint32_t timestamp, Device::State s, const vespalib::string& description,
                 const vespalib::string& location, bool global)
    : _state(s),
      _description(stripBacktrace(description)),
      _location(location),
      _global(global),
      _timestamp(timestamp)
{
}

IOEvent
IOEvent::createEventFromErrno(uint32_t timestamp,
                              int error, const vespalib::string& extraInfo,
                              const vespalib::string& location)
{
    vespalib::string err(vespalib::getErrorString(error));
    err += ": " + extraInfo;
    switch (error) {
        case ENOENT:
            return IOEvent(timestamp, Device::NOT_FOUND, err, location);
        case ENOTDIR:
        case ENAMETOOLONG:
        case ELOOP:
        case EISDIR: // Using directory as file
        case EOPNOTSUPP: // Operation not supported by filesystem
        case EROFS:
        case EMLINK:
        case ENXIO:
        case ESPIPE: // Descriptor is a pip/socket/fifo
            return IOEvent(timestamp, Device::PATH_FAILURE, err, location);
        case EACCES:
            return IOEvent(timestamp, Device::NO_PERMISSION, err, location);
        case EIO:   // IO error occured.
        case EINTR: // Read from slow device interrupted before any data.
            return IOEvent(timestamp, Device::IO_FAILURE, err, location);
        case EMFILE:
            return IOEvent(timestamp, Device::TOO_MANY_OPEN_FILES, err,
                           location, true);
        case EAGAIN: // Non-blocking read but no data available
        case EBADF:  // Invalid file descriptor
        case EFAULT: // Buffer pointer invalid
        case EINVAL: // Faulty input parameter
        case ENFILE:
        default:
            return IOEvent(timestamp, Device::INTERNAL_FAILURE, err, location);
    }
}

IOEvent
IOEvent::createEventFromIoException(vespalib::IoException& e, uint32_t timestamp)
{
    Device::State type = Device::INTERNAL_FAILURE;
    switch (e.getType()) {
        case vespalib::IoException::NOT_FOUND:
            type = Device::NOT_FOUND; break;
        case vespalib::IoException::ILLEGAL_PATH:
            type = Device::PATH_FAILURE; break;
        case vespalib::IoException::NO_PERMISSION:
            type = Device::NO_PERMISSION; break;
        case vespalib::IoException::DISK_PROBLEM:
            type = Device::IO_FAILURE; break;
        case vespalib::IoException::TOO_MANY_OPEN_FILES:
            type = Device::TOO_MANY_OPEN_FILES; break;
        case vespalib::IoException::INTERNAL_FAILURE:
        case vespalib::IoException::NO_SPACE:
        case vespalib::IoException::CORRUPT_DATA:
        case vespalib::IoException::DIRECTORY_HAVE_CONTENT:
        case vespalib::IoException::FILE_FULL:
        case vespalib::IoException::ALREADY_EXISTS:
        case vespalib::IoException::UNSPECIFIED:
            type = Device::INTERNAL_FAILURE; break;
    }
    return IOEvent(timestamp, type, e.getMessage(), e.getLocation());
}

void
IOEvent::print(std::ostream & os, bool verbose, const std::string& indent) const
{
    (void) indent;
    os << "IOEvent(";
    os << Device::getStateString(_state);
    if (verbose) {
        if (_description.size() > 0) {
            os << ", " << _description;
        }
        if (_location.size() > 0) {
            os << ", " << _location;
        }
        os << ", time " << _timestamp;
    }
    os << ")";
}

} // memfile

} // storage
