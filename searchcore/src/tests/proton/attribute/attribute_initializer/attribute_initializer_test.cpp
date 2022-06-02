// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_directory.h>
#include <vespa/searchcore/proton/attribute/attribute_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_initializer.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_initializer_test");

using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::SerialNum;
using search::test::DirectoryHandler;

const vespalib::string test_dir = "test_output";

namespace proton {

namespace {

const Config int32_sv(BasicType::Type::INT32);
const Config int16_sv(BasicType::Type::INT16);
const Config int32_array(BasicType::Type::INT32, CollectionType::Type::ARRAY);
const Config int32_wset(BasicType::Type::INT32, CollectionType::Type::WSET);
const Config string_wset(BasicType::Type::STRING, CollectionType::Type::WSET);
const Config predicate(BasicType::Type::PREDICATE);
const CollectionType wset2(CollectionType::Type::WSET, false, true);
const Config string_wset2(BasicType::Type::STRING, wset2);

Config getPredicateWithArity(uint32_t arity)
{
    Config ret(predicate);
    search::attribute::PredicateParams predicateParams;
    predicateParams.setArity(arity);
    ret.setPredicateParams(predicateParams);
    return ret;
}

Config getTensor(const vespalib::string &spec)
{
    Config ret(BasicType::Type::TENSOR);
    ret.setTensorType(vespalib::eval::ValueType::from_spec(spec));
    return ret;
}

Config get_int32_wset_fs()
{
    Config ret(int32_wset);
    ret.setFastSearch(true);
    return ret;
}

void
saveAttr(const vespalib::string &name, const Config &cfg, SerialNum serialNum, SerialNum createSerialNum)
{
    auto diskLayout = AttributeDiskLayout::create(test_dir);
    auto dir = diskLayout->createAttributeDir(name);
    auto writer = dir->getWriter();
    writer->createInvalidSnapshot(serialNum);
    auto snapshotdir = writer->getSnapshotDir(serialNum);
    std::filesystem::create_directory(std::filesystem::path(snapshotdir));
    auto av = search::AttributeFactory::createAttribute(snapshotdir + "/" + name, cfg);
    av->setCreateSerialNum(createSerialNum);
    av->addReservedDoc();
    uint32_t docId;
    av->addDoc(docId);
    assert(docId == 1u);
    av->clearDoc(docId);
    if (cfg.basicType().type() == BasicType::Type::INT32 &&
        cfg.collectionType().type() == CollectionType::Type::WSET) {
        auto &iav = dynamic_cast<search::IntegerAttribute &>(*av);
        iav.append(docId, 10, 1);
        iav.append(docId, 11, 1);
    }
    av->save();
    writer->markValidSnapshot(serialNum);
}

}

struct Fixture
{
    DirectoryHandler _dirHandler;
    std::shared_ptr<AttributeDiskLayout> _diskLayout;
    AttributeFactory       _factory;
    vespalib::ThreadStackExecutor _executor;
    Fixture();
    ~Fixture();
    std::unique_ptr<AttributeInitializer> createInitializer(AttributeSpec && spec, SerialNum serialNum);
};

Fixture::Fixture()
    : _dirHandler(test_dir),
      _diskLayout(AttributeDiskLayout::create(test_dir)),
      _factory(),
      _executor(1, 0x10000)
{
}

Fixture::~Fixture() = default;

std::unique_ptr<AttributeInitializer>
Fixture::createInitializer(AttributeSpec &&spec, SerialNum serialNum)
{
    return std::make_unique<AttributeInitializer>(_diskLayout->createAttributeDir(spec.getName()), "test.subdb", std::move(spec), serialNum, _factory, _executor);
}

TEST("require that integer attribute can be initialized")
{
    saveAttr("a", int32_sv, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int32_sv}, 5)->init().getAttribute();
    EXPECT_EQUAL(2u, av->getCreateSerialNum());
    EXPECT_EQUAL(2u, av->getNumDocs());
}

TEST("require that mismatching base type is not loaded")
{
    saveAttr("a", int32_sv, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int16_sv}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av->getCreateSerialNum());
    EXPECT_EQUAL(1u, av->getNumDocs());
}

TEST("require that mismatching collection type is not loaded")
{
    saveAttr("a", int32_sv, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int32_array}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av->getCreateSerialNum());
    EXPECT_EQUAL(1u, av->getNumDocs());
}

TEST("require that mismatching weighted set collection type params is not loaded")
{
    saveAttr("a", string_wset, 10, 2);
    saveAttr("b", string_wset2, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", string_wset2}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av->getCreateSerialNum());
    EXPECT_EQUAL(1u, av->getNumDocs());
    auto av2 = f.createInitializer({"b", string_wset}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av2->getCreateSerialNum());
    EXPECT_EQUAL(1u, av2->getNumDocs());
}

TEST("require that predicate attributes can be initialized")
{
    saveAttr("a", predicate, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", predicate}, 5)->init().getAttribute();
    EXPECT_EQUAL(2u, av->getCreateSerialNum());
    EXPECT_EQUAL(2u, av->getNumDocs());
}

TEST("require that predicate attributes will not be initialized with future-created attribute")
{
    saveAttr("a", predicate, 10, 8);
    Fixture f;
    auto av = f.createInitializer({"a", predicate}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av->getCreateSerialNum());
    EXPECT_EQUAL(1u, av->getNumDocs());
}

TEST("require that predicate attributes will not be initialized with mismatching type")
{
    saveAttr("a", predicate, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", getPredicateWithArity(4)}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av->getCreateSerialNum());
    EXPECT_EQUAL(1u, av->getNumDocs());
}

TEST("require that tensor attribute can be initialized")
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", getTensor("tensor(x[10])")}, 5)->init().getAttribute();
    EXPECT_EQUAL(2u, av->getCreateSerialNum());
    EXPECT_EQUAL(2u, av->getNumDocs());
}

TEST("require that tensor attributes will not be initialized with future-created attribute")
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 8);
    Fixture f;
    auto av = f.createInitializer({"a", getTensor("tensor(x[10])")}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av->getCreateSerialNum());
    EXPECT_EQUAL(1u, av->getNumDocs());
}

TEST("require that tensor attributes will not be initialized with mismatching type")
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", getTensor("tensor(x[11])")}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av->getCreateSerialNum());
    EXPECT_EQUAL(1u, av->getNumDocs());
}

TEST("require that too old attribute is not loaded")
{
    saveAttr("a", int32_sv, 3, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int32_sv}, 5)->init().getAttribute();
    EXPECT_EQUAL(5u, av->getCreateSerialNum());
    EXPECT_EQUAL(1u, av->getNumDocs());
}

TEST("require that transient memory usage is reported for first time posting list attribute load after enabling posting lists")
{
    saveAttr("a", int32_wset, 10, 2);
    Fixture f;
    auto avi = f.createInitializer({"a", get_int32_wset_fs()}, 5);
    EXPECT_EQUAL(40u, avi->get_transient_memory_usage());
}

TEST("require that transient memory usage is reported for normal posting list attribute load")
{
    saveAttr("a", get_int32_wset_fs(), 10, 2);
    Fixture f;
    auto avi = f.createInitializer({"a", get_int32_wset_fs()}, 5);
    EXPECT_EQUAL(24u, avi->get_transient_memory_usage());
}

TEST("require that transient memory usage is reported for attribute load without posting list")
{
    saveAttr("a", int32_wset, 10, 2);
    Fixture f;
    auto avi = f.createInitializer({"a", int32_wset}, 5);
    EXPECT_EQUAL(0u, avi->get_transient_memory_usage());
}

}

TEST_MAIN()
{
    std::filesystem::remove_all(std::filesystem::path(test_dir));
    TEST_RUN_ALL();
}
