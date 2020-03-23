// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/exceptions.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/fastos/file.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/tensor/default_nearest_neighbor_index_factory.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/doc_vector_access.h>
#include <vespa/searchlib/tensor/generic_tensor_attribute.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index_factory.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/log/log.h>
LOG_SETUP("tensorattribute_test");

using document::WrongTensorTypeException;
using search::AttributeGuard;
using search::AttributeVector;
using search::attribute::DistanceMetric;
using search::attribute::HnswIndexParams;
using search::tensor::DefaultNearestNeighborIndexFactory;
using search::tensor::DenseTensorAttribute;
using search::tensor::DocVectorAccess;
using search::tensor::GenericTensorAttribute;
using search::tensor::HnswIndex;
using search::tensor::NearestNeighborIndex;
using search::tensor::NearestNeighborIndexFactory;
using search::tensor::TensorAttribute;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::tensor::DefaultTensorEngine;
using vespalib::tensor::DenseTensor;
using vespalib::tensor::Tensor;

using DoubleVector = std::vector<double>;
using generation_t = vespalib::GenerationHandler::generation_t;

namespace vespalib::tensor {

static bool operator==(const Tensor &lhs, const Tensor &rhs)
{
    return lhs.equals(rhs);
}

}

vespalib::string sparseSpec("tensor(x{},y{})");
vespalib::string denseSpec("tensor(x[2],y[3])");
vespalib::string vec_2d_spec("tensor(x[2])");

Tensor::UP createTensor(const TensorSpec &spec) {
    auto value = DefaultTensorEngine::ref().from_spec(spec);
    if (value->is_double()) {
        return Tensor::UP(new DenseTensor<double>(ValueType::double_type(), {value->as_double()}));
    }
    Tensor *tensor = dynamic_cast<Tensor*>(value.get());
    ASSERT_TRUE(tensor != nullptr);
    value.release();
    return Tensor::UP(tensor);
}

TensorSpec
vec_2d(double x0, double x1)
{
    return TensorSpec(vec_2d_spec).add({{"x", 0}}, x0).add({{"x", 1}}, x1);
}

class MockNearestNeighborIndex : public NearestNeighborIndex {
private:
    using Entry = std::pair<uint32_t, DoubleVector>;
    using EntryVector = std::vector<Entry>;

    const DocVectorAccess& _vectors;
    EntryVector _adds;
    EntryVector _removes;
    generation_t _transfer_gen;
    generation_t _trim_gen;
    mutable size_t _memory_usage_cnt;

public:
    MockNearestNeighborIndex(const DocVectorAccess& vectors)
        : _vectors(vectors),
          _adds(),
          _removes(),
          _transfer_gen(std::numeric_limits<generation_t>::max()),
          _trim_gen(std::numeric_limits<generation_t>::max()),
          _memory_usage_cnt(0)
    {
    }
    void clear() {
        _adds.clear();
        _removes.clear();
    }
    void expect_empty_add() const {
        EXPECT_TRUE(_adds.empty());
    }
    void expect_add(uint32_t exp_docid, const DoubleVector& exp_vector) const {
        EXPECT_EQUAL(1u, _adds.size());
        EXPECT_EQUAL(exp_docid, _adds.back().first);
        EXPECT_EQUAL(exp_vector, _adds.back().second);
    }
    void expect_adds(const EntryVector &exp_adds) const {
        EXPECT_EQUAL(exp_adds, _adds);
    }
    void expect_empty_remove() const {
        EXPECT_TRUE(_removes.empty());
    }
    void expect_remove(uint32_t exp_docid, const DoubleVector& exp_vector) const {
        EXPECT_EQUAL(1u, _removes.size());
        EXPECT_EQUAL(exp_docid, _removes.back().first);
        EXPECT_EQUAL(exp_vector, _removes.back().second);
    }
    generation_t get_transfer_gen() const { return _transfer_gen; }
    generation_t get_trim_gen() const { return _trim_gen; }
    size_t memory_usage_cnt() const { return _memory_usage_cnt; }

    void add_document(uint32_t docid) override {
        auto vector = _vectors.get_vector(docid).typify<double>();
        _adds.emplace_back(docid, DoubleVector(vector.begin(), vector.end()));
    }
    void remove_document(uint32_t docid) override {
        auto vector = _vectors.get_vector(docid).typify<double>();
        _removes.emplace_back(docid, DoubleVector(vector.begin(), vector.end()));
    }
    void transfer_hold_lists(generation_t current_gen) override {
        _transfer_gen = current_gen;
    }
    void trim_hold_lists(generation_t first_used_gen) override {
        _trim_gen = first_used_gen;
    }
    vespalib::MemoryUsage memory_usage() const override {
        ++_memory_usage_cnt;
        return vespalib::MemoryUsage();
    }
    std::vector<Neighbor> find_top_k(uint32_t k, vespalib::tensor::TypedCells vector, uint32_t explore_k) const override {
        (void) k;
        (void) vector;
        (void) explore_k;
        return std::vector<Neighbor>();
    }
    
    search::tensor::DistanceFunction *distance_function() const override { return nullptr; }
};

class MockNearestNeighborIndexFactory : public NearestNeighborIndexFactory {

    std::unique_ptr<NearestNeighborIndex> make(const DocVectorAccess& vectors,
                                               size_t vector_size,
                                               ValueType::CellType cell_type,
                                               const search::attribute::HnswIndexParams& params) const override {
        (void) vector_size;
        (void) params;
        assert(cell_type == ValueType::CellType::DOUBLE);
        return std::make_unique<MockNearestNeighborIndex>(vectors);
    }
};

struct Fixture
{
    using BasicType = search::attribute::BasicType;
    using CollectionType = search::attribute::CollectionType;
    using Config = search::attribute::Config;

    Config _cfg;
    vespalib::string _name;
    vespalib::string _typeSpec;
    std::unique_ptr<NearestNeighborIndexFactory> _index_factory;
    std::shared_ptr<TensorAttribute> _tensorAttr;
    std::shared_ptr<AttributeVector> _attr;
    bool _denseTensors;
    bool _useDenseTensorAttribute;

    Fixture(const vespalib::string &typeSpec,
            bool useDenseTensorAttribute = false,
            bool enable_hnsw_index = false,
            bool use_mock_index = false)
        : _cfg(BasicType::TENSOR, CollectionType::SINGLE),
          _name("test"),
          _typeSpec(typeSpec),
          _index_factory(std::make_unique<DefaultNearestNeighborIndexFactory>()),
          _tensorAttr(),
          _attr(),
          _denseTensors(false),
          _useDenseTensorAttribute(useDenseTensorAttribute)
    {
        _cfg.setTensorType(ValueType::from_spec(typeSpec));
        if (_cfg.tensorType().is_dense()) {
            _denseTensors = true;
        }
        if (enable_hnsw_index) {
            _cfg.set_hnsw_index_params(HnswIndexParams(4, 20, DistanceMetric::Euclidean));
            if (use_mock_index) {
                _index_factory = std::make_unique<MockNearestNeighborIndexFactory>();
            }
        }
        _tensorAttr = makeAttr();
        _attr = _tensorAttr;
        _attr->addReservedDoc();
    }
    ~Fixture() {}

    std::shared_ptr<TensorAttribute> makeAttr() {
        if (_useDenseTensorAttribute) {
            assert(_denseTensors);
            return std::make_shared<DenseTensorAttribute>(_name, _cfg, *_index_factory);
        } else {
            return std::make_shared<GenericTensorAttribute>(_name, _cfg);
        }
    }

    const DenseTensorAttribute& as_dense_tensor() const {
        auto result = dynamic_cast<const DenseTensorAttribute*>(_tensorAttr.get());
        assert(result != nullptr);
        return *result;
    }

    MockNearestNeighborIndex& mock_index() {
        assert(as_dense_tensor().nearest_neighbor_index() != nullptr);
        auto mock_index = dynamic_cast<const MockNearestNeighborIndex*>(as_dense_tensor().nearest_neighbor_index());
        assert(mock_index != nullptr);
        return *const_cast<MockNearestNeighborIndex*>(mock_index);
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

    void set_empty_tensor(uint32_t docid) {
        set_tensor_internal(docid, *_tensorAttr->getEmptyTensor());
    }

    void set_tensor_internal(uint32_t docId, const Tensor &tensor) {
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

    void assertGetNoTensor(uint32_t docId) {
        AttributeGuard guard(_attr);
        Tensor::UP actTensor = _tensorAttr->getTensor(docId);
        EXPECT_FALSE(actTensor);
    }

    void assertGetTensor(const TensorSpec &expSpec, uint32_t docId) {
        Tensor::UP expTensor = createTensor(expSpec);
        AttributeGuard guard(_attr);
        Tensor::UP actTensor = _tensorAttr->getTensor(docId);
        EXPECT_TRUE(static_cast<bool>(actTensor));
        EXPECT_EQUAL(*expTensor, *actTensor);
    }

    void save() {
        bool saveok = _attr->save();
        EXPECT_TRUE(saveok);
    }

    void load() {
        _tensorAttr = makeAttr();
        _attr = _tensorAttr;
        bool loadok = _attr->load();
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
        return TensorSpec(denseSpec);
    }

    vespalib::string expEmptyDenseTensorSpec() const {
        return denseSpec;
    }

    void testEmptyAttribute();
    void testSetTensorValue();
    void testSaveLoad();
    void testCompaction();
    void testTensorTypeFileHeaderTag();
    void testEmptyTensor();
};


void
Fixture::testEmptyAttribute()
{
    EXPECT_EQUAL(1u, _attr->getNumDocs());
    EXPECT_EQUAL(1u, _attr->getCommittedDocIdLimit());
}

void
Fixture::testSetTensorValue()
{
    ensureSpace(4);
    EXPECT_EQUAL(5u, _attr->getNumDocs());
    TEST_DO(assertGetNoTensor(4));
    EXPECT_EXCEPTION(set_tensor(4, TensorSpec("double")),
                     WrongTensorTypeException,
                     "but other tensor type is 'double'");
    TEST_DO(assertGetNoTensor(4));
    set_empty_tensor(4);
    if (_denseTensors) {
        TEST_DO(assertGetTensor(expEmptyDenseTensor(), 4));
        set_tensor(3, expDenseTensor3());
        TEST_DO(assertGetTensor(expDenseTensor3(), 3));
    } else {
        TEST_DO(assertGetTensor(TensorSpec(sparseSpec), 4));
        set_tensor(3, TensorSpec(sparseSpec)
                .add({{"x", ""}, {"y", ""}}, 11));
        TEST_DO(assertGetTensor(TensorSpec(sparseSpec)
                                        .add({{"x", ""}, {"y", ""}}, 11), 3));
    }
    TEST_DO(assertGetNoTensor(2));
    TEST_DO(clearTensor(3));
    TEST_DO(assertGetNoTensor(3));
}

void
Fixture::testSaveLoad()
{
    ensureSpace(4);
    set_empty_tensor(4);
    if (_denseTensors) {
        set_tensor(3, expDenseTensor3());
    } else {
        set_tensor(3, TensorSpec(sparseSpec)
                .add({{"x", ""}, {"y", "1"}}, 11));
    }
    TEST_DO(save());
    TEST_DO(load());
    EXPECT_EQUAL(5u, _attr->getNumDocs());
    EXPECT_EQUAL(5u, _attr->getCommittedDocIdLimit());
    if (_denseTensors) {
        TEST_DO(assertGetTensor(expDenseTensor3(), 3));
        TEST_DO(assertGetTensor(expEmptyDenseTensor(), 4));
    } else {
        TEST_DO(assertGetTensor(TensorSpec(sparseSpec)
                                        .add({{"x", ""}, {"y", "1"}}, 11), 3));
        TEST_DO(assertGetTensor(TensorSpec(sparseSpec), 4));
    }
    TEST_DO(assertGetNoTensor(2));
}


void
Fixture::testCompaction()
{
    if (_useDenseTensorAttribute && _denseTensors) {
        LOG(info, "Skipping compaction test for tensor '%s' which is using free-lists", _cfg.tensorType().to_spec().c_str());
        return;
    }
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
    uint64_t iter = 0;
    uint64_t iterLimit = 100000;
    for (; iter < iterLimit; ++iter) {
        clearTensor(2);
        set_tensor(2, fill_tensor);
        newStatus = getStatus();
        if (newStatus.getUsed() < oldStatus.getUsed()) {
            break;
        }
        oldStatus = newStatus;
    }
    EXPECT_GREATER(iterLimit, iter);
    LOG(info,
        "iter = %" PRIu64 ", memory usage %" PRIu64 ", -> %" PRIu64,
        iter, oldStatus.getUsed(), newStatus.getUsed());
    TEST_DO(assertGetNoTensor(1));
    TEST_DO(assertGetTensor(fill_tensor, 2));
    TEST_DO(assertGetTensor(simple_tensor, 3));
    TEST_DO(assertGetTensor(empty_xy_tensor, 4));
}

void
Fixture::testTensorTypeFileHeaderTag()
{
    ensureSpace(4);
    TEST_DO(save());

    vespalib::FileHeader header;
    FastOS_File file;
    EXPECT_TRUE(file.OpenReadOnly("test.dat"));
    (void) header.readFile(file);
    file.Close();
    EXPECT_TRUE(header.hasTag("tensortype"));
    EXPECT_EQUAL(_typeSpec, header.getTag("tensortype").asString());
    if (_useDenseTensorAttribute) {
        EXPECT_EQUAL(1u, header.getTag("version").asInteger());
    } else {
        EXPECT_EQUAL(0u, header.getTag("version").asInteger());
    }
}


void
Fixture::testEmptyTensor()
{
    const TensorAttribute &tensorAttr = *_tensorAttr;
    Tensor::UP emptyTensor = tensorAttr.getEmptyTensor();
    if (_denseTensors) {
        vespalib::string expSpec = expEmptyDenseTensorSpec();
        EXPECT_EQUAL(emptyTensor->type(), ValueType::from_spec(expSpec));
    } else {
        EXPECT_EQUAL(emptyTensor->type(), tensorAttr.getConfig().tensorType());
        EXPECT_EQUAL(emptyTensor->type(), ValueType::from_spec(_typeSpec));
    }
}


template <class MakeFixture>
void testAll(MakeFixture &&f)
{
    TEST_DO(f()->testEmptyAttribute());
    TEST_DO(f()->testSetTensorValue());
    TEST_DO(f()->testSaveLoad());
    TEST_DO(f()->testCompaction());
    TEST_DO(f()->testTensorTypeFileHeaderTag());
    TEST_DO(f()->testEmptyTensor());
}

TEST("Test sparse tensors with generic tensor attribute")
{
    testAll([]() { return std::make_shared<Fixture>(sparseSpec); });
}

TEST("Test dense tensors with generic tensor attribute")
{
    testAll([]() { return std::make_shared<Fixture>(denseSpec); });
}

TEST("Test dense tensors with dense tensor attribute")
{
    testAll([]() { return std::make_shared<Fixture>(denseSpec, true); });
}

TEST_F("Hnsw index is NOT instantiated in dense tensor attribute by default",
       Fixture(vec_2d_spec, true, false))
{
    const auto& tensor = f.as_dense_tensor();
    EXPECT_TRUE(tensor.nearest_neighbor_index() == nullptr);
}

TEST_F("Hnsw index is instantiated in dense tensor attribute when specified in config",
       Fixture(vec_2d_spec, true, true))
{
    const auto& tensor = f.as_dense_tensor();
    ASSERT_TRUE(tensor.nearest_neighbor_index() != nullptr);
    auto hnsw_index = dynamic_cast<const HnswIndex*>(tensor.nearest_neighbor_index());
    ASSERT_TRUE(hnsw_index != nullptr);

    const auto& cfg = hnsw_index->config();
    EXPECT_EQUAL(8u, cfg.max_links_at_level_0());
    EXPECT_EQUAL(4u, cfg.max_links_on_inserts());
    EXPECT_EQUAL(20u, cfg.neighbors_to_explore_at_construction());
    EXPECT_TRUE(cfg.heuristic_select_neighbors());
}

class DenseTensorAttributeMockIndex : public Fixture {
public:
    DenseTensorAttributeMockIndex() : Fixture(vec_2d_spec, true, true, true) {}
};

TEST_F("setTensor() updates nearest neighbor index", DenseTensorAttributeMockIndex)
{
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

TEST_F("clearDoc() updates nearest neighbor index", DenseTensorAttributeMockIndex)
{
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

TEST_F("onLoad() updates nearest neighbor index", DenseTensorAttributeMockIndex)
{
    f.set_tensor(1, vec_2d(3, 5));
    f.set_tensor(2, vec_2d(7, 9));
    f.save();
    f.load();
    auto& index = f.mock_index();
    index.expect_adds({{1, {3, 5}}, {2, {7, 9}}});
}


TEST_F("commit() ensures transfer and trim hold lists on nearest neighbor index", DenseTensorAttributeMockIndex)
{
    auto& index = f.mock_index();
    TensorSpec spec = vec_2d(3, 5);

    f.set_tensor(1, spec);
    generation_t gen_1 = f.get_current_gen();
    EXPECT_EQUAL(gen_1 - 1, index.get_transfer_gen());
    EXPECT_EQUAL(gen_1, index.get_trim_gen());

    generation_t gen_2 = 0;
    {
        // Takes guard on gen_1
        auto guard = f._attr->makeReadGuard(false);
        f.set_tensor(2, spec);
        gen_2 = f.get_current_gen();
        EXPECT_GREATER(gen_2, gen_1);
        EXPECT_EQUAL(gen_2 - 1, index.get_transfer_gen());
        EXPECT_EQUAL(gen_1, index.get_trim_gen());
    }

    f.set_tensor(3, spec);
    generation_t gen_3 = f.get_current_gen();
    EXPECT_GREATER(gen_3, gen_2);
    EXPECT_EQUAL(gen_3 - 1, index.get_transfer_gen());
    EXPECT_EQUAL(gen_3, index.get_trim_gen());
}

TEST_F("Memory usage is extracted from index when updating stats on attribute", DenseTensorAttributeMockIndex)
{
    size_t before = f.mock_index().memory_usage_cnt();
    f.getStatus();
    size_t after = f.mock_index().memory_usage_cnt();
    EXPECT_EQUAL(before + 1, after);
}

TEST_MAIN() { TEST_RUN_ALL(); vespalib::unlink("test.dat"); }
