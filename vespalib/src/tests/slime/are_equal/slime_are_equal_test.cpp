// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/overload.h>
#include <functional>
#include <variant>

using namespace vespalib::slime::convenience;
using vespalib::make_string_short::fmt;
using vespalib::slime::NIX;

using Path = std::vector<std::variant<size_t,std::string_view>>;
using Hook = std::function<bool(const Path &, const Inspector &, const Inspector &)>;
using vespalib::slime::Inspector;

Slime parse(const std::string &json) {
    Slime slime;
    EXPECT_TRUE(vespalib::slime::JsonFormat::decode(json, slime));
    return slime;
}

const Inspector &full_obj() {
    static std::string str =
        "{"
        "  a: 'foo',"
        "  b: 'bar',"
        "  c: 'baz',"
        "  d: [1,2,3,4,5]"
        "}";
    static Slime slime = parse(str);
    return slime.get();
}

const Inspector &subset_obj() {
    static std::string str =
        "{"
        "  a: 'foo',"
        "  c: 'baz',"
        "  d: [1,2,3]"
        "}";
    static Slime slime = parse(str);
    return slime.get();
}

const Inspector &wildcard_obj() {
    static std::string str =
        "{"
        "  a: 'foo',"
        "  b: null,"
        "  c: null,"
        "  d: [null,2,3,null]"
        "}";
    static Slime slime = parse(str);
    return slime.get();
}

Slime add_data_and_nix(Slime slime) {
    Cursor &root = slime.get();
    char space1[3] = { 1, 2, 3 };
    char space2[3] = { 2, 4, 6 };
    root["ref"].addData(Memory(space1, 3));
    root["ref"].addNix();
    root["same"].addData(Memory(space1, 3));
    root["same"].addNix();
    root["err1"].addData(Memory(space2, 3));
    // err1: invalid nix vs valid nix
    return slime;
}

const Inspector &leaf_cmp_obj() {
    static std::string str =
        "{"
        " ref: [ true,   7, 2.0, 'foo'],"
        "same: [ true,   7, 2.0, 'foo'],"
        "err1: [false,   5, 2.5, 'bar'],"
        "err2: [    1, 7.0,   2,     3, '0x010203', 'null']"
        "}";
    static Slime slime = add_data_and_nix(parse(str));
    return slime.get();
}

std::string path_to_str(const Path &path) {
    size_t cnt = 0;
    std::string str("[");
    for (const auto &item: path) {
        if (cnt++ > 0) {
            str.append(",");
        }
        std::visit(vespalib::overload{
                [&str](size_t value)noexcept{ str.append(fmt("%zu", value)); },
                [&str](std::string_view value)noexcept{ str.append(value); }}, item);
    }
    str.append("]");
    return str;
}

std::string to_str(const Inspector &value) {
    if (!value.valid()) {
        return "<missing>";
    }
    vespalib::SimpleBuffer buf;
    vespalib::slime::JsonFormat::encode(value, buf, true);
    return buf.get().make_string();
}

Hook dump_mismatches(Hook hook) {
    return [hook](const Path &path, const Inspector &a, const Inspector &b)
           {
               bool result = hook(path, a, b);
               fprintf(stderr, "mismatch at %s: %s vs %s (%s)\n", path_to_str(path).c_str(),
                       to_str(a).c_str(), to_str(b).c_str(), result ? "allowed" : "FAIL");
               return result;
           };
}

void verify(const Inspector &a, const Inspector &b, Hook c, bool expect) {
    fprintf(stderr, "---> cmp\n");
    Hook my_hook = dump_mismatches(c);
    bool result = vespalib::slime::are_equal(a, b, my_hook);
    fprintf(stderr, "<--- cmp\n");
    EXPECT_EQ(result, expect);
}

TEST(SlimeAreEqualTest, strict_compare__used_by_eq_operator) {
    auto allow_nothing = [](const auto &, const auto &, const auto &)noexcept{ return false; }; 
    GTEST_DO(verify(    full_obj(),     full_obj(), allow_nothing,  true));
    GTEST_DO(verify(    full_obj(),   subset_obj(), allow_nothing, false));
    GTEST_DO(verify(  subset_obj(),     full_obj(), allow_nothing, false));
    GTEST_DO(verify(    full_obj(), wildcard_obj(), allow_nothing, false));
    GTEST_DO(verify(wildcard_obj(),     full_obj(), allow_nothing, false));
}

TEST(SlimeAreEqualTest, subset_compare) {
    auto allow_subset_ab = [](const auto &, const auto &a, const auto &)noexcept{ return !a.valid(); };
    auto allow_subset_ba = [](const auto &, const auto &, const auto &b)noexcept{ return !b.valid(); };
    GTEST_DO(verify(  subset_obj(),     full_obj(), allow_subset_ab,  true));
    GTEST_DO(verify(    full_obj(),   subset_obj(), allow_subset_ab, false));
    GTEST_DO(verify(    full_obj(),   subset_obj(), allow_subset_ba,  true));
    GTEST_DO(verify(  subset_obj(),     full_obj(), allow_subset_ba, false));
}

TEST(SlimeAreEqualTest, wildcard_compare) {
    auto allow_wildcard_a = [](const auto &, const auto &a, const auto &)noexcept
                            { return a.valid() && a.type().getId() == NIX::ID; };
    auto allow_wildcard_b = [](const auto &, const auto &, const auto &b)noexcept
                            { return b.valid() && b.type().getId() == NIX::ID; };
    GTEST_DO(verify(wildcard_obj(),     full_obj(), allow_wildcard_a, false));
    GTEST_DO(verify(wildcard_obj(),   subset_obj(), allow_wildcard_a,  true));
    GTEST_DO(verify(  subset_obj(), wildcard_obj(), allow_wildcard_a, false));
    GTEST_DO(verify(    full_obj(), wildcard_obj(), allow_wildcard_b, false));
    GTEST_DO(verify(  subset_obj(), wildcard_obj(), allow_wildcard_b,  true));
    GTEST_DO(verify(wildcard_obj(),   subset_obj(), allow_wildcard_b, false));
}

TEST(SlimeAreEqualTest, leaf_nodes) {
    auto allow_nothing = [](const auto &, const auto &, const auto &)noexcept{ return false; }; 
    const Inspector &root = leaf_cmp_obj();
    EXPECT_EQ(root["ref"].entries(),  6u);
    EXPECT_EQ(root["same"].entries(), 6u);
    EXPECT_EQ(root["err1"].entries(), 5u); // invalid nix at end
    EXPECT_EQ(root["err2"].entries(), 6u);
    for (size_t i = 0; i < 6; ++i) {
        GTEST_DO(verify(root["ref"][i], root["same"][i], allow_nothing, true));
        GTEST_DO(verify(root["ref"][i], root["err1"][i], allow_nothing, false));
        GTEST_DO(verify(root["ref"][i], root["err2"][i], allow_nothing, false));
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
