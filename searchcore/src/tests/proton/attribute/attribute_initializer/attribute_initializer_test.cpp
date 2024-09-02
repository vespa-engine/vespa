// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_directory.h>
#include <vespa/searchcore/proton/attribute/attribute_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_initializer.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_initializer_test");

using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::IMultiValueAttribute;
using search::SerialNum;
using search::test::DirectoryHandler;
using vespalib::Stash;

const std::string test_dir = "test_output";

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

Config getTensor(const std::string &spec)
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
saveAttr(const std::string &name, const Config &cfg, SerialNum serialNum, SerialNum createSerialNum, bool mutate_reserved_doc = false)
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
    if (mutate_reserved_doc) {
        av->clearDoc(0u);
        if (cfg.basicType().type() == BasicType::Type::STRING &&
            cfg.collectionType().type() == CollectionType::Type::WSET) {
            auto &sav = dynamic_cast<search::StringAttribute &>(*av);
            sav.append(0u, "badly", 15);
            sav.append(0u, "broken", 20);
        }
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
    std::unique_ptr<AttributeInitializer> createInitializer(AttributeSpec && spec, std::optional<SerialNum> serialNum);
};

Fixture::Fixture()
    : _dirHandler(test_dir),
      _diskLayout(AttributeDiskLayout::create(test_dir)),
      _factory(),
      _executor(1)
{
}

Fixture::~Fixture() = default;

std::unique_ptr<AttributeInitializer>
Fixture::createInitializer(AttributeSpec &&spec, std::optional<SerialNum> serialNum)
{
    return std::make_unique<AttributeInitializer>(_diskLayout->createAttributeDir(spec.getName()), "test.subdb", std::move(spec), serialNum, _factory, _executor);
}

class AttributeInitializerTest : public ::testing::Test
{
protected:
    AttributeInitializerTest();
    ~AttributeInitializerTest() override;
    static void SetUpTestSuite();
};

AttributeInitializerTest::AttributeInitializerTest()
    : ::testing::Test()
{
}

AttributeInitializerTest::~AttributeInitializerTest() = default;

void
AttributeInitializerTest::SetUpTestSuite()
{
    std::filesystem::remove_all(std::filesystem::path(test_dir));
}

TEST_F(AttributeInitializerTest, require_that_integer_attribute_can_be_initialized)
{
    saveAttr("a", int32_sv, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int32_sv}, 5)->init().getAttribute();
    EXPECT_EQ(2u, av->getCreateSerialNum());
    EXPECT_EQ(2u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_mismatching_base_type_is_not_loaded)
{
    saveAttr("a", int32_sv, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int16_sv}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_mismatching_collection_type_is_not_loaded)
{
    saveAttr("a", int32_sv, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int32_array}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_mismatching_weighted_set_collection_type_params_is_not_loaded)
{
    saveAttr("a", string_wset, 10, 2);
    saveAttr("b", string_wset2, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", string_wset2}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
    auto av2 = f.createInitializer({"b", string_wset}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av2->getCreateSerialNum());
    EXPECT_EQ(1u, av2->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_predicate_attributes_can_be_initialized)
{
    saveAttr("a", predicate, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", predicate}, 5)->init().getAttribute();
    EXPECT_EQ(2u, av->getCreateSerialNum());
    EXPECT_EQ(2u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_predicate_attributes_will_not_be_initialized_with_future_created_attribute)
{
    saveAttr("a", predicate, 10, 8);
    Fixture f;
    auto av = f.createInitializer({"a", predicate}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_predicate_attributes_will_not_be_initialized_with_mismatching_type)
{
    saveAttr("a", predicate, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", getPredicateWithArity(4)}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_tensor_attribute_can_be_initialized)
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", getTensor("tensor(x[10])")}, 5)->init().getAttribute();
    EXPECT_EQ(2u, av->getCreateSerialNum());
    EXPECT_EQ(2u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_tensor_attributes_will_not_be_initialized_with_future_created_attribute)
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 8);
    Fixture f;
    auto av = f.createInitializer({"a", getTensor("tensor(x[10])")}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_tensor_attributes_will_not_be_initialized_with_mismatching_type)
{
    saveAttr("a", getTensor("tensor(x[10])"), 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", getTensor("tensor(x[11])")}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_too_old_attribute_is_not_loaded)
{
    saveAttr("a", int32_sv, 3, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int32_sv}, 5)->init().getAttribute();
    EXPECT_EQ(5u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_transient_memory_usage_is_reported_for_first_time_posting_list_attribute_load_after_enabling_posting_lists)
{
    saveAttr("a", int32_wset, 10, 2);
    Fixture f;
    auto avi = f.createInitializer({"a", get_int32_wset_fs()}, 5);
    EXPECT_EQ(40u, avi->get_transient_memory_usage());
}

TEST_F(AttributeInitializerTest, require_that_transient_memory_usage_is_reported_for_normal_posting_list_attribute_load)
{
    saveAttr("a", get_int32_wset_fs(), 10, 2);
    Fixture f;
    auto avi = f.createInitializer({"a", get_int32_wset_fs()}, 5);
    EXPECT_EQ(24u, avi->get_transient_memory_usage());
}

TEST_F(AttributeInitializerTest, require_that_transient_memory_usage_is_reported_for_attribute_load_without_posting_list)
{
    saveAttr("a", int32_wset, 10, 2);
    Fixture f;
    auto avi = f.createInitializer({"a", int32_wset}, 5);
    EXPECT_EQ(0u, avi->get_transient_memory_usage());
}

TEST_F(AttributeInitializerTest, require_that_saved_attribute_is_ignored_when_serial_num_is_not_set)
{
    saveAttr("a", int32_sv, 10, 2);
    Fixture f;
    auto av = f.createInitializer({"a", int32_sv}, std::nullopt)->init().getAttribute();
    EXPECT_EQ(0u, av->getCreateSerialNum());
    EXPECT_EQ(1u, av->getNumDocs());
}

TEST_F(AttributeInitializerTest, require_that_reserved_document_is_reinitialized_during_load)
{
    saveAttr("a", string_wset, 10, 2, true);
    Fixture f;
    auto av = f.createInitializer({"a", string_wset}, 5)->init().getAttribute();
    EXPECT_EQ(2u, av->getCreateSerialNum());
    EXPECT_EQ(2u, av->getNumDocs());
    auto mvav = av->as_multi_value_attribute();
    ASSERT_TRUE(mvav != nullptr);
    Stash stash;
    auto read_view = mvav->make_read_view(IMultiValueAttribute::WeightedSetTag<const char*>(), stash);
    ASSERT_TRUE(read_view != nullptr);
    auto reserved_values = read_view->get_values(0u);
    EXPECT_EQ(0u, reserved_values.size());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
