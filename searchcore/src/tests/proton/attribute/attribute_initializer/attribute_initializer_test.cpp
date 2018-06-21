// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/attribute/attribute_directory.h>
#include <vespa/searchcore/proton/attribute/attribute_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_initializer.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/stllike/string.h>

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

void
saveAttr(const vespalib::string &name, const Config &cfg, SerialNum serialNum, SerialNum createSerialNum)
{
    auto diskLayout = AttributeDiskLayout::create(test_dir);
    auto dir = diskLayout->createAttributeDir(name);
    auto writer = dir->getWriter();
    writer->createInvalidSnapshot(serialNum);
    auto snapshotdir = writer->getSnapshotDir(serialNum);
    vespalib::mkdir(snapshotdir);
    auto av = search::AttributeFactory::createAttribute(snapshotdir + "/" + name, cfg);
    av->setCreateSerialNum(createSerialNum);
    av->addReservedDoc();
    uint32_t docId;
    av->addDoc(docId);
    assert(docId == 1u);
    av->clearDoc(docId);
    av->save();
    writer->markValidSnapshot(serialNum);
}

}

struct Fixture
{
    DirectoryHandler _dirHandler;
    std::shared_ptr<AttributeDiskLayout> _diskLayout;
    AttributeFactory       _factory;
    Fixture();
    ~Fixture();
    std::unique_ptr<AttributeInitializer> createInitializer(const AttributeSpec &spec, SerialNum serialNum);
};

Fixture::Fixture()
    : _dirHandler(test_dir),
      _diskLayout(AttributeDiskLayout::create(test_dir)),
      _factory()
{
}

Fixture::~Fixture() {}

std::unique_ptr<AttributeInitializer>
Fixture::createInitializer(const AttributeSpec &spec, SerialNum serialNum)
{
    return std::make_unique<AttributeInitializer>(_diskLayout->createAttributeDir(spec.getName()), "test.subdb", spec, serialNum, _factory);
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

}

TEST_MAIN()
{
    vespalib::rmdir(test_dir, true);
    TEST_RUN_ALL();
}
