// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blob_sequence_reader.h"
#include "serialized_tensor_attribute.h"
#include "serialized_tensor_attribute_saver.h"
#include "tensor_attribute.hpp"
#include <vespa/eval/eval/value.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/util/rcuvector.hpp>

using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::tensor {

namespace {

constexpr uint32_t TENSOR_ATTRIBUTE_VERSION = 0;

}

SerializedTensorAttribute::SerializedTensorAttribute(stringref name, const Config &cfg)
    : TensorAttribute(name, cfg, _serializedTensorStore)
{
}


SerializedTensorAttribute::~SerializedTensorAttribute()
{
    getGenerationHolder().clearHoldLists();
    _tensorStore.clearHoldLists();
}

void
SerializedTensorAttribute::setTensor(DocId docId, const vespalib::eval::Value &tensor)
{
    checkTensorType(tensor);
    EntryRef ref = _serializedTensorStore.setTensor(tensor);
    setTensorRef(docId, ref);
}


std::unique_ptr<Value>
SerializedTensorAttribute::getTensor(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (!ref.valid()) {
        return std::unique_ptr<Value>();
    }
    return _serializedTensorStore.getTensor(ref);
}

bool
SerializedTensorAttribute::onLoad()
{
    BlobSequenceReader tensorReader(*this);
    if (!tensorReader.hasData()) {
        return false;
    }
    setCreateSerialNum(tensorReader.getCreateSerialNum());
    assert(tensorReader.getVersion() == TENSOR_ATTRIBUTE_VERSION);
    uint32_t numDocs(tensorReader.getDocIdLimit());
    _refVector.reset();
    _refVector.unsafe_reserve(numDocs);
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        uint32_t tensorSize = tensorReader.getNextSize();
        auto raw = _serializedTensorStore.allocRawBuffer(tensorSize);
        if (tensorSize != 0) {
            tensorReader.readBlob(raw.data, tensorSize);
        }
        _refVector.push_back(raw.ref);
    }
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    return true;
}


std::unique_ptr<AttributeSaver>
SerializedTensorAttribute::onInitSave(vespalib::stringref fileName)
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().
                                             takeGuard());
    return std::make_unique<SerializedTensorAttributeSaver>
        (std::move(guard),
         this->createAttributeHeader(fileName),
         getRefCopy(),
         _serializedTensorStore);
}

void
SerializedTensorAttribute::compactWorst()
{
    doCompactWorst<SerializedTensorStore::RefType>();
}

}
