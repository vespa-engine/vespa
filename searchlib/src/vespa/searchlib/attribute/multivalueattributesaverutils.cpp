// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multivalueattributesaverutils.h"
#include <vespa/searchlib/util/bufferwriter.h>

namespace search::multivalueattributesaver {

CountWriter::CountWriter(IAttributeSaveTarget &saveTarget)
    : _countWriter(saveTarget.idxWriter().allocBufferWriter()),
      _cnt(0)
{
    uint32_t initialCount = 0;
    _countWriter->write(&initialCount, sizeof(uint32_t));
}

CountWriter::~CountWriter()
{
    _countWriter->flush();
}

void
CountWriter::writeCount(uint32_t count) {
    _cnt += count;
    uint32_t cnt32 = static_cast<uint32_t>(_cnt);
    _countWriter->write(&cnt32, sizeof(uint32_t));
}

}
