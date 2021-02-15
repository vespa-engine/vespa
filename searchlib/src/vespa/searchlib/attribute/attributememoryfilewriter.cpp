// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributememoryfilewriter.h"
#include "attributememoryfilebufferwriter.h"
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/size_literals.h>

namespace search {

namespace {

const uint32_t MIN_ALIGNMENT = 4_Ki;

}

AttributeMemoryFileWriter::AttributeMemoryFileWriter()
    : IAttributeFileWriter(),
      _bufs()
{
}


AttributeMemoryFileWriter::~AttributeMemoryFileWriter() = default;


AttributeMemoryFileWriter::Buffer
AttributeMemoryFileWriter::allocBuf(size_t size)
{
    return std::make_unique<BufferBuf>(size, MIN_ALIGNMENT);
}


void
AttributeMemoryFileWriter::writeBuf(Buffer buf)
{
    _bufs.emplace_back(std::move(buf));
}


void
AttributeMemoryFileWriter::writeTo(IAttributeFileWriter &writer)
{
    for (auto &buf : _bufs) {
        writer.writeBuf(std::move(buf));
    }
    _bufs.clear();
}


std::unique_ptr<BufferWriter>
AttributeMemoryFileWriter::allocBufferWriter()
{
    return std::make_unique<AttributeMemoryFileBufferWriter>(*this);
}


} // namespace search
