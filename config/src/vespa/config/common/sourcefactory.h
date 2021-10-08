// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include "source.h"
#include "configkey.h"
#include "iconfigholder.h"

namespace config {

/*
 * Source factory, creating possible config sources.
 */
class SourceFactory {
public:
    typedef std::unique_ptr<SourceFactory> UP;
    virtual Source::UP createSource(const IConfigHolder::SP & holder, const ConfigKey & key) const = 0;
    virtual ~SourceFactory() { }
};

} // namespace common

