// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/typed_cells.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/fastos/file.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/doc_vector_access.h>
#include <vespa/searchlib/tensor/empty_subspace.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/searchlib/tensor/hnsw_index_loader.hpp>
#include <vespa/searchlib/tensor/hnsw_index_saver.h>
#include <vespa/searchlib/tensor/inv_log_level_generator.h>
#include <vespa/searchlib/tensor/subspace_type.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/searchlib/util/fileutil.hpp>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/fake_doom.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/time.h>
#include <absl/flags/flag.h>
#include <absl/flags/parse.h>
#include <absl/flags/usage.h>
#include <benchmark/benchmark.h>
#include <filesystem>
#include <format>
#include <future>
#include <iostream>
#include <ranges>
#include <span>
#include <xxhash.h>

#include <vespa/log/log.h>
LOG_SETUP("hnsw_bench");

ABSL_FLAG(std::vector<std::string>, dataset_files, {},
          "List of dataset files in BIGANN format. The first file in the list must contain a metadata header.");
ABSL_FLAG(std::optional<uint32_t>, vector_count, std::nullopt,
          "Number of dataset vectors to ingest. If not specified, will ingest all available dataset vectors.");

ABSL_FLAG(std::string, data_type,            "int8", "Vector data type (int8, bfloat16, float, double)");
ABSL_FLAG(std::string, distance_metric,      "euclidean", "Vector distance metric (TODO)");
ABSL_FLAG(uint32_t,    max_links,            16, "HNSW max links per node (`m` parameter)");
ABSL_FLAG(uint32_t,    explore_neighbors,    200, "Additional neighbors to explore during insert (`ef` parameter)");
ABSL_FLAG(std::string, index_dir,            "", "Directory for saving and loading HNSW indexes");
ABSL_FLAG(bool,        check_symmetry,       false, "Verify HNSW graph symmetry after feeding has completed");
ABSL_FLAG(bool,        save_index,           false, "Save HNSW index after ingest");
ABSL_FLAG(uint32_t,    load_commit_interval, 256, "Commit HNSW graph every N vectors loaded");
ABSL_FLAG(uint32_t,    top_k,                10, "How many top hits to find during searches");
ABSL_FLAG(uint32_t,    explore_k,            10, "How many additional hits to explore during searches");
ABSL_FLAG(bool,        prefetch_tensors,     false, "Whether to explicitly prefetch tensor memory during searches");
ABSL_FLAG(uint32_t,    query_count,          1000, "How many queries to run against the index");
ABSL_FLAG(uint32_t,    report_batch_size,    100'000, "Reported feed progress ever N vectors fed");

using search::attribute::DistanceMetric;
using search::tensor::EmptySubspace;
using search::tensor::HnswIndex;
using search::tensor::HnswIndexConfig;
using search::tensor::HnswIndexSaver;
using search::tensor::HnswIndexType;
using search::tensor::InvLogLevelGenerator;
using search::tensor::NearestNeighborIndex;
using search::tensor::SubspaceType;
using search::tensor::VectorBundle;
using vespalib::GenerationHandler;
using vespalib::MemoryUsage;
using vespalib::Timer;
using vespalib::alloc::Alloc;
using vespalib::eval::CellType;
using vespalib::eval::CellTypeUtils;
using vespalib::eval::TypedCells;
using vespalib::eval::ValueType;

namespace fs = std::filesystem;

// TODO split into dataset and dataset descriptor/metadata?
struct Dataset {
    Alloc    _buf;
    uint32_t _vector_count;
    uint32_t _dimensions;
    CellType _cell_type;
    uint32_t _elem_size;
    uint32_t _single_vec_bytes;

    Dataset(Alloc buf, uint32_t vector_count,
            uint32_t dimensions, CellType data_type);
    Dataset(const Dataset&) = delete;
    Dataset& operator=(const Dataset&) = delete;
    Dataset(Dataset&&) noexcept;
    Dataset& operator=(Dataset&&) noexcept;
    ~Dataset();

    // TODO move to dataset?
    uint32_t doc_id_to_internal_index(uint32_t doc_id) const noexcept {
        // Doc ID 0 is an invalid doc ID sentinel, so we offset down by one.
        assert(doc_id > 0);
        assert(doc_id <= _vector_count);
        return doc_id - 1;
    }

    [[nodiscard]] std::span<const char> raw_vector_view(uint32_t doc_id) const noexcept {
        const uint32_t vec_idx = doc_id_to_internal_index(doc_id);
        return {static_cast<const char*>(_buf.get()) + (_single_vec_bytes * vec_idx), _single_vec_bytes};
    }
};

Dataset::Dataset(Alloc buf, uint32_t vector_count,
                 uint32_t dimensions, CellType data_type)
    : _buf(std::move(buf)),
      _vector_count(vector_count),
      _dimensions(dimensions),
      _cell_type(data_type),
      _elem_size(CellTypeUtils::mem_size(_cell_type, 1)),
      _single_vec_bytes(_elem_size * _dimensions)
{
}
Dataset::~Dataset() = default;
Dataset::Dataset(Dataset&&) noexcept = default;
Dataset& Dataset::operator=(Dataset&&) noexcept = default;

[[nodiscard]] constexpr std::string_view to_string(CellType ct) noexcept {
    if (ct == CellType::INT8) {
        return "int8";
    } else if (ct == CellType::BFLOAT16) {
        return "BFloat16";
    } else if (ct == CellType::FLOAT) {
        return "float";
    } else {
        return "double";
    }
}

// TODO refactor this hot mess
Dataset load_dataset_from_files(const std::vector<fs::path>& files,
                                CellType data_type,
                                std::optional<uint32_t> wanted_vector_count) {
    Timer load_timer;
    assert(!files.empty());
    // First step: figure out the total footprint of the dataset
    size_t total_size = 0;
    for (const auto& fp : files) {
        total_size += fs::file_size(fp); // throws if not found
    }
    if (total_size < 8) {
        throw std::runtime_error("Dataset too small to even contain a header");
    }
    // Adjust for header footprint
    total_size -= 8;
    std::println(std::cerr, "Dataset is contained in {} files totalling {} bytes", files.size(), total_size);
    Alloc dataset_buf;
    uint32_t vector_count;
    uint32_t dimensions;
    size_t bytes_read_total = 0, total_buffer_size = 0;
    // We use a dedicated read buffer to allow for reading with DirectIO while not needing sector alignment internally
    auto read_buf = Alloc::allocAlignedHeap(1_Mi, 4_Ki);
    auto* read_buf_ptr = static_cast<char*>(read_buf.get());
    char* write_buf_ptr = nullptr;
    for (size_t i = 0; i < files.size(); ++i) {
        const std::string file = files[i].string();
        FastOS_File f(file.c_str());
        f.EnableDirectIO();
        if (!f.OpenReadOnly()) {
            throw std::runtime_error(std::format("Failed to open file '{}' for reading", file));
        }
        // 1st file has header metadata for the dataset:
        //  uint32: vector count
        //  uint32: dimensionality
        // If the dataset is truncated, we use this to adjust the number of vectors we'll actually store.
        if (i == 0) {
            const ssize_t bytes_read = f.Read(read_buf_ptr, read_buf.size());
            if (bytes_read < 0) {
                throw std::runtime_error(std::format("Read failed for '{}'", file));
            }
            if (bytes_read < 8) {
                throw std::runtime_error("Short read of initial file header");
            }
            memcpy(&vector_count, read_buf_ptr, sizeof(uint32_t));
            memcpy(&dimensions, read_buf_ptr + sizeof(uint32_t), sizeof(uint32_t));
            // Best-effort sanity check in case non-header file was provided as first file.
            if (dimensions < 16 || dimensions > 8192) {
                throw std::runtime_error("Dataset header values look strange. Was the file with header info listed as the first input file?");
            }
            const uint32_t single_vec_size = CellTypeUtils::mem_size(data_type, dimensions);
            std::println(std::cerr, "Full dataset contains {} {} vectors with {} dimensions. Per-vector footprint is {} bytes.",
                         vector_count, to_string(data_type), dimensions, single_vec_size);
            const uint32_t actual_vectors = total_size / single_vec_size;
            assert(actual_vectors <= vector_count);
            if (wanted_vector_count) {
                if (*wanted_vector_count < vector_count) {
                    std::println(std::cerr, "Want to load {} initial vectors from the dataset", *wanted_vector_count);
                    vector_count = *wanted_vector_count;
                } else if (*wanted_vector_count > vector_count) {
                    std::println(std::cerr, "WARNING: Want to load {} vectors, but this is more than the dataset contains. Ignoring this silliness.", *wanted_vector_count);
                }
            }
            if (actual_vectors < vector_count) {
                std::println(std::cerr, "NOTE: Dataset appears truncated; provided files can only contain up to {} full vectors", actual_vectors);
                std::println(std::cerr, "NOTE: Adjusting loaded dataset down to {} vectors.", actual_vectors);
                vector_count = actual_vectors;
                const size_t lost_bytes_at_end = total_size % single_vec_size;
                if (lost_bytes_at_end != 0) {
                    std::println(std::cerr, "NOTE: Ignoring {} bytes at dataset end that are part of a truncated vector.", lost_bytes_at_end);
                }
            }
            total_buffer_size = static_cast<size_t>(vector_count) * single_vec_size;
            std::println(std::cerr, "Allocating {} bytes for data store buffer", total_buffer_size);
            dataset_buf = vespalib::alloc::Alloc::allocMMap(total_buffer_size);
            write_buf_ptr = static_cast<char*>(dataset_buf.get());
            const size_t to_copy = std::min(static_cast<size_t>(bytes_read - 8), total_buffer_size);
            bytes_read_total = to_copy;
            memcpy(write_buf_ptr, read_buf_ptr + 8, to_copy);
        }
        std::println(std::cerr, "Processing input file '{}'...", file);
        while (true) {
            assert(bytes_read_total <= total_buffer_size);
            const size_t to_read = std::min(read_buf.size(), total_buffer_size - bytes_read_total);
            // Always read with full buffer size to avoid any DirectIO size issues at the end
            const ssize_t bytes_read = f.Read(read_buf_ptr, read_buf.size());
            if (bytes_read < 0) {
                throw std::runtime_error(std::format("Read failed for '{}'", file));
            }
            const size_t to_copy = std::min(static_cast<size_t>(bytes_read), to_read);
            memcpy(write_buf_ptr + bytes_read_total, read_buf_ptr, to_copy);
            bytes_read_total += to_copy;
            if (to_copy < read_buf.size()) {
                std::println(std::cerr, "Done with file '{}'; {} bytes read thus far.", file, bytes_read_total);
                break;
            }
        }
        if (bytes_read_total >= total_buffer_size) {
            break;
        }
    }
    if (bytes_read_total != total_buffer_size) {
        throw std::runtime_error(std::format("Unexpected number of bytes read"));
    }
    std::println(std::cerr, "Done loading dataset in {}", std::chrono::duration<double>(load_timer.elapsed()));
    return {std::move(dataset_buf), vector_count, dimensions, data_type};
}

class DatasetDocVectorStore : public search::tensor::DocVectorAccess {
    Dataset       _dataset;
    // TODO refactor, move
    SubspaceType  _subspace_type;
    EmptySubspace _empty_subspace;
public:
    explicit DatasetDocVectorStore(Dataset dataset) noexcept;
    ~DatasetDocVectorStore() override;

    [[nodiscard]] uint32_t vector_count() const noexcept {
        return _dataset._vector_count;
    }
    [[nodiscard]] const SubspaceType& subspace_type() const noexcept {
        return _subspace_type;
    }

    TypedCells get_vector(uint32_t doc_id, uint32_t subspace) const noexcept override {
        auto bundle = get_vectors(doc_id);
        if (subspace < bundle.subspaces()) {
            return bundle.cells(subspace);
        }
        return _empty_subspace.cells();
    }
    VectorBundle get_vectors(uint32_t doc_id) const noexcept override {
        const auto ref = _dataset.raw_vector_view(doc_id);
        assert((ref.size() % _subspace_type.size()) == 0);
        uint32_t subspaces = ref.size() / _subspace_type.size();
        return {ref.data(), subspaces, _subspace_type};
    }

    void prefetch_vector(uint32_t doc_id) const noexcept override {
        const auto ref = _dataset.raw_vector_view(doc_id);
        for (size_t offset = 0; offset < ref.size(); offset += 64) {
            __builtin_prefetch(ref.data() + offset);
        }
    }
};

DatasetDocVectorStore::DatasetDocVectorStore(Dataset dataset) noexcept
    : _dataset(std::move(dataset)),
      _subspace_type(ValueType::make_type(_dataset._cell_type, {{"dims", _dataset._dimensions}})), // TODO multiple subspaces
      _empty_subspace(_subspace_type)
{}

DatasetDocVectorStore::~DatasetDocVectorStore() = default;

/*
 * TODO
 *   - Both preloaded vector store and actual production data store?
 *     - start out with only preloaded store
 *   - Only support saving/loading with prod data store?
 *     - start out with only saving/loading graph itself
 *   - Google Benchmark integration? How to structure, if so?
 *   - Benchmarks:
 *     - feed perf
 *     - query perf
 *       - with recall?
 *   - Auto-filter query truth files based on vector subset actually loaded?
 */

[[nodiscard]] HnswIndexConfig make_hnsw_index_config(uint32_t m, uint32_t ef) {
    return {2*m, m, ef, 10, true}; // TODO more configurable stuff
}

class BenchmarkIndex {
public:
    using IndexType = HnswIndex<HnswIndexType::SINGLE>;
private:

    constexpr static uint32_t min_docs_before_async_insert = 32;

    const CellType                         _cell_type;
    const DistanceMetric                   _distance_metric;
    const HnswIndexConfig                  _hnsw_config;
    std::unique_ptr<DatasetDocVectorStore> _vector_store;
    std::unique_ptr<IndexType>             _index;
    GenerationHandler                      _gen_handler;
    vespalib::BlockingThreadStackExecutor  _multi_prepare_workers;
    vespalib::BlockingThreadStackExecutor  _write_thread;
    std::atomic<uint32_t>                  _n_inserted;
    std::unique_ptr<vespalib::FakeDoom>    _doom;
public:
    BenchmarkIndex(Dataset dataset, const HnswIndexConfig& config, DistanceMetric distance_metric)
        : _cell_type(dataset._cell_type),
          _distance_metric(distance_metric),
          _hnsw_config(config),
          _vector_store(std::make_unique<DatasetDocVectorStore>(std::move(dataset))),
          _index(std::make_unique<IndexType>(*_vector_store,
                                             search::tensor::make_distance_function_factory(_distance_metric, _cell_type),
                                             std::make_unique<InvLogLevelGenerator>(_hnsw_config.max_links_on_inserts()),
                                             _hnsw_config)),
          _gen_handler(),
          _multi_prepare_workers(10, 50),
          _write_thread(1, 500),
          _n_inserted(0),
          _doom(std::make_unique<vespalib::FakeDoom>())
    {}

    ~BenchmarkIndex();

    [[nodiscard]] bool check_symmetry() const {
        return _index->check_link_symmetry();
    }
    [[nodiscard]] const DatasetDocVectorStore& vector_store() const noexcept {
        return *_vector_store;
    }
    [[nodiscard]] uint32_t dataset_vector_count() const noexcept {
        return _vector_store->vector_count();
    }
    [[nodiscard]] MemoryUsage memory_usage() const {
        return _index->memory_usage();
    }
    [[nodiscard]] const IndexType& index() const noexcept {
        return *_index;
    }
    [[nodiscard]] IndexType& index() noexcept {
        return *_index;
    }
    void sync_executors() {
        _multi_prepare_workers.sync();
        _write_thread.sync();
    }
    void add_document_no_prepare_step(uint32_t doc_id) {
        _index->add_document(doc_id);
        commit();
        _n_inserted.fetch_add(1, std::memory_order_relaxed);
    }
    void add_document(uint32_t doc_id) {
        // To avoid screaming about nullptr prepare results, synchronously insert the first few
        // documents into the index to avoid having an empty graph once the happy-go-lucky
        // prepare-searches start running.
        if (_n_inserted.load(std::memory_order_relaxed) < min_docs_before_async_insert) [[unlikely]] {
            add_document_no_prepare_step(doc_id);
            return;
        }
        auto guard = take_read_guard();
        using PrepUP = std::unique_ptr<search::tensor::PrepareResult>;
        std::promise<PrepUP> prepare_promise;
        auto prepare_future = prepare_promise.get_future();

        auto prepare_task = vespalib::makeLambdaTask([this, guard, doc_id, promise = std::move(prepare_promise)] mutable {
            const VectorBundle v = _vector_store->get_vectors(doc_id);
            auto prep = _index->prepare_add_document(doc_id, v, guard);
            promise.set_value(std::move(prep));
        });
        auto complete_task = vespalib::makeLambdaTask([this, doc_id, prepare_future = std::move(prepare_future)] mutable {
            auto prepare_result = prepare_future.get();
            _index->complete_add_document(doc_id, std::move(prepare_result));
            commit();
            _n_inserted.fetch_add(1, std::memory_order_relaxed);
        });
        auto r = _multi_prepare_workers.execute(std::move(prepare_task));
        assert(r.get() == nullptr);
        r = _write_thread.execute(std::move(complete_task));
        assert(r.get() == nullptr);
    }
    [[nodiscard]] GenerationHandler::Guard take_read_guard() const {
        return _gen_handler.takeGuard();
    }
    void commit() {
        _index->assign_generation(_gen_handler.getCurrentGeneration());
        _gen_handler.incGeneration();
        _index->reclaim_memory(_gen_handler.get_oldest_used_generation());
    }

    void fill_top_k_hits(const TypedCells& qv, std::vector<uint32_t>& top_k_out) {
        const uint32_t k = absl::GetFlag(FLAGS_top_k); // TODO move out
        const uint32_t explore_k = absl::GetFlag(FLAGS_explore_k); // TODO move out
        const bool prefetch_tensors = absl::GetFlag(FLAGS_prefetch_tensors); // TODO move out
        double exploration_slack = 0.0; // TODO configurable
        auto df = _index->distance_function_factory().for_query_vector(qv);
        NearestNeighborIndex::Stats stats;
        auto hits = _index->find_top_k(stats, k, *df, explore_k, exploration_slack, prefetch_tensors, _doom->get_doom(), 10000.0);
        top_k_out.clear();
        for (const auto& hit : hits) {
            top_k_out.emplace_back(hit.docid);
        }
    }

};

[[nodiscard]] CellType cell_type_from_flags() {
    auto data_type_str = absl::GetFlag(FLAGS_data_type);
    if (data_type_str == "int8") {
        return CellType::INT8;
    } else if (data_type_str == "bfloat16") {
        return CellType::BFLOAT16;
    } else if (data_type_str == "float") {
        return CellType::FLOAT;
    } else if (data_type_str == "double") {
        return CellType::DOUBLE;
    }
    throw std::invalid_argument(std::format("Unknown vector data type provided: {}", data_type_str));
}

[[nodiscard]] DistanceMetric distance_metric_from_flags() {
    auto metric_str = absl::GetFlag(FLAGS_distance_metric);
    if (metric_str == "euclidean") {
        return DistanceMetric::Euclidean;
    } else if (metric_str == "angular") {
        return DistanceMetric::Angular;
    } else if (metric_str == "geo_degrees") {
        return DistanceMetric::GeoDegrees;
    } else if (metric_str == "inner_product") {
        return DistanceMetric::InnerProduct;
    } else if (metric_str == "hamming") {
        return DistanceMetric::Hamming;
    } else if (metric_str == "prenormalized_angular") {
        return DistanceMetric::PrenormalizedAngular;
    } else if (metric_str == "dotproduct") {
        return DistanceMetric::Dotproduct;
    }
    throw std::invalid_argument(std::format("Unknown distance metric provided: {}", metric_str));
}

[[nodiscard]] std::vector<fs::path> dataset_files_from_flags() {
    auto dataset_files = absl::GetFlag(FLAGS_dataset_files);
    if (dataset_files.empty()) {
        throw std::invalid_argument("No dataset files provided");
    }
    std::vector<fs::path> paths;
    for (const auto& f : dataset_files) {
        paths.emplace_back(f);
    }
    return paths;
}

[[nodiscard]] std::optional<fs::path> index_dir_from_flags() {
    auto maybe_dir = absl::GetFlag(FLAGS_index_dir);
    if (maybe_dir.empty()) {
        return std::nullopt;
    }
    fs::path p(maybe_dir);
    if (!fs::is_directory(p)) {
        throw std::invalid_argument(std::format("'{}' is not a directory", p.string()));
    }
    return p;
}

BenchmarkIndex::~BenchmarkIndex() {
    sync_executors();
}

struct Hasher {
    XXH3_state_t* _state;
    Hasher() : _state(XXH3_createState()) {
        assert(_state);
        XXH3_64bits_reset(_state);
    }
    ~Hasher() { XXH3_freeState(_state); }
    void update(std::string_view buf) noexcept {
        XXH3_64bits_update(_state, buf.data(), buf.size());
    }
    [[nodiscard]] uint64_t finalize() noexcept {
        return XXH3_64bits_digest(_state);
    }
};

[[nodiscard]] uint64_t hash_file_names(const std::vector<fs::path>& files) {
    Hasher h;
    for (const auto& f : files) {
        h.update(f.filename().string());
    }
    return h.finalize();
}

class FileBufferWriter : public search::BufferWriter {
    Alloc _buf;
    FastOS_File _file;
public:
    explicit FileBufferWriter(const fs::path& file);
    ~FileBufferWriter() override;
    void flush() override {
        if (!_file.Write2(_buf.get(), usedLen())) {
            throw std::runtime_error("Failed to write index file");
        }
        rewind();
    }
};

FileBufferWriter::FileBufferWriter(const fs::path& file)
    : _buf(Alloc::allocAlignedHeap(1_Mi, 4_Ki)),
      _file(file.c_str())
{
    setup(_buf.get(), _buf.size());
    if (!_file.OpenWriteOnlyTruncate()) {
        throw std::runtime_error(std::format("Failed to open index file '{}' for writing", file.string()));
    }
    _file.EnableDirectIO();
}

// TODO fsync?
FileBufferWriter::~FileBufferWriter() = default;

void save_index_header(const vespalib::FileHeader& hdr, const fs::path& index_dir) {
    const fs::path hdr_save_path = index_dir / "hdr.bin";
    FastOS_File hdr_file(hdr_save_path.c_str());
    if (!hdr_file.OpenWriteOnlyTruncate()) {
        throw std::runtime_error(std::format("Failed to open index header file '{}' for write", hdr_save_path.string()));
    }
    hdr.writeFile(hdr_file);
    if (!hdr_file.Close()) {
        throw std::runtime_error("Failed to close index header file");
    }
}

// TODO make saving explicitly benchmark-able
void save_index(const BenchmarkIndex& index, const std::string& id, const fs::path& save_dir) {
    fs::path index_dir = save_dir / id;
    // TODO check this _before_ feeding all the data...
    if (fs::exists(index_dir)) {
        throw std::runtime_error(std::format("Index output directory {} already exists", index_dir.string()));
    }
    fs::create_directory(index_dir);

    vespalib::FileHeader hdr;
    auto saver = index.index().make_saver(hdr);
    save_index_header(hdr, index_dir);

    const fs::path graph_path = index_dir / "graph.bin";
    std::println(std::cerr, "Saving HNSW index to directory {}", index_dir.string());
    FileBufferWriter writer(graph_path);
    saver->save(writer);
    // TODO ID mapping for sparse tensors
}

void load_index_header(vespalib::FileHeader& hdr, const fs::path& index_dir) {
    const fs::path hdr_save_path = index_dir / "hdr.bin";
    FastOS_File hdr_file(hdr_save_path.c_str());
    if (!hdr_file.OpenReadOnlyExisting()) {
        throw std::runtime_error(std::format("Failed to open index header file '{}' for read", hdr_save_path.string()));
    }
    hdr.readFile(hdr_file);
}

// TODO make loading explicitly benchmark-able
void load_index(BenchmarkIndex& index, const std::string& id, const fs::path& save_dir) {
    fs::path index_dir = save_dir / id;
    if (!fs::exists(index_dir) || !fs::is_directory(index_dir)) {
        throw std::runtime_error(std::format("Index input directory {} is not a valid directory", index_dir.string()));
    }

    vespalib::FileHeader hdr;
    load_index_header(hdr, index_dir);

    std::println(std::cerr, "Loading HNSW graph from directory {}", index_dir.string());

    const fs::path graph_path = index_dir / "graph.bin";
    FastOS_File graph_file(graph_path.c_str());
    if (!graph_file.OpenReadOnlyExisting()) {
        throw std::runtime_error(std::format("Failed to open index graph file '{}' for read", graph_path.string()));
    }
    auto loader = index.index().make_loader(graph_file, hdr);
    const uint32_t load_commit_interval = std::max(absl::GetFlag(FLAGS_load_commit_interval), 1u);
    uint32_t n = 0;
    while (loader->load_next()) {
        if ((++n % load_commit_interval) == 0) {
            index.commit(); // Avoid bloating hold-lists
        }
    }
    index.commit();
    std::println(std::cerr, "Done loading graph from disk");
}

[[nodiscard]] bool index_is_saved_in_dir(const std::string& id, const fs::path& save_dir) {
    return fs::is_directory(save_dir / id);
}

[[nodiscard]] uint32_t max_links_from_flags() {
    return std::max(absl::GetFlag(FLAGS_max_links), 1u);
}

[[nodiscard]] uint32_t explore_neighbors_from_flags() {
    return absl::GetFlag(FLAGS_explore_neighbors);
}

int main(int argc, char** argv) {
    benchmark::MaybeReenterWithoutASLR(argc, argv);
    absl::SetProgramUsageMessage("A simple HNSW benchmarking tool for testing large indexes");
    absl::ParseCommandLine(argc, argv);
    benchmark::Initialize(&argc, argv); // note: `--help` is intercepted by absl flags

    try {
        const auto dataset_files       = dataset_files_from_flags();
        const auto wanted_vector_count = absl::GetFlag(FLAGS_vector_count);
        const auto cell_type           = cell_type_from_flags();
        const auto distance_metric     = distance_metric_from_flags();
        const auto maybe_index_dir     = index_dir_from_flags();
        const auto m                   = max_links_from_flags();
        const auto ef                  = explore_neighbors_from_flags();
        const auto hnsw_config         = make_hnsw_index_config(m, ef);

        Dataset ds = load_dataset_from_files(dataset_files, cell_type, wanted_vector_count);
        const std::string index_id = std::format("{:016x}_{}d_{}v_{}m_{}ef", hash_file_names(dataset_files),
                                                 ds._dimensions, ds._vector_count, m, ef);

        auto index = std::make_unique<BenchmarkIndex>(std::move(ds), hnsw_config, distance_metric);

        if (maybe_index_dir && index_is_saved_in_dir(index_id, *maybe_index_dir)) {
            Timer load_timer;
            load_index(*index, index_id, *maybe_index_dir);
            std::println(std::cerr, "Loaded index in {}", std::chrono::duration<double>(load_timer.elapsed()));
        } else {
            Timer feed_timer;
            const uint32_t report_batch_size = std::max(absl::GetFlag(FLAGS_report_batch_size), 1u);
            auto batch_start_time = vespalib::steady_clock::now();
            for (uint32_t doc_id = 1; doc_id <= index->dataset_vector_count(); ++doc_id) {
                index->add_document(doc_id);
                // This is no exact since we have in-flight async ops in the executor queues.
                // We assume that the overhead of printing these reports is so marginal that
                // it doesn't really matter in the grand scheme of things...
                if ((doc_id % report_batch_size) == 0) {
                    const auto now = vespalib::steady_clock::now();
                    const double elapsed_s = std::chrono::duration<double>(now - batch_start_time).count();
                    std::println(std::cerr, "{} vectors inserted ({:.2f}s since last report, {:.2f} vectors/s)",
                                 doc_id, elapsed_s, report_batch_size / elapsed_s);
                    batch_start_time = now;
                }
            }
            index->sync_executors();
            // TODO benchmark timings etc etc etc etc
            //  How to benchmark inserts since they change the underlying index? Recreate between each run?
            std::println(std::cerr, "Inserted {} vectors in {}", index->dataset_vector_count(),
                         std::chrono::duration<double>(feed_timer.elapsed()));
            const bool should_check_symmetry = absl::GetFlag(FLAGS_check_symmetry);
            if (should_check_symmetry) {
                std::println(std::cerr, "Checking graph symmetry");
                if (!index->check_symmetry()) {
                    std::println(std::cerr, "HNSW graph symmetry is broken post-inserts!");
                    return 1;
                }
                std::println(std::cerr, "Graph symmetry check OK");
            }
            if (maybe_index_dir) {
                Timer save_timer;
                save_index(*index, index_id, *maybe_index_dir);
                std::println(std::cerr, "Saved index in {}", std::chrono::duration<double>(save_timer.elapsed()));
            }
        }
        std::println(std::cerr, "Graph memory usage: {}", index->memory_usage().toString());
        // TODO move to explicit benchmark part
        const uint32_t n_queries = absl::GetFlag(FLAGS_query_count);
        if (n_queries > 0) {
            std::println(std::cerr, "Running {} queries using dataset as query vectors", n_queries);
            std::vector<uint32_t> hits;
            Timer query_timer;
            for (uint32_t i = 0; i < n_queries; ++i) {
                const uint32_t doc_id = (i % index->dataset_vector_count()) + 1;
                index->fill_top_k_hits(index->vector_store().get_vector(doc_id, 0), hits);
                benchmark::DoNotOptimize(hits.data());
                benchmark::ClobberMemory();
            }
            const auto qd = std::chrono::duration<double>(query_timer.elapsed());
            std::println(std::cerr, "Ran {} queries in {} ({}/query)", n_queries, qd,
                         std::chrono::duration<double, std::milli>(qd / n_queries));
        }
    } catch (std::exception& e) {
        std::println(std::cerr, "Error: {}", e.what());
        return 1;
    }
}
