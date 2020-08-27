// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/util/fileutil.h>

namespace search::tensor {

class TensorReader : public ReaderBase
{
private:
    FileReader<uint32_t> _tensorSizeReader;
public:
    TensorReader(AttributeVector &attr)
        : ReaderBase(attr),
          _tensorSizeReader(*_datFile)
    { }
    uint32_t getNextTensorSize() { return _tensorSizeReader.readHostOrder(); }
    void readTensor(void *buf, size_t len) { _datFile->ReadBuf(buf, len); }
};

} // namespace
