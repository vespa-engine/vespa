// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute_loader.h"
#include "dense_tensor_store.h"
#include "nearest_neighbor_index.h"
#include "nearest_neighbor_index_loader.h"
#include "tensor_attribute_constants.h"
#include "tensor_attribute_saver.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attribute_header.h>
#include <vespa/searchlib/attribute/blob_sequence_reader.h>
#include <vespa/searchlib/attribute/load_utils.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <mutex>
#include <condition_variable>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.tensor.tensor_attribute_loader");

using search::attribute::AttributeHeader;
using search::attribute::BlobSequenceReader;
using search::attribute::LoadUtils;
using vespalib::CpuUsage;
using vespalib::datastore::EntryRef;

namespace search::tensor {

inline namespace loader {

constexpr uint32_t LOAD_COMMIT_INTERVAL = 256;
const vespalib::string tensorTypeTag("tensortype");

bool
can_use_index_save_file(const search::attribute::Config &config, const AttributeHeader& header)
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
    return LoadUtils::file_exists(attr, TensorAttributeSaver::index_file_suffix());
}

bool
is_present(uint8_t presence_flag) {
    if (presence_flag == tensorIsNotPresent) {
        return false;
    }
    if (presence_flag != tensorIsPresent) {
        LOG_ABORT("should not be reached");
    }
    return true;
}

class IndexBuilder {
public:
    virtual ~IndexBuilder() = default;
    virtual void add(uint32_t lid) = 0;
    virtual void wait_complete() = 0;
};

/**
 * Will build nearest neighbor index in parallel. Note that indexing order is not guaranteed,
 * but that is inline with the guarantees vespa already has.
 */
class ThreadedIndexBuilder : public IndexBuilder {
public:
    ThreadedIndexBuilder(TensorAttribute& attr, vespalib::GenerationHandler& generation_handler, TensorStore& store, NearestNeighborIndex& index, vespalib::Executor& shared_executor)
        : _attr(attr),
          _generation_handler(generation_handler),
          _index(index),
          _shared_executor(shared_executor),
          _queue(MAX_PENDING),
          _pending(0)
    {
        (void) store;
    }
    void add(uint32_t lid) override;
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
        _index.complete_add_document(lid, std::move(prepared));
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
    TensorAttribute&        _attr;
    const vespalib::GenerationHandler& _generation_handler;
    NearestNeighborIndex&   _index;
    vespalib::Executor&     _shared_executor;
    std::mutex              _mutex;
    std::condition_variable _cond;
    Queue                   _queue;
    uint64_t                _pending; // _pending is only modified in forground thread
};

void
ThreadedIndexBuilder::add(uint32_t lid) {
    Entry item;
    while (pop(item)) {
        // First process items that are ready to complete
        complete(item.first, std::move(item.second));
    }
    // Then ensure that there no more than MAX_PENDING inflight
    drainUntilPending(MAX_PENDING);

    // Then we can issue a new one
    ++_pending;
    auto task = vespalib::makeLambdaTask([this, lid]() {
        auto prepared = _index.prepare_add_document(lid, _attr.get_vectors(lid),
                                                    _generation_handler.takeGuard());
        std::unique_lock guard(_mutex);
        _queue.push(std::make_pair(lid, std::move(prepared)));
        if (_queue.size() == 1) {
            _cond.notify_all();
        }
    });
    _shared_executor.execute(CpuUsage::wrap(std::move(task), CpuUsage::Category::SETUP));
}

class ForegroundIndexBuilder : public IndexBuilder {
public:
    ForegroundIndexBuilder(AttributeVector& attr, NearestNeighborIndex& index)
        : _attr(attr),
          _index(index)
    {
    }
    void add(uint32_t lid) override {
        _index.add_document(lid);
        if ((lid % LOAD_COMMIT_INTERVAL) == 0) {
            _attr.commit();
        }
    }
    void wait_complete() override {

    }
private:
    AttributeVector&      _attr;
    NearestNeighborIndex& _index;
};

}

TensorAttributeLoader::TensorAttributeLoader(TensorAttribute& attr, GenerationHandler& generation_handler, RefVector& ref_vector, TensorStore& store, NearestNeighborIndex* index)
    : _attr(attr),
      _generation_handler(generation_handler),
      _ref_vector(ref_vector),
      _store(store),
      _index(index)
{
}

TensorAttributeLoader::~TensorAttributeLoader() = default;

void
TensorAttributeLoader::load_dense_tensor_store(BlobSequenceReader& reader, uint32_t docid_limit, DenseTensorStore& dense_store)
{
    assert(reader.getVersion() == DENSE_TENSOR_ATTRIBUTE_VERSION);
    uint8_t presence_flag = 0;
    for (uint32_t lid = 0; lid < docid_limit; ++lid) {
        reader.readBlob(&presence_flag, sizeof(presence_flag));
        if (is_present(presence_flag)) {
            auto raw = dense_store.allocRawBuffer();
            reader.readBlob(raw.data, dense_store.getBufSize());
            _ref_vector.push_back(AtomicEntryRef(raw.ref));
        } else {
            _ref_vector.push_back(AtomicEntryRef());
        }
        if ((lid % LOAD_COMMIT_INTERVAL) == 0) {
            _attr.commit();
        }
    }
}

void
TensorAttributeLoader::load_tensor_store(BlobSequenceReader& reader, uint32_t docid_limit)
{
    assert(reader.getVersion() == TENSOR_ATTRIBUTE_VERSION);
    vespalib::Array<char> buffer(1024);
    for (uint32_t lid = 0; lid < docid_limit; ++lid) {
        uint32_t tensorSize = reader.getNextSize();
        if (tensorSize != 0) {
            if (tensorSize > buffer.size()) {
                buffer.resize(tensorSize + 1024);
            }
            reader.readBlob(&buffer[0], tensorSize);
            vespalib::nbostream source(&buffer[0], tensorSize);
            EntryRef ref = _store.store_encoded_tensor(source);
            _ref_vector.push_back(AtomicEntryRef(ref));
        } else {
            EntryRef invalid;
            _ref_vector.push_back(AtomicEntryRef(invalid));
        }
        if ((lid % LOAD_COMMIT_INTERVAL) == 0) {
            _attr.commit();
        }
    }
}

void
TensorAttributeLoader::build_index(vespalib::Executor* executor, uint32_t docid_limit)
{
    std::unique_ptr<IndexBuilder> builder;
    if (executor != nullptr) {
        builder = std::make_unique<ThreadedIndexBuilder>(_attr, _generation_handler, _store, *_index, *executor);
    } else {
        builder = std::make_unique<ForegroundIndexBuilder>(_attr, *_index);
    }
    for (uint32_t lid = 0; lid < docid_limit; ++lid) {
        auto ref = _ref_vector[lid].load_relaxed();
        if (ref.valid()) {
            builder->add(lid);
        }
    }
    builder->wait_complete();
    _attr.commit();
}

bool
TensorAttributeLoader::load_index()
{
    FileWithHeader index_file(LoadUtils::openFile(_attr, TensorAttributeSaver::index_file_suffix()));
    try {
        auto index_loader = _index->make_loader(index_file.file(), index_file.header());
        size_t cnt = 0;
        while (index_loader->load_next()) {
            if ((++cnt % LOAD_COMMIT_INTERVAL) == 0) {
                _attr.commit();
            }
        }
        _attr.commit();
    } catch (const std::runtime_error& ex) {
        LOG(error, "Exception while loading nearest neighbor index for tensor attribute '%s': %s",
            _attr.getName().c_str(), ex.what());
        return false;
    }
    return true;
}

bool
TensorAttributeLoader::on_load(vespalib::Executor* executor)
{
    BlobSequenceReader reader(_attr);
    if (!reader.hasData()) {
        return false;
    }
    _attr.setCreateSerialNum(reader.getCreateSerialNum());
    assert(_attr.getConfig().tensorType().to_spec() ==
           reader.getDatHeader().getTag(tensorTypeTag).asString());
    uint32_t docid_limit(reader.getDocIdLimit());
    _ref_vector.reset();
    _ref_vector.unsafe_reserve(docid_limit);
    auto dense_store = _store.as_dense();
    if (dense_store != nullptr) {
        load_dense_tensor_store(reader, docid_limit, *dense_store);
    } else {
        load_tensor_store(reader, docid_limit);
    }
    _attr.commit();
    _attr.getStatus().setNumDocs(docid_limit);
    _attr.setCommittedDocIdLimit(docid_limit);
    if (_index != nullptr) {
        bool use_index_file = false;
        if (has_index_file(_attr)) {
            auto header = AttributeHeader::extractTags(reader.getDatHeader(), _attr.getBaseFileName());
            use_index_file = can_use_index_save_file(_attr.getConfig(), header);
        }
        if (use_index_file) {
            if (!load_index()) {
                return false;
            }
        } else {
            build_index(executor, docid_limit);
        }
    }
    return true;
}

}
