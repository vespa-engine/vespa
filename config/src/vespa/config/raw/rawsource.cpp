// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rawsource.h"
#include <vespa/config/common/misc.h>
#include <vespa/config/common/iconfigholder.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace config {

RawSource::~RawSource() = default;

RawSource::RawSource(std::shared_ptr<IConfigHolder> holder, const vespalib::string & payload)
    : _holder(std::move(holder)),
      _payload(payload)
{
}

void
RawSource::getConfig()
{
    _holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(readConfig()), true, 1));
}

void
RawSource::reload(int64_t generation)
{
    (void) generation;
}

void
RawSource::close()
{
}

StringVector
RawSource::readConfig()
{
    vespalib::asciistream is(_payload);
    return getlines(is);
}

}
