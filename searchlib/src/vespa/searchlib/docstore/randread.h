// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>

class FastOS_FileInterface;

namespace vespalib { class DataBuffer; }

namespace search {

class FileRandRead
{
public:
    using FSP = std::shared_ptr<FastOS_FileInterface>;
    virtual ~FileRandRead() = default;
    virtual FSP read(size_t offset, vespalib::DataBuffer & buffer, size_t sz) = 0;
    virtual int64_t getSize() const = 0;
};

}
