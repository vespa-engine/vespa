// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("tensorattribute_test");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/attribute/tensorattribute.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/vespalib/tensor/tensor_factory.h>
#include <vespa/vespalib/tensor/default_tensor.h>
#include <vespa/vespalib/tensor/simple/simple_tensor_builder.h>

using search::attribute::TensorAttribute;
using search::AttributeGuard;
using search::AttributeVector;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorCells;
using vespalib::tensor::TensorDimensions;
using vespalib::tensor::TensorFactory;
using vespalib::tensor::TensorType;
using vespalib::tensor::SimpleTensorBuilder;

namespace vespalib {
namespace tensor {

static bool operator==(const Tensor &lhs, const Tensor &rhs)
{
    return lhs.equals(rhs);
}

}
}


struct Fixture
{
    using BasicType = search::attribute::BasicType;
    using CollectionType = search::attribute::CollectionType;
    using Config = search::attribute::Config;

    Config _cfg;
    vespalib::string _name;
    std::shared_ptr<TensorAttribute> _tensorAttr;
    std::shared_ptr<AttributeVector> _attr;
    vespalib::tensor::DefaultTensor::builder _builder;

    Fixture(const vespalib::string &typeSpec)
        : _cfg(BasicType::TENSOR, CollectionType::SINGLE),
          _name("test"),
          _tensorAttr(),
          _attr()
    {
        _cfg.setTensorType(TensorType::fromSpec(typeSpec));
        _tensorAttr = std::make_shared<TensorAttribute>(_name, _cfg);
        _attr = _tensorAttr;
        _attr->addReservedDoc();
    }

    Tensor::UP createTensor(const TensorCells &cells) {
        return TensorFactory::create(cells, _builder);
    }
    Tensor::UP createTensor(const TensorCells &cells,
                            const TensorDimensions &dimensions) {
        return TensorFactory::create(cells, dimensions, _builder);
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

    void save() {
        bool saveok = _attr->save();
        EXPECT_TRUE(saveok);
    }

    void load() {
        _tensorAttr = std::make_shared<TensorAttribute>(_name, _cfg);
        _attr = _tensorAttr;
        bool loadok = _attr->load();
        EXPECT_TRUE(loadok);
    }
};


TEST_F("Test empty tensor attribute", Fixture("tensor()"))
{
    EXPECT_EQUAL(1u, f._attr->getNumDocs());
    EXPECT_EQUAL(1u, f._attr->getCommittedDocIdLimit());
}


TEST_F("Test setting tensor value", Fixture("tensor(x{}, y{})"))
{
    f.ensureSpace(4);
    EXPECT_EQUAL(5u, f._attr->getNumDocs());
    EXPECT_EQUAL(5u, f._attr->getCommittedDocIdLimit());
    TEST_DO(f.assertGetNoTensor(4));
    f.setTensor(4, *f.createTensor({}, {}));
    TEST_DO(f.assertGetTensor({}, {"x", "y"}, 4));
    f.setTensor(3, *f.createTensor({ {{}, 3} }, { "x", "y"}));
    TEST_DO(f.assertGetTensor({ {{}, 3} }, { "x", "y"}, 3));
    TEST_DO(f.assertGetNoTensor(2));
    TEST_DO(f.clearTensor(3));
    TEST_DO(f.assertGetNoTensor(3));
}


TEST_F("Test saving / loading tensor attribute", Fixture("tensor(x{}, y{})"))
{
    f.ensureSpace(4);
    f.setTensor(4, *f.createTensor({}, {}));
    f.setTensor(3, *f.createTensor({ {{}, 3} }, { "x", "y"}));
    TEST_DO(f.save());
    TEST_DO(f.load());
    EXPECT_EQUAL(5u, f._attr->getNumDocs());
    EXPECT_EQUAL(5u, f._attr->getCommittedDocIdLimit());
    TEST_DO(f.assertGetTensor({ {{}, 3} }, { "x", "y"}, 3));
    TEST_DO(f.assertGetTensor({}, {"x", "y"}, 4));
    TEST_DO(f.assertGetNoTensor(2));
}


TEST_F("Test compaction of tensor attribute", Fixture("tensor(x{}, y{})"))
{
    f.ensureSpace(4);
    Tensor::UP emptytensor = f.createTensor({}, {});
    Tensor::UP emptyxytensor = f.createTensor({}, {"x", "y"});
    Tensor::UP simpletensor = f.createTensor({ {{}, 3} }, { "x", "y"});
    Tensor::UP filltensor = f.createTensor({ {{}, 5} }, { "x", "y"});
    f.setTensor(4, *emptytensor);
    f.setTensor(3, *simpletensor);
    f.setTensor(2, *filltensor);
    f.clearTensor(2);
    f.setTensor(2, *filltensor);
    search::attribute::Status oldStatus = f.getStatus();
    search::attribute::Status newStatus = oldStatus;
    uint64_t iter = 0;
    uint64_t iterLimit = 100000;
    for (; iter < iterLimit; ++iter) {
        f.clearTensor(2);
        f.setTensor(2, *filltensor);
        newStatus = f.getStatus();
        if (newStatus.getUsed() < oldStatus.getUsed()) {
            break;
        }
        oldStatus = newStatus;
    }
    EXPECT_GREATER(iterLimit, iter);
    LOG(info,
        "iter = %" PRIu64 ", memory usage %" PRIu64 ", -> %" PRIu64,
        iter, oldStatus.getUsed(), newStatus.getUsed());
    TEST_DO(f.assertGetNoTensor(1));
    TEST_DO(f.assertGetTensor(*filltensor, 2));
    TEST_DO(f.assertGetTensor(*simpletensor, 3));
    TEST_DO(f.assertGetTensor(*emptyxytensor, 4));
}

TEST_F("Test tensortype file header tag", Fixture("tensor(x[10])"))
{
    f.ensureSpace(4);
    TEST_DO(f.save());

    vespalib::FileHeader header;
    FastOS_File file;
    EXPECT_TRUE(file.OpenReadOnly("test.dat"));
    (void) header.readFile(file);
    file.Close();
    EXPECT_TRUE(header.hasTag("tensortype"));
    EXPECT_EQUAL("tensor(x[10])", header.getTag("tensortype").asString());
}

TEST_F("Require that tensor attribute can provide empty tensor of correct type", Fixture("tensor(x[10])"))
{
    const TensorAttribute &tensorAttr = *f._tensorAttr;
    Tensor::UP emptyTensor = tensorAttr.getEmptyTensor();
    EXPECT_EQUAL(emptyTensor->getType(), tensorAttr.getConfig().tensorType());
    EXPECT_EQUAL(emptyTensor->getType(), TensorType::fromSpec("tensor(x[10])"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
