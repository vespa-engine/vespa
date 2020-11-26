// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include "protocolserialization5_2.h"
#include "storagecommand.h"
#include "serializationhelper.h"

namespace storage::mbusprot {

using documentapi::TestAndSetCondition;

void ProtocolSerialization5_2::onEncode(GBBuf & buf, const api::PutCommand & cmd) const
{
    ProtocolSerialization5_0::onEncode(buf, cmd);
    encodeTasCondition(buf, cmd);
}

api::StorageCommand::UP
ProtocolSerialization5_2::onDecodePutCommand(BBuf & buf) const
{
    auto cmd = ProtocolSerialization5_0::onDecodePutCommand(buf);
    decodeTasCondition(*cmd, buf);
    return cmd;
}

void ProtocolSerialization5_2::onEncode(GBBuf & buf, const api::RemoveCommand & cmd) const
{
    ProtocolSerialization4_2::onEncode(buf, cmd);
    encodeTasCondition(buf, cmd);
}

api::StorageCommand::UP
ProtocolSerialization5_2::onDecodeRemoveCommand(BBuf & buf) const
{
    auto cmd = ProtocolSerialization4_2::onDecodeRemoveCommand(buf);
    decodeTasCondition(*cmd, buf);
    return cmd;
}

void ProtocolSerialization5_2::onEncode(GBBuf & buf, const api::UpdateCommand & cmd) const
{
    ProtocolSerialization5_0::onEncode(buf, cmd);
    encodeTasCondition(buf, cmd);
}

api::StorageCommand::UP
ProtocolSerialization5_2::onDecodeUpdateCommand(BBuf & buf) const
{
    auto cmd = ProtocolSerialization5_0::onDecodeUpdateCommand(buf);
    decodeTasCondition(*cmd, buf);
    return cmd;
}

void ProtocolSerialization5_2::decodeTasCondition(api::StorageCommand & storageCmd, BBuf & buf) {
    auto & cmd = static_cast<api::TestAndSetCommand &>(storageCmd);
    cmd.setCondition(TestAndSetCondition(SH::getString(buf)));
}

void ProtocolSerialization5_2::encodeTasCondition(GBBuf & buf, const api::StorageCommand & storageCmd) {
    auto & cmd = static_cast<const api::TestAndSetCommand &>(storageCmd);
    buf.putString(cmd.getCondition().getSelection());
}

}
