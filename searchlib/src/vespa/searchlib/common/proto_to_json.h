// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <google/protobuf/message.h>

namespace search::common {

std::string protobuf_message_to_json(const google::protobuf::Message& message);

}
