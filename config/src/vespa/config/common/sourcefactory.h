// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "source.h"
#include "configkey.h"

namespace config {

class IConfigHolder;

/*
 * Source factory, creating possible config sources.
 */
class SourceFactory {
public:
    virtual std::unique_ptr<Source> createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const = 0;
    virtual ~SourceFactory() = default;
};

} // namespace common

