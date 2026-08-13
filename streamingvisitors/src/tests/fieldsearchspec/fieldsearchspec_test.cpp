// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/searchvisitor/indexenvironment.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vsm/vsm/fieldsearchspec.h>

using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::TableManager;
using vespa::config::search::vsm::VsmfieldsConfig;
using vespa::config::search::vsm::VsmfieldsConfigBuilder;
using vsm::FieldIdT;
using vsm::FieldSearchSpecMap;
using vsm::StringFieldIdTMap;

namespace {

FieldInfo::DataType to_data_type(VsmfieldsConfig::Fieldspec::Searchmethod search_method) {
    // Same mapping as streaming::IndexEnvPrototype::detectFields().
    if (search_method == VsmfieldsConfig::Fieldspec::Searchmethod::NEAREST_NEIGHBOR ||
        search_method == VsmfieldsConfig::Fieldspec::Searchmethod::NONE)
    {
        return FieldInfo::DataType::TENSOR;
    }
    return FieldInfo::DataType::DOUBLE;
}

} // namespace

/*
 * The vsm field id space and the field id space of the index environment are
 * both derived from the vsmfields config and must come out identical, since vsm
 * hits are looked up in the index environment by field id. These tests pin that
 * invariant, including the reserved "no field" at id 0.
 */
class FieldSearchSpecMapTest : public ::testing::Test {
    TableManager           _table_manager;
    VsmfieldsConfigBuilder _fields;

protected:
    streaming::IndexEnvironment _index_env;
    FieldSearchSpecMap          _spec_map;

    FieldSearchSpecMapTest();
    ~FieldSearchSpecMapTest() override;

    void add_field(const std::string& name) {
        VsmfieldsConfigBuilder::Fieldspec spec;
        spec.name = name;
        spec.searchmethod = VsmfieldsConfigBuilder::Fieldspec::Searchmethod::AUTOUTF8;
        _fields.fieldspec.emplace_back(spec);
    }
    void add_index(const std::string& index_name, const std::vector<std::string>& field_names) {
        if (_fields.documenttype.empty()) {
            _fields.documenttype.resize(1);
            _fields.documenttype.back().name = "test";
        }
        VsmfieldsConfigBuilder::Documenttype::Index index;
        index.name = index_name;
        for (const auto& field_name : field_names) {
            VsmfieldsConfigBuilder::Documenttype::Index::Field field;
            field.name = field_name;
            index.field.emplace_back(field);
        }
        _fields.documenttype.back().index.emplace_back(index);
    }
    void build() {
        // Mirrors what RankManager and SearchVisitor do at config time.
        for (const auto& spec : _fields.fieldspec) {
            _index_env.addField(spec.name, false, to_data_type(spec.searchmethod));
        }
        _index_env.add_virtual_fields();
        _spec_map.buildFromConfig(std::make_shared<VsmfieldsConfig>(_fields), _index_env);
    }
    FieldIdT id_of(const std::string& name) const { return _spec_map.nameIdMap().fieldNo(name); }
};

FieldSearchSpecMapTest::FieldSearchSpecMapTest()
    : ::testing::Test(), _table_manager(), _fields(), _index_env(_table_manager), _spec_map() {
}

FieldSearchSpecMapTest::~FieldSearchSpecMapTest() = default;

TEST_F(FieldSearchSpecMapTest, no_field_is_first_in_index_environment) {
    build();
    ASSERT_EQ(1u, _index_env.getNumFields());
    ASSERT_NE(nullptr, _index_env.getField(0));
    EXPECT_TRUE(_index_env.getField(0)->is_no_field());
    EXPECT_EQ(nullptr, _index_env.getFieldByName(""));
    EXPECT_TRUE(_spec_map.specMap().empty());
    EXPECT_TRUE(_spec_map.nameIdMap().map().empty());
}

TEST_F(FieldSearchSpecMapTest, vsm_field_ids_start_at_one_and_match_the_index_environment) {
    add_field("a");
    add_field("b.c");
    add_field("b.d");
    add_index("a", {"a"});
    add_index("b", {"b.c", "b.d"});
    build();

    // 1 "no field" + 3 real fields + 1 virtual field 'b'.
    ASSERT_EQ(5u, _index_env.getNumFields());
    EXPECT_EQ(1u, id_of("a"));
    EXPECT_EQ(2u, id_of("b.c"));
    EXPECT_EQ(3u, id_of("b.d"));
    EXPECT_TRUE(_index_env.getFieldByName("b")->type() == FieldType::VIRTUAL);
    EXPECT_EQ(_index_env.getFieldByName("b")->id(), id_of("b"));

    // Only the real fields get a search spec; the reserved id 0 does not.
    EXPECT_EQ(3u, _spec_map.specMap().size());
    EXPECT_FALSE(_spec_map.specMap().contains(0));
    for (const auto& entry : _spec_map.specMap()) {
        EXPECT_EQ(entry.first, entry.second.id());
    }

    // Every name known to vsm resolves to the very same id in the index environment.
    for (const auto& entry : _spec_map.nameIdMap().map()) {
        SCOPED_TRACE("name=" + entry.first);
        EXPECT_FALSE(entry.first.empty());
        EXPECT_NE(0u, entry.second);
        const auto* field = _index_env.getFieldByName(entry.first);
        ASSERT_NE(nullptr, field);
        EXPECT_EQ(field->id(), entry.second);
    }
    EXPECT_TRUE(StringFieldIdTMap::npos == id_of(""));
}

TEST_F(FieldSearchSpecMapTest, id_matches_index_in_the_index_environment) {
    add_field("a");
    add_field("b.c");
    build();
    for (uint32_t i = 0; i < _index_env.getNumFields(); ++i) {
        SCOPED_TRACE("i=" + std::to_string(i));
        const auto* field = _index_env.getField(i);
        ASSERT_NE(nullptr, field);
        EXPECT_EQ(i, field->id());
    }
    EXPECT_EQ(nullptr, _index_env.getField(_index_env.getNumFields()));
}

TEST_F(FieldSearchSpecMapTest, views_only_refer_to_known_field_ids) {
    add_field("a");
    add_field("b");
    add_index("both", {"a", "b"});
    build();
    const auto& index_map = _spec_map.documentTypeMap().find("test")->second;
    auto        itr = index_map.find("both");
    ASSERT_TRUE(itr != index_map.end());
    EXPECT_EQ(vsm::FieldIdTList({1, 2}), itr->second);
}

GTEST_MAIN_RUN_ALL_TESTS()
