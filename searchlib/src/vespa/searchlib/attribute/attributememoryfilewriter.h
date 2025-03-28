// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributefilewriter.h"
#include <vector>

namespace search
{

/*
 * Class to write to a memory buffer representation of a single
 * attribute vector file (without header). Used by AttributeMemorySaveTarget.
 */
class AttributeMemoryFileWriter : public IAttributeFileWriter
{
    std::vector<Buffer> _bufs;
public:
    AttributeMemoryFileWriter();
    ~AttributeMemoryFileWriter() override;
    Buffer allocBuf(size_t size) override;
    void writeBuf(Buffer buf) override;
    std::unique_ptr<BufferWriter> allocBufferWriter() override;
    void writeTo(IAttributeFileWriter &writer);
};


} // namespace search
