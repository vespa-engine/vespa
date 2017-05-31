// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributememoryfilebufferwriter.h"

namespace search {

AttributeMemoryFileBufferWriter::
AttributeMemoryFileBufferWriter(IAttributeFileWriter &memoryFileWriter)
    : AttributeFileBufferWriter(memoryFileWriter)
{
}


AttributeMemoryFileBufferWriter::~AttributeMemoryFileBufferWriter()
{
}


void
AttributeMemoryFileBufferWriter::onFlush(size_t nowLen)
{
    _buf->moveFreeToData(nowLen);
    assert(_buf->getDataLen() == nowLen);
    _fileWriter.writeBuf(std::move(_buf));
    _buf = _fileWriter.allocBuf(BUFFER_SIZE);
}

} // namespace search
