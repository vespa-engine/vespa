// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/documentapi/messagebus/messages/documentreply.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <mutex>

namespace config { class ConfigUri; }
namespace storage {

namespace api {
    class StorageCommand;
    class StorageReply;
}

class BucketResolver;
class PriorityConverter;
/**
   Converts messages from storageapi to documentapi and
   vice versa.
*/
class DocumentApiConverter
{
public:
    DocumentApiConverter(const config::ConfigUri &configUri,
                         std::shared_ptr<const BucketResolver> bucketResolver);
    ~DocumentApiConverter();

    std::unique_ptr<api::StorageCommand> toStorageAPI(documentapi::DocumentMessage& msg);
    std::unique_ptr<api::StorageReply> toStorageAPI(documentapi::DocumentReply& reply, api::StorageCommand& originalCommand);
    void transferReplyState(storage::api::StorageReply& from, mbus::Reply& to);
    std::unique_ptr<mbus::Message> toDocumentAPI(api::StorageCommand& cmd);
    const PriorityConverter& getPriorityConverter() const { return *_priConverter; }

    // BucketResolver getter and setter are both thread safe.
    std::shared_ptr<const BucketResolver> bucketResolver() const;
    void setBucketResolver(std::shared_ptr<const BucketResolver> resolver);
private:
    mutable std::mutex _mutex;
    std::unique_ptr<PriorityConverter> _priConverter;
    std::shared_ptr<const BucketResolver> _bucketResolver;
};

}  // namespace storage
