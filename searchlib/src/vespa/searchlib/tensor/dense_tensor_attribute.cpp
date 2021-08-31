// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute.h"
#include "dense_tensor_attribute_saver.h"
#include "nearest_neighbor_index.h"
#include "nearest_neighbor_index_saver.h"
#include "tensor_attribute.hpp"
#include <vespa/eval/eval/value.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/attribute/load_utils.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/mmap_file_allocator_factory.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.tensor.dense_tensor_attribute");

using search::attribute::LoadUtils;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::slime::ObjectInserter;

namespace search::tensor {

namespace {

constexpr uint32_t DENSE_TENSOR_ATTRIBUTE_VERSION = 1;
const vespalib::string tensorTypeTag("tensortype");

class BlobSequenceReader : public ReaderBase
{
private:
    static constexpr uint8_t tensorIsNotPresent = 0;
    static constexpr uint8_t tensorIsPresent = 1;
public:
    BlobSequenceReader(AttributeVector &attr);
    ~BlobSequenceReader();
    bool is_present();
    void readTensor(void *buf, size_t len) { _datFile->ReadBuf(buf, len); }
};

BlobSequenceReader::BlobSequenceReader(AttributeVector &attr)
    : ReaderBase(attr)
{
}
BlobSequenceReader::~BlobSequenceReader() = default;

bool
BlobSequenceReader::is_present() {
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

bool
can_use_index_save_file(const search::attribute::Config &config, const search::attribute::AttributeHeader &header)
{
    if (!config.hnsw_index_params().has_value() || !header.get_hnsw_index_params().has_value()) {
        return false;
    }
    const auto &config_params = config.hnsw_index_params().value();
    const auto &header_params = header.get_hnsw_index_params().value();
    if ((config_params.max_links_per_node() != header_params.max_links_per_node()) ||
        (config_params.distance_metric() != header_params.distance_metric())) {
        return false;
    }
    return true;
}

std::unique_ptr<vespalib::alloc::MemoryAllocator>
make_memory_allocator(const vespalib::string& name, bool swappable)
{
    if (swappable) {
        return vespalib::alloc::MmapFileAllocatorFactory::instance().make_memory_allocator(name);
    }
    return {};
}

}

void
DenseTensorAttribute::internal_set_tensor(DocId docid, const vespalib::eval::Value& tensor)
{
    checkTensorType(tensor);
    consider_remove_from_index(docid);
    EntryRef ref = _denseTensorStore.setTensor(tensor);
    setTensorRef(docid, ref);
}

void
DenseTensorAttribute::consider_remove_from_index(DocId docid)
{
    if (_index && _refVector[docid].valid()) {
        _index->remove_document(docid);
    }
}

vespalib::MemoryUsage
DenseTensorAttribute::update_stat()
{
    vespalib::MemoryUsage result = TensorAttribute::update_stat();
    if (_index) {
        result.merge(_index->memory_usage());
    }
    return result;
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

void
DenseTensorAttribute::populate_address_space_usage(AddressSpaceUsage& usage) const
{
    TensorAttribute::populate_address_space_usage(usage);
    if (_index) {
        _index->populate_address_space_usage(usage);
    }
}

DenseTensorAttribute::DenseTensorAttribute(vespalib::stringref baseFileName, const Config& cfg,
                                           const NearestNeighborIndexFactory& index_factory)
    : TensorAttribute(baseFileName, cfg, _denseTensorStore),
      _denseTensorStore(cfg.tensorType(), make_memory_allocator(getName(), cfg.paged())),
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
DenseTensorAttribute::setTensor(DocId docId, const vespalib::eval::Value &tensor)
{
    internal_set_tensor(docId, tensor);
    if (_index) {
        _index->add_document(docId);
    }
}

std::unique_ptr<PrepareResult>
DenseTensorAttribute::prepare_set_tensor(DocId docid, const vespalib::eval::Value& tensor) const
{
    if (_index) {
        return _index->prepare_add_document(docid, tensor.cells(), getGenerationHandler().takeGuard());
    }
    return std::unique_ptr<PrepareResult>();
}

void
DenseTensorAttribute::complete_set_tensor(DocId docid, const vespalib::eval::Value& tensor,
                                          std::unique_ptr<PrepareResult> prepare_result)
{
    internal_set_tensor(docid, tensor);
    if (_index) {
        _index->complete_add_document(docid, std::move(prepare_result));
    }
}

std::unique_ptr<vespalib::eval::Value>
DenseTensorAttribute::getTensor(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    if (!ref.valid()) {
        return {};
    }
    return _denseTensorStore.getTensor(ref);
}

vespalib::eval::TypedCells
DenseTensorAttribute::extract_cells_ref(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = _refVector[docId];
    }
    return _denseTensorStore.get_typed_cells(ref);
}
namespace {
class Loader {
public:
    virtual ~Loader() = default;
    virtual void load(uint32_t lid, vespalib::datastore::EntryRef ref) = 0;
    virtual void wait_complete() = 0;
};
}

/**
 * Will load and index documents in parallel. Note that indexing order is not guaranteed,
 * but that is inline with the guarantees vespa already has.
 */
class DenseTensorAttribute::ThreadedLoader : public Loader {
public:
    ThreadedLoader(DenseTensorAttribute & attr, vespalib::Executor & shared_executor)
        : _attr(attr),
          _shared_executor(shared_executor),
          _queue(MAX_PENDING),
          _pending(0)
    {}
    void load(uint32_t lid, vespalib::datastore::EntryRef ref) override;
    void wait_complete() override {
        drainUntilPending(0);
    }
private:
    using Entry = std::pair<uint32_t, std::unique_ptr<PrepareResult>>;
    using Queue = vespalib::ArrayQueue<Entry>;

    bool pop(Entry & entry) {
        std::unique_lock guard(_mutex);
        if (_queue.empty()) return false;
        entry = std::move(_queue.front());
        _queue.pop();
        return true;
    }
    void drainQ() {
        Queue queue(MAX_PENDING);
        {
            std::unique_lock guard(_mutex);
            queue.swap(_queue);
        }
        while (!queue.empty()) {
            auto item = std::move(queue.front());
            queue.pop();
            complete(item.first, std::move(item.second));
        }
    }

    void complete(uint32_t lid, std::unique_ptr<PrepareResult> prepared) {
        _attr.setCommittedDocIdLimit(std::max(_attr.getCommittedDocIdLimit(), lid + 1));
        _attr._index->complete_add_document(lid, std::move(prepared));
        --_pending;
        if ((lid % 256) == 0) {
            _attr.commit();
        };
    }
    void drainUntilPending(uint32_t maxPending) {
        while (_pending > maxPending) {
            {
                std::unique_lock guard(_mutex);
                while (_queue.empty()) {
                    _cond.wait(guard);
                }
            }
            drainQ();
        }
    }
    static constexpr uint32_t MAX_PENDING = 1000;
    DenseTensorAttribute  & _attr;
    vespalib::Executor    & _shared_executor;
    std::mutex              _mutex;
    std::condition_variable _cond;
    Queue                   _queue;
    uint64_t                _pending; // _pending is only modified in forground thread
};

void
DenseTensorAttribute::ThreadedLoader::load(uint32_t lid, vespalib::datastore::EntryRef ref) {
    Entry item;
    while (pop(item)) {
        // First process items that are ready to complete
        complete(item.first, std::move(item.second));
    }
    // Then ensure that there no mor ethan MAX_PENDING inflight
    drainUntilPending(MAX_PENDING);

    // Then we can issue a new one
    ++_pending;
    _shared_executor.execute(vespalib::makeLambdaTask([this, ref, lid]() {
        auto prepared = _attr._index->prepare_add_document(lid, _attr._denseTensorStore.get_typed_cells(ref),
                                                           _attr.getGenerationHandler().takeGuard());
        std::unique_lock guard(_mutex);
        _queue.push(std::make_pair(lid, std::move(prepared)));
        if (_queue.size() == 1) {
            _cond.notify_all();
        }
    }));
}
class DenseTensorAttribute::ForegroundLoader : public Loader {
public:
    ForegroundLoader(DenseTensorAttribute & attr) : _attr(attr) {}
    void load(uint32_t lid, vespalib::datastore::EntryRef) override {
        // This ensures that get_vector() (via getTensor()) is able to find the newly added tensor.
        _attr.setCommittedDocIdLimit(lid + 1);
        _attr._index->add_document(lid);
        if ((lid % 256) == 0) {
            _attr.commit();
        }
    }
    void wait_complete() override {

    }
private:
    DenseTensorAttribute & _attr;
};

bool
DenseTensorAttribute::onLoad(vespalib::Executor *executor)
{
    BlobSequenceReader tensorReader(*this);
    if (!tensorReader.hasData()) {
        return false;
    }
    bool has_index_file = LoadUtils::file_exists(*this, DenseTensorAttributeSaver::index_file_suffix());
    bool use_index_file = has_index_file && _index && can_use_index_save_file(getConfig(), search::attribute::AttributeHeader::extractTags(tensorReader.getDatHeader()));

    setCreateSerialNum(tensorReader.getCreateSerialNum());
    assert(tensorReader.getVersion() == DENSE_TENSOR_ATTRIBUTE_VERSION);
    assert(getConfig().tensorType().to_spec() ==
           tensorReader.getDatHeader().getTag(tensorTypeTag).asString());
    uint32_t numDocs(tensorReader.getDocIdLimit());
    _refVector.reset();
    _refVector.unsafe_reserve(numDocs);
    std::unique_ptr<Loader> loader;
    if (_index && !use_index_file) {
        if (executor != nullptr) {
            loader = std::make_unique<ThreadedLoader>(*this, *executor);
        } else {
            loader = std::make_unique<ForegroundLoader>(*this);
        }
    }
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        if (tensorReader.is_present()) {
            auto raw = _denseTensorStore.allocRawBuffer();
            tensorReader.readTensor(raw.data, _denseTensorStore.getBufSize());
            _refVector.push_back(raw.ref);
            if (loader) {
                loader->load(lid, raw.ref);
            }
        } else {
            _refVector.push_back(EntryRef());
        }
    }
    if (loader) {
        loader->wait_complete();
    }
    commit();
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    if (_index && use_index_file) {
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
    vespalib::GenerationHandler::Guard guard(getGenerationHandler().takeGuard());
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
DenseTensorAttribute::onCommit()
{
    TensorAttribute::onCommit();
    if (_index) {
        if (_index->consider_compact(getConfig().getCompactionStrategy())) {
            incGeneration();
            updateStat(true);
        }
    }
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

void
DenseTensorAttribute::onShrinkLidSpace()
{
    TensorAttribute::onShrinkLidSpace();
    if (_index) {
        _index->shrink_lid_space(getCommittedDocIdLimit());
    }
}

vespalib::eval::TypedCells
DenseTensorAttribute::get_vector(uint32_t docid) const
{
    assert(docid < _refVector.size());
    EntryRef ref = _refVector[docid];
    return _denseTensorStore.get_typed_cells(ref);
}

}
