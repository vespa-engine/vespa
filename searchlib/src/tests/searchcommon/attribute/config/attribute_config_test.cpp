// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using vespalib::eval::ValueType;
using search::GrowStrategy;
using search::DictionaryConfig;


struct Fixture
{
    Config _config;
    Fixture()
        : _config()
    { }

    Fixture(BasicType bt,
            CollectionType ct = CollectionType::SINGLE,
            bool fastSearch_ = false)
        : _config(bt, ct, fastSearch_)
    { }
};

TEST(AttributeConfigTest, test_default_attribute_config)
{
    Fixture f;
    EXPECT_EQ(BasicType::Type::NONE, f._config.basicType().type());
    EXPECT_EQ(CollectionType::Type::SINGLE,
                 f._config.collectionType().type());
    EXPECT_TRUE(!f._config.fastSearch());
    EXPECT_TRUE(!f._config.getIsFilter());
    EXPECT_TRUE(!f._config.fastAccess());
    EXPECT_TRUE(f._config.tensorType().is_error());
}

TEST(AttributeConfigTest, test_integer_weightedset_attribute_config)
{
    Fixture f(BasicType::Type::INT32, CollectionType::Type::WSET);
    EXPECT_EQ(BasicType::Type::INT32, f._config.basicType().type());
    EXPECT_EQ(CollectionType::Type::WSET, f._config.collectionType().type());
    EXPECT_TRUE(!f._config.fastSearch());
    EXPECT_TRUE(!f._config.getIsFilter());
    EXPECT_TRUE(!f._config.fastAccess());
    EXPECT_TRUE(f._config.tensorType().is_error());
}


TEST(AttributeConfigTest, test_operator_equals_on_attribute_config)
{
    Config cfg1(BasicType::Type::INT32, CollectionType::Type::WSET);
    Config cfg2(BasicType::Type::INT32, CollectionType::Type::ARRAY);
    Config cfg3(BasicType::Type::INT32, CollectionType::Type::WSET);

    EXPECT_TRUE(cfg1 != cfg2);
    EXPECT_TRUE(cfg2 != cfg3);
    EXPECT_TRUE(cfg1 == cfg3);
}


TEST(AttributeConfigTest, test_operator_equals_on_attribute_config_for_tensor_type)
{
    Config cfg1(BasicType::Type::TENSOR);
    Config cfg2(BasicType::Type::TENSOR);
    Config cfg3(BasicType::Type::TENSOR);

    ValueType dense_x = ValueType::from_spec("tensor(x[10])");
    ValueType sparse_x = ValueType::from_spec("tensor(x{})");

    EXPECT_TRUE(cfg1 == cfg2);
    EXPECT_TRUE(cfg2 == cfg3);
    EXPECT_TRUE(cfg1 == cfg3);

    cfg1.setTensorType(dense_x);
    cfg3.setTensorType(dense_x);
    EXPECT_EQ(dense_x, cfg1.tensorType());
    EXPECT_EQ(dense_x, cfg3.tensorType());
    EXPECT_TRUE(!cfg1.tensorType().is_error());
    EXPECT_TRUE(cfg2.tensorType().is_error());
    EXPECT_TRUE(!cfg3.tensorType().is_error());

    EXPECT_TRUE(cfg1 != cfg2);
    EXPECT_TRUE(cfg2 != cfg3);
    EXPECT_TRUE(cfg1 == cfg3);

    cfg3.setTensorType(sparse_x);
    EXPECT_EQ(sparse_x, cfg3.tensorType());
    EXPECT_TRUE(!cfg3.tensorType().is_error());
    EXPECT_TRUE(cfg1 != cfg3);
}

TEST(AttributeConfigTest, Test_GrowStrategy_consistency) {
    GrowStrategy g(1024, 0.5, 17, 3, 0.4f);
    EXPECT_EQ(1024u, g.getInitialCapacity());
    EXPECT_EQ(0.5, g.getGrowFactor());
    EXPECT_EQ(17u, g.getGrowDelta());
    EXPECT_EQ(3u, g.getMinimumCapacity());
    EXPECT_EQ(0.4f, g.getMultiValueAllocGrowFactor());
}

TEST(AttributeConfigTest, DictionaryConfig) {
    using Type = DictionaryConfig::Type;
    using Match = DictionaryConfig::Match;
    EXPECT_EQ(Type::BTREE, DictionaryConfig().getType());
    EXPECT_EQ(Match::UNCASED, DictionaryConfig().getMatch());

    EXPECT_EQ(Type::BTREE, DictionaryConfig(Type::BTREE).getType());
    EXPECT_EQ(Match::UNCASED, DictionaryConfig(Type::BTREE).getMatch());
    EXPECT_EQ(Match::UNCASED, DictionaryConfig(Type::BTREE, Match::UNCASED).getMatch());
    EXPECT_EQ(Match::CASED, DictionaryConfig(Type::BTREE, Match::CASED).getMatch());

    EXPECT_EQ(Type::HASH, DictionaryConfig(Type::HASH).getType());
    EXPECT_EQ(Type::BTREE_AND_HASH, DictionaryConfig(Type::BTREE_AND_HASH).getType());

    EXPECT_EQ(DictionaryConfig(Type::BTREE), DictionaryConfig(Type::BTREE));
    EXPECT_EQ(DictionaryConfig(Type::HASH), DictionaryConfig(Type::HASH));
    EXPECT_EQ(DictionaryConfig(Type::BTREE_AND_HASH), DictionaryConfig(Type::BTREE_AND_HASH));
    EXPECT_NE(DictionaryConfig(Type::HASH), DictionaryConfig(Type::BTREE));
    EXPECT_NE(DictionaryConfig(Type::BTREE), DictionaryConfig(Type::HASH));
    EXPECT_TRUE(Config().set_dictionary_config(DictionaryConfig(Type::HASH)) ==
                Config().set_dictionary_config(DictionaryConfig(Type::HASH)));
    EXPECT_FALSE(Config().set_dictionary_config(DictionaryConfig(Type::HASH)) ==
                 Config().set_dictionary_config(DictionaryConfig(Type::BTREE)));
    EXPECT_FALSE(Config().set_dictionary_config(DictionaryConfig(Type::HASH)) !=
                 Config().set_dictionary_config(DictionaryConfig(Type::HASH)));
    EXPECT_TRUE(Config().set_dictionary_config(DictionaryConfig(Type::HASH)) !=
                Config().set_dictionary_config(DictionaryConfig(Type::BTREE)));
}


GTEST_MAIN_RUN_ALL_TESTS()
