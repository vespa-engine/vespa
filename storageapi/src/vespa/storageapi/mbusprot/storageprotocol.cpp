// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "storageprotocol.h"
#include "serializationhelper.h"
#include "storagecommand.h"
#include "storagereply.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <sstream>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".storage.api.mbusprot.protocol");

namespace storage::mbusprot {

mbus::string StorageProtocol::NAME = "StorageProtocol";

StorageProtocol::StorageProtocol(const std::shared_ptr<const document::DocumentTypeRepo> repo,
                                 const documentapi::LoadTypeSet& loadTypes,
                                 bool configForcedBucketSpaceSerialization)
    : _serializer5_0(repo, loadTypes),
      _serializer5_1(repo, loadTypes),
      _serializer5_2(repo, loadTypes),
      _serializer6_0(repo, loadTypes),
      _configForcedBucketSpaceSerialization(configForcedBucketSpaceSerialization)
{
}

StorageProtocol::~StorageProtocol() {}

mbus::IRoutingPolicy::UP
StorageProtocol::createPolicy(const mbus::string&, const mbus::string&) const
{
    return mbus::IRoutingPolicy::UP();
}

namespace {
    vespalib::Version version6_0(6, 240, 0);
    vespalib::Version version5_2(5, 93, 30);
    vespalib::Version version5_1(5, 1, 0);
    vespalib::Version version5_0(5, 0, 12);
    vespalib::Version version5_0beta(4, 3, 0);
}


static bool
suppressEncodeWarning(const api::StorageMessage *msg)
{
    const auto *req = dynamic_cast<const api::RequestBucketInfoCommand *>(msg);
    return ((req != nullptr) && (req->getBucketSpace() != document::FixedBucketSpaces::default_space()));
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
        if (message.getInternalMessage().get() == 0) {
            throw vespalib::IllegalArgumentException(
                "Given storage message wrapper does not contain a "
                "storage message.",
                VESPA_STRLOC);
        }

        if (version < version5_1) {
            if (version < version5_0beta) {
                LOGBP(warning,
                      "No support for using messagebus for version %s."
                      "Minimum version is %s. Thus we cannot serialize %s.",
                      version.toString().c_str(),
                      version5_0beta.toString().c_str(),
                      message.getInternalMessage()->toString().c_str());

                return mbus::Blob(0);
            } else {
                return encodeMessage(_serializer5_0, routable, message, version5_0, version);
            }
        } else if (version < version5_2) {
            return encodeMessage(_serializer5_1, routable, message, version5_1, version);
        } else {
            if (_configForcedBucketSpaceSerialization) {
                return encodeMessage(_serializer6_0, routable, message, version6_0, version);
            } else {
               if (version < version6_0) {
                   return encodeMessage(_serializer5_2, routable, message, version5_2, version);
               } else {
                   return encodeMessage(_serializer6_0, routable, message, version6_0, version);
               }
            }
        }

    } catch (std::exception & e) {
        if (!(version < version6_0 &&
              suppressEncodeWarning(message.getInternalMessage().get()))) {
            LOGBP(warning, "Failed to encode %s storage protocol message %s: %s",
                  version.toString().c_str(),
                  message.getInternalMessage()->toString().c_str(),
                  e.what());
        }
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
        if (version < version5_1) {
            if (version < version5_0beta) {
                LOGBP(error,
                      "No support for using messagebus for version %s."
                      "Minimum version is %s.",
                      version.toString().c_str(),
                      version5_0beta.toString().c_str());
            } else {
                return decodeMessage(_serializer5_0, data, type, version5_0, version);
            }
        } else if (version < version5_2) {
            return decodeMessage(_serializer5_1, data, type, version5_1, version);
        } else {
            if (_configForcedBucketSpaceSerialization) {
                return decodeMessage(_serializer6_0, data, type, version6_0, version);
            } else {
                if (version < version6_0) {
                    return decodeMessage(_serializer5_2, data, type, version5_2, version);
                } else {
                    return decodeMessage(_serializer6_0, data, type, version6_0, version);
                }
            }
        }
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
