// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("attribute_initializer_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcore/proton/test/directory_handler.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchcore/proton/attribute/attribute_directory.h>
#include <vespa/searchcore/proton/attribute/attribute_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_initializer.h>

using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::SerialNum;

const vespalib::string test_dir = "test_output";

namespace proton
{

namespace {

const Config predicate(BasicType::Type::PREDICATE);

Config getPredicate2(uint32_t arity)
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
    test::DirectoryHandler _dirHandler;
    std::shared_ptr<AttributeDiskLayout> _diskLayout;
    AttributeFactory       _factory;
    Fixture();
    ~Fixture();
    std::unique_ptr<AttributeInitializer> createInitializer(const vespalib::string &name, const Config &cfg, SerialNum serialNum);
};

Fixture::Fixture()
    : _dirHandler(test_dir),
      _diskLayout(AttributeDiskLayout::create(test_dir)),
      _factory()
{
}

Fixture::~Fixture() {}

std::unique_ptr<AttributeInitializer>
Fixture::createInitializer(const vespalib::string &name, const Config &cfg, SerialNum serialNum)
{
    return std::make_unique<AttributeInitializer>(_diskLayout->createAttributeDir(name), "test.subdb", cfg, serialNum, _factory);
}


TEST("require that predicate attributes can be initialized")
{
    saveAttr("a", predicate, 10, 2);
    Fixture f;
    auto av = f.createInitializer("a", predicate, 5)->init();
    EXPECT_EQUAL(2, av->getCreateSerialNum());
    EXPECT_EQUAL(2, av->getNumDocs());
}

TEST("require that predicate attributes will not be initialized with future-created attribute")
{
    saveAttr("a", predicate, 10, 8);
    Fixture f;
    auto av = f.createInitializer("a", predicate, 5)->init();
    EXPECT_EQUAL(5, av->getCreateSerialNum());
    EXPECT_EQUAL(1, av->getNumDocs());
}

TEST("require that predicate attributes will not be initialized with mismatching type")
{
    saveAttr("a", predicate, 10, 2);
    Fixture f;
    auto av = f.createInitializer("a", getPredicate2(4), 5)->init();
    EXPECT_EQUAL(5, av->getCreateSerialNum());
    EXPECT_EQUAL(1, av->getNumDocs());
}

TEST("require that tensor attribute can be initialized")
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 2);
    Fixture f;
    auto av = f.createInitializer("a", getTensor("tensor(x[10])"), 5)->init();
    EXPECT_EQUAL(2, av->getCreateSerialNum());
    EXPECT_EQUAL(2, av->getNumDocs());
}

TEST("require that tensor attributes will not be initialized with future-created attribute")
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 8);
    Fixture f;
    auto av = f.createInitializer("a", getTensor("tensor(x[10])"), 5)->init();
    EXPECT_EQUAL(5, av->getCreateSerialNum());
    EXPECT_EQUAL(1, av->getNumDocs());
}

TEST("require that tensor attributes will not be initialized with mismatching type")
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 2);
    Fixture f;
    auto av = f.createInitializer("a", getTensor("tensor(x[11])"), 5)->init();
    EXPECT_EQUAL(5, av->getCreateSerialNum());
    EXPECT_EQUAL(1, av->getNumDocs());
}

}

TEST_MAIN()
{
    vespalib::rmdir(test_dir, true);
    TEST_RUN_ALL();
}
