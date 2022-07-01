// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "storageprotocol.h"
#include "serializationhelper.h"
#include "storagecommand.h"
#include "storagereply.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <cassert>
#include <sstream>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".storage.api.mbusprot.protocol");

namespace storage::mbusprot {

mbus::string StorageProtocol::NAME = "StorageProtocol";

StorageProtocol::StorageProtocol(const std::shared_ptr<const document::DocumentTypeRepo> repo)
    : _serializer7_0(repo)
{
}

StorageProtocol::~StorageProtocol() = default;

mbus::IRoutingPolicy::UP
StorageProtocol::createPolicy(const mbus::string&, const mbus::string&) const
{
    return mbus::IRoutingPolicy::UP();
}

namespace {
vespalib::Version version7_0(7, 41, 19);
}

static mbus::Blob
encodeMessage(const ProtocolSerialization & serializer,
              const mbus::Routable & routable,
              const StorageMessage & message,
              const vespalib::Version & serializerVersion,
              const vespalib::Version & actualVersion)
{
    mbus::Blob blob(serializer.encode(*message.getInternalMessage()));

    if (LOG_WOULD_LOG(spam)) {
        std::ostringstream messageStream;
        document::StringUtil::printAsHex(messageStream, blob.data(), blob.size());

        LOG(spam, "Encoded message of protocol %s type %s using "
            "%s serialization as version is %s:\n%s",
            routable.getProtocol().c_str(),
            message.getInternalMessage()->getType().toString().c_str(),
            serializerVersion.toString().c_str(),
            actualVersion.toString().c_str(),
            messageStream.str().c_str());
    }

    return blob;
}


mbus::Blob
StorageProtocol::encode(const vespalib::Version& version,
                        const mbus::Routable& routable) const
{
    const StorageMessage & message(dynamic_cast<const StorageMessage &>(routable));

    try {
        assert(message.getInternalMessage());
        if (version < version7_0) {
            LOGBP(error,
                  "Cannot encode message on version %s."
                  "Minimum version is %s. Cannot serialize %s.",
                  version.toString().c_str(),
                  version7_0.toString().c_str(),
                  message.getInternalMessage()->toString().c_str());

            return mbus::Blob(0);
        }
        return encodeMessage(_serializer7_0, routable, message, version7_0, version);
    } catch (std::exception & e) {
        LOGBP(warning, "Failed to encode %s storage protocol message %s: %s",
              version.toString().c_str(),
              message.getInternalMessage()->toString().c_str(),
              e.what());
    }

    return mbus::Blob(0);
}

static mbus::Routable::UP
decodeMessage(const ProtocolSerialization & serializer,
              mbus::BlobRef data,
              const api::MessageType & type,
              const vespalib::Version & serializerVersion,
              const vespalib::Version & actualVersion)
{
    if (LOG_WOULD_LOG(spam)) {
        std::ostringstream messageStream;
        document::StringUtil::printAsHex(messageStream, data.data(), data.size());

        LOG(spam,
            "Decoding %s of version %s "
            "using %s decoder from:\n%s",
            type.toString().c_str(),
            actualVersion.toString().c_str(),
            serializerVersion.toString().c_str(),
            messageStream.str().c_str());
    }

    if (type.isReply()) {
        return std::make_unique<StorageReply>(data, serializer);
    } else {
        auto command = serializer.decodeCommand(data);
        if (command && command->getInternalMessage()) {
            command->getInternalMessage()->setApproxByteSize(data.size());
        }
        return mbus::Routable::UP(command.release());
    }
}

mbus::Routable::UP
StorageProtocol::decode(const vespalib::Version & version,
                        mbus::BlobRef data) const
{
    try {
        document::ByteBuffer buf(data.data(), data.size());
        auto & type = api::MessageType::get(
            static_cast<api::MessageType::Id>(SerializationHelper::getInt(buf)));

        StorageMessage::UP message;
        if (version < version7_0) {
            LOGBP(error,
                  "Cannot decode message on version %s. Minimum version is %s.",
                  version.toString().c_str(),
                  version7_0.toString().c_str());
            return mbus::Routable::UP();
        }
        return decodeMessage(_serializer7_0, data, type, version7_0, version);
    } catch (std::exception & e) {
        std::ostringstream ost;
        ost << "Failed to decode " << version.toString() << " messagebus "
            << "storage protocol message: " << e.what() << "\n";
        document::StringUtil::printAsHex(ost, data.data(), data.size());
        LOGBP(warning, "%s", ost.str().c_str());
    }
    return mbus::Routable::UP();
}

}
