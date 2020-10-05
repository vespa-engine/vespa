// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_sparse_map.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;

class StringList {
private:
    std::vector<vespalib::string> _str_list;
    std::vector<vespalib::stringref> _ref_list;
    std::vector<const vespalib::stringref *> _ref_ptr_list;
public:
    StringList(const std::vector<vespalib::string> &list)
        : _str_list(list), _ref_list(), _ref_ptr_list()
    {
        for (const auto &str: _str_list) {
            _ref_list.push_back(str);
        }
        for (const auto &ref: _ref_list) {
            _ref_ptr_list.push_back(&ref);
        }
    }
    ~StringList();
    ConstArrayRef<vespalib::string> direct_str() const { return _str_list; }
    ConstArrayRef<vespalib::stringref> direct_ref() const { return _ref_list; }
    ConstArrayRef<const vespalib::stringref *> indirect_ref() const { return _ref_ptr_list; }
};
StringList::~StringList() = default;
using SL = StringList;

TEST(SimpleSparseMapTest, simple_sparse_map_basic_usage_works) {
    SL a1({"a","a","a"});
    SL a2({"a","a","b"});
    SL a3({"a","b","a"});
    SL a4({"b","a","a"});
    SimpleSparseMap map(3, 128);
    EXPECT_EQ(map.size(), 0);
    map.add_mapping(a1.direct_str());
    map.add_mapping(a2.direct_ref());
    map.add_mapping(a3.indirect_ref());
    EXPECT_EQ(map.size(), 3);
    EXPECT_EQ(map.lookup(a1.direct_str()), 0);
    EXPECT_EQ(map.lookup(a1.direct_ref()), 0);
    EXPECT_EQ(map.lookup(a1.indirect_ref()), 0);
    EXPECT_EQ(map.lookup(a2.direct_str()), 1);
    EXPECT_EQ(map.lookup(a2.direct_ref()), 1);
    EXPECT_EQ(map.lookup(a2.indirect_ref()), 1);
    EXPECT_EQ(map.lookup(a3.direct_str()), 2);
    EXPECT_EQ(map.lookup(a3.direct_ref()), 2);
    EXPECT_EQ(map.lookup(a3.indirect_ref()), 2);
    EXPECT_EQ(map.lookup(a4.direct_str()), map.npos());
    EXPECT_EQ(map.lookup(a4.direct_ref()), map.npos());
    EXPECT_EQ(map.lookup(a4.indirect_ref()), map.npos());
    EXPECT_EQ(SimpleSparseMap::npos(), map.npos());
    EXPECT_EQ(map.labels().size(), 9);
    auto dump = [&](auto addr_tag, auto subspace, auto hash) {
        auto addr = map.make_addr(addr_tag);
        fprintf(stderr, "   [%s,%s,%s]: %u (%zu)\n", addr[0].label.c_str(), addr[1].label.c_str(), addr[2].label.c_str(), subspace, hash);
    };
    map.each_map_entry(dump);
}

TEST(SimpleSparseMapTest, simple_sparse_map_works_with_no_labels) {
    SL empty({});
    SimpleSparseMap map1(0, 1);
    SimpleSparseMap map2(0, 1);
    SimpleSparseMap map3(0, 1);
    EXPECT_EQ(map1.size(), 0);
    EXPECT_EQ(map2.size(), 0);
    EXPECT_EQ(map3.size(), 0);
    map1.add_mapping(empty.direct_str());
    map2.add_mapping(empty.direct_ref());
    map3.add_mapping(empty.indirect_ref());
    EXPECT_EQ(map1.size(), 1);
    EXPECT_EQ(map2.size(), 1);
    EXPECT_EQ(map3.size(), 1);
    EXPECT_EQ(map1.lookup(empty.direct_str()), 0);
    EXPECT_EQ(map1.lookup(empty.direct_ref()), 0);
    EXPECT_EQ(map1.lookup(empty.indirect_ref()), 0);
    EXPECT_EQ(map2.lookup(empty.direct_str()), 0);
    EXPECT_EQ(map2.lookup(empty.direct_ref()), 0);
    EXPECT_EQ(map2.lookup(empty.indirect_ref()), 0);
    EXPECT_EQ(map3.lookup(empty.direct_str()), 0);
    EXPECT_EQ(map3.lookup(empty.direct_ref()), 0);
    EXPECT_EQ(map3.lookup(empty.indirect_ref()), 0);
    EXPECT_EQ(map1.labels().size(), 0);
    EXPECT_EQ(map2.labels().size(), 0);
    EXPECT_EQ(map3.labels().size(), 0);
}

TEST(SimpleSparseMapTest, size_of_internal_types) {
    fprintf(stderr, "simple sparse map hash node size: %zu\n", sizeof(hash_node<SimpleSparseMap::MapType::value_type>));
}

GTEST_MAIN_RUN_ALL_TESTS()
