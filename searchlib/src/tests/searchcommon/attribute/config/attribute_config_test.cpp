// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchcommon/attribute/config.h>

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

TEST_F("test default attribute config", Fixture)
{
    EXPECT_EQUAL(BasicType::Type::NONE, f._config.basicType().type());
    EXPECT_EQUAL(CollectionType::Type::SINGLE,
                 f._config.collectionType().type());
    EXPECT_TRUE(!f._config.fastSearch());
    EXPECT_TRUE(!f._config.getEnableOnlyBitVector());
    EXPECT_TRUE(!f._config.getIsFilter());
    EXPECT_TRUE(!f._config.fastAccess());
    EXPECT_TRUE(f._config.tensorType().is_error());
}

TEST_F("test integer weightedset attribute config",
       Fixture(BasicType::Type::INT32, CollectionType::Type::WSET))
{
    EXPECT_EQUAL(BasicType::Type::INT32, f._config.basicType().type());
    EXPECT_EQUAL(CollectionType::Type::WSET, f._config.collectionType().type());
    EXPECT_TRUE(!f._config.fastSearch());
    EXPECT_TRUE(!f._config.getEnableOnlyBitVector());
    EXPECT_TRUE(!f._config.getIsFilter());
    EXPECT_TRUE(!f._config.fastAccess());
    EXPECT_TRUE(f._config.tensorType().is_error());
}


TEST("test operator== on attribute config")
{
    Config cfg1(BasicType::Type::INT32, CollectionType::Type::WSET);
    Config cfg2(BasicType::Type::INT32, CollectionType::Type::ARRAY);
    Config cfg3(BasicType::Type::INT32, CollectionType::Type::WSET);

    EXPECT_TRUE(cfg1 != cfg2);
    EXPECT_TRUE(cfg2 != cfg3);
    EXPECT_TRUE(cfg1 == cfg3);
}


TEST("test operator== on attribute config for tensor type")
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
    EXPECT_EQUAL(dense_x, cfg1.tensorType());
    EXPECT_EQUAL(dense_x, cfg3.tensorType());
    EXPECT_TRUE(!cfg1.tensorType().is_error());
    EXPECT_TRUE(cfg2.tensorType().is_error());
    EXPECT_TRUE(!cfg3.tensorType().is_error());

    EXPECT_TRUE(cfg1 != cfg2);
    EXPECT_TRUE(cfg2 != cfg3);
    EXPECT_TRUE(cfg1 == cfg3);

    cfg3.setTensorType(sparse_x);
    EXPECT_EQUAL(sparse_x, cfg3.tensorType());
    EXPECT_TRUE(!cfg3.tensorType().is_error());
    EXPECT_TRUE(cfg1 != cfg3);
}

TEST("Test GrowStrategy consistency") {
    GrowStrategy g(1024, 0.5, 17, 3, 0.4f);
    EXPECT_EQUAL(1024u, g.getInitialCapacity());
    EXPECT_EQUAL(0.5, g.getGrowFactor());
    EXPECT_EQUAL(17u, g.getGrowDelta());
    EXPECT_EQUAL(3u, g.getMinimumCapacity());
    EXPECT_EQUAL(0.4f, g.getMultiValueAllocGrowFactor());
}

TEST("DictionaryConfig") {
    using Type = DictionaryConfig::Type;
    using Match = DictionaryConfig::Match;
    EXPECT_EQUAL(Type::BTREE, DictionaryConfig().getType());
    EXPECT_EQUAL(Match::UNCASED, DictionaryConfig().getMatch());

    EXPECT_EQUAL(Type::BTREE, DictionaryConfig(Type::BTREE).getType());
    EXPECT_EQUAL(Match::UNCASED, DictionaryConfig(Type::BTREE).getMatch());
    EXPECT_EQUAL(Match::UNCASED, DictionaryConfig(Type::BTREE, Match::UNCASED).getMatch());
    EXPECT_EQUAL(Match::CASED, DictionaryConfig(Type::BTREE, Match::CASED).getMatch());

    EXPECT_EQUAL(Type::HASH, DictionaryConfig(Type::HASH).getType());
    EXPECT_EQUAL(Type::BTREE_AND_HASH, DictionaryConfig(Type::BTREE_AND_HASH).getType());

    EXPECT_EQUAL(DictionaryConfig(Type::BTREE), DictionaryConfig(Type::BTREE));
    EXPECT_EQUAL(DictionaryConfig(Type::HASH), DictionaryConfig(Type::HASH));
    EXPECT_EQUAL(DictionaryConfig(Type::BTREE_AND_HASH), DictionaryConfig(Type::BTREE_AND_HASH));
    EXPECT_NOT_EQUAL(DictionaryConfig(Type::HASH), DictionaryConfig(Type::BTREE));
    EXPECT_NOT_EQUAL(DictionaryConfig(Type::BTREE), DictionaryConfig(Type::HASH));
    EXPECT_TRUE(Config().set_dictionary_config(DictionaryConfig(Type::HASH)) ==
                Config().set_dictionary_config(DictionaryConfig(Type::HASH)));
    EXPECT_FALSE(Config().set_dictionary_config(DictionaryConfig(Type::HASH)) ==
                 Config().set_dictionary_config(DictionaryConfig(Type::BTREE)));
    EXPECT_FALSE(Config().set_dictionary_config(DictionaryConfig(Type::HASH)) !=
                 Config().set_dictionary_config(DictionaryConfig(Type::HASH)));
    EXPECT_TRUE(Config().set_dictionary_config(DictionaryConfig(Type::HASH)) !=
                Config().set_dictionary_config(DictionaryConfig(Type::BTREE)));
}


TEST_MAIN() { TEST_RUN_ALL(); }
