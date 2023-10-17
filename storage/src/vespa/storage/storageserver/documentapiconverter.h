// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/documentapi/messagebus/messages/documentreply.h>
#include <mutex>

namespace config { class ConfigUri; }
namespace storage {

namespace api {
    class StorageCommand;
    class StorageReply;
}

struct BucketResolver;
class PriorityConverter;
/**
   Converts messages from storageapi to documentapi and
   vice versa.
*/
class DocumentApiConverter
{
public:
    explicit DocumentApiConverter(std::shared_ptr<const BucketResolver> bucketResolver);
    ~DocumentApiConverter();

    [[nodiscard]] std::unique_ptr<api::StorageCommand> toStorageAPI(documentapi::DocumentMessage& msg);
    [[nodiscard]] std::unique_ptr<api::StorageReply> toStorageAPI(documentapi::DocumentReply& reply, api::StorageCommand& originalCommand);
    void transferReplyState(storage::api::StorageReply& from, mbus::Reply& to);
    [[nodiscard]] std::unique_ptr<mbus::Message> toDocumentAPI(api::StorageCommand& cmd);
    const PriorityConverter& getPriorityConverter() const { return *_priConverter; }

    // BucketResolver getter and setter are both thread safe.
    [[nodiscard]] std::shared_ptr<const BucketResolver> bucketResolver() const;
    void setBucketResolver(std::shared_ptr<const BucketResolver> resolver);
private:
    mutable std::mutex _mutex;
    std::unique_ptr<PriorityConverter> _priConverter;
    std::shared_ptr<const BucketResolver> _bucketResolver;
};

}  // namespace storage
