// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/exceptions.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/fastos/file.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/generic_tensor_attribute.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/log/log.h>
LOG_SETUP("tensorattribute_test");

using document::WrongTensorTypeException;
using search::AttributeGuard;
using search::AttributeVector;
using search::attribute::HnswIndexParams;
using search::tensor::DenseTensorAttribute;
using search::tensor::GenericTensorAttribute;
using search::tensor::HnswIndex;
using search::tensor::TensorAttribute;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::tensor::DefaultTensorEngine;
using vespalib::tensor::DenseTensor;
using vespalib::tensor::Tensor;

namespace vespalib::tensor {

static bool operator==(const Tensor &lhs, const Tensor &rhs)
{
    return lhs.equals(rhs);
}

}

vespalib::string sparseSpec("tensor(x{},y{})");
vespalib::string denseSpec("tensor(x[2],y[3])");

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

struct Fixture
{
    using BasicType = search::attribute::BasicType;
    using CollectionType = search::attribute::CollectionType;
    using Config = search::attribute::Config;

    Config _cfg;
    vespalib::string _name;
    vespalib::string _typeSpec;
    std::shared_ptr<TensorAttribute> _tensorAttr;
    std::shared_ptr<AttributeVector> _attr;
    bool _denseTensors;
    bool _useDenseTensorAttribute;

    Fixture(const vespalib::string &typeSpec,
            bool useDenseTensorAttribute = false,
            bool enable_hnsw_index = false)
        : _cfg(BasicType::TENSOR, CollectionType::SINGLE),
          _name("test"),
          _typeSpec(typeSpec),
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
            _cfg.set_hnsw_index_params(HnswIndexParams(4, 20));
        }
        _tensorAttr = makeAttr();
        _attr = _tensorAttr;
        _attr->addReservedDoc();
    }

    std::shared_ptr<TensorAttribute> makeAttr() {
        if (_useDenseTensorAttribute) {
            assert(_denseTensors);
            return std::make_shared<DenseTensorAttribute>(_name, _cfg);
        } else {
            return std::make_shared<GenericTensorAttribute>(_name, _cfg);
        }
    }

    const DenseTensorAttribute& as_dense_tensor() const {
        auto result = dynamic_cast<const DenseTensorAttribute*>(_tensorAttr.get());
        assert(result != nullptr);
        return *result;
    }

    void ensureSpace(uint32_t docId) {
        while (_attr->getNumDocs() <= docId) {
            uint32_t newDocId = 0u;
            _attr->addDoc(newDocId);
            _attr->commit();
        }
    }

    void clearTensor(uint32_t docId) {
        ensureSpace(docId);
        _tensorAttr->clearDoc(docId);
        _attr->commit();
    }

    void setTensor(uint32_t docId, const Tensor &tensor) {
        ensureSpace(docId);
        _tensorAttr->setTensor(docId, tensor);
        _attr->commit();
    }

    search::attribute::Status getStatus() {
        _attr->commit(true);
        return _attr->getStatus();
    }

    void
    assertGetNoTensor(uint32_t docId) {
        AttributeGuard guard(_attr);
        Tensor::UP actTensor = _tensorAttr->getTensor(docId);
        EXPECT_FALSE(actTensor);
    }

    void
    assertGetTensor(const Tensor &expTensor, uint32_t docId)
    {
        AttributeGuard guard(_attr);
        Tensor::UP actTensor = _tensorAttr->getTensor(docId);
        EXPECT_TRUE(static_cast<bool>(actTensor));
        EXPECT_EQUAL(expTensor, *actTensor);
    }

    void
    assertGetTensor(const TensorSpec &expSpec, uint32_t docId)
    {
        Tensor::UP expTensor = createTensor(expSpec);
        assertGetTensor(*expTensor, docId);
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

    Tensor::UP expDenseTensor3() const
    {
        return createTensor(TensorSpec(denseSpec)
                            .add({{"x", 0}, {"y", 1}}, 11)
                            .add({{"x", 1}, {"y", 2}}, 0));
    }

    Tensor::UP expDenseFillTensor() const
    {
        return createTensor(TensorSpec(denseSpec)
                            .add({{"x", 0}, {"y", 0}}, 5)
                            .add({{"x", 1}, {"y", 2}}, 0));
    }

    Tensor::UP expEmptyDenseTensor() const
    {
        return createTensor(TensorSpec(denseSpec));
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
    EXPECT_EQUAL(5u, _attr->getCommittedDocIdLimit());
    TEST_DO(assertGetNoTensor(4));
    EXPECT_EXCEPTION(setTensor(4, *createTensor(TensorSpec("double"))),
                     WrongTensorTypeException,
                     "but other tensor type is 'double'");
    TEST_DO(assertGetNoTensor(4));
    setTensor(4, *_tensorAttr->getEmptyTensor());
    if (_denseTensors) {
        TEST_DO(assertGetTensor(*expEmptyDenseTensor(), 4));
        setTensor(3, *expDenseTensor3());
        TEST_DO(assertGetTensor(*expDenseTensor3(), 3));
    } else {
        TEST_DO(assertGetTensor(TensorSpec(sparseSpec), 4));
        setTensor(3, *createTensor(TensorSpec(sparseSpec)
                                   .add({{"x", ""}, {"y", ""}}, 11)));
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
    setTensor(4, *_tensorAttr->getEmptyTensor());
    if (_denseTensors) {
        setTensor(3, *expDenseTensor3());
    } else {
        setTensor(3, *createTensor(TensorSpec(sparseSpec)
                                   .add({{"x", ""}, {"y", "1"}}, 11)));
    }
    TEST_DO(save());
    TEST_DO(load());
    EXPECT_EQUAL(5u, _attr->getNumDocs());
    EXPECT_EQUAL(5u, _attr->getCommittedDocIdLimit());
    if (_denseTensors) {
        TEST_DO(assertGetTensor(*expDenseTensor3(), 3));
        TEST_DO(assertGetTensor(*expEmptyDenseTensor(), 4));
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
    Tensor::UP emptytensor = _tensorAttr->getEmptyTensor();
    Tensor::UP emptyxytensor = createTensor(TensorSpec(sparseSpec));
    Tensor::UP simpletensor = createTensor(TensorSpec(sparseSpec)
                                           .add({{"x", ""}, {"y", "1"}}, 11));
    Tensor::UP filltensor = createTensor(TensorSpec(sparseSpec)
                                         .add({{"x", ""}, {"y", ""}}, 5));
    if (_denseTensors) {
        emptyxytensor = expEmptyDenseTensor();
        simpletensor = expDenseTensor3();
        filltensor = expDenseFillTensor();
    }
    setTensor(4, *emptytensor);
    setTensor(3, *simpletensor);
    setTensor(2, *filltensor);
    clearTensor(2);
    setTensor(2, *filltensor);
    search::attribute::Status oldStatus = getStatus();
    search::attribute::Status newStatus = oldStatus;
    uint64_t iter = 0;
    uint64_t iterLimit = 100000;
    for (; iter < iterLimit; ++iter) {
        clearTensor(2);
        setTensor(2, *filltensor);
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
    TEST_DO(assertGetTensor(*filltensor, 2));
    TEST_DO(assertGetTensor(*simpletensor, 3));
    TEST_DO(assertGetTensor(*emptyxytensor, 4));
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
       Fixture("tensor(x[2])", true, false))
{
    const auto& tensor = f.as_dense_tensor();
    EXPECT_TRUE(tensor.nearest_neighbor_index() == nullptr);
}

TEST_F("Hnsw index is instantiated in dense tensor attribute when specified in config",
       Fixture("tensor(x[2])", true, true))
{
    const auto& tensor = f.as_dense_tensor();
    ASSERT_TRUE(tensor.nearest_neighbor_index() != nullptr);
    auto hnsw_index = dynamic_cast<const HnswIndex*>(tensor.nearest_neighbor_index());
    ASSERT_TRUE(hnsw_index != nullptr);

    const auto& cfg = hnsw_index->config();
    EXPECT_EQUAL(8u, cfg.max_links_at_level_0());
    EXPECT_EQUAL(4u, cfg.max_links_at_hierarchic_levels());
    EXPECT_EQUAL(20u, cfg.neighbors_to_explore_at_construction());
    EXPECT_TRUE(cfg.heuristic_select_neighbors());
}

TEST_MAIN() { TEST_RUN_ALL(); vespalib::unlink("test.dat"); }
