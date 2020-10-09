// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_sparse_map.h>
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
    bool is_eq(ConstArrayRef<FastSparseMap::HashedLabel> addr) const {
        if (addr.size() != _str_list.size()) {
            return false;
        }
        for (size_t i = 0; i < addr.size(); ++i) {
            if (addr[i].label != _str_list[i]) {
                return false;
            }
        }
        return true;
    }
};
StringList::~StringList() = default;
using SL = StringList;

TEST(FastSparseMapTest, fast_sparse_map_basic_usage_works) {
    SL a1({"a","a","a"});
    SL a2({"a","a","b"});
    SL a3({"a","b","a"});
    SL a4({"b","a","a"});
    FastSparseMap map(3, 128);
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
    EXPECT_EQ(FastSparseMap::npos(), map.npos());
    EXPECT_EQ(map.labels().size(), 9);
    std::set<uint64_t> seen_hashes;
    std::map<uint32_t, uint32_t> addr_map;
    auto my_fun = [&](uint32_t addr_tag, uint32_t subspace, uint64_t hash) {
        addr_map[addr_tag] = subspace;
        seen_hashes.insert(hash);
    };
    map.each_map_entry(my_fun);
    EXPECT_EQ(seen_hashes.size(), 3);
    EXPECT_EQ(addr_map.size(), 3);
    EXPECT_NE(addr_map.find(0), addr_map.end());
    EXPECT_EQ(addr_map[0], 0);
    EXPECT_EQ(addr_map[3], 1);
    EXPECT_EQ(addr_map[6], 2);
    EXPECT_EQ(addr_map.size(), 3);
    EXPECT_TRUE(a1.is_eq(map.make_addr(0)));
    EXPECT_FALSE(a2.is_eq(map.make_addr(0)));
    EXPECT_TRUE(a2.is_eq(map.make_addr(3)));
    EXPECT_TRUE(a3.is_eq(map.make_addr(6)));
}

TEST(FastSparseMapTest, fast_sparse_map_works_with_no_labels) {
    SL empty({});
    FastSparseMap map1(0, 1);
    FastSparseMap map2(0, 1);
    FastSparseMap map3(0, 1);
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

TEST(FastSparseMapTest, size_of_internal_types) {
    fprintf(stderr, "fast sparse map hash node size: %zu\n", sizeof(hash_node<FastSparseMap::MapType::value_type>));
}

GTEST_MAIN_RUN_ALL_TESTS()
