// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/readerbase.h>

namespace search::attribute {

/**
 *  Utility for reading an attribute data file where
 *  the format is a sequence of blobs (size, byte[size]).
 **/
class BlobSequenceReader : public ReaderBase
{
private:
    FileReader<uint32_t> _sizeReader;
public:
    BlobSequenceReader(AttributeVector &attr)
        : ReaderBase(attr),
          _sizeReader(&_datFile.file())
    { }
    uint32_t getNextSize() { return _sizeReader.readHostOrder(); }
    void readBlob(void *buf, size_t len);
};

} // namespace
