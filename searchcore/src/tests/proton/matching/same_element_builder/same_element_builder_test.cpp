// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchcore/proton/matching/fakesearchcontext.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/same_element_builder.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/location.h>
#include <vespa/searchlib/query/tree/range.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>

using proton::matching::FakeSearchContext;
using proton::matching::ProtonLocationTerm;
using proton::matching::ProtonNumberTerm;
using proton::matching::ProtonPrefixTerm;
using proton::matching::ProtonRangeTerm;
using proton::matching::ProtonRegExpTerm;
using proton::matching::ProtonStringTerm;
using proton::matching::ProtonSubstringTerm;
using proton::matching::ProtonSuffixTerm;
using proton::matching::SameElementBuilder;
using proton::matching::ViewResolver;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::test::IndexEnvironment;
using search::query::Location;
using search::query::Range;
using search::query::Weight;
using search::queryeval::Blueprint;
using search::queryeval::EmptyBlueprint;
using search::queryeval::FakeBlueprint;
using search::queryeval::FakeRequestContext;
using search::queryeval::IntermediateBlueprint;
using search::queryeval::SameElementBlueprint;

using CollectionType = FieldInfo::CollectionType;

struct FakeTerms {
    ViewResolver        resolver;
    IndexEnvironment    idx_env;
    ProtonStringTerm    idx_string_term;
    ProtonStringTerm    attr_string_term;
    ProtonStringTerm    both_string_term;

    ProtonNumberTerm    idx_number_term;
    ProtonLocationTerm  idx_location_term;
    ProtonPrefixTerm    idx_prefix_term;
    ProtonRangeTerm     attr_range_term;
    ProtonSubstringTerm attr_substring_term;
    ProtonSuffixTerm    attr_suffix_term;
    ProtonRegExpTerm    attr_regexp_term;

    FakeTerms()
        : resolver(),
          idx_env(),
          idx_string_term("term", "idx", 1, Weight(1)),
          attr_string_term("term", "attr", 2, Weight(1)),
          both_string_term("term", "both", 3, Weight(1)),
          idx_number_term("term", "idx", 4, Weight(1)),
          idx_location_term(Location(), "idx", 5, Weight(1)),
          idx_prefix_term("term", "idx", 6, Weight(1)),
          attr_range_term(Range(), "attr", 7, Weight(1)),
          attr_substring_term("term", "attr", 8, Weight(1)),
          attr_suffix_term("term", "attr", 9, Weight(1)),
          attr_regexp_term("term", "attr", 10, Weight(1))
    {
        resolver.add("both", "idx");
        resolver.add("both", "attr");
        idx_env.getFields().emplace_back(FieldType::INDEX, CollectionType::ARRAY, "idx", 1);
        idx_env.getFields().emplace_back(FieldType::ATTRIBUTE, CollectionType::ARRAY, "attr", 2);
        idx_string_term.resolve(resolver, idx_env);
        attr_string_term.resolve(resolver, idx_env);
        both_string_term.resolve(resolver, idx_env);
        idx_number_term.resolve(resolver, idx_env);
        idx_location_term.resolve(resolver, idx_env);
        idx_prefix_term.resolve(resolver, idx_env);
        attr_range_term.resolve(resolver, idx_env);
        attr_substring_term.resolve(resolver, idx_env);
        attr_suffix_term.resolve(resolver, idx_env);
        attr_regexp_term.resolve(resolver, idx_env);
    }
};

struct BuilderFixture {
    FakeRequestContext req_ctx;
    FakeSearchContext  ctx;
    SameElementBuilder builder;
    BuilderFixture() : req_ctx(), ctx(), builder(req_ctx, ctx) {
        ctx.attr().tag("attr");
        ctx.addIdx(0).idx(0).getFake().tag("idx");
    }
};

const FakeBlueprint *as_fake(const Blueprint *bp) {
    const IntermediateBlueprint *parent = dynamic_cast<const IntermediateBlueprint*>(bp);
    if ((parent != nullptr) && (parent->childCnt() == 1)) {
        return as_fake(&parent->getChild(0));
    }
    return dynamic_cast<const FakeBlueprint*>(bp);    
}

void verify_children(Blueprint *bp, std::initializer_list<const char *> tags) {
    SameElementBlueprint *se = dynamic_cast<SameElementBlueprint*>(bp);
    ASSERT_TRUE(se != nullptr);
    ASSERT_EQUAL(se->terms().size(), tags.size());
    size_t idx = 0;
    for (const char *tag: tags) {
        const FakeBlueprint *fake = as_fake(se->terms()[idx++].get());
        ASSERT_TRUE(fake != nullptr);
        EXPECT_EQUAL(fake->tag(), tag);
    }
}

TEST_FF("require that same element blueprint can be built", BuilderFixture(), FakeTerms()) {
    f1.builder.add_child(f2.idx_string_term);
    f1.builder.add_child(f2.attr_string_term);
    Blueprint::UP result = f1.builder.build();
    TEST_DO(verify_children(result.get(), {"idx", "attr"}));
}

TEST_FF("require that terms searching multiple fields are ignored", BuilderFixture(), FakeTerms()) {
    f1.builder.add_child(f2.idx_string_term);
    f1.builder.add_child(f2.attr_string_term);
    f1.builder.add_child(f2.both_string_term); // ignored
    Blueprint::UP result = f1.builder.build();
    TEST_DO(verify_children(result.get(), {"idx", "attr"}));
}

TEST_FF("require that all relevant term types can be used", BuilderFixture(), FakeTerms()) {
    f1.builder.add_child(f2.idx_string_term);
    f1.builder.add_child(f2.idx_number_term);
    f1.builder.add_child(f2.idx_location_term);
    f1.builder.add_child(f2.idx_prefix_term);
    f1.builder.add_child(f2.attr_range_term);
    f1.builder.add_child(f2.attr_substring_term);
    f1.builder.add_child(f2.attr_suffix_term);
    f1.builder.add_child(f2.attr_regexp_term);
    Blueprint::UP result = f1.builder.build();
    TEST_DO(verify_children(result.get(), {"idx", "idx", "idx", "idx", "attr", "attr", "attr", "attr"}));
}

TEST_F("require that building same element with no children gives EmptyBlueprint", BuilderFixture()) {
    Blueprint::UP result = f1.builder.build();
    EXPECT_TRUE(dynamic_cast<EmptyBlueprint*>(result.get()) != nullptr);
}

TEST_MAIN() { TEST_RUN_ALL(); }
