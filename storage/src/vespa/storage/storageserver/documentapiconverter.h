// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/documentapi/messagebus/messages/documentreply.h>
#include <vespa/document/repo/documenttyperepo.h>

namespace config { class ConfigUri; }
namespace storage {

namespace api {
    class StorageCommand;
    class StorageReply;
}

class PriorityConverter;
/**
   Converts messages from storageapi to documentapi and
   vice versa.
*/
class DocumentApiConverter
{
public:
    DocumentApiConverter(const config::ConfigUri & configUri);
    ~DocumentApiConverter();

    std::unique_ptr<api::StorageCommand> toStorageAPI(documentapi::DocumentMessage& msg, const document::DocumentTypeRepo::SP &repo);
    std::unique_ptr<api::StorageReply> toStorageAPI(documentapi::DocumentReply& reply, api::StorageCommand& originalCommand);
    void transferReplyState(storage::api::StorageReply& from, mbus::Reply& to);
    std::unique_ptr<mbus::Message> toDocumentAPI(api::StorageCommand& cmd, const document::DocumentTypeRepo::SP &repo);
    const PriorityConverter& getPriorityConverter() const { return *_priConverter; }
private:
    std::unique_ptr<PriorityConverter> _priConverter;
};

}  // namespace storage
