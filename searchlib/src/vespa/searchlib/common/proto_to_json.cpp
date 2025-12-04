// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proto_to_json.h"
#include <google/protobuf/util/json_util.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.common.proto_to_json");

//-----------------------------------------------------------------------------

namespace search::common {

std::string protobuf_message_to_json(const google::protobuf::Message & message) {
    using namespace google::protobuf::util;
    JsonPrintOptions options;
    options.add_whitespace = true;
    options.always_print_fields_with_no_presence = true;
    options.always_print_enums_as_ints = false;
    options.preserve_proto_field_names = true;
    options.unquote_int64_if_possible = true;
    std::string output;
    auto status = MessageToJsonString(message, &output, options);
    if (! status.ok()) {
        LOG(warning, "MessageToJsonString returned BAD status");
    }
    return output;
}

}
