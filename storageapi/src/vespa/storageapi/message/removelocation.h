// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/defs.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>

namespace storage::api {

class RemoveLocationCommand : public BucketInfoCommand
{
public:
    RemoveLocationCommand(const vespalib::stringref & documentSelection, const document::Bucket &bucket);
    ~RemoveLocationCommand();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    const vespalib::string& getDocumentSelection() const { return _documentSelection; }
    DECLARE_STORAGECOMMAND(RemoveLocationCommand, onRemoveLocation);
private:
    vespalib::string _documentSelection;
};

class RemoveLocationReply : public BucketInfoReply
{
public:
    RemoveLocationReply(const RemoveLocationCommand& cmd);
    DECLARE_STORAGEREPLY(RemoveLocationReply, onRemoveLocationReply)
};

}
