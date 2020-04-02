// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute.h"
#include "dense_tensor_attribute_saver.h"
#include "nearest_neighbor_index.h"
#include "nearest_neighbor_index_saver.h"
#include "tensor_attribute.hpp"
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/load_utils.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/vespalib/data/slime/inserter.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.tensor.dense_tensor_attribute");

using search::attribute::LoadUtils;
using vespalib::eval::ValueType;
using vespalib::slime::ObjectInserter;
using vespalib::tensor::MutableDenseTensorView;
using vespalib::tensor::Tensor;

namespace search::tensor {

namespace {

constexpr uint32_t DENSE_TENSOR_ATTRIBUTE_VERSION = 1;
const vespalib::string tensorTypeTag("tensortype");

class TensorReader : public ReaderBase
{
private:
    static constexpr uint8_t tensorIsNotPresent = 0;
    static constexpr uint8_t tensorIsPresent = 1;
public:
    TensorReader(AttributeVector &attr);
    ~TensorReader();
    bool is_present();
    void readTensor(void *buf, size_t len) { _datFile->ReadBuf(buf, len); }
};

TensorReader::TensorReader(AttributeVector &attr)
    : ReaderBase(attr)
{
}
TensorReader::~TensorReader() = default;

bool
TensorReader::is_present() {
    unsigned char detect;
    _datFile->ReadBuf(&detect, sizeof(detect));
    if (detect == tensorIsNotPresent) {
        return false;
    }
    if (detect != tensorIsPresent) {
        LOG_ABORT("should not be reached");
    }
    return true;
}

}

void
DenseTensorAttribute::consider_remove_from_index(DocId docid)
{
    if (_index && _refVector[docid].valid()) {
        _index->remove_document(docid);
    }
}

vespalib::MemoryUsage
DenseTensorAttribute::memory_usage() const
{
    vespalib::MemoryUsage result = TensorAttribute::memory_usage();
    if (_index) {
        result.merge(_index->memory_usage());
    }
    return result;
}

DenseTensorAttribute::DenseTensorAttribute(vespalib::stringref baseFileName, const Config& cfg,
                                           const NearestNeighborIndexFactory& index_factory)
    : TensorAttribute(baseFileName, cfg, _denseTensorStore),
      _denseTensorStore(cfg.tensorType()),
      _index()
{
    if (cfg.hnsw_index_params().has_value()) {
        auto tensor_type = cfg.tensorType();
        assert(tensor_type.dimensions().size() == 1);
        assert(tensor_type.is_dense());
        size_t vector_size = tensor_type.dimensions()[0].size;
        _index = index_factory.make(*this, vector_size, tensor_type.cell_type(), cfg.hnsw_index_params().value());
    }
}


DenseTensorAttribute::~DenseTensorAttribute()
{
    getGenerationHolder().clearHoldLists();
    _tensorStore.clearHoldLists();
}

uint32_t
DenseTensorAttribute::clearDoc(DocId docId)
{
    consider_remove_from_index(docId);
    return TensorAttribute::clearDoc(docId);
}

void
DenseTensorAttribute::setTensor(DocId docId, const Tensor &tensor)
{
    checkTensorType(tensor);
    consider_remove_from_index(docId);
    EntryRef ref = _denseTensorStore.setTensor(tensor);
    setTensorRef(docId, ref);
    if (_index) {
        _index->add_document(docId);
    }
}


std::unique_ptr<Tensor>
DenseTensorAttribute::getTensor(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (!ref.valid()) {
        return std::unique_ptr<Tensor>();
    }
    return _denseTensorStore.getTensor(ref);
}

void
DenseTensorAttribute::getTensor(DocId docId, MutableDenseTensorView &tensor) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    _denseTensorStore.getTensor(ref, tensor);
}

bool
DenseTensorAttribute::onLoad()
{
    TensorReader tensorReader(*this);
    if (!tensorReader.hasData()) {
        return false;
    }
    bool has_index_file = LoadUtils::file_exists(*this, DenseTensorAttributeSaver::index_file_suffix());

    setCreateSerialNum(tensorReader.getCreateSerialNum());
    assert(tensorReader.getVersion() == DENSE_TENSOR_ATTRIBUTE_VERSION);
    assert(getConfig().tensorType().to_spec() ==
           tensorReader.getDatHeader().getTag(tensorTypeTag).asString());
    uint32_t numDocs(tensorReader.getDocIdLimit());
    _refVector.reset();
    _refVector.unsafe_reserve(numDocs);
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        if (tensorReader.is_present()) {
            auto raw = _denseTensorStore.allocRawBuffer();
            tensorReader.readTensor(raw.data, _denseTensorStore.getBufSize());
            _refVector.push_back(raw.ref);
            if (_index && !has_index_file) {
                // This ensures that get_vector() (via getTensor()) is able to find the newly added tensor.
                setCommittedDocIdLimit(lid + 1);
                _index->add_document(lid);
            }
        } else {
            _refVector.push_back(EntryRef());
        }
    }
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    if (_index && has_index_file) {
        auto buffer = LoadUtils::loadFile(*this, DenseTensorAttributeSaver::index_file_suffix());
        if (!_index->load(*buffer)) {
            return false;
        }
    }
    return true;
}


std::unique_ptr<AttributeSaver>
DenseTensorAttribute::onInitSave(vespalib::stringref fileName)
{
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().
                                             takeGuard());
    auto index_saver = (_index ? _index->make_saver() : std::unique_ptr<NearestNeighborIndexSaver>());
    return std::make_unique<DenseTensorAttributeSaver>
        (std::move(guard),
         this->createAttributeHeader(fileName),
         getRefCopy(),
         _denseTensorStore,
         std::move(index_saver));
}

void
DenseTensorAttribute::compactWorst()
{
    doCompactWorst<DenseTensorStore::RefType>();
}

uint32_t
DenseTensorAttribute::getVersion() const
{
    return DENSE_TENSOR_ATTRIBUTE_VERSION;
}

void
DenseTensorAttribute::onGenerationChange(generation_t next_gen)
{
    // TODO: Change onGenerationChange() to send current generation instead of next generation.
    //       This applies for entire attribute vector code.
    TensorAttribute::onGenerationChange(next_gen);
    if (_index) {
        _index->transfer_hold_lists(next_gen - 1);
    }
}

void
DenseTensorAttribute::removeOldGenerations(generation_t first_used_gen)
{
    TensorAttribute::removeOldGenerations(first_used_gen);
    if (_index) {
        _index->trim_hold_lists(first_used_gen);
    }
}

void
DenseTensorAttribute::get_state(const vespalib::slime::Inserter& inserter) const
{
    auto& object = inserter.insertObject();
    populate_state(object);
    if (_index) {
        ObjectInserter index_inserter(object, "nearest_neighbor_index");
        _index->get_state(index_inserter);
    }
}

vespalib::tensor::TypedCells
DenseTensorAttribute::get_vector(uint32_t docid) const
{
    assert(docid < _refVector.size());
    EntryRef ref = _refVector[docid];
    return _denseTensorStore.get_typed_cells(ref);
}

}
