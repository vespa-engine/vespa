// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("tensorattribute_test");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/tensor/generic_tensor_attribute.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/vespalib/tensor/tensor_factory.h>
#include <vespa/vespalib/tensor/default_tensor.h>
#include <vespa/vespalib/io/fileutil.h>

using search::attribute::TensorAttribute;
using search::attribute::DenseTensorAttribute;
using search::attribute::GenericTensorAttribute;
using search::AttributeGuard;
using search::AttributeVector;
using vespalib::eval::ValueType;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorCells;
using vespalib::tensor::DenseTensorCells;
using vespalib::tensor::TensorDimensions;
using vespalib::tensor::TensorFactory;

namespace vespalib {
namespace tensor {

static bool operator==(const Tensor &lhs, const Tensor &rhs)
{
    return lhs.equals(rhs);
}

}
}

vespalib::string sparseSpec("tensor(x{},y{})");
vespalib::string denseSpec("tensor(x[2],y[3])");

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
    vespalib::tensor::DefaultTensor::builder _builder;
    bool _denseTensors;
    bool _useDenseTensorAttribute;

    Fixture(const vespalib::string &typeSpec,
            bool useDenseTensorAttribute = false)
        : _cfg(BasicType::TENSOR, CollectionType::SINGLE),
          _name("test"),
          _typeSpec(typeSpec),
          _tensorAttr(),
          _attr(),
          _builder(),
          _denseTensors(false),
          _useDenseTensorAttribute(useDenseTensorAttribute)
    {
        _cfg.setTensorType(ValueType::from_spec(typeSpec));
        if (_cfg.tensorType().is_dense()) {
            _denseTensors = true;
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

    Tensor::UP createTensor(const TensorCells &cells) {
        return TensorFactory::create(cells, _builder);
    }
    Tensor::UP createTensor(const TensorCells &cells,
                            const TensorDimensions &dimensions) {
        return TensorFactory::create(cells, dimensions, _builder);
    }
    Tensor::UP createDenseTensor(const DenseTensorCells &cells) {
        return TensorFactory::createDense(cells);
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
    assertGetTensor(const TensorCells &expCells,
                    const TensorDimensions &expDimensions,
                    uint32_t docId)
    {
        Tensor::UP expTensor = createTensor(expCells, expDimensions);
        assertGetTensor(*expTensor, docId);
    }

    void
    assertGetDenseTensor(const DenseTensorCells &expCells,
                         uint32_t docId)
    {
        Tensor::UP expTensor = createDenseTensor(expCells);
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
    setTensor(4, *createTensor({}, {}));
    if (_denseTensors) {
        TEST_DO(assertGetDenseTensor({{{{"x",1},{"y",2}}, 0} }, 4));
        setTensor(3, *createTensor({ {{}, 11} }, { "x", "y"}));
        TEST_DO(assertGetDenseTensor({    {{{"x",0},{"y",0}}, 11},
                                          {{{"x",1},{"y",2}}, 0} }, 3));
    } else {
        TEST_DO(assertGetTensor({}, {"x", "y"}, 4));
        setTensor(3, *createTensor({ {{}, 11} }, { "x", "y"}));
        TEST_DO(assertGetTensor({ {{}, 11} }, { "x", "y"}, 3));
    }
    TEST_DO(assertGetNoTensor(2));
    TEST_DO(clearTensor(3));
    TEST_DO(assertGetNoTensor(3));
}

void
Fixture::testSaveLoad()
{
    ensureSpace(4);
    setTensor(4, *createTensor({}, {}));
    setTensor(3, *createTensor({ {{}, 11} }, { "x", "y"}));
    TEST_DO(save());
    TEST_DO(load());
    EXPECT_EQUAL(5u, _attr->getNumDocs());
    EXPECT_EQUAL(5u, _attr->getCommittedDocIdLimit());
    if (_denseTensors) {
        TEST_DO(assertGetDenseTensor({    {{{"x",0},{"y",0}}, 11},
                                          {{{"x",1},{"y",2}}, 0} }, 3));
        TEST_DO(assertGetDenseTensor({{{{"x",1},{"y",2}}, 0} }, 4));
    } else {
        TEST_DO(assertGetTensor({ {{}, 11} }, { "x", "y"}, 3));
        TEST_DO(assertGetTensor({}, {"x", "y"}, 4));
    }
    TEST_DO(assertGetNoTensor(2));
}


void
Fixture::testCompaction()
{
    ensureSpace(4);
    Tensor::UP emptytensor = createTensor({}, {});
    Tensor::UP emptyxytensor = createTensor({}, {"x", "y"});
    Tensor::UP simpletensor = createTensor({ {{}, 11} }, { "x", "y"});
    Tensor::UP filltensor = createTensor({ {{}, 5} }, { "x", "y"});
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
    if (_denseTensors) {
        emptyxytensor = createDenseTensor({{{{"x",1},{"y",2}}, 0} });
        simpletensor = createDenseTensor({    {{{"x",0},{"y",0}}, 11},
                                              {{{"x",1},{"y",2}}, 0} });
        filltensor = createDenseTensor({    {{{"x",0},{"y",0}}, 5},
                                            {{{"x",1},{"y",2}}, 0} });
    }
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
    EXPECT_EQUAL(emptyTensor->getType(), tensorAttr.getConfig().tensorType());
    EXPECT_EQUAL(emptyTensor->getType(), ValueType::from_spec(_typeSpec));
}


TEST_F("Test empty sparse tensor attribute", Fixture("tensor()"))
{
    f.testEmptyAttribute();
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

TEST_MAIN() { TEST_RUN_ALL(); vespalib::unlink("test.dat"); }
