// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_message_sender_stub.h"
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <string>
#include <sstream>
#include <stdexcept>

namespace storage {

DistributorMessageSenderStub::DistributorMessageSenderStub()
    : _stub_impl(),
      _pending_message_tracker(nullptr),
      _operation_sequencer(nullptr)
{}

DistributorMessageSenderStub::~DistributorMessageSenderStub() = default;

}
