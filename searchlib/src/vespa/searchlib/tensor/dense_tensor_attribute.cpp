// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute.h"
#include "dense_tensor_attribute_saver.h"
#include "nearest_neighbor_index.h"
#include "nearest_neighbor_index_loader.h"
#include "nearest_neighbor_index_saver.h"
#include "tensor_attribute.hpp"
#include <vespa/eval/eval/value.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/load_utils.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/mmap_file_allocator_factory.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.tensor.dense_tensor_attribute");

using search::attribute::LoadUtils;
using vespalib::CpuUsage;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::slime::ObjectInserter;

namespace search::tensor {

namespace {

constexpr uint32_t DENSE_TENSOR_ATTRIBUTE_VERSION = 1;
constexpr uint32_t LOAD_COMMIT_INTERVAL = 256;
const vespalib::string tensorTypeTag("tensortype");

class BlobSequenceReader : public ReaderBase
{
private:
    static constexpr uint8_t tensorIsNotPresent = 0;
    static constexpr uint8_t tensorIsPresent = 1;
    bool _use_index_file;
    FileWithHeader _index_file;

public:
    BlobSequenceReader(AttributeVector& attr, bool has_index);
    ~BlobSequenceReader();
    bool is_present();
    void readTensor(void *buf, size_t len) { _datFile.file().ReadBuf(buf, len); }
    bool use_index_file() const { return _use_index_file; }
    FastOS_FileInterface& index_file() { return _index_file.file(); }
};

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

bool
has_index_file(AttributeVector& attr)
{
    return LoadUtils::file_exists(attr, DenseTensorAttributeSaver::index_file_suffix());
}

BlobSequenceReader::BlobSequenceReader(AttributeVector& attr, bool has_index)
    : ReaderBase(attr),
      _use_index_file(has_index && has_index_file(attr) &&
                      can_use_index_save_file(attr.getConfig(),
                                              search::attribute::AttributeHeader::extractTags(getDatHeader(), attr.getBaseFileName()))),
      _index_file(_use_index_file ?
                  attribute::LoadUtils::openFile(attr, DenseTensorAttributeSaver::index_file_suffix()) :
                  std::unique_ptr<Fast_BufferedFile>())
{
}

BlobSequenceReader::~BlobSequenceReader() = default;

bool
BlobSequenceReader::is_present() {
    unsigned char detect;
    _datFile.file().ReadBuf(&detect, sizeof(detect));
    if (detect == tensorIsNotPresent) {
        return false;
    }
    if (detect != tensorIsPresent) {
        LOG_ABORT("should not be reached");
    }
    return true;
}

}

bool
DenseTensorAttribute::tensor_is_unchanged(DocId docid, const vespalib::eval::Value& new_tensor) const
{
    auto old_tensor = extract_cells_ref(docid);
    return _comp.equals(old_tensor, new_tensor.cells());
}

void
DenseTensorAttribute::internal_set_tensor(DocId docid, const vespalib::eval::Value& tensor)
{
    consider_remove_from_index(docid);
    EntryRef ref = _denseTensorStore.setTensor(tensor);
    setTensorRef(docid, ref);
}

void
DenseTensorAttribute::consider_remove_from_index(DocId docid)
{
    if (_index && _refVector[docid].load_relaxed().valid()) {
        _index->remove_document(docid);
    }
}

vespalib::MemoryUsage
DenseTensorAttribute::update_stat()
{
    vespalib::MemoryUsage result = TensorAttribute::update_stat();
    if (_index) {
        result.merge(_index->update_stat(getConfig().getCompactionStrategy()));
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
      _denseTensorStore(cfg.tensorType(), get_memory_allocator()),
      _index(),
      _comp(cfg.tensorType())
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
    checkTensorType(tensor);
    internal_set_tensor(docId, tensor);
    if (_index) {
        _index->add_document(docId);
    }
}

std::unique_ptr<PrepareResult>
DenseTensorAttribute::prepare_set_tensor(DocId docid, const vespalib::eval::Value& tensor) const
{
    checkTensorType(tensor);
    if (_index) {
        if (tensor_is_unchanged(docid, tensor)) {
            // Don't make changes to the nearest neighbor index when the inserted tensor is unchanged.
            // With this optimization we avoid doing unnecessary costly work, first removing the vector point, then inserting the same point.
            return {};
        }
        return _index->prepare_add_document(docid, tensor.cells(), getGenerationHandler().takeGuard());
    }
    return {};
}

void
DenseTensorAttribute::complete_set_tensor(DocId docid, const vespalib::eval::Value& tensor,
                                          std::unique_ptr<PrepareResult> prepare_result)
{
    if (_index && !prepare_result) {
        // The tensor is unchanged.
        return;
    }
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
        ref = acquire_entry_ref(docId);
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
        ref = acquire_entry_ref(docId);
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
        if ((lid % LOAD_COMMIT_INTERVAL) == 0) {
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
    auto task = vespalib::makeLambdaTask([this, ref, lid]() {
        auto prepared = _attr._index->prepare_add_document(lid, _attr._denseTensorStore.get_typed_cells(ref),
                                                           _attr.getGenerationHandler().takeGuard());
        std::unique_lock guard(_mutex);
        _queue.push(std::make_pair(lid, std::move(prepared)));
        if (_queue.size() == 1) {
            _cond.notify_all();
        }
    });
    _shared_executor.execute(CpuUsage::wrap(std::move(task), CpuUsage::Category::SETUP));
}
class DenseTensorAttribute::ForegroundLoader : public Loader {
public:
    ForegroundLoader(DenseTensorAttribute & attr) : _attr(attr) {}
    void load(uint32_t lid, vespalib::datastore::EntryRef) override {
        // This ensures that get_vector() (via getTensor()) is able to find the newly added tensor.
        _attr.setCommittedDocIdLimit(lid + 1);
        _attr._index->add_document(lid);
        if ((lid % LOAD_COMMIT_INTERVAL) == 0) {
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
    BlobSequenceReader reader(*this, _index.get() != nullptr);
    if (!reader.hasData()) {
        return false;
    }
    setCreateSerialNum(reader.getCreateSerialNum());
    assert(reader.getVersion() == DENSE_TENSOR_ATTRIBUTE_VERSION);
    assert(getConfig().tensorType().to_spec() ==
           reader.getDatHeader().getTag(tensorTypeTag).asString());
    uint32_t numDocs(reader.getDocIdLimit());
    _refVector.reset();
    _refVector.unsafe_reserve(numDocs);
    std::unique_ptr<Loader> loader;
    if (_index && !reader.use_index_file()) {
        if (executor != nullptr) {
            loader = std::make_unique<ThreadedLoader>(*this, *executor);
        } else {
            loader = std::make_unique<ForegroundLoader>(*this);
        }
    }
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        if (reader.is_present()) {
            auto raw = _denseTensorStore.allocRawBuffer();
            reader.readTensor(raw.data, _denseTensorStore.getBufSize());
            _refVector.push_back(AtomicEntryRef(raw.ref));
            if (loader) {
                loader->load(lid, raw.ref);
            }
        } else {
            _refVector.push_back(AtomicEntryRef());
        }
    }
    if (loader) {
        loader->wait_complete();
    }
    commit();
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    if (_index && reader.use_index_file()) {
        try {
            auto index_loader = _index->make_loader(reader.index_file());
            size_t cnt = 0;
            while (index_loader->load_next()) {
                if ((++cnt % LOAD_COMMIT_INTERVAL) == 0) {
                    commit();
                }
            }
        } catch (const std::runtime_error& ex) {
            LOG(error, "Exception while loading nearest neighbor index for tensor attribute '%s': %s",
                getName().c_str(), ex.what());
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
    EntryRef ref = acquire_entry_ref(docid);
    return _denseTensorStore.get_typed_cells(ref);
}

}
