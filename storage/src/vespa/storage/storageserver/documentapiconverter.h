// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "priorityconverter.h"
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/documentapi/messagebus/messages/documentreply.h>
#include <vespa/document/repo/documenttyperepo.h>

namespace storage {

/**
   Converts messages from storageapi to documentapi and
   vice versa.
*/
class DocumentApiConverter
{
public:
    DocumentApiConverter(const config::ConfigUri & configUri)
        : _priConverter(configUri) {}

    std::unique_ptr<storage::api::StorageCommand> toStorageAPI(
            documentapi::DocumentMessage& msg,
            const document::DocumentTypeRepo::SP &repo);

    std::unique_ptr<storage::api::StorageReply> toStorageAPI(documentapi::DocumentReply& reply, api::StorageCommand& originalCommand);

    void transferReplyState(storage::api::StorageReply& from, mbus::Reply& to);

    std::unique_ptr<mbus::Message> toDocumentAPI(
            storage::api::StorageCommand& cmd,
            const document::DocumentTypeRepo::SP &repo);

    const PriorityConverter& getPriorityConverter() const { return _priConverter; }
private:
    PriorityConverter _priConverter;
};

}  // namespace storage

