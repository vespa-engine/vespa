// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "jsonconfigformatter.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/simple_buffer.h>

using vespalib::SimpleBuffer;
using vespalib::Output;
using vespalib::slime::JsonFormat;

namespace config {

JsonConfigFormatter::JsonConfigFormatter(bool compact)
    : _compact(compact)
{
}

void
JsonConfigFormatter::encode(ConfigDataBuffer & buffer) const
{
    SimpleBuffer buf;
    JsonFormat::encode(buffer.slimeObject(), buf, _compact);
    buffer.setEncodedString(buf.get().make_string());
}

size_t
JsonConfigFormatter::decode(ConfigDataBuffer & buffer) const
{
    std::string ref(buffer.getEncodedString());
    return JsonFormat::decode(ref, buffer.slimeObject());
}

} // namespace config
