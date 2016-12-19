// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "returncode.h"
#include <vespa/messagebus/errorcode.h>

#include <vespa/document/util/bytebuffer.h>
#include <vespa/fnet/frt/error.h>
#include <ostream>

namespace storage {
namespace api {

ReturnCode::ReturnCode()
    : _result(OK),
      _message()
{
}

ReturnCode::ReturnCode(Result result, const vespalib::stringref & msg)
    : _result(result),
      _message(msg)
{
}

ReturnCode::ReturnCode(const document::DocumentTypeRepo &repo,
                       document::ByteBuffer& buffer)
    : _result(OK),
      _message()
{
    deserialize(repo, buffer);
}

void ReturnCode::
onDeserialize(const document::DocumentTypeRepo &, document::ByteBuffer& buffer)
{
    int32_t result;
    buffer.getInt(result);
    _result = static_cast<Result>(result);
    int32_t size;
    buffer.getInt(size);
    const char * p = buffer.getBufferAtPos();
    buffer.incPos(size);
    _message.assign(p, size);
}

void ReturnCode::onSerialize(document::ByteBuffer& buffer) const
{
    buffer.putInt(_result);
    buffer.putInt(_message.size());
    buffer.putBytes(_message.c_str(), _message.size());
}

size_t ReturnCode::getSerializedSize() const
{
    return 2 * sizeof(int32_t) + _message.size();
}

void
ReturnCode::print(std::ostream& out, bool verbose,
                  const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "ReturnCode(" << ReturnCode::getResultString(getResult());
    if (getMessage().size() > 0) out << ", " << getMessage();
    out << ")";
}

vespalib::string ReturnCode::getResultString(Result result) {
    return documentapi::DocumentProtocol::getErrorName(result);
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
        case mbus::ErrorCode::SERVICE_OOS:
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

} // api
} // storage
