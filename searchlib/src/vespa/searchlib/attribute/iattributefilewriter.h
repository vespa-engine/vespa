// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib { class DataBuffer; }
namespace search {

class BufferWriter;

/*
 * Interface class to write to a single attribute vector file. Used by
 * IAttributSaver.
 */
class IAttributeFileWriter
{
public:
    using BufferBuf = vespalib::DataBuffer;
    using Buffer = std::unique_ptr<BufferBuf>;

    virtual ~IAttributeFileWriter() = default;

    /*
     * Allocate a buffer that can later be passed on to writeBuf.
     */
    virtual Buffer allocBuf(size_t size) = 0;

    /**
     * Writes the given data.  Multiple calls are allowed, but only the
     * last call can provide an unaligned buffer.
     **/
    virtual void writeBuf(Buffer buf) = 0;

    virtual std::unique_ptr<BufferWriter> allocBufferWriter() = 0;
};

} // namespace search
