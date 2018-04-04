// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#pragma once

#include "protocolserialization5_1.h"
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/storageapi/message/persistence.h>

namespace storage {
namespace mbusprot {

class ProtocolSerialization5_2 : public ProtocolSerialization5_1
{
public:
    ProtocolSerialization5_2(const std::shared_ptr<const document::DocumentTypeRepo>& repo,
                             const documentapi::LoadTypeSet & loadTypes)
        : ProtocolSerialization5_1(repo, loadTypes)
    {}

protected:
    void onEncode(GBBuf &, const api::PutCommand &) const override;
    void onEncode(GBBuf &, const api::RemoveCommand &) const override;
    void onEncode(GBBuf &, const api::UpdateCommand &) const override;

    SCmd::UP onDecodePutCommand(BBuf &) const override;
    SCmd::UP onDecodeRemoveCommand(BBuf &) const override;
    SCmd::UP onDecodeUpdateCommand(BBuf &) const override;

    static void decodeTasCondition(api::StorageCommand & cmd, BBuf & buf);
    static void encodeTasCondition(GBBuf & buf, const api::StorageCommand & cmd);
};

} // mbusprot
} // storage
