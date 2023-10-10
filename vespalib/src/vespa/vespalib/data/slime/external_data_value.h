// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "external_memory.h"

namespace vespalib::slime {

/**
 * A data value backed by external memory.
 **/
class ExternalDataValue : public Value
{
private:
    ExternalMemory::UP _value;
public:
    ExternalDataValue(ExternalMemory::UP data) : _value(std::move(data)) {}
    Memory asData() const override { return _value->get(); }
    Type type() const override { return DATA::instance; }
};

} // namespace vespalib::slime
