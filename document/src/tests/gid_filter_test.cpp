// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright 2016 Yahoo! Technologies Norway AS

#include <cppunit/TestFixture.h>
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/document/select/gid_filter.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/base/testdocrepo.h>

namespace document {
namespace select {

class GidFilterTest : public CppUnit::TestFixture {
    struct Fixture {
        TestDocRepo _repo;
        BucketIdFactory _id_factory;
        std::unique_ptr<Node> _root;
        
        Fixture(vespalib::stringref selection);
        ~Fixture();

        Fixture(Fixture&&) = default;
        Fixture& operator=(Fixture&&) = default;

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

    CPPUNIT_TEST_SUITE(GidFilterTest);
    CPPUNIT_TEST(same_user_for_selection_and_gid_returns_match);
    CPPUNIT_TEST(differing_user_for_selection_and_gid_returns_mismatch);
    CPPUNIT_TEST(user_location_constraint_is_order_invariant);
    CPPUNIT_TEST(non_location_selection_always_matches);
    CPPUNIT_TEST(location_selection_does_not_match_non_location_id);
    CPPUNIT_TEST(simple_conjunctive_location_expressions_are_filtered);
    CPPUNIT_TEST(complex_conjunctive_location_expressions_are_filtered);
    CPPUNIT_TEST(simple_disjunctive_location_expressions_are_not_filtered);
    CPPUNIT_TEST(complex_disjunctive_location_expressions_are_not_filtered);
    CPPUNIT_TEST(non_location_id_comparisons_are_not_filtered);
    CPPUNIT_TEST(unsupported_location_comparison_operands_not_filtered);
    CPPUNIT_TEST(default_constructed_filter_always_matches);
    CPPUNIT_TEST(most_significant_32_bits_are_ignored);
    CPPUNIT_TEST(gid_filters_may_be_copy_constructed);
    CPPUNIT_TEST(gid_filters_may_be_copy_assigned);
    CPPUNIT_TEST(same_group_for_selection_and_gid_returns_match);
    CPPUNIT_TEST(differing_group_for_selection_and_gid_returns_mismatch);
    CPPUNIT_TEST(composite_user_comparison_sub_expressions_not_supported);
    CPPUNIT_TEST(composite_group_comparison_sub_expressions_not_supported);
    CPPUNIT_TEST_SUITE_END();
public:
    void same_user_for_selection_and_gid_returns_match();
    void differing_user_for_selection_and_gid_returns_mismatch();
    void user_location_constraint_is_order_invariant();
    void non_location_selection_always_matches();
    void location_selection_does_not_match_non_location_id();
    void simple_conjunctive_location_expressions_are_filtered();
    void complex_conjunctive_location_expressions_are_filtered();
    void simple_disjunctive_location_expressions_are_not_filtered();
    void complex_disjunctive_location_expressions_are_not_filtered();
    void non_location_id_comparisons_are_not_filtered();
    void unsupported_location_comparison_operands_not_filtered();
    void default_constructed_filter_always_matches();
    void most_significant_32_bits_are_ignored();
    void gid_filters_may_be_copy_constructed();
    void gid_filters_may_be_copy_assigned();
    void same_group_for_selection_and_gid_returns_match();
    void differing_group_for_selection_and_gid_returns_mismatch();
    void composite_user_comparison_sub_expressions_not_supported();
    void composite_group_comparison_sub_expressions_not_supported();
};

GidFilterTest::Fixture::Fixture(vespalib::stringref selection)
    : _repo(),
      _id_factory(),
      _root(Parser(_repo.getTypeRepo(), _id_factory).parse(selection))
{ }
GidFilterTest::Fixture::~Fixture() { }

CPPUNIT_TEST_SUITE_REGISTRATION(GidFilterTest);

void
GidFilterTest::same_user_for_selection_and_gid_returns_match()
{
    CPPUNIT_ASSERT(might_match("id.user == 12345",
                               "id::testdoctype1:n=12345:foo"));
    // User locations are defined over [0, 2**63-1]
    CPPUNIT_ASSERT(might_match("id.user == 0",
                               "id::testdoctype1:n=0:foo"));
    CPPUNIT_ASSERT(might_match("id.user == 9223372036854775807" ,
                               "id::testdoctype1:n=9223372036854775807:foo"));
}

void
GidFilterTest::differing_user_for_selection_and_gid_returns_mismatch()
{
    CPPUNIT_ASSERT(!might_match("id.user == 1", "id::testdoctype1:n=2000:foo"));
    // Similar, but non-identical, bit patterns
    CPPUNIT_ASSERT(!might_match("id.user == 12345",
                                "id::testdoctype1:n=12346:foo"));
    CPPUNIT_ASSERT(!might_match("id.user == 12345",
                                "id::testdoctype1:n=12344:foo"));
}

void
GidFilterTest::user_location_constraint_is_order_invariant()
{
    CPPUNIT_ASSERT(might_match("12345 == id.user",
                               "id::testdoctype1:n=12345:foo"));
    CPPUNIT_ASSERT(!might_match("12345 == id.user",
                                "id::testdoctype1:n=12346:foo"));
}

void
GidFilterTest::non_location_selection_always_matches()
{
    CPPUNIT_ASSERT(might_match("testdoctype1.headerval == 67890",
                               "id::testdoctype1:n=12345:foo"));
}

void
GidFilterTest::location_selection_does_not_match_non_location_id()
{
    // Test name is a half-truth; the MD5-derived ID _will_ give a false
    // positive every 2**32 or so document ID when the stars and their bit
    // patterns align :)
    CPPUNIT_ASSERT(!might_match("id.user == 987654321",
                                "id::testdoctype1::foo"));

    CPPUNIT_ASSERT(!might_match("id.group == 'snusmumrikk'",
                                "id::testdoctype1::foo"));
}

void
GidFilterTest::simple_conjunctive_location_expressions_are_filtered()
{
    // A conjunctive expression in this context is one where there exist a
    // location predicate and the result of the entire expression can only
    // be true iff the location predicate matches.
    CPPUNIT_ASSERT(might_match("id.user == 12345 and true",
                               "id::testdoctype1:n=12345:bar"));
    CPPUNIT_ASSERT(might_match("true and id.user == 12345",
                               "id::testdoctype1:n=12345:bar"));

    CPPUNIT_ASSERT(!might_match("id.user == 123456 and true",
                                "id::testdoctype1:n=12345:bar"));
    CPPUNIT_ASSERT(!might_match("true and id.user == 123456",
                                "id::testdoctype1:n=12345:bar"));
}

void
GidFilterTest::complex_conjunctive_location_expressions_are_filtered()
{
    CPPUNIT_ASSERT(might_match("(((testdoctype1.headerval < 5) and (1 != 2)) "
                               "and id.user == 12345)",
                               "id::testdoctype1:n=12345:bar"));
    CPPUNIT_ASSERT(!might_match("(((1 != 2) and (id.user==12345)) and "
                                "(2 != 3)) and (testdoctype1.headerval < 5)",
                                "id::testdoctype1:n=23456:bar"));
    // In this case the expression contains a disjunction but the outcome
    // of evaluating it still strongly depends on the location predicate.
    CPPUNIT_ASSERT(might_match("((id.user == 12345 and true) and "
                               "(true or false))",
                               "id::testdoctype1:n=12345:bar"));
    CPPUNIT_ASSERT(!might_match("((id.user == 12345 and true) and "
                                "(true or false))",
                                "id::testdoctype1:n=23456:bar"));
}

void
GidFilterTest::simple_disjunctive_location_expressions_are_not_filtered()
{
    // Documents mismatch location but match selection as a whole.
    CPPUNIT_ASSERT(might_match("id.user == 12345 or true",
                               "id::testdoctype1:n=12345678:bar"));
    CPPUNIT_ASSERT(might_match("true or id.user == 12345",
                               "id::testdoctype1:n=12345678:bar"));
}

void
GidFilterTest::complex_disjunctive_location_expressions_are_not_filtered()
{
    CPPUNIT_ASSERT(might_match("((id.user == 12345) and true) or false",
                               "id::testdoctype1:n=12345678:bar"));
    CPPUNIT_ASSERT(might_match("((id.user == 12345) or false) and true",
                               "id::testdoctype1:n=12345678:bar"));
}

void
GidFilterTest::non_location_id_comparisons_are_not_filtered()
{
    // Note: these selections are syntactically valid but semantically
    // invalid (comparing strings to integers), but are used to catch any
    // logic holes where an id node is indiscriminately treated as something
    // from which we should derive a GID-related integer.
    CPPUNIT_ASSERT(might_match("id.namespace == 123456",
                               "id::testdoctype1:n=12345678:bar"));
    CPPUNIT_ASSERT(might_match("id.type == 1234",
                               "id::testdoctype1:n=12345678:bar"));
    CPPUNIT_ASSERT(might_match("id.scheme == 555",
                               "id::testdoctype1:n=12345678:bar"));
    CPPUNIT_ASSERT(might_match("id.specific == 7654",
                               "id::testdoctype1:n=12345678:bar"));
}

void
GidFilterTest::unsupported_location_comparison_operands_not_filtered()
{
    CPPUNIT_ASSERT(might_match("id.user == 'rick & morty'",
                               "id::testdoctype1:n=12345678:bar"));
    CPPUNIT_ASSERT(might_match("id.group == 56789",
                               "id::testdoctype1:n=12345678:bar"));
    CPPUNIT_ASSERT(might_match("id.user == testdoctype1.headervalue",
                               "id::testdoctype1:n=12345678:bar"));
    CPPUNIT_ASSERT(might_match("id.group == testdoctype1.headervalue",
                               "id::testdoctype1:g=helloworld:bar"));
}

void
GidFilterTest::default_constructed_filter_always_matches()
{
    GidFilter filter;
    CPPUNIT_ASSERT(filter.gid_might_match_selection(
            DocumentId("id::testdoctype1:n=12345678:bar").getGlobalId()));
    CPPUNIT_ASSERT(filter.gid_might_match_selection(
            DocumentId("id::testdoctype1::foo").getGlobalId()));
}

void
GidFilterTest::most_significant_32_bits_are_ignored()
{
    // The fact that the 32 MSB are effectively ignored is an artifact of
    // how the GID location extraction is historically performed and is not
    // necessarily the optimum (in particular, an XOR combination of the upper
    // and lower 32 bits would likely be much better), but it's what the
    // behavior currently is and should thus be tested.

    // The following locations have the same 32 LSB:
    CPPUNIT_ASSERT(might_match("id.user == 12345678901",
                               "id::testdoctype1:n=29525548085:bar"));
}

void
GidFilterTest::gid_filters_may_be_copy_constructed()
{
    Fixture f("id.user == 1337");
    GidFilter filter = GidFilter::for_selection_root_node(*f._root);

    GidFilter copy_constructed(filter);
    CPPUNIT_ASSERT(copy_constructed.gid_might_match_selection(
            id_to_gid("id::testdoctype1:n=1337:zoid")));
    CPPUNIT_ASSERT(!copy_constructed.gid_might_match_selection(
            id_to_gid("id::testdoctype1:n=555:zoid")));

}

void
GidFilterTest::gid_filters_may_be_copy_assigned()
{
    Fixture f("id.user == 1337");
    GidFilter filter = GidFilter::for_selection_root_node(*f._root);

    GidFilter copy_assigned;
    copy_assigned = filter;

    CPPUNIT_ASSERT(copy_assigned.gid_might_match_selection(
            id_to_gid("id::testdoctype1:n=1337:zoid")));
    CPPUNIT_ASSERT(!copy_assigned.gid_might_match_selection(
            id_to_gid("id::testdoctype1:n=555:zoid")));
}

void
GidFilterTest::same_group_for_selection_and_gid_returns_match()
{
    CPPUNIT_ASSERT(might_match("id.group == 'bjarne'",
                               "id::testdoctype1:g=bjarne:foo"));
    CPPUNIT_ASSERT(might_match("id.group == 'andrei'",
                               "id::testdoctype1:g=andrei:bar"));
}

void
GidFilterTest::differing_group_for_selection_and_gid_returns_mismatch()
{
    CPPUNIT_ASSERT(!might_match("id.group == 'cult of bjarne'",
                                "id::testdoctype1:g=stl:foo"));
    CPPUNIT_ASSERT(!might_match("id.group == 'sutters mill'",
                                "id::testdoctype1:g=andrei:bar"));
}

void
GidFilterTest::composite_user_comparison_sub_expressions_not_supported()
{
    // Technically this is a mismatch, but we currently only want to support
    // the simple, obvious cases since this is not an expected use case.
    CPPUNIT_ASSERT(might_match("id.user == (1 + 2)",
                               "id::testdoctype1:n=20:foo"));
}

void
GidFilterTest::composite_group_comparison_sub_expressions_not_supported()
{
    CPPUNIT_ASSERT(might_match("id.group == 'foo'+'bar'",
                               "id::testdoctype1:g=sputnik_hits:foo"));
}

} // select
} // document
