// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search {

class IGidToLidMapper;

/*
 * Interface to factory class for creating classes mapping from gid to lid.
 */
class IGidToLidMapperFactory
{
public:
    using SP = std::shared_ptr<IGidToLidMapperFactory>;
    virtual ~IGidToLidMapperFactory() = default;
    virtual std::unique_ptr<IGidToLidMapper> getMapper() const = 0;
};

} // namespace search
