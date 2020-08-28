// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_attribute.h"
#include "direct_tensor_saver.h"

#include <vespa/eval/tensor/tensor.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/util/array.h>

#include "blob_sequence_reader.h"
#include "tensor_deserialize.h"

using vespalib::tensor::Tensor;

namespace search::tensor {

DirectTensorAttribute::DirectTensorAttribute(stringref name, const Config &cfg)
    : TensorAttribute(name, cfg, _direct_store)
{
}

DirectTensorAttribute::~DirectTensorAttribute()
{
    getGenerationHolder().clearHoldLists();
    _tensorStore.clearHoldLists();
}

bool
DirectTensorAttribute::onLoad()
{
    BlobSequenceReader tensorReader(*this);
    if (!tensorReader.hasData()) {
        return false;
    }
    setCreateSerialNum(tensorReader.getCreateSerialNum());
    assert(tensorReader.getVersion() == getVersion());
    uint32_t numDocs = tensorReader.getDocIdLimit();
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
            EntryRef ref = _direct_store.set_tensor(deserialize_tensor(&buffer[0], tensorSize));
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

void
DirectTensorAttribute::set_tensor(DocId lid, std::unique_ptr<Tensor> tensor)
{
    checkTensorType(*tensor);
    EntryRef ref = _direct_store.set_tensor(std::move(tensor));
    setTensorRef(lid, ref);
}

void
DirectTensorAttribute::setTensor(DocId lid, const Tensor &tensor)
{
    set_tensor(lid, tensor.clone());
}

std::unique_ptr<Tensor>
DirectTensorAttribute::getTensor(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (ref.valid()) {
        auto ptr = _direct_store.get_tensor(ref);
        if (ptr) {
            return ptr->clone();
        }
    }
    std::unique_ptr<Tensor> empty;
    return empty;
}

const Tensor &
DirectTensorAttribute::get_tensor_ref(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (ref.valid()) {
        auto ptr = _direct_store.get_tensor(ref);
        if (ptr) {
            return *ptr;
        }
    }
    return *_emptyTensor;
}

void
DirectTensorAttribute::getTensor(DocId, vespalib::tensor::MutableDenseTensorView &) const
{
    notImplemented();
}

std::unique_ptr<AttributeSaver>
DirectTensorAttribute::onInitSave(vespalib::stringref fileName)
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().
                                             takeGuard());
    return std::make_unique<DirectTensorAttributeSaver>
        (std::move(guard),
         this->createAttributeHeader(fileName),
         getRefCopy(),
         _direct_store);
}

void
DirectTensorAttribute::compactWorst()
{
    doCompactWorst<DirectTensorStore::RefType>();
}

} // namespace
