// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "returncode.h"
#include <ostream>

namespace storage::api {

ReturnCode & ReturnCode::operator = (ReturnCode &&) noexcept = default;

ReturnCode::ReturnCode(Result result, vespalib::stringref msg)
    : _result(result),
      _message()
{
    if ( ! msg.empty()) {
        _message = std::make_unique<vespalib::string>(msg);
    }
}

ReturnCode::ReturnCode(const ReturnCode & rhs)
    : _result(rhs._result),
      _message()
{
    if (rhs._message) {
        _message = std::make_unique<vespalib::string>(*rhs._message);
    }
}

ReturnCode &
ReturnCode::operator = (const ReturnCode & rhs) {
    return operator=(ReturnCode(rhs));
}

vespalib::string
ReturnCode::getResultString(Result result) {
    return documentapi::DocumentProtocol::getErrorName(result);
}

vespalib::string
ReturnCode::toString() const {
    vespalib::string ret = "ReturnCode(";
    ret += getResultString(_result);
    if ( _message && ! _message->empty()) {
        ret += ", ";
        ret += *_message;
    }
    ret += ")";
    return ret;
}

std::ostream &
operator << (std::ostream & os, const ReturnCode & returnCode) {
    return os << returnCode.toString();
}

bool
ReturnCode::isBusy() const
{
    // Casting to suppress -Wswitch since we're comparing against enum values
    // not present in the ReturnCode::Result enum.
    switch (static_cast<uint32_t>(_result)) {
        case mbus::ErrorCode::SEND_QUEUE_FULL:
        case mbus::ErrorCode::SESSION_BUSY:
        case mbus::ErrorCode::TIMEOUT:
        case Protocol::ERROR_BUSY:
            return true;
        default:
            return false;
    }
}

bool
ReturnCode::isNodeDownOrNetwork() const
{
    switch (static_cast<uint32_t>(_result)) {
        case mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE:
        case mbus::ErrorCode::CONNECTION_ERROR:
        case mbus::ErrorCode::UNKNOWN_SESSION:
        case mbus::ErrorCode::HANDSHAKE_FAILED:
        case mbus::ErrorCode::NO_SERVICES_FOR_ROUTE:
        case mbus::ErrorCode::NETWORK_ERROR:
        case mbus::ErrorCode::UNKNOWN_PROTOCOL:
        case Protocol::ERROR_NODE_NOT_READY:
        case Protocol::ERROR_NOT_CONNECTED:
            return true;
        default:
            return false;
    }
}

bool
ReturnCode::isCriticalForMaintenance() const
{
    if (_result >= static_cast<uint32_t>(mbus::ErrorCode::FATAL_ERROR)) {
        return true;
    }

    switch (static_cast<uint32_t>(_result)) {
        case Protocol::ERROR_INTERNAL_FAILURE:
        case Protocol::ERROR_NO_SPACE:
        case Protocol::ERROR_UNPARSEABLE:
        case Protocol::ERROR_ILLEGAL_PARAMETERS:
        case Protocol::ERROR_NOT_IMPLEMENTED:
        case Protocol::ERROR_UNKNOWN_COMMAND:
        case Protocol::ERROR_PROCESSING_FAILURE:
        case Protocol::ERROR_IGNORED:
            return true;
        default:
            return false;
    }
}

bool
ReturnCode::isCriticalForVisitor() const
{
    return isCriticalForMaintenance();
}

bool
ReturnCode::isCriticalForVisitorDispatcher() const
{
    return isCriticalForMaintenance();
}

bool
ReturnCode::isNonCriticalForIntegrityChecker() const
{
    switch (static_cast<uint32_t>(_result)) {
        case Protocol::ERROR_ABORTED:
        case Protocol::ERROR_BUCKET_DELETED:
        case Protocol::ERROR_BUCKET_NOT_FOUND:
            return true;
        default:
            return false;
    }
}

bool
ReturnCode::isShutdownRelated() const
{
    switch (static_cast<uint32_t>(_result)) {
        case Protocol::ERROR_ABORTED:
            return true;
        default:
            return false;
    }
}

bool
ReturnCode::isBucketDisappearance() const
{
    switch (static_cast<uint32_t>(_result)) {
        case Protocol::ERROR_BUCKET_NOT_FOUND:
        case Protocol::ERROR_BUCKET_DELETED:
            return true;
        default:
            return false;
    }
}

bool
ReturnCode::operator==(const ReturnCode& code) const {
    return (_result == code._result) && (getMessage() == code.getMessage());
}

bool
ReturnCode::operator!=(const ReturnCode& code) const {
    return (_result != code._result) || (getMessage() != code.getMessage());
}
}
