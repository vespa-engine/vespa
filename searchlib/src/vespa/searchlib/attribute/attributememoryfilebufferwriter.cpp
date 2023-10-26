// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributememoryfilebufferwriter.h"
#include <vespa/vespalib/data/databuffer.h>

namespace search {

AttributeMemoryFileBufferWriter::
AttributeMemoryFileBufferWriter(IAttributeFileWriter &memoryFileWriter)
    : AttributeFileBufferWriter(memoryFileWriter)
{
}


AttributeMemoryFileBufferWriter::~AttributeMemoryFileBufferWriter() = default;


void
AttributeMemoryFileBufferWriter::onFlush(size_t nowLen)
{
    _buf->moveFreeToData(nowLen);
    assert(_buf->getDataLen() == nowLen);
    _fileWriter.writeBuf(std::move(_buf));
    _buf = _fileWriter.allocBuf(BUFFER_SIZE);
}

} // namespace search
