// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_range_term_expander.

#include <vespa/searchlib/predicate/predicate_range_term_expander.h>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/testkit/test_kit.h>

using search::predicate::PredicateRangeTermExpander;
using std::vector;
using vespalib::string;

namespace {

struct MyRangeHandler {
    vector<string> expected_labels;
    string expected_edge_label;
    uint64_t expected_edge_value;
    size_t i;
    ~MyRangeHandler() {
        EXPECT_EQUAL(expected_labels.size(), i);
    }
    void handleRange(const string &label) {
        TEST_STATE(("handleRange: " + label).c_str());
        ASSERT_TRUE(i < expected_labels.size());
        EXPECT_EQUAL(expected_labels[i++], label);
    }
    void handleEdge(const string &label, uint64_t value) {
        TEST_STATE(("handleEdge: " + label).c_str());
        EXPECT_EQUAL(expected_edge_label, label);
        EXPECT_EQUAL(expected_edge_value, value);
    }
};

TEST("require that small range is expanded") {
    PredicateRangeTermExpander expander(10);
    MyRangeHandler range_handler{{
                "key=40-49",
                "key=0-99",
                "key=0-999",
                "key=0-9999",
                "key=0-99999",
                "key=0-999999",
                "key=0-9999999",
                "key=0-99999999",
                "key=0-999999999",
                "key=0-9999999999",
                "key=0-99999999999",
                "key=0-999999999999",
                "key=0-9999999999999",
                "key=0-99999999999999",
                "key=0-999999999999999",
                "key=0-9999999999999999",
                "key=0-99999999999999999",
                "key=0-999999999999999999"}, "key=40", 2, 0};
    expander.expand("key", 42, range_handler);
}

TEST("require that large range is expanded") {
    PredicateRangeTermExpander expander(10);
    MyRangeHandler range_handler{{
                "key=123456789012345670-123456789012345679",
                "key=123456789012345600-123456789012345699",
                "key=123456789012345000-123456789012345999",
                "key=123456789012340000-123456789012349999",
                "key=123456789012300000-123456789012399999",
                "key=123456789012000000-123456789012999999",
                "key=123456789010000000-123456789019999999",
                "key=123456789000000000-123456789099999999",
                "key=123456789000000000-123456789999999999",
                "key=123456780000000000-123456789999999999",
                "key=123456700000000000-123456799999999999",
                "key=123456000000000000-123456999999999999",
                "key=123450000000000000-123459999999999999",
                "key=123400000000000000-123499999999999999",
                "key=123000000000000000-123999999999999999",
                "key=120000000000000000-129999999999999999",
                "key=100000000000000000-199999999999999999",
                "key=0-999999999999999999"},
                "key=123456789012345670", 8, 0};
    expander.expand("key", 123456789012345678, range_handler);
}

TEST("require that max range is expanded") {
    PredicateRangeTermExpander expander(10);
    MyRangeHandler range_handler{{}, "key=9223372036854775800", 7, 0};
    expander.expand("key", 9223372036854775807, range_handler);
}

TEST("require that small negative range is expanded") {
    PredicateRangeTermExpander expander(10);
    MyRangeHandler range_handler{{
                "key=-49-40",
                "key=-99-0",
                "key=-999-0",
                "key=-9999-0",
                "key=-99999-0",
                "key=-999999-0",
                "key=-9999999-0",
                "key=-99999999-0",
                "key=-999999999-0",
                "key=-9999999999-0",
                "key=-99999999999-0",
                "key=-999999999999-0",
                "key=-9999999999999-0",
                "key=-99999999999999-0",
                "key=-999999999999999-0",
                "key=-9999999999999999-0",
                "key=-99999999999999999-0",
                "key=-999999999999999999-0"}, "key=-40", 2, 0};
    expander.expand("key", -42, range_handler);
}

TEST("require that min range is expanded") {
    PredicateRangeTermExpander expander(10);
    MyRangeHandler range_handler{{}, "key=-9223372036854775800", 8, 0};
    expander.expand("key", -9223372036854775808ull, range_handler);
}
TEST("require that min range - 9 is expanded") {
    PredicateRangeTermExpander expander(10);
    MyRangeHandler range_handler{{
            "key=-9223372036854775799-9223372036854775790",
            "key=-9223372036854775799-9223372036854775700"},
            "key=-9223372036854775790", 9, 0};
    expander.expand("key", -9223372036854775799ll, range_handler);
}

TEST("require that min range is expanded with arity 8") {
    PredicateRangeTermExpander expander(8);
    MyRangeHandler range_handler{{}, "key=-9223372036854775808", 0, 0};
    expander.expand("key", -9223372036854775808ull, range_handler);
}

TEST("require that small range is expanded in arity 2") {
    PredicateRangeTermExpander expander(2);
    MyRangeHandler range_handler{{
                "key=42-43",
                "key=40-43",
                "key=40-47",
                "key=32-47",
                "key=32-63",
                "key=0-63",
                "key=0-127",
                "key=0-255",
                "key=0-511",
                "key=0-1023",
                "key=0-2047",
                "key=0-4095",
                "key=0-8191",
                "key=0-16383",
                "key=0-32767",
                "key=0-65535",
                "key=0-131071",
                "key=0-262143",
                "key=0-524287",
                "key=0-1048575",
                "key=0-2097151",
                "key=0-4194303",
                "key=0-8388607",
                "key=0-16777215",
                "key=0-33554431",
                "key=0-67108863",
                "key=0-134217727",
                "key=0-268435455",
                "key=0-536870911",
                "key=0-1073741823",
                "key=0-2147483647",
                "key=0-4294967295",
                "key=0-8589934591",
                "key=0-17179869183",
                "key=0-34359738367",
                "key=0-68719476735",
                "key=0-137438953471",
                "key=0-274877906943",
                "key=0-549755813887",
                "key=0-1099511627775",
                "key=0-2199023255551",
                "key=0-4398046511103",
                "key=0-8796093022207",
                "key=0-17592186044415",
                "key=0-35184372088831",
                "key=0-70368744177663",
                "key=0-140737488355327",
                "key=0-281474976710655",
                "key=0-562949953421311",
                "key=0-1125899906842623",
                "key=0-2251799813685247",
                "key=0-4503599627370495",
                "key=0-9007199254740991",
                "key=0-18014398509481983",
                "key=0-36028797018963967",
                "key=0-72057594037927935",
                "key=0-144115188075855871",
                "key=0-288230376151711743",
                "key=0-576460752303423487",
                "key=0-1152921504606846975",
                "key=0-2305843009213693951",
                "key=0-4611686018427387903",
                "key=0-9223372036854775807"}, "key=42", 0, 0};
    expander.expand("key", 42, range_handler);
}

TEST("require that small negative range is expanded in arity 2") {
    PredicateRangeTermExpander expander(2);
    MyRangeHandler range_handler{{
                "key=-43-42",
                "key=-43-40",
                "key=-47-40",
                "key=-47-32",
                "key=-63-32",
                "key=-63-0",
                "key=-127-0",
                "key=-255-0",
                "key=-511-0",
                "key=-1023-0",
                "key=-2047-0",
                "key=-4095-0",
                "key=-8191-0",
                "key=-16383-0",
                "key=-32767-0",
                "key=-65535-0",
                "key=-131071-0",
                "key=-262143-0",
                "key=-524287-0",
                "key=-1048575-0",
                "key=-2097151-0",
                "key=-4194303-0",
                "key=-8388607-0",
                "key=-16777215-0",
                "key=-33554431-0",
                "key=-67108863-0",
                "key=-134217727-0",
                "key=-268435455-0",
                "key=-536870911-0",
                "key=-1073741823-0",
                "key=-2147483647-0",
                "key=-4294967295-0",
                "key=-8589934591-0",
                "key=-17179869183-0",
                "key=-34359738367-0",
                "key=-68719476735-0",
                "key=-137438953471-0",
                "key=-274877906943-0",
                "key=-549755813887-0",
                "key=-1099511627775-0",
                "key=-2199023255551-0",
                "key=-4398046511103-0",
                "key=-8796093022207-0",
                "key=-17592186044415-0",
                "key=-35184372088831-0",
                "key=-70368744177663-0",
                "key=-140737488355327-0",
                "key=-281474976710655-0",
                "key=-562949953421311-0",
                "key=-1125899906842623-0",
                "key=-2251799813685247-0",
                "key=-4503599627370495-0",
                "key=-9007199254740991-0",
                "key=-18014398509481983-0",
                "key=-36028797018963967-0",
                "key=-72057594037927935-0",
                "key=-144115188075855871-0",
                "key=-288230376151711743-0",
                "key=-576460752303423487-0",
                "key=-1152921504606846975-0",
                "key=-2305843009213693951-0",
                "key=-4611686018427387903-0",
                "key=-9223372036854775807-0"}, "key=-42", 0, 0};
    expander.expand("key", -42, range_handler);
}

TEST("require that upper bound is used") {
    PredicateRangeTermExpander expander(10, -99, 9999);
    MyRangeHandler range_handler{{
                "key=40-49",
                "key=0-99",
                "key=0-999",
                "key=0-9999"}, "key=40", 2, 0};
    expander.expand("key", 42, range_handler);
}

TEST("require that lower bound is used") {
    PredicateRangeTermExpander expander(10, -9999, 99);
    MyRangeHandler range_handler{{
                "key=-49-40",
                "key=-99-0",
                "key=-999-0",
                "key=-9999-0"}, "key=-40", 2, 0};
    expander.expand("key", -42, range_handler);
}

TEST("require that value outside bounds is not used") {
    PredicateRangeTermExpander expander(10, -99, 99);
    MyRangeHandler range_handler{{}, "handleEdge is never called", 2, 0};
    expander.expand("key", 100, range_handler);
}

TEST("require that upper and lower bound > 0 works") {
    PredicateRangeTermExpander expander(10, 100, 9999);
    MyRangeHandler range_handler{{
                "key=140-149",
                "key=100-199",
                "key=0-999",
                "key=0-9999"}, "key=140", 2, 0};
    expander.expand("key", 142, range_handler);
}

TEST("require that search close to uneven upper bound is sensible") {
    PredicateRangeTermExpander expander(10, -99, 1234);
    MyRangeHandler range_handler{{
                "key=40-49",
                "key=0-99",
                "key=0-999",
                "key=0-9999"}, "key=40", 2, 0};
    expander.expand("key", 42, range_handler);
}

TEST("require that search close to max uneven upper bound is sensible") {
    PredicateRangeTermExpander expander(10, 0, 9223372036854771234);
    MyRangeHandler range_handler{{
                "key=9223372036854770000-9223372036854770009",
                "key=9223372036854770000-9223372036854770099",
                "key=9223372036854770000-9223372036854770999"},
                "key=9223372036854770000", 0, 0};
    expander.expand("key", 9223372036854770000, range_handler);
}

}  // namespace
