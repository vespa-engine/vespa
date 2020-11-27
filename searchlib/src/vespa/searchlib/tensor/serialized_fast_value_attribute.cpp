// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialized_fast_value_attribute.h"
#include "streamed_value_saver.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/eval/streamed/streamed_value_utils.h>
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

namespace {

struct ValueBlock : LabelBlock {
    TypedCells cells;
};

class ValueBlockStream {
private:
    const StreamedValueStore::DataFromType &_from_type;
    LabelBlockStream _label_block_stream;
    const char *_cells_ptr;

    size_t dsss() const { return _from_type.dense_subspace_size; }
    auto cell_type() const { return _from_type.cell_type; }
public:
    ValueBlock next_block() {
        auto labels = _label_block_stream.next_block();
        if (labels) {
            TypedCells subspace_cells(_cells_ptr, cell_type(), dsss());
            _cells_ptr += CellTypeUtils::mem_size(cell_type(), dsss());
            return ValueBlock{labels, subspace_cells};
        } else {
            TypedCells none(nullptr, cell_type(), 0);
            return ValueBlock{labels, none};
        }
    }
    
    ValueBlockStream(const StreamedValueStore::DataFromType &from_type,
                     const StreamedValueStore::StreamedValueData &from_store)
      : _from_type(from_type),
        _label_block_stream(from_store.num_subspaces,
                            from_store.labels_buffer,
                            from_type.num_mapped_dimensions),
        _cells_ptr((const char *)from_store.cells_ref.data)
    {
        _label_block_stream.reset();
    }
    
    ~ValueBlockStream();
};

ValueBlockStream::~ValueBlockStream() = default;

void report_problematic_subspace(size_t idx,
                                 const StreamedValueStore::DataFromType &from_type,
                                 const StreamedValueStore::StreamedValueData &from_store)
{
    LOG(error, "PROBLEM: add_mapping returned same index=%zu twice", idx);
    FastValueIndex temp_index(from_type.num_mapped_dimensions,
                            from_store.num_subspaces);
    auto from_start = ValueBlockStream(from_type, from_store);
    while (auto redo_block = from_start.next_block()) {
        if (idx == temp_index.map.add_mapping(redo_block.address)) {
            vespalib::string msg = "Block with address[ ";
            for (vespalib::stringref ref : redo_block.address) {
                msg.append("'").append(ref).append("' ");
            }
            msg.append("]");
            LOG(error, "%s maps to subspace %zu", msg.c_str(), idx);
        }
    }
}

/**
 * This Value implementation is almost exactly like FastValue, but
 * instead of owning its type and cells it just has a reference to
 * data stored elsewhere.
 * XXX: we should find a better name for this, and move it
 * (together with the helper classes above) to its own file,
 * and add associated unit tests.
 **/
class OnlyFastValueIndex : public Value {
private:
    const ValueType &_type;
    TypedCells _cells;
    FastValueIndex my_index;
public:
    OnlyFastValueIndex(const ValueType &type,
                       const StreamedValueStore::DataFromType &from_type,
                       const StreamedValueStore::StreamedValueData &from_store)
      : _type(type),
        _cells(from_store.cells_ref),
        my_index(from_type.num_mapped_dimensions,
                 from_store.num_subspaces)
    {
        assert(_type.cell_type() == _cells.type);
        std::vector<vespalib::stringref> address(from_type.num_mapped_dimensions);
        auto block_stream = ValueBlockStream(from_type, from_store);
        size_t ss = 0;
        while (auto block = block_stream.next_block()) {
            size_t idx = my_index.map.add_mapping(block.address);
            if (idx != ss) {
                report_problematic_subspace(idx, from_type, from_store);
            }
            ++ss;
        }
        assert(ss == from_store.num_subspaces);
    }


    ~OnlyFastValueIndex();
    
    const ValueType &type() const final override { return _type; }
    TypedCells cells() const final override { return _cells; }
    const Index &index() const final override { return my_index; }
    vespalib::MemoryUsage get_memory_usage() const final override {
        auto usage = self_memory_usage<OnlyFastValueIndex>();
        usage.merge(my_index.map.estimate_extra_memory_usage());
        return usage;
    }
};

OnlyFastValueIndex::~OnlyFastValueIndex() = default;

}

SerializedFastValueAttribute::SerializedFastValueAttribute(stringref name, const Config &cfg)
  : TensorAttribute(name, cfg, _streamedValueStore),
    _tensor_type(cfg.tensorType()),
    _streamedValueStore(_tensor_type),
    _data_from_type(_tensor_type)
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
    EntryRef ref = _streamedValueStore.store_tensor(tensor);
    setTensorRef(docId, ref);
    if (!ref.valid()) {
        checkTensorType(tensor);
    }
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
    if (auto data_from_store = _streamedValueStore.get_tensor_data(ref)) {
        return std::make_unique<OnlyFastValueIndex>(_tensor_type,
                                                    _data_from_type,
                                                    data_from_store);
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
