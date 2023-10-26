// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefilebufferwriter.h"
#include <vespa/vespalib/data/databuffer.h>

namespace search {

AttributeFileBufferWriter::
AttributeFileBufferWriter(IAttributeFileWriter &fileWriter)
    : BufferWriter(),
      _buf(),
      _bytesWritten(0),
      _incompleteBuffers(0),
      _fileWriter(fileWriter)
{
    _buf = _fileWriter.allocBuf(BUFFER_SIZE);
    assert(_buf->getFreeLen() >= BUFFER_SIZE);
    setup(_buf->getFree(), BUFFER_SIZE);
}


AttributeFileBufferWriter::~AttributeFileBufferWriter()
{
    assert(usedLen() == 0);
}


void
AttributeFileBufferWriter::flush()
{
    assert(_incompleteBuffers == 0); // all previous buffers must have been full
    size_t nowLen = usedLen();
    if (nowLen != BUFFER_SIZE) {
        // buffer is not full, only allowed for last buffer
        ++_incompleteBuffers;
    }
    if (nowLen == 0) {
        return; // empty buffer
    }
    assert(_buf->getDataLen() == 0);
    onFlush(nowLen);
    assert(_buf->getFreeLen() >= BUFFER_SIZE);
    setup(_buf->getFree(), BUFFER_SIZE);
    _bytesWritten += nowLen;
}

} // namespace search
