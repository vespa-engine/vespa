// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dictionaryfile.h"
#include <vespa/fastos/file.h>

namespace search {
namespace index {


DictionaryFileSeqRead::~DictionaryFileSeqRead()
{
}


DictionaryFileSeqWrite::~DictionaryFileSeqWrite()
{
}


DictionaryFileRandRead::DictionaryFileRandRead()
    : _memoryMapped(false)
{
}


DictionaryFileRandRead::~DictionaryFileRandRead()
{
}


void
DictionaryFileRandRead::afterOpen(FastOS_FileInterface &file)
{
    _memoryMapped = file.MemoryMapPtr(0) != NULL;
}


} // namespace index

} // namespace search
