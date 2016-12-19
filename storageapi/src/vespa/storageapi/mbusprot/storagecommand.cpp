// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "storagecommand.h"

namespace storage {
namespace mbusprot {

StorageCommand::StorageCommand(const storage::api::StorageCommand::SP& cmd)
    : mbus::Message(),
      _cmd(cmd)
{ }

} // mbusprot
} // storage
