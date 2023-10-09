// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configdatabuffer.h"
#include <vespa/vespalib/data/slime/slime.h>

namespace config {

ConfigDataBuffer::ConfigDataBuffer() :
    _slime(std::make_unique<vespalib::Slime>())
{ }

ConfigDataBuffer::~ConfigDataBuffer() { }

} // namespace config
