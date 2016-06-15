// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".index.dictionaryfile");
#include "dictionaryfile.h"

namespace search
{

namespace index
{


DictionaryFileSeqRead::~DictionaryFileSeqRead(void)
{
}


DictionaryFileSeqWrite::~DictionaryFileSeqWrite(void)
{
}


DictionaryFileRandRead::DictionaryFileRandRead(void)
    : _memoryMapped(false)
{
}


DictionaryFileRandRead::~DictionaryFileRandRead(void)
{
}


void
DictionaryFileRandRead::afterOpen(FastOS_FileInterface &file)
{
    _memoryMapped = file.MemoryMapPtr(0) != NULL;
}


} // namespace index

} // namespace search
