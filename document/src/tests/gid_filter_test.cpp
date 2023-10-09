// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/select/gid_filter.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/base/testdocrepo.h>
#include <gtest/gtest.h>

namespace document::select {

class GidFilterTest : public ::testing::Test {

protected:
    struct Fixture {
        TestDocRepo _repo;
        BucketIdFactory _id_factory;
        std::unique_ptr<Node> _root;
        
        Fixture(vespalib::stringref selection);
        ~Fixture();

        Fixture(const Fixture&) = delete;
        Fixture(Fixture&&) = delete;
        Fixture& operator=(const Fixture&) = delete;
        Fixture& operator=(Fixture&&) = delete;

        static Fixture for_selection(vespalib::stringref s) {
            return Fixture(s);
        }
    };

    static GlobalId id_to_gid(vespalib::stringref id_string) {
        return DocumentId(id_string).getGlobalId();
    }

    bool might_match(vespalib::stringref selection,
                     vespalib::stringref id_string) const
    {
        Fixture f(selection);
        auto filter = GidFilter::for_selection_root_node(*f._root);
        return filter.gid_might_match_selection(id_to_gid(id_string));
    }
};

GidFilterTest::Fixture::Fixture(vespalib::stringref selection)
    : _repo(),
      _id_factory(),
      _root(Parser(_repo.getTypeRepo(), _id_factory).parse(selection))
{ }
GidFilterTest::Fixture::~Fixture() { }

TEST_F(GidFilterTest, same_user_for_selection_and_gid_returns_match)
{
    EXPECT_TRUE(might_match("id.user == 12345",
                            "id::testdoctype1:n=12345:foo"));
    // User locations are defined over [0, 2**63-1]
    EXPECT_TRUE(might_match("id.user == 0",
                            "id::testdoctype1:n=0:foo"));
    EXPECT_TRUE(might_match("id.user == 9223372036854775807" ,
                            "id::testdoctype1:n=9223372036854775807:foo"));
}

TEST_F(GidFilterTest, differing_user_for_selection_and_gid_returns_mismatch)
{
    EXPECT_TRUE(!might_match("id.user == 1", "id::testdoctype1:n=2000:foo"));
    // Similar, but non-identical, bit patterns
    EXPECT_TRUE(!might_match("id.user == 12345",
                             "id::testdoctype1:n=12346:foo"));
    EXPECT_TRUE(!might_match("id.user == 12345",
                             "id::testdoctype1:n=12344:foo"));
}

TEST_F(GidFilterTest, user_location_constraint_is_order_invariant)
{
    EXPECT_TRUE(might_match("12345 == id.user",
                            "id::testdoctype1:n=12345:foo"));
    EXPECT_TRUE(!might_match("12345 == id.user",
                             "id::testdoctype1:n=12346:foo"));
}

TEST_F(GidFilterTest, non_location_selection_always_matches)
{
    EXPECT_TRUE(might_match("testdoctype1.headerval == 67890",
                            "id::testdoctype1:n=12345:foo"));
}

TEST_F(GidFilterTest, location_selection_does_not_match_non_location_id)
{
    // Test name is a half-truth; the MD5-derived ID _will_ give a false
    // positive every 2**32 or so document ID when the stars and their bit
    // patterns align :)
    EXPECT_TRUE(!might_match("id.user == 987654321",
                             "id::testdoctype1::foo"));

    EXPECT_TRUE(!might_match("id.group == 'snusmumrikk'",
                             "id::testdoctype1::foo"));
}

TEST_F(GidFilterTest, simple_conjunctive_location_expressions_are_filtered)
{
    // A conjunctive expression in this context is one where there exist a
    // location predicate and the result of the entire expression can only
    // be true iff the location predicate matches.
    EXPECT_TRUE(might_match("id.user == 12345 and true",
                            "id::testdoctype1:n=12345:bar"));
    EXPECT_TRUE(might_match("true and id.user == 12345",
                            "id::testdoctype1:n=12345:bar"));

    EXPECT_TRUE(!might_match("id.user == 123456 and true",
                             "id::testdoctype1:n=12345:bar"));
    EXPECT_TRUE(!might_match("true and id.user == 123456",
                             "id::testdoctype1:n=12345:bar"));
}

TEST_F(GidFilterTest, complex_conjunctive_location_expressions_are_filtered)
{
    EXPECT_TRUE(might_match("(((testdoctype1.headerval < 5) and (1 != 2)) "
                            "and id.user == 12345)",
                               "id::testdoctype1:n=12345:bar"));
    EXPECT_TRUE(!might_match("(((1 != 2) and (id.user==12345)) and "
                             "(2 != 3)) and (testdoctype1.headerval < 5)",
                             "id::testdoctype1:n=23456:bar"));
    // In this case the expression contains a disjunction but the outcome
    // of evaluating it still strongly depends on the location predicate.
    EXPECT_TRUE(might_match("((id.user == 12345 and true) and "
                            "(true or false))",
                            "id::testdoctype1:n=12345:bar"));
    EXPECT_TRUE(!might_match("((id.user == 12345 and true) and "
                             "(true or false))",
                             "id::testdoctype1:n=23456:bar"));
}

TEST_F(GidFilterTest, simple_disjunctive_location_expressions_are_not_filtered)
{
    // Documents mismatch location but match selection as a whole.
    EXPECT_TRUE(might_match("id.user == 12345 or true",
                            "id::testdoctype1:n=12345678:bar"));
    EXPECT_TRUE(might_match("true or id.user == 12345",
                            "id::testdoctype1:n=12345678:bar"));
}

TEST_F(GidFilterTest, complex_disjunctive_location_expressions_are_not_filtered)
{
    EXPECT_TRUE(might_match("((id.user == 12345) and true) or false",
                            "id::testdoctype1:n=12345678:bar"));
    EXPECT_TRUE(might_match("((id.user == 12345) or false) and true",
                            "id::testdoctype1:n=12345678:bar"));
}

TEST_F(GidFilterTest, non_location_id_comparisons_are_not_filtered)
{
    // Note: these selections are syntactically valid but semantically
    // invalid (comparing strings to integers), but are used to catch any
    // logic holes where an id node is indiscriminately treated as something
    // from which we should derive a GID-related integer.
    EXPECT_TRUE(might_match("id.namespace == 123456",
                            "id::testdoctype1:n=12345678:bar"));
    EXPECT_TRUE(might_match("id.type == 1234",
                            "id::testdoctype1:n=12345678:bar"));
    EXPECT_TRUE(might_match("id.scheme == 555",
                            "id::testdoctype1:n=12345678:bar"));
    EXPECT_TRUE(might_match("id.specific == 7654",
                            "id::testdoctype1:n=12345678:bar"));
}

TEST_F(GidFilterTest, unsupported_location_comparison_operands_not_filtered)
{
    EXPECT_TRUE(might_match("id.user == 'rick & morty'",
                            "id::testdoctype1:n=12345678:bar"));
    EXPECT_TRUE(might_match("id.group == 56789",
                            "id::testdoctype1:n=12345678:bar"));
    EXPECT_TRUE(might_match("id.user == testdoctype1.headervalue",
                            "id::testdoctype1:n=12345678:bar"));
    EXPECT_TRUE(might_match("id.group == testdoctype1.headervalue",
                            "id::testdoctype1:g=helloworld:bar"));
}

TEST_F(GidFilterTest, default_constructed_filter_always_matches)
{
    GidFilter filter;
    EXPECT_TRUE(filter.gid_might_match_selection(
            DocumentId("id::testdoctype1:n=12345678:bar").getGlobalId()));
    EXPECT_TRUE(filter.gid_might_match_selection(
            DocumentId("id::testdoctype1::foo").getGlobalId()));
}

TEST_F(GidFilterTest, most_significant_32_bits_are_ignored)
{
    // The fact that the 32 MSB are effectively ignored is an artifact of
    // how the GID location extraction is historically performed and is not
    // necessarily the optimum (in particular, an XOR combination of the upper
    // and lower 32 bits would likely be much better), but it's what the
    // behavior currently is and should thus be tested.

    // The following locations have the same 32 LSB:
    EXPECT_TRUE(might_match("id.user == 12345678901",
                            "id::testdoctype1:n=29525548085:bar"));
}

TEST_F(GidFilterTest, gid_filters_may_be_copy_constructed)
{
    Fixture f("id.user == 1337");
    GidFilter filter = GidFilter::for_selection_root_node(*f._root);

    GidFilter copy_constructed(filter);
    EXPECT_TRUE(copy_constructed.gid_might_match_selection(
            id_to_gid("id::testdoctype1:n=1337:zoid")));
    EXPECT_TRUE(!copy_constructed.gid_might_match_selection(
            id_to_gid("id::testdoctype1:n=555:zoid")));

}

TEST_F(GidFilterTest, gid_filters_may_be_copy_assigned)
{
    Fixture f("id.user == 1337");
    GidFilter filter = GidFilter::for_selection_root_node(*f._root);

    GidFilter copy_assigned;
    copy_assigned = filter;

    EXPECT_TRUE(copy_assigned.gid_might_match_selection(
            id_to_gid("id::testdoctype1:n=1337:zoid")));
    EXPECT_TRUE(!copy_assigned.gid_might_match_selection(
            id_to_gid("id::testdoctype1:n=555:zoid")));
}

TEST_F(GidFilterTest, same_group_for_selection_and_gid_returns_match)
{
    EXPECT_TRUE(might_match("id.group == 'bjarne'",
                            "id::testdoctype1:g=bjarne:foo"));
    EXPECT_TRUE(might_match("id.group == 'andrei'",
                            "id::testdoctype1:g=andrei:bar"));
}

TEST_F(GidFilterTest, differing_group_for_selection_and_gid_returns_mismatch)
{
    EXPECT_TRUE(!might_match("id.group == 'cult of bjarne'",
                             "id::testdoctype1:g=stl:foo"));
    EXPECT_TRUE(!might_match("id.group == 'sutters mill'",
                             "id::testdoctype1:g=andrei:bar"));
}

TEST_F(GidFilterTest, composite_user_comparison_sub_expressions_not_supported)
{
    // Technically this is a mismatch, but we currently only want to support
    // the simple, obvious cases since this is not an expected use case.
    EXPECT_TRUE(might_match("id.user == (1 + 2)",
                            "id::testdoctype1:n=20:foo"));
}

TEST_F(GidFilterTest, composite_group_comparison_sub_expressions_not_supported)
{
    EXPECT_TRUE(might_match("id.group == 'foo'+'bar'",
                            "id::testdoctype1:g=sputnik_hits:foo"));
}

}
