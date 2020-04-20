// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/defs.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>

namespace storage::api {

class RemoveLocationCommand : public BucketInfoCommand
{
public:
    RemoveLocationCommand(vespalib::stringref documentSelection, const document::Bucket &bucket);
    ~RemoveLocationCommand() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    const vespalib::string& getDocumentSelection() const { return _documentSelection; }
    DECLARE_STORAGECOMMAND(RemoveLocationCommand, onRemoveLocation);
private:
    vespalib::string _documentSelection;
};

class RemoveLocationReply : public BucketInfoReply
{
    uint32_t _documents_removed;
public:
    explicit RemoveLocationReply(const RemoveLocationCommand& cmd, uint32_t docs_removed = 0);
    void set_documents_removed(uint32_t docs_removed) noexcept {
        _documents_removed = docs_removed;
    }
    uint32_t documents_removed() const noexcept { return _documents_removed; }
    DECLARE_STORAGEREPLY(RemoveLocationReply, onRemoveLocationReply)
};

}
