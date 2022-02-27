// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/element_similarity_feature.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <initializer_list>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using CollectionType = FieldInfo::CollectionType;

const vespalib::string DEFAULT   = "elementSimilarity(foo)";
const vespalib::string PROXIMITY = "elementSimilarity(foo).proximity";
const vespalib::string ORDER     = "elementSimilarity(foo).order";
const vespalib::string QUERY     = "elementSimilarity(foo).query_coverage";
const vespalib::string FIELD     = "elementSimilarity(foo).field_coverage";
const vespalib::string WEIGHT    = "elementSimilarity(foo).weight";

FtIndex indexFoo() {
    FtIndex idx;
    idx.field("foo");
    return idx;
}

//-----------------------------------------------------------------------------

struct BlueprintFactoryFixture {
    BlueprintFactory factory;
    BlueprintFactoryFixture() : factory()
    {
        setup_search_features(factory);
    }
};

struct IndexFixture {
    IndexEnvironment indexEnv;
    IndexFixture() : indexEnv()
    {
        IndexEnvironmentBuilder builder(indexEnv);
        builder.addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "foo");
        builder.addField(FieldType::INDEX, CollectionType::ARRAY, "bar");
        builder.addField(FieldType::INDEX, CollectionType::SINGLE, "baz");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "fox");
        set("elementSimilarity(foo).output.proximity", "max(p)");
        set("elementSimilarity(foo).output.order", "max(o)");
        set("elementSimilarity(foo).output.query_coverage", "max(q)");
        set("elementSimilarity(foo).output.field_coverage", "max(f)");
        set("elementSimilarity(foo).output.weight", "max(w)");
        set("elementSimilarity(bar).output.default", "avg(1)");
    }
    IndexFixture &set(const vespalib::string &key, const vespalib::string &value) {
        Properties tmp;
        tmp.add(key, value);
        indexEnv.getProperties().import(tmp);
        return *this;
    }
};

struct FeatureDumpFixture : public IDumpFeatureVisitor {
    std::vector<vespalib::string> actual;
    FeatureDumpFixture() : IDumpFeatureVisitor(), actual() {}
    ~FeatureDumpFixture() override;
    virtual void visitDumpFeature(const vespalib::string &name) override {
        actual.push_back(name);
    }
};

FeatureDumpFixture::~FeatureDumpFixture() = default;

struct RankFixture : BlueprintFactoryFixture {
    RankFixture() : BlueprintFactoryFixture() {}
    double get_feature(const vespalib::string &query, const FtIndex &index, const vespalib::string &select,
                       const IndexFixture &idx_env = IndexFixture())
    {
        std::vector<vespalib::string> names({"elementSimilarity(foo).default", // use 'default' explicitly to verify default output name
                                                     "elementSimilarity(foo).proximity",
                                                     "elementSimilarity(foo).order",
                                                     "elementSimilarity(foo).query_coverage",
                                                     "elementSimilarity(foo).field_coverage",
                                                     "elementSimilarity(foo).weight"});
        FtFeatureTest ft(factory, names);
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "foo");
        ft.getIndexEnv().getBuilder().getIndexEnv().getProperties().import(idx_env.indexEnv.getProperties());
        FtTestApp::FT_SETUP(ft, FtUtil::toQuery(query), index, 1);
        {
            RankResult stale;
            EXPECT_TRUE(ft.executeOnly(stale, 2));
            EXPECT_EQUAL(0.0, stale.getScore(select));
        }
        RankResult actual;
        EXPECT_TRUE(ft.executeOnly(actual, 1));
        return actual.getScore(select);
    }
};

//-----------------------------------------------------------------------------

double prox(uint32_t dist) {
    return (dist > 8) ? 0 : (1.0 - (((dist-1)/8.0) * ((dist-1)/8.0)));
}

double sum(std::initializer_list<double> values) {
    double my_sum = 0.0;
    for (double value: values) {
        my_sum += value;
    }
    return my_sum;
}

double comb(std::initializer_list<double> values) {
    return (sum(values)/values.size());
}

double mix(double proximity, double order, double query, double field) {
    return (0.35 * proximity) + (0.15 * order) + (0.30 * query) + (0.20 * field);
}

//-----------------------------------------------------------------------------

template <typename A, typename B>
bool cmp_lists_impl(const A &a, const B &b) {
    std::vector<typename A::value_type> tmp_a(a.begin(), a.end());
    std::vector<typename B::value_type> tmp_b(b.begin(), b.end());
    std::sort(tmp_a.begin(), tmp_a.end());
    std::sort(tmp_b.begin(), tmp_b.end());
    if (!EXPECT_EQUAL(tmp_a.size(), tmp_b.size())) {
        return false;
    }
    for (size_t i = 0; i < tmp_a.size(); ++i) {
        if(!EXPECT_EQUAL(tmp_a[i], tmp_b[i])) {
            return false;
        }
    }
    return true;
}

template <typename T>
void dump_list(const vespalib::string &name, const T &list) {
    fprintf(stderr, "list(name: '%s', size: %zu)\n", name.c_str(), list.size());
    std::vector<typename T::value_type> tmp(list.begin(), list.end());
    std::sort(tmp.begin(), tmp.end());
    for (vespalib::string item: tmp) {
        fprintf(stderr, "  '%s'\n", item.c_str());        
    }
}

template <typename A, typename B>
bool cmp_lists(const A &a, const B &b) {
    if(!cmp_lists_impl(a, b)) {
        dump_list("expected", a);
        dump_list("actual", b);
        return false;
    }
    return true;
};

//-----------------------------------------------------------------------------

TEST_F("require that blueprint can be created from factory", BlueprintFactoryFixture) {
    Blueprint::SP bp = f.factory.createBlueprint("elementSimilarity");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<ElementSimilarityBlueprint*>(bp.get()) != 0);
}

TEST_FFF("require that appropriate features are dumped", ElementSimilarityBlueprint, IndexFixture, FeatureDumpFixture) {
    f1.visitDumpFeatures(f2.indexEnv, f3);
    EXPECT_TRUE(cmp_lists(std::vector<vespalib::string>({"elementSimilarity(foo)",
                                "elementSimilarity(foo).proximity",
                                "elementSimilarity(foo).order",
                                "elementSimilarity(foo).query_coverage",
                                "elementSimilarity(foo).field_coverage",
                                "elementSimilarity(foo).weight",
                                "elementSimilarity(bar)"}),
                          f3.actual));
}

bool try_setup(ElementSimilarityBlueprint &blueprint, const IndexFixture &index, const vespalib::string &field) {
    DummyDependencyHandler deps(blueprint);
    blueprint.setName(vespalib::make_string("%s(%s)", blueprint.getBaseName().c_str(), field.c_str()));
    return ((Blueprint&)blueprint).setup(index.indexEnv, std::vector<vespalib::string>(1, field));    
}

TEST_FF("require that setup can be done on weighted set index field", ElementSimilarityBlueprint, IndexFixture) {
    EXPECT_TRUE(try_setup(f1, f2, "foo"));
}

TEST_FF("require that setup can be done on array index field", ElementSimilarityBlueprint, IndexFixture) {
    EXPECT_TRUE(try_setup(f1, f2, "bar"));
}

TEST_FF("require that setup can be done on single value index field", ElementSimilarityBlueprint, IndexFixture) {
    EXPECT_TRUE(try_setup(f1, f2, "baz"));
}

TEST_FF("require that setup can not be done on single value attribute field", ElementSimilarityBlueprint, IndexFixture) {
    EXPECT_FALSE(try_setup(f1, f2, "fox"));
}

TEST_FF("require that setup will fail if output expression does not contain an aggregator", ElementSimilarityBlueprint, IndexFixture) {
    f2.set("elementSimilarity(foo).output.default", "p");
    EXPECT_FALSE(try_setup(f1, f2, "foo"));
}

TEST_FF("require that setup will fail if output expression contains an unknown aggregator", ElementSimilarityBlueprint, IndexFixture) {
    f2.set("elementSimilarity(foo).output.default", "bogus(p)");
    EXPECT_FALSE(try_setup(f1, f2, "foo"));
}

TEST_FF("require that setup will fail if output expression contains an unknown symbol", ElementSimilarityBlueprint, IndexFixture) {
    f2.set("elementSimilarity(foo).output.default", "max(bogus)");
    EXPECT_FALSE(try_setup(f1, f2, "foo"));
}

TEST_FF("require that setup will fail if output expression is malformed", ElementSimilarityBlueprint, IndexFixture) {
    f2.set("elementSimilarity(foo).output.default", "max(w+)");
    EXPECT_FALSE(try_setup(f1, f2, "foo"));
}

TEST_F("require that no match gives zero outputs", RankFixture) {
    EXPECT_EQUAL(0.0, f1.get_feature("x", indexFoo().element("y"), DEFAULT));
    EXPECT_EQUAL(0.0, f1.get_feature("x", indexFoo().element("y"), PROXIMITY));
    EXPECT_EQUAL(0.0, f1.get_feature("x", indexFoo().element("y"), ORDER));
    EXPECT_EQUAL(0.0, f1.get_feature("x", indexFoo().element("y"), QUERY));
    EXPECT_EQUAL(0.0, f1.get_feature("x", indexFoo().element("y"), FIELD));
}

TEST_F("require that minal perfect match gives max outputs", RankFixture) {
    EXPECT_EQUAL(1.0, f1.get_feature("x", indexFoo().element("x"), DEFAULT));
    EXPECT_EQUAL(1.0, f1.get_feature("x", indexFoo().element("x"), PROXIMITY));
    EXPECT_EQUAL(1.0, f1.get_feature("x", indexFoo().element("x"), ORDER));
    EXPECT_EQUAL(1.0, f1.get_feature("x", indexFoo().element("x"), QUERY));
    EXPECT_EQUAL(1.0, f1.get_feature("x", indexFoo().element("x"), FIELD));
}

TEST_F("require that larger perfect match gives max outputs", RankFixture) {
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), DEFAULT));
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), PROXIMITY));
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), ORDER));
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), QUERY));
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e f g", indexFoo().element("a b c d e f g"), FIELD));
}

TEST_F("require that extra query terms reduces order but not proximity", RankFixture) {
    EXPECT_EQUAL(1.0, f1.get_feature("x y", indexFoo().element("x"), PROXIMITY));
    EXPECT_EQUAL(1.0, f1.get_feature("x y y", indexFoo().element("x"), PROXIMITY));
    EXPECT_EQUAL(1.0, f1.get_feature("x y y y", indexFoo().element("x"), PROXIMITY));

    EXPECT_EQUAL(0.0, f1.get_feature("x y", indexFoo().element("x"), ORDER));
    EXPECT_EQUAL(0.0, f1.get_feature("x y y", indexFoo().element("x"), ORDER));
    EXPECT_EQUAL(0.0, f1.get_feature("x y y y", indexFoo().element("x"), ORDER));
}

TEST_F("require that extra field terms reduces proximity but not order", RankFixture) {
    EXPECT_EQUAL(prox(2), f1.get_feature("x", indexFoo().element("x y"), PROXIMITY));
    EXPECT_EQUAL(prox(3), f1.get_feature("x", indexFoo().element("x y y"), PROXIMITY));
    EXPECT_EQUAL(prox(4), f1.get_feature("x", indexFoo().element("x y y y"), PROXIMITY));

    EXPECT_EQUAL(1.0, f1.get_feature("x", indexFoo().element("x y"), ORDER));
    EXPECT_EQUAL(1.0, f1.get_feature("x", indexFoo().element("x y y"), ORDER));
    EXPECT_EQUAL(1.0, f1.get_feature("x", indexFoo().element("x y y y"), ORDER));
}

TEST_F("require that proximity acts as expected", RankFixture) {
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(2), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("a x b c d e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(3), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("a x x b c d e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(4), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("a x x x b c d e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(2), prox(2), prox(2), prox(2)}), f1.get_feature("a b c d e", indexFoo().element("a x b x c x d x e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(2), prox(2), prox(1), prox(3)}), f1.get_feature("a b c d e", indexFoo().element("a x b x c d x x e"), PROXIMITY));
}

TEST_F("require that field order does not affect proximity score", RankFixture) {
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e", indexFoo().element("d c a b e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(2), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("d x c a b e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(3), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("d x x c a b e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(4), prox(1), prox(1), prox(1)}), f1.get_feature("a b c d e", indexFoo().element("d x x x c a b e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(2), prox(2), prox(2), prox(2)}), f1.get_feature("a b c d e", indexFoo().element("d x c x a x b x e"), PROXIMITY));
    EXPECT_EQUAL(comb({prox(2), prox(2), prox(1), prox(3)}), f1.get_feature("a b c d e", indexFoo().element("d x c x a b x x e"), PROXIMITY));
}

TEST_F("require that order score acts as expected", RankFixture) {
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), ORDER));
    EXPECT_EQUAL(comb({1.0, 1.0, 1.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("a b c e d"), ORDER));
    EXPECT_EQUAL(comb({0.0, 1.0, 1.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("b a c e d"), ORDER));
    EXPECT_EQUAL(comb({0.0, 1.0, 0.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("b a e d c"), ORDER));
    EXPECT_EQUAL(comb({0.0, 0.0, 0.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("e d c b a"), ORDER));
}

TEST_F("require that proximity does not affect order score", RankFixture) {
    EXPECT_EQUAL(1.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), ORDER));
    EXPECT_EQUAL(comb({1.0, 1.0, 1.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("a x b x c x e x d"), ORDER));
    EXPECT_EQUAL(comb({0.0, 1.0, 1.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("b x a x c x e x d"), ORDER));
    EXPECT_EQUAL(comb({0.0, 1.0, 0.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("b x a x e x d x c"), ORDER));
    EXPECT_EQUAL(comb({0.0, 0.0, 0.0, 0.0}), f1.get_feature("a b c d e", indexFoo().element("e x d x c x b x a"), ORDER));
}

TEST_F("require that query coverage acts as expected", RankFixture) {
    EXPECT_EQUAL(5.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), QUERY));
    EXPECT_EQUAL(4.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b c d"), QUERY));
    EXPECT_EQUAL(3.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b c"), QUERY));
    EXPECT_EQUAL(2.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b"), QUERY));
    EXPECT_EQUAL(4.0/7.0, f1.get_feature("a!200 b!200 c d e", indexFoo().element("a b"), QUERY));
    EXPECT_EQUAL(2.0/7.0, f1.get_feature("a b c!500", indexFoo().element("a b"), QUERY));
    EXPECT_EQUAL(5.0/7.0, f1.get_feature("a b c!500", indexFoo().element("c"), QUERY));
}

TEST_F("require that field coverage acts as expected", RankFixture) {
    EXPECT_EQUAL(5.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b c d e"), FIELD));
    EXPECT_EQUAL(4.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a x c d e"), FIELD));
    EXPECT_EQUAL(3.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a b x x e"), FIELD));
    EXPECT_EQUAL(2.0/5.0, f1.get_feature("a b c d e", indexFoo().element("x x x d e"), FIELD));
}

TEST_F("require that first unique match is used per query term", RankFixture) {
    EXPECT_EQUAL(prox(3), f1.get_feature("a b", indexFoo().element("a a a b"), PROXIMITY));
    EXPECT_EQUAL(1.0, f1.get_feature("a b", indexFoo().element("a a a b"), ORDER));
    EXPECT_EQUAL(1.0, f1.get_feature("a b", indexFoo().element("a a a b"), QUERY));
    EXPECT_EQUAL(2.0/4.0, f1.get_feature("a b", indexFoo().element("a a a b"), FIELD));

    EXPECT_EQUAL(comb({prox(1), prox(2)}), f1.get_feature("a b a", indexFoo().element("a a a b"), PROXIMITY));
    EXPECT_EQUAL(0.5, f1.get_feature("a b a", indexFoo().element("a a a b"), ORDER));
    EXPECT_EQUAL(1.0, f1.get_feature("a b a", indexFoo().element("a a a b"), QUERY));
    EXPECT_EQUAL(3.0/4.0, f1.get_feature("a b a", indexFoo().element("a a a b"), FIELD));
}

TEST_F("require that default score combines individual signals appropriately", RankFixture) {
    EXPECT_EQUAL(comb({prox(1), prox(3), prox(2)}), f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), PROXIMITY));
    EXPECT_EQUAL(comb({1.0, 0.0, 1.0}), f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), ORDER));
    EXPECT_EQUAL(4.0/5.0, f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), QUERY));
    EXPECT_EQUAL(4.0/7.0, f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), FIELD));
    EXPECT_EQUAL(mix(comb({prox(1), prox(3), prox(2)}), comb({1.0, 0.0, 1.0}), 4.0/5.0, 4.0/7.0),
                 f1.get_feature("a b c d e", indexFoo().element("a c x x b x d"), DEFAULT));
    EXPECT_EQUAL(7.0 * mix(comb({prox(1), prox(3), prox(2)}), comb({1.0, 0.0, 1.0}), 4.0/5.0, 4.0/7.0),
                 f1.get_feature("a b c d e", indexFoo().element("a c x x b x d", 7), DEFAULT));
}

TEST_FF("require that max aggregation works", RankFixture, IndexFixture) {
    f2.set("elementSimilarity(foo).output.default", "max(w)");
    EXPECT_EQUAL(5.0, f1.get_feature("x", indexFoo().element("x y", 5), DEFAULT, f2));
    EXPECT_EQUAL(5.0, f1.get_feature("x", indexFoo().element("x y", 5).element("x y", 3), DEFAULT, f2));
    EXPECT_EQUAL(5.0, f1.get_feature("x", indexFoo().element("x y", 3).element("x y", 5), DEFAULT, f2));
}

TEST_FF("require that avg aggregation works", RankFixture, IndexFixture) {
    f2.set("elementSimilarity(foo).output.default", "avg(w)");
    EXPECT_EQUAL(5.0, f1.get_feature("x", indexFoo().element("x y", 5), DEFAULT, f2));
    EXPECT_EQUAL(4.0, f1.get_feature("x", indexFoo().element("x y", 5).element("x y", 3), DEFAULT, f2));
    EXPECT_EQUAL(4.0, f1.get_feature("x", indexFoo().element("x y", 3).element("x y", 5), DEFAULT, f2));
}

TEST_FF("require that sum aggregation works", RankFixture, IndexFixture) {
    f2.set("elementSimilarity(foo).output.default", "sum(w)");
    EXPECT_EQUAL(5.0, f1.get_feature("x", indexFoo().element("x y", 5), DEFAULT, f2));
    EXPECT_EQUAL(8.0, f1.get_feature("x", indexFoo().element("x y", 5).element("x y", 3), DEFAULT, f2));
    EXPECT_EQUAL(8.0, f1.get_feature("x", indexFoo().element("x y", 3).element("x y", 5), DEFAULT, f2));
}

TEST_FF("require that element demultiplexing works", RankFixture, IndexFixture) {
    f2.set("elementSimilarity(foo).output.default", "sum(q)");
    EXPECT_EQUAL(sum({0.25, 0.5, 0.5, 0.25, 0.5}),
                 f1.get_feature("x y z t", indexFoo()
                                .element("x")
                                .element("x y")
                                .element("x z")
                                .element("y")
                                .element("x z"), DEFAULT, f2));
}

TEST_MAIN() { TEST_RUN_ALL(); }
