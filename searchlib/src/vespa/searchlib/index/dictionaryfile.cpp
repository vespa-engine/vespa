// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dictionaryfile.h"
#include <vespa/fastos/file.h>

namespace search::index {

DictionaryFileSeqRead::~DictionaryFileSeqRead() = default;

DictionaryFileSeqWrite::~DictionaryFileSeqWrite() = default;

DictionaryFileRandRead::DictionaryFileRandRead()
    : _memoryMapped(false)
{
}

DictionaryFileRandRead::~DictionaryFileRandRead() = default;

void
DictionaryFileRandRead::afterOpen(FastOS_FileInterface &file)
{
    _memoryMapped = (file.MemoryMapPtr(0) != nullptr);
}

}
