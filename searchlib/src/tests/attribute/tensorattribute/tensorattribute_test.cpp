// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>
#include <vespa/searchlib/tensor/default_nearest_neighbor_index_factory.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>
#include <vespa/searchlib/tensor/doc_vector_access.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/searchlib/tensor/mips_distance_transform.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index_factory.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index_loader.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index_saver.h>
#include <vespa/searchlib/tensor/serialized_fast_value_attribute.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/util/mmap_file_allocator_factory.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/fastos/file.h>
#include <filesystem>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("tensorattribute_test");

using document::WrongTensorTypeException;
using search::AddressSpaceUsage;
using search::AttributeGuard;
using search::AttributeVector;
using search::attribute::DistanceMetric;
using search::attribute::HnswIndexParams;
using search::queryeval::GlobalFilter;
using search::queryeval::NearestNeighborBlueprint;
using search::tensor::DefaultNearestNeighborIndexFactory;
using search::tensor::DenseTensorAttribute;
using search::tensor::DirectTensorAttribute;
using search::tensor::DistanceCalculator;
using search::tensor::DocVectorAccess;
using search::tensor::HnswIndex;
using search::tensor::HnswIndexType;
using search::tensor::HnswTestNode;
using search::tensor::MipsDistanceFunctionFactoryBase;
using search::tensor::NearestNeighborIndex;
using search::tensor::NearestNeighborIndexFactory;
using search::tensor::NearestNeighborIndexLoader;
using search::tensor::NearestNeighborIndexSaver;
using search::tensor::PrepareResult;
using search::tensor::SerializedFastValueAttribute;
using search::tensor::TensorAttribute;
using search::tensor::VectorBundle;
using vespalib::SharedStringRepo;
using vespalib::datastore::CompactionStrategy;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::CellType;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

using DoubleVector = std::vector<double>;
using generation_t = vespalib::GenerationHandler::generation_t;

std::string sparseSpec("tensor(x{},y{})");
std::string denseSpec("tensor(x[2],y[3])");
std::string a_dimension("a");
std::string b_dimension("b");
std::string x_dimension("x");
std::string vec_2d_spec("tensor(x[2])");
std::string vec_mixed_1m_2d_spec("tensor(a{},x[2])");
std::string vec_mixed_2m_2d_spec("tensor(a{},b{},x[2])");
std::vector<std::string> vec_specs{vec_2d_spec, vec_mixed_1m_2d_spec, vec_mixed_2m_2d_spec};

Value::UP createTensor(const TensorSpec &spec) {
    return value_from_spec(spec, FastValueBuilderFactory::get());
}

std::vector<std::string>
to_string_labels(std::span<const vespalib::string_id> labels)
{
    std::vector<std::string> result;
    for (auto& label : labels) {
        result.emplace_back(SharedStringRepo::Handle::string_from_id(label));
    }
    return result;
}

TensorSpec
vec_2d(double x0, double x1)
{
    return TensorSpec(vec_2d_spec).add({{"x", 0}}, x0).add({{"x", 1}}, x1);
}

TensorSpec
vec_mixed_2d(uint32_t mapped_dimensions, std::vector<std::vector<double>> val)
{
    TensorSpec spec(vec_specs[mapped_dimensions]);
    for (uint32_t a = 0; a < val.size(); ++a) {
        TensorSpec::Address address;
        address.insert(std::make_pair(a_dimension, std::to_string(a)));
        if (mapped_dimensions > 1) {
            address.insert(std::make_pair(b_dimension, std::to_string(a + 10)));
        }
        address.insert(std::make_pair(x_dimension, 0u));
        for (uint32_t x = 0; x < val[a].size(); ++x) {
            address.find(x_dimension)->second = x;
            spec.add(address, val[a][x]);
        }
    }
    return spec;
}

TensorSpec
typed_vec_2d(uint32_t mapped_dimensions, double x0, double x1)
{
    if (mapped_dimensions == 0) {
        return vec_2d(x0, x1);
    } else {
        return vec_mixed_2d(mapped_dimensions, {{x0, x1}});
    }
}

class MockIndexSaver : public NearestNeighborIndexSaver {
private:
    int _index_value;

public:
    explicit MockIndexSaver(int index_value) noexcept : _index_value(index_value) {}
    void save(search::BufferWriter& writer) const override {
        writer.write(&_index_value, sizeof(int));
        writer.flush();
    }
};

class MockIndexLoader : public NearestNeighborIndexLoader {
private:
    int& _index_value;
    search::FileReader<int> _reader;

public:
    MockIndexLoader(int& index_value, FastOS_FileInterface& file)
        : _index_value(index_value),
          _reader(&file)
    {}
    bool load_next() override {
        _index_value = _reader.readHostOrder();
        return false;
    }
};

class MockPrepareResult : public PrepareResult {
public:
    uint32_t docid;
    explicit MockPrepareResult(uint32_t docid_in) noexcept : docid(docid_in) {}
};

class MockNearestNeighborIndex : public NearestNeighborIndex {
private:
    using Entry = std::pair<uint32_t, DoubleVector>;
    using EntryVector = std::vector<Entry>;

    const DocVectorAccess& _vectors;
    EntryVector _adds;
    EntryVector _removes;
    mutable EntryVector _prepare_adds;
    EntryVector _complete_adds;
    generation_t _transfer_gen;
    generation_t _trim_gen;
    mutable size_t _memory_usage_cnt;
    int _index_value;

public:
    explicit MockNearestNeighborIndex(const DocVectorAccess& vectors)
        : _vectors(vectors),
          _adds(),
          _removes(),
          _prepare_adds(),
          _complete_adds(),
          _transfer_gen(std::numeric_limits<generation_t>::max()),
          _trim_gen(std::numeric_limits<generation_t>::max()),
          _memory_usage_cnt(0),
          _index_value(0)
    {
    }
    void clear() {
        _adds.clear();
        _removes.clear();
        _prepare_adds.clear();
        _complete_adds.clear();
    }
    int get_index_value() const {
        return _index_value;
    }
    void save_index_with_value(int value) {
        _index_value = value;
    }
    void expect_empty_add() const {
        EXPECT_TRUE(_adds.empty());
    }
    void expect_empty_prepare_add() const {
        EXPECT_TRUE(_prepare_adds.empty());
    }
    void expect_empty_complete_add() const {
        EXPECT_TRUE(_complete_adds.empty());
    }
    void expect_entry(uint32_t exp_docid, const DoubleVector& exp_vector, const EntryVector& entries) const {
        EXPECT_EQ(1u, entries.size());
        if (entries.size() >= 1u) {
            EXPECT_EQ(exp_docid, entries.back().first);
            EXPECT_EQ(exp_vector, entries.back().second);
        }
    }
    void expect_add(uint32_t exp_docid, const DoubleVector& exp_vector) const {
        expect_entry(exp_docid, exp_vector, _adds);
    }
    void expect_adds(const EntryVector &exp_adds) const {
        EXPECT_EQ(exp_adds, _adds);
    }
    void expect_prepare_adds(const EntryVector &exp) const {
        EXPECT_EQ(exp, _prepare_adds);
    }
    void expect_complete_adds(const EntryVector &exp) const {
        EXPECT_EQ(exp, _complete_adds);
    }
    void expect_empty_remove() const {
        EXPECT_TRUE(_removes.empty());
    }
    void expect_remove(uint32_t exp_docid, const DoubleVector& exp_vector) const {
        expect_entry(exp_docid, exp_vector, _removes);
    }
    void expect_prepare_add(uint32_t exp_docid, const DoubleVector& exp_vector) const {
        expect_entry(exp_docid, exp_vector, _prepare_adds);
    }
    void expect_complete_add(uint32_t exp_docid, const DoubleVector& exp_vector) const {
        expect_entry(exp_docid, exp_vector, _complete_adds);
    }
    generation_t get_transfer_gen() const { return _transfer_gen; }
    generation_t get_trim_gen() const { return _trim_gen; }
    size_t memory_usage_cnt() const { return _memory_usage_cnt; }

    void add_document(uint32_t docid) override {
        auto vector = _vectors.get_vector(docid, 0).typify<double>();
        _adds.emplace_back(docid, DoubleVector(vector.begin(), vector.end()));
    }
    std::unique_ptr<PrepareResult> prepare_add_document(uint32_t docid,
                                                        VectorBundle vectors,
                                                        vespalib::GenerationHandler::Guard guard) const override {
        (void) guard;
        assert(vectors.subspaces() == 1);
        auto d_vector = vectors.cells(0).typify<double>();
        _prepare_adds.emplace_back(docid, DoubleVector(d_vector.begin(), d_vector.end()));
        return std::make_unique<MockPrepareResult>(docid);
    }
    void complete_add_document(uint32_t docid,
                               std::unique_ptr<PrepareResult> prepare_result) override {
        auto* mock_result = dynamic_cast<MockPrepareResult*>(prepare_result.get());
        assert(mock_result);
        EXPECT_EQ(docid, mock_result->docid);
        auto vector = _vectors.get_vector(docid, 0).typify<double>();
        _complete_adds.emplace_back(docid, DoubleVector(vector.begin(), vector.end()));
    }
    void remove_document(uint32_t docid) override {
        auto vector = _vectors.get_vector(docid, 0).typify<double>();
        _removes.emplace_back(docid, DoubleVector(vector.begin(), vector.end()));
    }
    void assign_generation(generation_t current_gen) override {
        _transfer_gen = current_gen;
    }
    void reclaim_memory(generation_t oldest_used_gen) override {
        _trim_gen = oldest_used_gen;
    }
    bool consider_compact(const CompactionStrategy&) override {
        return false;
    }
    vespalib::MemoryUsage update_stat(const CompactionStrategy&) override {
        ++_memory_usage_cnt;
        return {};
    }
    vespalib::MemoryUsage memory_usage() const override {
        ++_memory_usage_cnt;
        return {};
    }
    void populate_address_space_usage(AddressSpaceUsage&) const override {}
    void get_state(const vespalib::slime::Inserter&) const override {}
    void shrink_lid_space(uint32_t) override { }
    std::unique_ptr<NearestNeighborIndexSaver> make_saver(vespalib::GenericHeader& header) const override {
        (void) header;
        if (_index_value != 0) {
            return std::make_unique<MockIndexSaver>(_index_value);
        }
        return {};
    }
    std::unique_ptr<NearestNeighborIndexLoader> make_loader(FastOS_FileInterface& file, const vespalib::GenericHeader& header) override {
        (void) header;
        return std::make_unique<MockIndexLoader>(_index_value, file);
    }
    std::vector<Neighbor> find_top_k(uint32_t k,
                                     const search::tensor::BoundDistanceFunction &df,
                                     uint32_t explore_k,
                                     const vespalib::Doom& doom,
                                     double distance_threshold) const override
    {
        (void) k;
        (void) df;
        (void) explore_k;
        (void) doom;
        (void) distance_threshold;
        return {};
    }
    std::vector<Neighbor> find_top_k_with_filter(uint32_t k,
                                                 const search::tensor::BoundDistanceFunction &df,
                                                 const GlobalFilter& filter, uint32_t explore_k,
                                                 const vespalib::Doom& doom,
                                                 double distance_threshold) const override
    {
        (void) k;
        (void) df;
        (void) explore_k;
        (void) filter;
        (void) doom;
        (void) distance_threshold;
        return {};
    }

    search::tensor::DistanceFunctionFactory &distance_function_factory() const override {
        static search::tensor::DistanceFunctionFactory::UP my_dist_fun = search::tensor::make_distance_function_factory(search::attribute::DistanceMetric::Euclidean, vespalib::eval::CellType::DOUBLE);
        return *my_dist_fun;
    }

    uint32_t check_consistency(uint32_t) const noexcept override {
        return 0;
    }
};

class MockNearestNeighborIndexFactory : public NearestNeighborIndexFactory {

    std::unique_ptr<NearestNeighborIndex> make(const DocVectorAccess& vectors,
                                               size_t vector_size,
                                               bool multi_vector_index,
                                               CellType cell_type,
                                               const search::attribute::HnswIndexParams& params) const override {
        (void) vector_size;
        (void) params;
        (void) multi_vector_index;
        assert(cell_type == CellType::DOUBLE);
        return std::make_unique<MockNearestNeighborIndex>(vectors);
    }
};

const std::string test_dir = "test_data/";
const std::string attr_name = test_dir + "my_attr";

const std::string hnsw_max_squared_norm = "hnsw.max_squared_norm";

struct FixtureTraits {
    bool use_dense_tensor_attribute = false;
    bool use_direct_tensor_attribute = false;
    bool enable_hnsw_index = false;
    bool use_mock_index = false;
    bool use_mmap_file_allocator = false;
    bool use_mips_distance = false;

    FixtureTraits dense() && {
        use_dense_tensor_attribute = true;
        enable_hnsw_index = false;
        return *this;
    }

    FixtureTraits mmap_file_allocator() && {
        use_mmap_file_allocator = true;
        return *this;
    }

    FixtureTraits hnsw() && {
        use_dense_tensor_attribute = true;
        enable_hnsw_index = true;
        use_mock_index = false;
        return *this;
    }

    FixtureTraits mixed_hnsw() && {
        use_dense_tensor_attribute = false;
        enable_hnsw_index = true;
        use_mock_index = false;
        return *this;
    }

    FixtureTraits mock_hnsw() && {
        use_dense_tensor_attribute = true;
        enable_hnsw_index = true;
        use_mock_index = true;
        return *this;
    }

    FixtureTraits mips_hnsw() && {
        use_dense_tensor_attribute = true;
        enable_hnsw_index = true;
        use_mock_index = false;
        use_mips_distance = true;
        return *this;
    }

    FixtureTraits direct() && {
        use_dense_tensor_attribute = false;
        use_direct_tensor_attribute = true;
        return *this;
    }

};

struct WrapValue {
    std::unique_ptr<Value> _value;
    WrapValue()
        : _value()
    {
    }
    WrapValue(std::unique_ptr<Value> value)
        : _value(std::move(value))
    {
    }
    WrapValue(const TensorSpec& spec)
        : _value(createTensor(spec))
    {
    }
    bool operator==(const WrapValue& rhs) const {
        if (_value) {
            return rhs._value && *_value == *rhs._value;
        } else {
            return !rhs._value;
        }
    }
};

void PrintTo(const WrapValue& value, std::ostream* os) {
    if (value._value) {
        *os << *value._value;
    } else {
        *os << "null";
    }
}

struct Fixture {
    using BasicType = search::attribute::BasicType;
    using CollectionType = search::attribute::CollectionType;
    using Config = search::attribute::Config;

    search::test::DirectoryHandler _dir_handler;
    Config _cfg;
    std::string _name;
    std::string _typeSpec;
    ValueType _tensor_type;
    bool _use_mock_index;
    std::unique_ptr<NearestNeighborIndexFactory> _index_factory;
    std::shared_ptr<TensorAttribute> _tensorAttr;
    std::shared_ptr<AttributeVector> _attr;
    vespalib::ThreadStackExecutor _executor;
    bool _denseTensors;
    FixtureTraits _traits;
    std::string _mmap_allocator_base_dir;

    explicit Fixture(const std::string &typeSpec, FixtureTraits traits = FixtureTraits());

    ~Fixture();

    void setup() {
        _cfg.setTensorType(ValueType::from_spec(_typeSpec));
        if (_cfg.tensorType().is_dense()) {
            _denseTensors = true;
        }
        if (_traits.use_mmap_file_allocator) {
            _cfg.setPaged(true);
        }
        if (_traits.use_mock_index) {
            _index_factory = std::make_unique<MockNearestNeighborIndexFactory>();
        } else {
            _index_factory = std::make_unique<DefaultNearestNeighborIndexFactory>();
        }
        _tensorAttr = makeAttr();
        _attr = _tensorAttr;
        _attr->addReservedDoc();
    }

    void set_hnsw_index_params(const HnswIndexParams &params) {
        _cfg.set_hnsw_index_params(params);
        setup();
    }

    void disable_hnsw_index() {
        _cfg.clear_hnsw_index_params();
        setup();
    }

    std::shared_ptr<TensorAttribute> makeAttr() {
        if (_traits.use_dense_tensor_attribute) {
            assert(_denseTensors);
            return std::make_shared<DenseTensorAttribute>(_name, _cfg, *_index_factory);
        } else if (_traits.use_direct_tensor_attribute) {
            return std::make_shared<DirectTensorAttribute>(_name, _cfg);
        } else {
            return std::make_shared<SerializedFastValueAttribute>(_name, _cfg);
        }
    }

    const DenseTensorAttribute& as_dense_tensor() const {
        auto result = dynamic_cast<const DenseTensorAttribute*>(_tensorAttr.get());
        assert(result != nullptr);
        return *result;
    }

    template <typename IndexType>
    IndexType& get_nearest_neighbor_index() {
        assert(_tensorAttr->nearest_neighbor_index() != nullptr);
        auto index = dynamic_cast<const IndexType*>(_tensorAttr->nearest_neighbor_index());
        assert(index != nullptr);
        return *const_cast<IndexType*>(index);
    }

    HnswIndex<HnswIndexType::SINGLE>& hnsw_index() {
        return get_nearest_neighbor_index<HnswIndex<HnswIndexType::SINGLE>>();
    }

    template <HnswIndexType type>
    HnswIndex<type>& hnsw_typed_index() {
        return get_nearest_neighbor_index<HnswIndex<type>>();
    }

    MockNearestNeighborIndex& mock_index() {
        return get_nearest_neighbor_index<MockNearestNeighborIndex>();
    }

    void ensureSpace(uint32_t docId) {
        while (_attr->getNumDocs() <= docId) {
            uint32_t newDocId = 0u;
            _attr->addDoc(newDocId);
        }
    }

    void clearTensor(uint32_t docId) {
        ensureSpace(docId);
        _tensorAttr->clearDoc(docId);
        _attr->commit();
    }

    void set_tensor(uint32_t docid, const TensorSpec &spec) {
        set_tensor_internal(docid, *createTensor(spec));
    }

    std::unique_ptr<PrepareResult> prepare_set_tensor(uint32_t docid, const TensorSpec& spec) const {
        return _tensorAttr->prepare_set_tensor(docid, *createTensor(spec));
    }

    void complete_set_tensor(uint32_t docid, const TensorSpec& spec, std::unique_ptr<PrepareResult> prepare_result) {
        ensureSpace(docid);
        _tensorAttr->complete_set_tensor(docid, *createTensor(spec), std::move(prepare_result));
        _attr->commit();
    }

    void set_empty_tensor(uint32_t docid) {
        set_tensor_internal(docid, *_tensorAttr->getEmptyTensor());
    }

    void set_tensor_internal(uint32_t docId, const Value &tensor) {
        ensureSpace(docId);
        _tensorAttr->setTensor(docId, tensor);
        _attr->commit();
    }

    generation_t get_current_gen() const {
        return _attr->getCurrentGeneration();
    }

    search::attribute::Status getStatus() {
        _attr->commit(true);
        return _attr->getStatus();
    }

    WrapValue get_tensor(uint32_t docId) {
        AttributeGuard guard(_attr);
        return { _tensorAttr->getTensor(docId) };
    }

    bool save() {
        return _attr->save();
    }

    bool load() {
        _tensorAttr = makeAttr();
        _attr = _tensorAttr;
        return _attr->load();
    }

    void loadWithExecutor() {
        _tensorAttr = makeAttr();
        _attr = _tensorAttr;
        bool loadok = _attr->load(&_executor);
        EXPECT_TRUE(loadok);
    }

    TensorSpec expDenseTensor3() const {
        return TensorSpec(denseSpec)
                .add({{"x", 0}, {"y", 1}}, 11)
                .add({{"x", 1}, {"y", 2}}, 0);
    }

    TensorSpec expDenseFillTensor() const {
        return TensorSpec(denseSpec)
                .add({{"x", 0}, {"y", 0}}, 5)
                .add({{"x", 1}, {"y", 2}}, 0);
    }

    TensorSpec expEmptyDenseTensor() const {
        return {denseSpec};
    }

    std::string expEmptyDenseTensorSpec() const {
        return denseSpec;
    }

    uint32_t count_mapped_dimensions() { return _tensor_type.count_mapped_dimensions(); }

    vespalib::FileHeader get_file_header();
    void set_example_tensors();
    void assert_example_tensors();
    void save_example_tensors_with_mock_index();
    void testEmptyAttribute();
    void testSetTensorValue();
    void testSaveLoad();
    void testCompaction();
    void testTensorTypeFileHeaderTag();
    void testEmptyTensor();
    void testSerializedTensorRef();
    void testOnHoldAccounting();
    void test_populate_address_space_usage();
    void test_mmap_file_allocator();
};

Fixture::Fixture(const std::string &typeSpec, FixtureTraits traits)
    : _dir_handler(test_dir),
      _cfg(BasicType::TENSOR, CollectionType::SINGLE),
      _name(attr_name),
      _typeSpec(typeSpec),
      _tensor_type(ValueType::from_spec(typeSpec)),
      _index_factory(),
      _tensorAttr(),
      _attr(),
      _executor(1),
      _denseTensors(false),
      _traits(traits),
      _mmap_allocator_base_dir("mmap-file-allocator-factory-dir")
{
    if (traits.enable_hnsw_index) {
        auto dm = traits.use_mips_distance ? DistanceMetric::Dotproduct : DistanceMetric::Euclidean;
        _cfg.set_distance_metric(dm);
        _cfg.set_hnsw_index_params(HnswIndexParams(4, 20, dm));
    }
    vespalib::alloc::MmapFileAllocatorFactory::instance().setup(_mmap_allocator_base_dir);
    setup();
}

Fixture::~Fixture()
{
    vespalib::alloc::MmapFileAllocatorFactory::instance().setup("");
    std::filesystem::remove_all(std::filesystem::path(_mmap_allocator_base_dir));
}

void
Fixture::set_example_tensors()
{
    set_tensor(1, vec_2d(3, 5));
    set_tensor(2, vec_2d(7, 9));
}

void
Fixture::assert_example_tensors()
{
    EXPECT_EQ(WrapValue(vec_2d(3, 5)), get_tensor(1));
    EXPECT_EQ(WrapValue(vec_2d(7, 9)), get_tensor(2));
}

void
Fixture::save_example_tensors_with_mock_index()
{
    set_example_tensors();
    mock_index().save_index_with_value(123);
    EXPECT_TRUE(save());
    EXPECT_TRUE(std::filesystem::exists(std::filesystem::path(_name + ".nnidx")));
}

void
Fixture::testEmptyAttribute()
{
    SCOPED_TRACE("testEmptyAttribute");
    EXPECT_EQ(1u, _attr->getNumDocs());
    EXPECT_EQ(1u, _attr->getCommittedDocIdLimit());
}

void
Fixture::testSetTensorValue()
{
    SCOPED_TRACE("testSetTensorValue");
    ensureSpace(4);
    EXPECT_EQ(5u, _attr->getNumDocs());
    EXPECT_EQ(WrapValue(), get_tensor(4));
    VESPA_EXPECT_EXCEPTION(set_tensor(4, TensorSpec("double")),
                     WrongTensorTypeException,
                     "but other tensor type is 'double'");
    EXPECT_EQ(WrapValue(), get_tensor(4));
    set_empty_tensor(4);
    if (_denseTensors) {
        EXPECT_EQ(WrapValue(expEmptyDenseTensor()), get_tensor(4));
        set_tensor(3, expDenseTensor3());
        EXPECT_EQ(WrapValue(expDenseTensor3()), get_tensor(3));
    } else {
        EXPECT_EQ(WrapValue(TensorSpec(sparseSpec)), get_tensor(4));
        set_tensor(3, TensorSpec(sparseSpec)
                .add({{"x", ""}, {"y", ""}}, 11));
        EXPECT_EQ(WrapValue(TensorSpec(sparseSpec)
                                        .add({{"x", ""}, {"y", ""}}, 11)), get_tensor(3));
    }
    EXPECT_EQ(WrapValue(), get_tensor(2));
    clearTensor(3);
    EXPECT_EQ(WrapValue(), get_tensor(3));
}

void
Fixture::testSaveLoad()
{
    SCOPED_TRACE("testSaveLoad");
    ensureSpace(4);
    set_empty_tensor(4);
    if (_denseTensors) {
        set_tensor(3, expDenseTensor3());
    } else {
        set_tensor(3, TensorSpec(sparseSpec)
                .add({{"x", ""}, {"y", "1"}}, 11));
    }
    EXPECT_TRUE(save());
    EXPECT_TRUE(load());
    EXPECT_EQ(5u, _attr->getNumDocs());
    EXPECT_EQ(5u, _attr->getCommittedDocIdLimit());
    if (_denseTensors) {
        EXPECT_EQ(WrapValue(expDenseTensor3()), get_tensor(3));
        EXPECT_EQ(WrapValue(expEmptyDenseTensor()), get_tensor(4));
    } else {
        EXPECT_EQ(WrapValue(TensorSpec(sparseSpec)
                                        .add({{"x", ""}, {"y", "1"}}, 11)), get_tensor(3));
        EXPECT_EQ(WrapValue(TensorSpec(sparseSpec)), get_tensor(4));
    }
    EXPECT_EQ(WrapValue(), get_tensor(2));
}

void
Fixture::testCompaction()
{
    SCOPED_TRACE("testCompaction");
    ensureSpace(4);
    TensorSpec empty_xy_tensor(sparseSpec);
    TensorSpec simple_tensor = TensorSpec(sparseSpec)
            .add({{"x", ""}, {"y", "1"}}, 11);
    TensorSpec fill_tensor = TensorSpec(sparseSpec)
            .add({{"x", ""}, {"y", ""}}, 5);
    if (_denseTensors) {
        empty_xy_tensor = expEmptyDenseTensor();
        simple_tensor = expDenseTensor3();
        fill_tensor = expDenseFillTensor();
    }
    set_empty_tensor(4);
    set_tensor(3, simple_tensor);
    set_tensor(2, fill_tensor);
    clearTensor(2);
    set_tensor(2, fill_tensor);
    search::attribute::Status oldStatus = getStatus();
    search::attribute::Status newStatus = oldStatus;
    auto guard = _attr->makeReadGuard(false);
    uint64_t iter = 2049;
    uint64_t iterLimit = 100000;
    for (; iter < iterLimit; ++iter) {
        clearTensor(2);
        set_tensor(2, fill_tensor);
        if ((iter & (iter - 1)) == 0) {
            // Temporarily drop read guard when iter crosses a power of 2.
            guard.reset();
            _attr->commit(true);
            _attr->commit(true);
            guard = _attr->makeReadGuard(false);
        }
        newStatus = getStatus();
        if (newStatus.getUsed() < oldStatus.getUsed()) {
            break;
        }
        oldStatus = newStatus;
    }
    EXPECT_GT(iterLimit, iter);
    LOG(info,
        "iter = %" PRIu64 ", memory usage %" PRIu64 " -> %" PRIu64,
        iter, oldStatus.getUsed(), newStatus.getUsed());
    EXPECT_EQ(WrapValue(), get_tensor(1));
    EXPECT_EQ(WrapValue(fill_tensor), get_tensor(2));
    EXPECT_EQ(WrapValue(simple_tensor), get_tensor(3));
    EXPECT_EQ(WrapValue(empty_xy_tensor), get_tensor(4));
}

vespalib::FileHeader
Fixture::get_file_header()
{
    vespalib::FileHeader header;
    FastOS_File file;
    std::string file_name = attr_name + ".dat";
    EXPECT_TRUE(file.OpenReadOnly(file_name.c_str()));
    (void) header.readFile(file);
    return header;
}

void
Fixture::testTensorTypeFileHeaderTag()
{
    SCOPED_TRACE("testTensorTypeFileHeaderTag");
    ensureSpace(4);
    EXPECT_TRUE(save());

    auto header = get_file_header();
    EXPECT_TRUE(header.hasTag("tensortype"));
    EXPECT_EQ(_typeSpec, header.getTag("tensortype").asString());
    if (_traits.use_dense_tensor_attribute) {
        EXPECT_EQ(1u, header.getTag("version").asInteger());
    } else {
        EXPECT_EQ(0u, header.getTag("version").asInteger());
    }
}

void
Fixture::testEmptyTensor()
{
    SCOPED_TRACE("testEmptyTensor");
    const TensorAttribute &tensorAttr = *_tensorAttr;
    Value::UP emptyTensor = tensorAttr.getEmptyTensor();
    if (_denseTensors) {
        std::string expSpec = expEmptyDenseTensorSpec();
        EXPECT_EQ(emptyTensor->type(), ValueType::from_spec(expSpec));
    } else {
        EXPECT_EQ(emptyTensor->type(), tensorAttr.getConfig().tensorType());
        EXPECT_EQ(emptyTensor->type(), ValueType::from_spec(_typeSpec));
    }
}

void
Fixture::testSerializedTensorRef()
{
    SCOPED_TRACE("testSerializedTensorRef");
    const TensorAttribute &tensorAttr = *_tensorAttr;
    if (_traits.use_dense_tensor_attribute || _traits.use_direct_tensor_attribute) {
        EXPECT_FALSE(tensorAttr.supports_get_serialized_tensor_ref());
        return;
    }
    EXPECT_TRUE(tensorAttr.supports_get_serialized_tensor_ref());
    if (_denseTensors) {
        set_tensor(3, expDenseTensor3());
    } else {
        set_tensor(3, TensorSpec(sparseSpec)
                   .add({{"x", "one"}, {"y", "two"}}, 11)
                   .add({{"x", "three"}, {"y", "four"}}, 17));
    }
    auto ref = tensorAttr.get_serialized_tensor_ref(3);
    auto vectors = ref.get_vectors();
    if (_denseTensors) {
        EXPECT_EQ(1u, vectors.subspaces());
        auto cells = vectors.cells(0).typify<double>();
        auto labels = ref.get_labels(0);
        EXPECT_EQ(0u, labels.size());
        EXPECT_EQ((std::vector<double>{0.0, 11.0, 0.0, 0.0, 0.0, 0.0}), (std::vector<double>{ cells.begin(), cells.end() }));
    } else {
        EXPECT_EQ(2u, vectors.subspaces());
        auto cells = vectors.cells(0).typify<double>();
        auto labels = ref.get_labels(0);
        EXPECT_EQ((std::vector<std::string>{"one", "two"}), to_string_labels(labels));
        EXPECT_EQ((std::vector<double>{11.0}), (std::vector<double>{ cells.begin(), cells.end() }));
        cells = vectors.cells(1).typify<double>();
        labels = ref.get_labels(1);
        EXPECT_EQ((std::vector<std::string>{"three", "four"}), to_string_labels(labels));
        EXPECT_EQ((std::vector<double>{17.0}), (std::vector<double>{ cells.begin(), cells.end() }));
    }
    clearTensor(3);
}

void
Fixture::testOnHoldAccounting()
{
    SCOPED_TRACE("testOnHoldAccounting");
    {
        AttributeGuard guard(_attr);
        EXPECT_EQ(0u, getStatus().getOnHold());
        set_empty_tensor(1);
        clearTensor(1);
        EXPECT_NE(0u, getStatus().getOnHold());
    }
    EXPECT_EQ(0u, getStatus().getOnHold());
}

void
Fixture::test_populate_address_space_usage()
{
    SCOPED_TRACE("test_populate_address_space_usage");
    search::AddressSpaceUsage usage = _attr->getAddressSpaceUsage();
    const auto& all = usage.get_all();
    if (_denseTensors) {
        EXPECT_EQ(1u, all.size());
        EXPECT_EQ(1u, all.count("tensor-store"));
    } else {
        EXPECT_EQ(2u, all.size());
        EXPECT_EQ(1u, all.count("tensor-store"));
        EXPECT_EQ(1u, all.count("shared-string-repo"));
    }
}

void
Fixture::test_mmap_file_allocator()
{
    SCOPED_TRACE("test_mmap_file_allocator");
    std::filesystem::path allocator_dir(_mmap_allocator_base_dir + "/0.my_attr");
    if (!_traits.use_mmap_file_allocator) {
        EXPECT_FALSE(std::filesystem::is_directory(allocator_dir));
    } else {
        EXPECT_TRUE(std::filesystem::is_directory(allocator_dir));
        int entry_cnt = 0;
        for (auto& entry : std::filesystem::directory_iterator(allocator_dir)) {
            EXPECT_LT(0u, entry.file_size());
            ++entry_cnt;
        }
        EXPECT_LT(0, entry_cnt);
    }
}

template <class MakeFixture>
void testAll(MakeFixture &&f)
{
    f()->testEmptyAttribute();
    f()->testSetTensorValue();
    f()->testSaveLoad();
    f()->testCompaction();
    f()->testTensorTypeFileHeaderTag();
    f()->testEmptyTensor();
    f()->testSerializedTensorRef();
    f()->testOnHoldAccounting();
    f()->test_populate_address_space_usage();
    f()->test_mmap_file_allocator();
}

TEST(TensorAttributeTest, Test_sparse_tensors_with_generic_tensor_attribute)
{
    testAll([]() { return std::make_shared<Fixture>(sparseSpec); });
}

TEST(TensorAttributeTest, Test_sparse_tensors_with_generic_tensor_attribute_with_paged_setting)
{
    testAll([]() { return std::make_shared<Fixture>(sparseSpec, FixtureTraits().mmap_file_allocator()); });
}

TEST(TensorAttributeTest, Test_sparse_tensors_with_direct_tensor_attribute)
{
    testAll([]() { return std::make_shared<Fixture>(sparseSpec, FixtureTraits().direct()); });
}

TEST(TensorAttributeTest, Test_dense_tensors_with_generic_tensor_attribute)
{
    testAll([]() { return std::make_shared<Fixture>(denseSpec); });
}

TEST(TensorAttributeTest, Test_dense_tensors_with_generic_tensor_attribute_with_paged_setting)
{
    testAll([]() { return std::make_shared<Fixture>(denseSpec, FixtureTraits().mmap_file_allocator()); });
}

TEST(TensorAttributeTest, Test_dense_tensors_with_dense_tensor_attribute)
{
    testAll([]() { return std::make_shared<Fixture>(denseSpec, FixtureTraits().dense()); });
}

TEST(TensorAttributeTest, Test_dense_tensors_with_dense_tensor_attribute_with_paged_setting)
{
    testAll([]() { return std::make_shared<Fixture>(denseSpec, FixtureTraits().dense().mmap_file_allocator()); });
}

TEST(TensorAttributeTest, Hnsw_index_is_NOT_instantiated_in_dense_tensor_attribute_by_default)
{
    Fixture f(vec_2d_spec, FixtureTraits().dense());
    const auto& tensor = f.as_dense_tensor();
    EXPECT_TRUE(tensor.nearest_neighbor_index() == nullptr);
}


template <HnswIndexType type>
class TensorAttributeHnswIndex : public Fixture
{
public:
    TensorAttributeHnswIndex(const std::string &type_spec, FixtureTraits traits)
        : Fixture(type_spec, traits)
    {
    }
    void test_setup();
    void test_save_load(bool multi_node);
    void test_address_space_usage();
};

template <HnswIndexType type>
void
TensorAttributeHnswIndex<type>::test_setup()
{
    auto& index = hnsw_typed_index<type>();
    const auto& cfg = index.config();
    EXPECT_EQ(8u, cfg.max_links_at_level_0());
    EXPECT_EQ(4u, cfg.max_links_on_inserts());
    EXPECT_EQ(20u, cfg.neighbors_to_explore_at_construction());
    EXPECT_TRUE(cfg.heuristic_select_neighbors());
}

void
expect_level_0(uint32_t exp_nodeid, const HnswTestNode& node)
{
    ASSERT_GE(node.size(), 1u);
    ASSERT_EQ(1u, node.level(0).size());
    EXPECT_EQ(exp_nodeid, node.level(0)[0]);
}

template <HnswIndexType type>
void
TensorAttributeHnswIndex<type>::test_save_load(bool multi_node)
{
    uint32_t mapped_dimensions = count_mapped_dimensions();
    // Set two points that will be linked together in level 0 of the hnsw graph.
    if (multi_node) {
        set_tensor(1, vec_mixed_2d(mapped_dimensions, {{3, 5}, {7, 9}}));
    } else {
        set_tensor(1, typed_vec_2d(mapped_dimensions, 3, 5));
        set_tensor(2, typed_vec_2d(mapped_dimensions, 7, 9));
    }

    auto old_attr = _attr;
    auto &index_a = hnsw_typed_index<type>();
    expect_level_0(2, index_a.get_node(1));
    expect_level_0(1, index_a.get_node(2));
    EXPECT_TRUE(save());
    EXPECT_TRUE(std::filesystem::exists(std::filesystem::path(attr_name + ".nnidx")));

    EXPECT_TRUE(load());
    auto &index_b = hnsw_typed_index<type>();
    EXPECT_NE(&index_a, &index_b);
    expect_level_0(2, index_b.get_node(1));
    expect_level_0(1, index_b.get_node(2));
}

template <HnswIndexType type>
void
TensorAttributeHnswIndex<type>::test_address_space_usage()
{
    bool dense = type == HnswIndexType::SINGLE;
    search::AddressSpaceUsage usage = _attr->getAddressSpaceUsage();
    const auto& all = usage.get_all();
    EXPECT_EQ(dense ? 3u : 5u, all.size());
    EXPECT_EQ(1u, all.count("tensor-store"));
    EXPECT_EQ(1u, all.count("hnsw-levels-store"));
    EXPECT_EQ(1u, all.count("hnsw-links-store"));
    if (!dense) {
        EXPECT_EQ(1u, all.count("hnsw-nodeid-mapping"));
        EXPECT_EQ(1u, all.count("shared-string-repo"));
    }
}

class DenseTensorAttributeHnswIndex : public TensorAttributeHnswIndex<HnswIndexType::SINGLE> {
public:
    DenseTensorAttributeHnswIndex() : TensorAttributeHnswIndex<HnswIndexType::SINGLE>(vec_2d_spec, FixtureTraits().hnsw()) {}
};

class MixedTensorAttributeHnswIndex : public TensorAttributeHnswIndex<HnswIndexType::MULTI> {
public:
    MixedTensorAttributeHnswIndex(uint32_t mapped_dimensions) : TensorAttributeHnswIndex<HnswIndexType::MULTI>(vec_specs[mapped_dimensions], FixtureTraits().mixed_hnsw()) {}
};

class MixedTensorAttributeTest : public ::testing::TestWithParam<uint32_t> {
protected:
    MixedTensorAttributeTest();
    ~MixedTensorAttributeTest() override;
};

MixedTensorAttributeTest::MixedTensorAttributeTest()
    : ::testing::TestWithParam<uint32_t>()
{
}

MixedTensorAttributeTest::~MixedTensorAttributeTest() = default;

TEST(TensorAttributeTest, Hnsw_index_is_instantiated_in_dense_tensor_attribute_when_specified_in_config)
{
    DenseTensorAttributeHnswIndex f;
    f.test_setup();
}

TEST(TensorAttributeTest, Hnsw_index_is_integrated_in_dense_tensor_attribute_and_can_be_saved_and_loaded)
{
    DenseTensorAttributeHnswIndex f;
    f.test_save_load(false);
}

TEST_P(MixedTensorAttributeTest, Hnsw_index_is_instantiated_in_mixed_tensor_attribute_when_specified_in_config)
{
    MixedTensorAttributeHnswIndex f(GetParam());
    f.test_setup();
}

TEST_P(MixedTensorAttributeTest, Hnsw_index_is_integrated_in_mixed_tensor_attribute_and_can_be_saved_and_loaded)
{
    MixedTensorAttributeHnswIndex f(GetParam());
    f.test_save_load(false);
}

TEST_P(MixedTensorAttributeTest, Hnsw_index_is_integrated_in_mixed_tensor_attribute_and_can_be_saved_and_loaded_with_multiple_points_per_document)
{
    MixedTensorAttributeHnswIndex f(GetParam());
    f.test_save_load(true);
}

TEST(TensorAttributeTest, Populates_address_space_usage_in_dense_tensor_attribute_with_hnsw_index)
{
    DenseTensorAttributeHnswIndex f;
    f.test_address_space_usage();
}

TEST_P(MixedTensorAttributeTest, Populates_address_space_usage_in_mixed_tensor_attribute_with_hnsw_index)
{
    MixedTensorAttributeHnswIndex f(GetParam());
    f.test_address_space_usage();
}

class DenseTensorAttributeMockIndex : public Fixture {
public:
    DenseTensorAttributeMockIndex() : Fixture(vec_2d_spec, FixtureTraits().mock_hnsw()) {}
    void add_vec_a();
};

void
DenseTensorAttributeMockIndex::add_vec_a()
{
    auto& index = mock_index();
    auto vec_a = vec_2d(3, 5);
    auto prepare_result = prepare_set_tensor(1, vec_a);
    index.expect_prepare_add(1, {3, 5});
    complete_set_tensor(1, vec_a, std::move(prepare_result));
    EXPECT_EQ(WrapValue(vec_a), get_tensor(1));
    index.expect_complete_add(1, {3, 5});
    index.clear();
}

TEST(TensorAttributeTest, setTensor_updates_nearest_neighbor_index)
{
    DenseTensorAttributeMockIndex f;
    auto& index = f.mock_index();

    f.set_tensor(1, vec_2d(3, 5));
    index.expect_add(1, {3, 5});
    index.expect_empty_remove();
    index.clear();

    // Replaces previous value.
    f.set_tensor(1, vec_2d(7, 9));
    index.expect_remove(1, {3, 5});
    index.expect_add(1, {7, 9});
}

TEST(TensorAttributeTest, nearest_neighbor_index_can_be_updated_in_two_phases)
{
    DenseTensorAttributeMockIndex f;
    auto& index = f.mock_index();
    f.add_vec_a();
    {
        // Replaces previous value.
        auto vec_b = vec_2d(7, 9);
        auto prepare_result = f.prepare_set_tensor(1, vec_b);
        index.expect_prepare_add(1, {7, 9});
        f.complete_set_tensor(1, vec_b, std::move(prepare_result));
        index.expect_remove(1, {3, 5});
        EXPECT_EQ(WrapValue(vec_b), f.get_tensor(1));
        index.expect_complete_add(1, {7, 9});
    }
}

TEST(TensorAttributeTest, nearest_neighbor_index_is_NOT_updated_when_tensor_value_is_unchanged)
{
    DenseTensorAttributeMockIndex f;
    auto& index = f.mock_index();
    f.add_vec_a();
    {
        // Replaces previous value with the same value
        auto vec_b = vec_2d(3, 5);
        auto prepare_result = f.prepare_set_tensor(1, vec_b);
        EXPECT_TRUE(prepare_result.get() == nullptr);
        index.expect_empty_prepare_add();
        f.complete_set_tensor(1, vec_b, std::move(prepare_result));
        EXPECT_EQ(WrapValue(vec_b), f.get_tensor(1));
        index.expect_empty_complete_add();
        index.expect_empty_add();
    }
}

TEST(TensorAttributeTest, nearest_neighbor_index_is_updated_when_value_changes_from_A_to_B_to_A)
{
    DenseTensorAttributeMockIndex f;
    auto& index = f.mock_index();
    f.add_vec_a();
    {
        // Prepare replace of A with B
        auto vec_b = vec_2d(7, 9);
        auto prepare_result_b = f.prepare_set_tensor(1, vec_b);
        index.expect_prepare_add(1, {7, 9});
        index.clear();
        // Prepare replace of B with A, but prepare sees original A
        auto vec_a = vec_2d(3, 5);
        auto prepare_result_a = f.prepare_set_tensor(1, vec_a);
        EXPECT_TRUE(prepare_result_a.get() == nullptr);
        index.expect_empty_prepare_add();
        index.clear();
        // Complete set B
        f.complete_set_tensor(1, vec_b, std::move(prepare_result_b));
        index.expect_remove(1, {3, 5});
        EXPECT_EQ(WrapValue(vec_b), f.get_tensor(1));
        index.expect_complete_add(1, {7, 9});
        index.expect_empty_add();
        index.clear();
        // Complete set A, no prepare result but tensor cells changed
        f.complete_set_tensor(1, vec_a, std::move(prepare_result_a));
        index.expect_remove(1, {7, 9});
        index.expect_empty_complete_add();
        index.expect_add(1, {3, 5});
        EXPECT_EQ(WrapValue(vec_a), f.get_tensor(1));
    }
}

TEST(TensorAttributeTest, clearDoc_updates_nearest_neighbor_index)
{
    DenseTensorAttributeMockIndex f;
    auto& index = f.mock_index();

    // Nothing to clear.
    f.clearTensor(1);
    index.expect_empty_remove();
    index.expect_empty_add();

    // Clears previous value.
    f.set_tensor(1, vec_2d(3, 5));
    index.clear();
    f.clearTensor(1);
    index.expect_remove(1, {3, 5});
    index.expect_empty_add();
}

TEST(TensorAttributeTest, commit_ensures_transfer_and_trim_hold_lists_on_nearest_neighbor_index)
{
    DenseTensorAttributeMockIndex f;
    auto& index = f.mock_index();
    TensorSpec spec = vec_2d(3, 5);

    f.set_tensor(1, spec);
    generation_t gen_1 = f.get_current_gen();
    EXPECT_EQ(gen_1 - 1, index.get_transfer_gen());
    EXPECT_EQ(gen_1, index.get_trim_gen());

    generation_t gen_2 = 0;
    {
        // Takes guard on gen_1
        auto guard = f._attr->makeReadGuard(false);
        f.set_tensor(2, spec);
        gen_2 = f.get_current_gen();
        EXPECT_GT(gen_2, gen_1);
        EXPECT_EQ(gen_2 - 1, index.get_transfer_gen());
        EXPECT_EQ(gen_1, index.get_trim_gen());
    }

    f.set_tensor(3, spec);
    generation_t gen_3 = f.get_current_gen();
    EXPECT_GT(gen_3, gen_2);
    EXPECT_EQ(gen_3 - 1, index.get_transfer_gen());
    EXPECT_EQ(gen_3, index.get_trim_gen());
}

TEST(TensorAttributeTest, Memory_usage_is_extracted_from_index_when_updating_stats_on_attribute)
{
    DenseTensorAttributeMockIndex f;
    size_t before = f.mock_index().memory_usage_cnt();
    f.getStatus();
    size_t after = f.mock_index().memory_usage_cnt();
    EXPECT_EQ(before + 1, after);
}

TEST(TensorAttributeTest, Nearest_neighbor_index_can_be_saved_to_disk_and_then_loaded_from_file)
{
    DenseTensorAttributeMockIndex f;
    f.save_example_tensors_with_mock_index();

    EXPECT_TRUE(f.load()); // index is loaded from saved file
    auto& index = f.mock_index();
    EXPECT_EQ(123, index.get_index_value());
    index.expect_adds({});
}

TEST(TensorAttributeTest, onLoad_reconstructs_nearest_neighbor_index_if_save_file_does_not_exists)
{
    DenseTensorAttributeMockIndex f;
    f.set_example_tensors();
    EXPECT_TRUE(f.save());
    EXPECT_FALSE(std::filesystem::exists(std::filesystem::path(attr_name + ".nnidx")));

    EXPECT_TRUE(f.load()); // index is reconstructed by adding all loaded tensors
    auto& index = f.mock_index();
    EXPECT_EQ(0, index.get_index_value());
    index.expect_adds({{1, {3, 5}}, {2, {7, 9}}});
}

TEST(TensorAttributeTest, onLoad_ignores_saved_nearest_neighbor_index_if_not_enabled_in_config)
{
    DenseTensorAttributeMockIndex f;
    f.save_example_tensors_with_mock_index();
    f.disable_hnsw_index();
    EXPECT_TRUE(f.load());
    f.assert_example_tensors();
    EXPECT_EQ(f.as_dense_tensor().nearest_neighbor_index(), nullptr);
}

TEST(TensorAttributeTest, onLoad_uses_executor_if_major_index_parameters_are_changed)
{
    DenseTensorAttributeMockIndex f;
    f.save_example_tensors_with_mock_index();
    f.set_hnsw_index_params(HnswIndexParams(5, 20, DistanceMetric::Euclidean));
    EXPECT_EQ(0ul, f._executor.getStats().acceptedTasks);
    f.loadWithExecutor();
    EXPECT_EQ(2ul, f._executor.getStats().acceptedTasks);
    f.assert_example_tensors();
    auto& index = f.mock_index();
    EXPECT_EQ(0, index.get_index_value());
    index.expect_adds({});
    index.expect_prepare_adds({{1, {3, 5}}, {2, {7, 9}}});
    index.expect_complete_adds({{1, {3, 5}}, {2, {7, 9}}});
}

TEST(TensorAttributeTest, onLoad_ignores_saved_nearest_neighbor_index_if_major_index_parameters_are_changed)
{
    DenseTensorAttributeMockIndex f;
    f.save_example_tensors_with_mock_index();
    f.set_hnsw_index_params(HnswIndexParams(5, 20, DistanceMetric::Euclidean));
    EXPECT_EQ(0ul, f._executor.getStats().acceptedTasks);
    EXPECT_TRUE(f.load());
    EXPECT_EQ(0ul, f._executor.getStats().acceptedTasks);
    f.assert_example_tensors();
    auto& index = f.mock_index();
    EXPECT_EQ(0, index.get_index_value());
    index.expect_adds({{1, {3, 5}}, {2, {7, 9}}});
}

TEST(TensorAttributeTest, onLoad_uses_saved_nearest_neighbor_index_if_only_minor_index_parameters_are_changed)
{
    DenseTensorAttributeMockIndex f;
    f.save_example_tensors_with_mock_index();
    f.set_hnsw_index_params(HnswIndexParams(4, 21, DistanceMetric::Euclidean));
    EXPECT_TRUE(f.load());
    f.assert_example_tensors();
    auto& index = f.mock_index();
    EXPECT_EQ(123, index.get_index_value());
    index.expect_adds({});
}

TEST(TensorAttributeTest, Nearest_neighbor_index_type_is_added_to_attribute_file_header)
{
    DenseTensorAttributeMockIndex f;
    f.save_example_tensors_with_mock_index();
    auto header = f.get_file_header();
    EXPECT_TRUE(header.hasTag("nearest_neighbor_index"));
    EXPECT_EQ("hnsw", header.getTag("nearest_neighbor_index").asString());
}

class DenseTensorAttributeMipsIndex : public Fixture {
public:
    DenseTensorAttributeMipsIndex() : Fixture(vec_2d_spec, FixtureTraits().mips_hnsw()) {}
};

TEST(TensorAttributeTest, Nearest_neighbor_index_with_mips_distance_metrics_stores_square_of_max_distance)
{
    DenseTensorAttributeMipsIndex f;
    f.set_example_tensors();
    EXPECT_TRUE(f.save());
    auto header = f.get_file_header();
    EXPECT_TRUE(header.hasTag(hnsw_max_squared_norm));
    EXPECT_EQ(130.0, header.getTag(hnsw_max_squared_norm).asFloat());
    EXPECT_TRUE(f.load());
    auto& norm_store = dynamic_cast<MipsDistanceFunctionFactoryBase&>(f.hnsw_index().distance_function_factory()).get_max_squared_norm_store();
    EXPECT_EQ(130.0, norm_store.get_max());
}

template <typename ParentT>
class NearestNeighborBlueprintFixtureBase : public ParentT {
private:
    std::unique_ptr<Value> _query_tensor;

public:
    NearestNeighborBlueprintFixtureBase()
        : _query_tensor()
    {
        this->set_tensor(1, vec_2d(1, 1));
        this->set_tensor(2, vec_2d(2, 2));
        this->set_tensor(3, vec_2d(3, 3));
        this->set_tensor(4, vec_2d(4, 4));
        this->set_tensor(5, vec_2d(5, 5));
        this->set_tensor(6, vec_2d(6, 6));
        this->set_tensor(7, vec_2d(7, 7));
        this->set_tensor(8, vec_2d(8, 8));
        this->set_tensor(9, vec_2d(9, 9));
        this->set_tensor(10, vec_2d(0, 0));
    }

    ~NearestNeighborBlueprintFixtureBase();

    const Value& create_query_tensor(const TensorSpec& spec) {
        _query_tensor = SimpleValue::from_spec(spec);
        return *_query_tensor;
    }

    std::unique_ptr<NearestNeighborBlueprint> make_blueprint(bool approximate = true,
                                                             double global_filter_lower_limit = 0.05,
                                                             double target_hits_max_adjustment_factor = 20.0) {
        search::queryeval::FieldSpec field("foo", 0, 0);
        auto bp = std::make_unique<NearestNeighborBlueprint>(
            field,
            std::make_unique<DistanceCalculator>(this->as_dense_tensor(),
                                                 create_query_tensor(vec_2d(17, 42))),
            3, approximate, 5, 100100.25,
            global_filter_lower_limit, 1.0, target_hits_max_adjustment_factor, vespalib::Doom::never());
        EXPECT_EQ(11u, bp->getState().estimate().estHits);
        EXPECT_EQ(100100.25 * 100100.25, bp->get_distance_threshold());
        return bp;
    }
};

template <typename ParentT>
NearestNeighborBlueprintFixtureBase<ParentT>::~NearestNeighborBlueprintFixtureBase() = default;

class DenseTensorAttributeWithoutIndex : public Fixture {
public:
    DenseTensorAttributeWithoutIndex() : Fixture(vec_2d_spec, FixtureTraits().dense()) {}
};

using NNBA = NearestNeighborBlueprint::Algorithm;
using NearestNeighborBlueprintFixture = NearestNeighborBlueprintFixtureBase<DenseTensorAttributeMockIndex>;
using NearestNeighborBlueprintWithoutIndexFixture = NearestNeighborBlueprintFixtureBase<DenseTensorAttributeWithoutIndex>;

TEST(TensorAttributeTest, NN_blueprint_can_use_brute_force)
{
    NearestNeighborBlueprintFixture f;
    auto bp = f.make_blueprint(false);
    EXPECT_EQ(NNBA::EXACT, bp->get_algorithm());
}

TEST(TensorAttributeTest, NN_blueprint_handles_empty_filter_for_post_filtering)
{
    NearestNeighborBlueprintFixture f;
    auto bp = f.make_blueprint();
    auto empty_filter = GlobalFilter::create();
    bp->set_global_filter(*empty_filter, 0.6);
    // targetHits is adjusted based on the estimated hit ratio of the query.
    EXPECT_EQ(3u, bp->get_target_hits());
    EXPECT_EQ(5u, bp->get_adjusted_target_hits());
    EXPECT_EQ(5u, bp->getState().estimate().estHits);
    EXPECT_EQ(NNBA::INDEX_TOP_K, bp->get_algorithm());
}

TEST(TensorAttributeTest, NN_blueprint_adjustment_of_targetHits_is_bound_for_post_filtering)
{
    NearestNeighborBlueprintFixture f;
    auto bp = f.make_blueprint(true, 0.05, 3.5);
    auto empty_filter = GlobalFilter::create();
    bp->set_global_filter(*empty_filter, 0.2);
    // targetHits is adjusted based on the estimated hit ratio of the query,
    // but bound by target-hits-max-adjustment-factor
    EXPECT_EQ(3u, bp->get_target_hits());
    EXPECT_EQ(10u, bp->get_adjusted_target_hits());
    EXPECT_EQ(10u, bp->getState().estimate().estHits);
    EXPECT_EQ(NNBA::INDEX_TOP_K, bp->get_algorithm());
}

TEST(TensorAttributeTest, NN_blueprint_handles_strong_filter_for_pre_filtering)
{
    NearestNeighborBlueprintFixture f;
    auto bp = f.make_blueprint();
    auto filter = search::BitVector::create(1,11);
    filter->setBit(3);
    filter->invalidateCachedCount();
    auto strong_filter = GlobalFilter::create(std::move(filter));
    bp->set_global_filter(*strong_filter, 0.25);
    EXPECT_EQ(3u, bp->get_target_hits());
    EXPECT_EQ(3u, bp->get_adjusted_target_hits());
    EXPECT_EQ(1u, bp->getState().estimate().estHits);
    EXPECT_EQ(NNBA::INDEX_TOP_K_WITH_FILTER, bp->get_algorithm());
}

TEST(TensorAttributeTest, NN_blueprint_handles_weak_filter_for_pre_filtering)
{
    NearestNeighborBlueprintFixture f;
    auto bp = f.make_blueprint();
    auto filter = search::BitVector::create(1,11);
    filter->setBit(1);
    filter->setBit(3);
    filter->setBit(5);
    filter->setBit(7);
    filter->setBit(9);
    filter->invalidateCachedCount();
    auto weak_filter = GlobalFilter::create(std::move(filter));
    bp->set_global_filter(*weak_filter, 0.6);
    EXPECT_EQ(3u, bp->get_target_hits());
    EXPECT_EQ(3u, bp->get_adjusted_target_hits());
    EXPECT_EQ(3u, bp->getState().estimate().estHits);
    EXPECT_EQ(NNBA::INDEX_TOP_K_WITH_FILTER, bp->get_algorithm());
}

TEST(TensorAttributeTest, NN_blueprint_handles_strong_filter_triggering_exact_search)
{
    NearestNeighborBlueprintFixture f;
    auto bp = f.make_blueprint(true, 0.2);
    auto filter = search::BitVector::create(1,11);
    filter->setBit(3);
    filter->invalidateCachedCount();
    auto strong_filter = GlobalFilter::create(std::move(filter));
    bp->set_global_filter(*strong_filter, 0.6);
    EXPECT_EQ(3u, bp->get_target_hits());
    EXPECT_EQ(3u, bp->get_adjusted_target_hits());
    EXPECT_EQ(11u, bp->getState().estimate().estHits);
    EXPECT_EQ(NNBA::EXACT_FALLBACK, bp->get_algorithm());
}

TEST(TensorAttributeTest, NN_blueprint_wants_global_filter_when_having_index)
{
    NearestNeighborBlueprintFixture f;
    auto bp = f.make_blueprint();
    EXPECT_TRUE(bp->getState().want_global_filter());
}

TEST(TensorAttributeTest, NN_blueprint_do_NOT_want_global_filter_when_explicitly_using_brute_force)
{
    NearestNeighborBlueprintFixture f;
    auto bp = f.make_blueprint(false);
    EXPECT_FALSE(bp->getState().want_global_filter());
}

TEST(TensorAttributeTest, NN_blueprint_do_NOT_want_global_filter_when_NOT_having_index_for_implicit_brute_force)
{
    NearestNeighborBlueprintWithoutIndexFixture f;
    auto bp = f.make_blueprint();
    EXPECT_FALSE(bp->getState().want_global_filter());
}

auto test_values = ::testing::Values(1u, 2u);

INSTANTIATE_TEST_SUITE_P(MixedTensors, MixedTensorAttributeTest, test_values, testing::PrintToStringParamName());

GTEST_MAIN_RUN_ALL_TESTS()
