// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
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
    ASSERT_TRUE(vespalib::slime::JsonFormat::decode(json, slime));
    return slime;
}

const Inspector &full_obj() {
    static vespalib::string str =
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
    static vespalib::string str =
        "{"
        "  a: 'foo',"
        "  c: 'baz',"
        "  d: [1,2,3]"
        "}";
    static Slime slime = parse(str);
    return slime.get();
}

const Inspector &wildcard_obj() {
    static vespalib::string str =
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
    static vespalib::string str =
        "{"
        " ref: [ true,   7, 2.0, 'foo'],"
        "same: [ true,   7, 2.0, 'foo'],"
        "err1: [false,   5, 2.5, 'bar'],"
        "err2: [    1, 7.0,   2,     3, '0x010203', 'null']"
        "}";
    static Slime slime = add_data_and_nix(parse(str));
    return slime.get();
}

vespalib::string path_to_str(const Path &path) {
    size_t cnt = 0;
    vespalib::string str("[");
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

vespalib::string to_str(const Inspector &value) {
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
    EXPECT_EQUAL(result, expect);
}

TEST("strict compare (used by == operator)") {
    auto allow_nothing = [](const auto &, const auto &, const auto &)noexcept{ return false; }; 
    TEST_DO(verify(    full_obj(),     full_obj(), allow_nothing,  true));
    TEST_DO(verify(    full_obj(),   subset_obj(), allow_nothing, false));
    TEST_DO(verify(  subset_obj(),     full_obj(), allow_nothing, false));
    TEST_DO(verify(    full_obj(), wildcard_obj(), allow_nothing, false));
    TEST_DO(verify(wildcard_obj(),     full_obj(), allow_nothing, false));
}

TEST("subset compare") {
    auto allow_subset_ab = [](const auto &, const auto &a, const auto &)noexcept{ return !a.valid(); };
    auto allow_subset_ba = [](const auto &, const auto &, const auto &b)noexcept{ return !b.valid(); };
    TEST_DO(verify(  subset_obj(),     full_obj(), allow_subset_ab,  true));
    TEST_DO(verify(    full_obj(),   subset_obj(), allow_subset_ab, false));
    TEST_DO(verify(    full_obj(),   subset_obj(), allow_subset_ba,  true));
    TEST_DO(verify(  subset_obj(),     full_obj(), allow_subset_ba, false));
}

TEST("wildcard compare") {
    auto allow_wildcard_a = [](const auto &, const auto &a, const auto &)noexcept
                            { return a.valid() && a.type().getId() == NIX::ID; };
    auto allow_wildcard_b = [](const auto &, const auto &, const auto &b)noexcept
                            { return b.valid() && b.type().getId() == NIX::ID; };
    TEST_DO(verify(wildcard_obj(),     full_obj(), allow_wildcard_a, false));
    TEST_DO(verify(wildcard_obj(),   subset_obj(), allow_wildcard_a,  true));
    TEST_DO(verify(  subset_obj(), wildcard_obj(), allow_wildcard_a, false));
    TEST_DO(verify(    full_obj(), wildcard_obj(), allow_wildcard_b, false));
    TEST_DO(verify(  subset_obj(), wildcard_obj(), allow_wildcard_b,  true));
    TEST_DO(verify(wildcard_obj(),   subset_obj(), allow_wildcard_b, false));
}

TEST("leaf nodes") {
    auto allow_nothing = [](const auto &, const auto &, const auto &)noexcept{ return false; }; 
    const Inspector &root = leaf_cmp_obj();
    EXPECT_EQUAL(root["ref"].entries(),  6u);
    EXPECT_EQUAL(root["same"].entries(), 6u);
    EXPECT_EQUAL(root["err1"].entries(), 5u); // invalid nix at end
    EXPECT_EQUAL(root["err2"].entries(), 6u);
    for (size_t i = 0; i < 6; ++i) {
        TEST_DO(verify(root["ref"][i], root["same"][i], allow_nothing, true));
        TEST_DO(verify(root["ref"][i], root["err1"][i], allow_nothing, false));
        TEST_DO(verify(root["ref"][i], root["err2"][i], allow_nothing, false));
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
