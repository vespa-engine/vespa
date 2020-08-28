// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_attribute.h"

#include <vespa/eval/tensor/tensor.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/util/array.h>

#include "blob_sequence_reader.h"
#include "tensor_deserialize.h"

using vespalib::tensor::Tensor;

namespace search::tensor {

constexpr uint32_t TENSOR_ATTRIBUTE_VERSION = 0;

bool
DirectTensorAttribute::onLoad()
{
    BlobSequenceReader tensorReader(*this);
    if (!tensorReader.hasData()) {
        return false;
    }
    setCreateSerialNum(tensorReader.getCreateSerialNum());
    assert(tensorReader.getVersion() == TENSOR_ATTRIBUTE_VERSION);
    uint32_t numDocs = tensorReader.getDocIdLimit();
    vespalib::Array<char> buffer(1024);
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        uint32_t tensorSize = tensorReader.getNextSize();
        if (tensorSize != 0) {
            if (tensorSize > buffer.size()) {
                buffer.resize(tensorSize + 1024);
            }
            tensorReader.readBlob(&buffer[0], tensorSize);
            setTensor(lid, deserialize_tensor(&buffer[0], tensorSize));
        }
    }
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    return true;
}

void
DirectTensorAttribute::setTensor(DocId , std::unique_ptr<Tensor> )
{
    // XXX missing
}

} // namespace
