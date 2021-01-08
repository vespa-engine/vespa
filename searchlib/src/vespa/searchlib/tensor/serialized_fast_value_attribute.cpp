// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialized_fast_value_attribute.h"
#include "streamed_value_saver.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/util/rcuvector.hpp>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.serialized_fast_value_attribute");

#include "blob_sequence_reader.h"
#include "tensor_attribute.hpp"

using namespace vespalib;
using namespace vespalib::eval;

namespace search::tensor {

SerializedFastValueAttribute::SerializedFastValueAttribute(stringref name, const Config &cfg)
  : TensorAttribute(name, cfg, _streamedValueStore),
    _tensor_type(cfg.tensorType()),
    _streamedValueStore(_tensor_type)
{
}


SerializedFastValueAttribute::~SerializedFastValueAttribute()
{
    getGenerationHolder().clearHoldLists();
    _tensorStore.clearHoldLists();
}

void
SerializedFastValueAttribute::setTensor(DocId docId, const vespalib::eval::Value &tensor)
{
    checkTensorType(tensor);
    EntryRef ref = _streamedValueStore.store_tensor(tensor);
    assert(ref.valid());
    setTensorRef(docId, ref);
}

std::unique_ptr<Value>
SerializedFastValueAttribute::getTensor(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (!ref.valid()) {
        return {};
    }
    if (const auto * ptr = _streamedValueStore.get_tensor_entry(ref)) {
        return ptr->create_fast_value_view(_tensor_type);
    }
    return {};
}

bool
SerializedFastValueAttribute::onLoad()
{
    BlobSequenceReader tensorReader(*this);
    if (!tensorReader.hasData()) {
        return false;
    }
    setCreateSerialNum(tensorReader.getCreateSerialNum());
    assert(tensorReader.getVersion() == getVersion());
    uint32_t numDocs(tensorReader.getDocIdLimit());
    _refVector.reset();
    _refVector.unsafe_reserve(numDocs);
    vespalib::Array<char> buffer(1024);
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        uint32_t tensorSize = tensorReader.getNextSize();
        if (tensorSize != 0) {
            if (tensorSize > buffer.size()) {
                buffer.resize(tensorSize + 1024);
            }
            tensorReader.readBlob(&buffer[0], tensorSize);
            vespalib::nbostream source(&buffer[0], tensorSize);
            EntryRef ref = _streamedValueStore.store_encoded_tensor(source);
            _refVector.push_back(ref);
        } else {
            EntryRef invalid;
            _refVector.push_back(invalid);
        }
    }
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    return true;
}


std::unique_ptr<AttributeSaver>
SerializedFastValueAttribute::onInitSave(vespalib::stringref fileName)
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().
                                             takeGuard());
    return std::make_unique<StreamedValueSaver>
        (std::move(guard),
         this->createAttributeHeader(fileName),
         getRefCopy(),
         _streamedValueStore);
}

void
SerializedFastValueAttribute::compactWorst()
{
    doCompactWorst<StreamedValueStore::RefType>();
}

}
