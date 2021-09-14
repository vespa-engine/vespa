// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/util/fileutil.h>

namespace search::tensor {

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
          _sizeReader(_datFile.file())
    { }
    uint32_t getNextSize() { return _sizeReader.readHostOrder(); }
    void readBlob(void *buf, size_t len) { _datFile.file().ReadBuf(buf, len); }
};

} // namespace
