// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/attribute/singlenumericattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericpostattribute.hpp>
#include <vespa/searchlib/query/tree/location.h>
#include <vespa/searchlib/query/tree/point.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("attributeblueprint_test");

using search::AttributeGuard;
using search::AttributeVector;
using search::IAttributeManager;
using search::SingleStringExtAttribute;
using search::attribute::IAttributeContext;
using search::fef::MatchData;
using search::fef::TermFieldMatchData;
using search::query::Location;
using search::query::Node;
using search::query::Point;
using search::query::SimpleLocationTerm;
using search::query::SimplePrefixTerm;
using search::query::SimpleStringTerm;
using search::query::Weight;
using search::queryeval::Blueprint;
using search::queryeval::EmptyBlueprint;
using search::queryeval::FieldSpec;
using search::queryeval::NearestNeighborBlueprint;
using search::queryeval::SearchIterator;
using search::queryeval::FakeRequestContext;
using std::string;
using std::vector;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::tensor::DefaultTensorEngine;
using namespace search::attribute;
using namespace search;

namespace {

const string field = "field";
const int32_t weight = 1;

class MyAttributeManager : public IAttributeManager {
    AttributeVector::SP _attribute_vector;

public:
    MyAttributeManager(MyAttributeManager && rhs) :
        IAttributeManager(),
        _attribute_vector(std::move(rhs._attribute_vector))
    {
    }
    MyAttributeManager(AttributeVector *attr)
        : _attribute_vector(attr)
    {
    }

    MyAttributeManager(AttributeVector::SP attr)
        : _attribute_vector(std::move(attr))
    {
    }

    AttributeGuard::UP getAttribute(const string &) const override {
        return AttributeGuard::UP(new AttributeGuard(_attribute_vector));
    }

    std::unique_ptr<attribute::AttributeReadGuard>
    getAttributeReadGuard(const string &, bool stableEnumGuard) const override {
        if (_attribute_vector) {
            return _attribute_vector->makeReadGuard(stableEnumGuard);
        } else {
            return std::unique_ptr<attribute::AttributeReadGuard>();
        }
    }
    void getAttributeList(vector<AttributeGuard> &) const override {
        assert(!"Not implemented");
    }
    IAttributeContext::UP createContext() const override {
        assert(!"Not implemented");
        return IAttributeContext::UP();
    }

    void asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor>) const override {
        assert(!"Not implemented");
    }
};

constexpr uint32_t DOCID_LIMIT = 3;

bool search(const Node &node, IAttributeManager &attribute_manager, bool expect_attribute_search_context = true) {
    AttributeContext ac(attribute_manager);
    FakeRequestContext requestContext(&ac);
    MatchData::UP md(MatchData::makeTestInstance(1, 1));
    AttributeBlueprintFactory source;
    Blueprint::UP result = source.createBlueprint(requestContext, FieldSpec(field, 0, 0), node);
    ASSERT_TRUE(result.get());
    EXPECT_TRUE(!result->getState().estimate().empty);
    EXPECT_EQUAL(3u, result->getState().estimate().estHits);
    if (expect_attribute_search_context) {
        EXPECT_TRUE(result->get_attribute_search_context() != nullptr);
    } else {
        EXPECT_TRUE(result->get_attribute_search_context() == nullptr);
    }
    result->fetchPostings(true);
    result->setDocIdLimit(DOCID_LIMIT);
    SearchIterator::UP iterator = result->createSearch(*md, true);
    ASSERT_TRUE((bool)iterator);
    iterator->initRange(1, 3);
    EXPECT_TRUE(!iterator->seek(1));
    return iterator->seek(2);
}

bool search(const string &term, IAttributeManager &attribute_manager) {
    TEST_STATE(term.c_str());
    SimpleStringTerm node(term, "field", 0, Weight(0));
    bool ret = search(node, attribute_manager);
    return ret;
}

template <typename T> struct AttributeVectorTypeFinder {
    typedef SingleStringExtAttribute Type;
    static void add(Type & a, const T & v) { a.add(v, weight); }
};
template <> struct AttributeVectorTypeFinder<int64_t> {
    typedef search::SingleValueNumericAttribute<search::IntegerAttributeTemplate<int64_t> > Type;
    static void add(Type & a, int64_t v) { a.set(a.getNumDocs()-1, v); a.commit(); }
};

struct FastSearchLongAttribute {
    typedef search::SingleValueNumericPostingAttribute< search::EnumAttribute<search::IntegerAttributeTemplate<int64_t> > > Type;
    static void add(Type & a, int64_t v) { a.update(a.getNumDocs()-1, v); a.commit(); }
};

template <typename AT, typename T>
MyAttributeManager fill(typename AT::Type * attr, T value) {
    AttributeVector::DocId docid;
    attr->addDoc(docid);
    attr->addDoc(docid);
    attr->addDoc(docid);
    assert(DOCID_LIMIT-1 == docid);
    AT::add(*attr, value);
    return MyAttributeManager(attr);
}

template <typename T>
MyAttributeManager makeAttributeManager(T value) {
    typedef AttributeVectorTypeFinder<T> AT;
    typedef typename AT::Type AttributeVectorType;
    AttributeVectorType *attr = new AttributeVectorType(field);
    return fill<AT, T>(attr, value);
}

MyAttributeManager makeFastSearchLongAttribute(int64_t value) {
    typedef FastSearchLongAttribute::Type AttributeVectorType;
    Config cfg(BasicType::fromType(int64_t()), CollectionType::SINGLE);
    cfg.setFastSearch(true);
    AttributeVectorType *attr = new AttributeVectorType(field, cfg);
    return fill<FastSearchLongAttribute, int64_t>(attr, value);
}

}  // namespace

TEST("requireThatIteratorsCanBeCreated") {
    MyAttributeManager attribute_manager = makeAttributeManager("foo");

    EXPECT_TRUE(search("foo", attribute_manager));
}

TEST("requireThatRangeTermsWorkToo") {
    MyAttributeManager attribute_manager = makeAttributeManager(int64_t(42));

    EXPECT_TRUE(search("[23;46]", attribute_manager));
    EXPECT_TRUE(!search("[10;23]", attribute_manager));
    EXPECT_TRUE(!search(">43", attribute_manager));
    EXPECT_TRUE(search("[10;]", attribute_manager));
}

TEST("requireThatPrefixTermsWork")
{
    MyAttributeManager attribute_manager = makeAttributeManager("foo");

    SimplePrefixTerm node("fo", "field", 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager));
}

TEST("requireThatLocationTermsWork") {
    // 0xcc is z-curve for (10, 10).
    MyAttributeManager attribute_manager = makeAttributeManager(int64_t(0xcc));

    SimpleLocationTerm node(Location(Point(10, 10), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager, false));
    node = SimpleLocationTerm(Location(Point(100, 100), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(!search(node, attribute_manager, false));
    node = SimpleLocationTerm(Location(Point(13, 13), 4, 0), field, 0, Weight(0));
    EXPECT_TRUE(!search(node, attribute_manager, false));
    node = SimpleLocationTerm(Location(Point(10, 13), 3, 0), field, 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager, false));
}

TEST("requireThatFastSearchLocationTermsWork") {
    // 0xcc is z-curve for (10, 10).
    MyAttributeManager attribute_manager = makeFastSearchLongAttribute(int64_t(0xcc));

    SimpleLocationTerm node(Location(Point(10, 10), 3, 0), field, 0, Weight(0));
#if 0
    EXPECT_TRUE(search(node, attribute_manager));
    node = SimpleLocationTerm(Location(Point(100, 100), 3, 0),field, 0, Weight(0));
    EXPECT_TRUE(!search(node, attribute_manager));
    node = SimpleLocationTerm(Location(Point(13, 13), 4, 0),field, 0, Weight(0));
    EXPECT_TRUE(!search(node, attribute_manager));
    node = SimpleLocationTerm(Location(Point(10, 13), 3, 0),field, 0, Weight(0));
    EXPECT_TRUE(search(node, attribute_manager));
#endif
}

AttributeVector::SP
make_tensor_attribute(const vespalib::string& name, const vespalib::string& tensor_spec)
{
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    cfg.setTensorType(ValueType::from_spec(tensor_spec));
    return AttributeFactory::createAttribute(name, cfg);
}

AttributeVector::SP
make_int_attribute(const vespalib::string& name)
{
    Config cfg(BasicType::INT32, CollectionType::SINGLE);
    return AttributeFactory::createAttribute(name, cfg);
}

template <typename BlueprintType>
const BlueprintType*
as_type(const Blueprint& blueprint)
{
    const auto* result = dynamic_cast<const BlueprintType*>(&blueprint);
    ASSERT_TRUE(result != nullptr);
    return result;
}

class NearestNeighborFixture {
public:
    MyAttributeManager mgr;
    vespalib::string attr_name;
    AttributeContext attr_ctx;
    FakeRequestContext request_ctx;
    AttributeBlueprintFactory source;

    NearestNeighborFixture(AttributeVector::SP attr)
        : mgr(attr),
          attr_name(attr->getName()),
          attr_ctx(mgr),
          request_ctx(&attr_ctx),
          source()
    {
    }
    void set_query_tensor(const TensorSpec& tensor_spec) {
        request_ctx.set_query_tensor("query_tensor", tensor_spec);
    }
    Blueprint::UP create_blueprint() {
        query::NearestNeighborTerm term("query_tensor", attr_name, 0, Weight(0), 7);
        return source.createBlueprint(request_ctx, FieldSpec(attr_name, 0, 0), term);
    }
};

TEST_F("nearest neighbor blueprint is created by attribute blueprint factory",
       NearestNeighborFixture(make_tensor_attribute(field, "tensor(x[2])")))
{
    TensorSpec dense_x_2 = TensorSpec("tensor(x[2])").add({{"x", 0}}, 3).add({{"x", 1}}, 5);
    f.set_query_tensor(dense_x_2);

    auto result = f.create_blueprint();
    const auto* nearest = as_type<NearestNeighborBlueprint>(*result);
    EXPECT_EQUAL("tensor(x[2])", nearest->get_attribute_tensor().getTensorType().to_spec());
    EXPECT_EQUAL(dense_x_2, DefaultTensorEngine::ref().to_spec(nearest->get_query_tensor()));
    EXPECT_EQUAL(7u, nearest->get_target_num_hits());
}

void
expect_empty_blueprint(AttributeVector::SP attr, const TensorSpec& query_tensor, bool insert_query_tensor = true)
{
    NearestNeighborFixture f(attr);
    if (insert_query_tensor) {
        f.set_query_tensor(query_tensor);
    }
    auto result = f.create_blueprint();
    as_type<EmptyBlueprint>(*result);
}

void
expect_empty_blueprint(AttributeVector::SP attr)
{
    expect_empty_blueprint(std::move(attr), TensorSpec("double"), false);
}

TEST("empty blueprint is created when nearest neighbor term is invalid")
{
    TensorSpec sparse_x = TensorSpec("tensor(x{})").add({{"x", 0}}, 3);
    TensorSpec dense_y_2 = TensorSpec("tensor(y[2])").add({{"y", 0}}, 3).add({{"y", 1}}, 5);
    expect_empty_blueprint(make_int_attribute(field)); // attribute is not a tensor
    expect_empty_blueprint(make_tensor_attribute(field, "tensor(x{})")); // attribute is not a dense tensor
    expect_empty_blueprint(make_tensor_attribute(field, "tensor(x[2],y[2])")); // tensor type is not of order 1
    expect_empty_blueprint(make_tensor_attribute(field, "tensor(x[2])")); // query tensor not found
    expect_empty_blueprint(make_tensor_attribute(field, "tensor(x[2])"), sparse_x); // query tensor is not dense
    expect_empty_blueprint(make_tensor_attribute(field, "tensor(x[2])"), dense_y_2); // tensor types are not equal
}
    
TEST_MAIN() { TEST_RUN_ALL(); }
