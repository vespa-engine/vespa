// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_blueprint_factory.h"
#include "attribute_weighted_set_blueprint.h"
#include "i_document_weight_attribute.h"
#include "iterator_pack.h"
#include "predicate_attribute.h"
#include "attribute_blueprint_params.h"
#include "document_weight_or_filter_search.h"
#include <vespa/eval/eval/value.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/common/locationiterators.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchlib/query/query_term_decoder.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/queryeval/andsearchstrict.h>
#include <vespa/searchlib/queryeval/create_blueprint_visitor_helper.h>
#include <vespa/searchlib/queryeval/document_weight_search_iterator.h>
#include <vespa/searchlib/queryeval/dot_product_blueprint.h>
#include <vespa/searchlib/queryeval/dot_product_search.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/field_spec.hpp>
#include <vespa/searchlib/queryeval/filter_wrapper.h>
#include <vespa/searchlib/queryeval/get_weight_from_node.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/matching_elements_search.h>
#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>
#include <vespa/searchlib/queryeval/orlikesearch.h>
#include <vespa/searchlib/queryeval/predicate_blueprint.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_blueprint.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_search.h>
#include <vespa/searchlib/queryeval/weighted_set_term_blueprint.h>
#include <vespa/searchlib/queryeval/weighted_set_term_search.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>
#include <sstream>
#include <charconv>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attribute_blueprint_factory");

using search::attribute::BasicType;
using search::attribute::SearchContextParams;
using search::attribute::CollectionType;
using search::attribute::IAttributeVector;
using search::attribute::ISearchContext;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::fef::TermFieldMatchDataPosition;
using search::query::Location;
using search::query::LocationTerm;
using search::query::MultiTerm;
using search::query::Node;
using search::query::NumberTerm;
using search::query::PredicateQuery;
using search::query::PrefixTerm;
using search::query::RangeTerm;
using search::query::RegExpTerm;
using search::query::StackDumpCreator;
using search::query::StringTerm;
using search::query::SubstringTerm;
using search::query::SuffixTerm;
using search::queryeval::AndBlueprint;
using search::queryeval::AndSearchStrict;
using search::queryeval::Blueprint;
using search::queryeval::ComplexLeafBlueprint;
using search::queryeval::CreateBlueprintVisitorHelper;
using search::queryeval::DotProductBlueprint;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecBaseList;
using search::queryeval::FilterWrapper;
using search::queryeval::IRequestContext;
using search::queryeval::NoUnpack;
using search::queryeval::OrLikeSearch;
using search::queryeval::OrSearch;
using search::queryeval::ParallelWeakAndBlueprint;
using search::queryeval::PredicateBlueprint;
using search::queryeval::SearchIterator;
using search::queryeval::Searchable;
using search::queryeval::SimpleLeafBlueprint;
using search::queryeval::WeightedSetTermBlueprint;
using search::tensor::DenseTensorAttribute;
using search::tensor::ITensorAttribute;
using vespalib::Issue;
using vespalib::geo::ZCurve;
using vespalib::make_string;
using vespalib::string;
using vespalib::stringref;

namespace search {
namespace {

class NodeAsKey final : public IDocumentWeightAttribute::LookupKey {
public:
    NodeAsKey(const Node & node, vespalib::string & scratchPad)
        : _node(node),
          _scratchPad(scratchPad)
    { }

    stringref asString() const override {
        return queryeval::termAsString(_node, _scratchPad);
    }

private:
    const Node       & _node;
    vespalib::string & _scratchPad;
};
//-----------------------------------------------------------------------------

/**
 * Blueprint for creating regular, stack-based attribute iterators.
 **/
class AttributeFieldBlueprint : public SimpleLeafBlueprint
{
private:
    const IAttributeVector& _attr;
    // Must take a copy of the query term for visitMembers()
    // as only a few ISearchContext implementations exposes the query term.
    vespalib::string _query_term;
    ISearchContext::UP _search_context;
    enum Type {INT, FLOAT, OTHER};
    Type _type;

public:
    AttributeFieldBlueprint(const FieldSpec &field, const IAttributeVector &attribute,
                            const string &query_stack, const SearchContextParams &params)
        : AttributeFieldBlueprint(field, attribute, QueryTermDecoder::decodeTerm(query_stack), params)
    { }
    AttributeFieldBlueprint(const FieldSpec &field, const IAttributeVector &attribute,
                            QueryTermSimple::UP term, const SearchContextParams &params)
        : SimpleLeafBlueprint(field),
          _attr(attribute),
          _query_term(term->getTermString()),
          _search_context(attribute.createSearchContext(std::move(term), params)),
          _type(OTHER)
    {
        uint32_t estHits = _search_context->approximateHits();
        HitEstimate estimate(estHits, estHits == 0);
        setEstimate(estimate);
        if (attribute.isFloatingPointType()) {
            _type = FLOAT;
        } else if (attribute.isIntegerType()) {
            _type = INT;
        }
    }

    SearchIteratorUP createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override {
        assert(tfmda.size() == 1);
        return _search_context->createIterator(tfmda[0], strict);
    }

    SearchIterator::UP createSearch(fef::MatchData &md, bool strict) const override {
        const State &state = getState();
        assert(state.numFields() == 1);
        return _search_context->createIterator(state.field(0).resolve(md), strict);
    }

    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
        (void) constraint; // We provide an iterator with exact results, so no need to take constraint into consideration.
        auto wrapper = std::make_unique<FilterWrapper>(getState().numFields());
        wrapper->wrap(createLeafSearch(wrapper->tfmda(), strict));
        return wrapper;
    }

    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override {
        _search_context->fetchPostings(execInfo);
    }

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;

    const attribute::ISearchContext *get_attribute_search_context() const override {
        return _search_context.get();
    }
    bool getRange(vespalib::string &from, vespalib::string &to) const override;
};

vespalib::string
get_type(const IAttributeVector& attr)
{
    auto coll_type = CollectionType(attr.getCollectionType());
    auto basic_type = BasicType(attr.getBasicType());
    if (coll_type.type() == CollectionType::SINGLE) {
        return basic_type.asString();
    }
    std::ostringstream oss;
    oss << coll_type.asString() << "<" << basic_type.asString() << ">";
    return oss.str();
}

void
visit_attribute(vespalib::ObjectVisitor& visitor, const IAttributeVector& attr)
{
    visitor.openStruct("attribute", "IAttributeVector");
    visitor.visitString("name", attr.getName());
    visitor.visitString("type", get_type(attr));
    visitor.visitBool("fast_search", attr.getIsFastSearch());
    visitor.visitBool("filter", attr.getIsFilter());
    visitor.closeStruct();
}

void
AttributeFieldBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit_attribute(visitor, _attr);
    visit(visitor, "query_term", _query_term);
}

//-----------------------------------------------------------------------------

template <bool is_strict>
struct LocationPreFilterIterator : public OrLikeSearch<is_strict, NoUnpack>
{
    explicit LocationPreFilterIterator(OrSearch::Children children)
        : OrLikeSearch<is_strict, NoUnpack>(std::move(children), NoUnpack()) {}
    void doUnpack(uint32_t) override {}
};

class LocationPreFilterBlueprint : public ComplexLeafBlueprint
{
private:
    const IAttributeVector &_attribute;
    std::vector<ISearchContext::UP> _rangeSearches;
    bool _should_use;

public:
    LocationPreFilterBlueprint(const FieldSpec &field, const IAttributeVector &attribute, const ZCurve::RangeVector &rangeVector, const SearchContextParams & scParams)
        : ComplexLeafBlueprint(field),
          _attribute(attribute),
          _rangeSearches(),
          _should_use(false)
    {
        uint64_t estHits(0);
        const IAttributeVector &attr(_attribute);
        for (const ZCurve::Range & r : rangeVector) {
            query::Range qr(r.min(), r.max());
            query::SimpleRangeTerm rt(qr, "", 0, query::Weight(0));
            string stack(StackDumpCreator::create(rt));
            _rangeSearches.push_back(attr.createSearchContext(QueryTermDecoder::decodeTerm(stack), scParams));
            estHits += _rangeSearches.back()->approximateHits();
            LOG(debug, "Range '%s' estHits %" PRId64, qr.getRangeString().c_str(), estHits);
        }
        if (estHits > attr.getNumDocs()) {
            estHits = attr.getNumDocs();
        }
        if (estHits * 10 < attr.getNumDocs()) {
            _should_use = true;
        }
        HitEstimate estimate(estHits, estHits == 0);
        setEstimate(estimate);
        set_allow_termwise_eval(true);
    }

    ~LocationPreFilterBlueprint() override;

    bool should_use() const { return _should_use; }

    SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override
    {
        OrSearch::Children children;
        for (auto & search : _rangeSearches) {
            children.push_back(search->createIterator(tfmda[0], strict));
        }
        if (strict) {
            return std::make_unique<LocationPreFilterIterator<true>>(std::move(children));
        } else {
            return std::make_unique<LocationPreFilterIterator<false>>(std::move(children));
        }
    }
    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
        return create_default_filter(strict, constraint);
    }

    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override {
        for (auto & search : _rangeSearches) {
            search->fetchPostings(execInfo);
        }
    }

    void visitMembers(vespalib::ObjectVisitor& visitor) const override {
        LeafBlueprint::visitMembers(visitor);
        visit_attribute(visitor, _attribute);
    }
};

LocationPreFilterBlueprint::~LocationPreFilterBlueprint() = default;

//-----------------------------------------------------------------------------

class LocationPostFilterBlueprint : public ComplexLeafBlueprint
{
private:
    const IAttributeVector  &_attribute;
    common::Location _location;

public:
    LocationPostFilterBlueprint(const FieldSpec &field, const IAttributeVector &attribute, const Location &loc)
        : ComplexLeafBlueprint(field),
          _attribute(attribute),
          _location(loc)
    {
        uint32_t estHits = 0;
        if (loc.valid()) {
            _location.setVec(attribute);
            estHits = _attribute.getNumDocs();
        }
        LOG(debug, "location %s in attribute with numdocs %u", loc.getOldFormatString().c_str(), estHits);
        HitEstimate estimate(estHits, estHits == 0);
        setEstimate(estimate);
    }

    ~LocationPostFilterBlueprint() override;

    const common::Location &location() const { return _location; }

    SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override
    {
        if (tfmda.size() == 1) {
            // search in exactly one field
            fef::TermFieldMatchData &tfmd = *tfmda[0];
            return search::common::create_location_iterator(tfmd,
                                                            _attribute.getNumDocs(),
                                                            strict,
                                                            _location);
        } else {
            LOG(debug, "wrong size tfmda: %zu (fallback to old location iterator)\n", tfmda.size());
        }
        return FastS_AllocLocationIterator(_attribute.getNumDocs(), strict, _location);
    }
    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
        return create_default_filter(strict, constraint);
    }
    void visitMembers(vespalib::ObjectVisitor& visitor) const override {
        LeafBlueprint::visitMembers(visitor);
        visit_attribute(visitor, _attribute);
    }
};

//-----------------------------------------------------------------------------

Blueprint::UP
make_location_blueprint(const FieldSpec &field, const IAttributeVector &attribute, const Location &loc,
                        const SearchContextParams & scParams) {
    LOG(debug, "make_location_blueprint(fieldId[%u], p[%d,%d], r[%u], aspect[%u], bb[[%d,%d],[%d,%d]])",
        field.getFieldId(),
        loc.point.x, loc.point.y, loc.radius,
        loc.x_aspect.multiplier,
        loc.bounding_box.x.low, loc.bounding_box.x.high,
        loc.bounding_box.y.low, loc.bounding_box.y.high);
    auto post_filter = std::make_unique<LocationPostFilterBlueprint>(field, attribute, loc);
    const common::Location &location = post_filter->location();
    if (location.bounding_box.x.low > location.bounding_box.x.high ||
        location.bounding_box.y.low > location.bounding_box.y.high)
    {
        return std::make_unique<queryeval::EmptyBlueprint>(field);
    }
    ZCurve::RangeVector rangeVector = ZCurve::find_ranges(
            location.bounding_box.x.low,
            location.bounding_box.y.low,
            location.bounding_box.x.high,
            location.bounding_box.y.high);
    auto pre_filter = std::make_unique<LocationPreFilterBlueprint>(field, attribute, rangeVector, scParams);
    if (!pre_filter->should_use()) {
        LOG(debug, "only use post filter");
        return post_filter;
    }
    auto root = std::make_unique<AndBlueprint>();
    root->addChild(std::move(pre_filter));
    root->addChild(std::move(post_filter));
    return root;
}

LocationPostFilterBlueprint::~LocationPostFilterBlueprint() = default;

class LookupKey : public IDocumentWeightAttribute::LookupKey {
public:
    LookupKey(MultiTerm & terms, uint32_t index) : _terms(terms), _index(index) {}

    stringref asString() const override {
        return _terms.getAsString(_index).first;
    }

    bool asInteger(int64_t &value) const override {
        value = _terms.getAsInteger(_index).first;
        return true;
    }

private:
    const MultiTerm & _terms;
    uint32_t          _index;
};

//-----------------------------------------------------------------------------

template <typename SearchType>
class DirectWeightedSetBlueprint : public ComplexLeafBlueprint
{
private:
    HitEstimate                                         _estimate;
    std::vector<int32_t>                                _weights;
    std::vector<IDocumentWeightAttribute::LookupResult> _terms;
    vespalib::string                                    _field_name;
    const IAttributeVector                             &_iattr;
    const IDocumentWeightAttribute                     &_attr;
    vespalib::datastore::EntryRef                       _dictionary_snapshot;

public:
    DirectWeightedSetBlueprint(const FieldSpec &field, const IAttributeVector &iattr, const IDocumentWeightAttribute &attr, size_t size_hint)
        : ComplexLeafBlueprint(field),
          _estimate(),
          _weights(),
          _terms(),
          _field_name(field.getName()),
          _iattr(iattr),
          _attr(attr),
          _dictionary_snapshot(_attr.get_dictionary_snapshot())
    {
        set_allow_termwise_eval(true);
        _weights.reserve(size_hint);
        _terms.reserve(size_hint);
    }
    ~DirectWeightedSetBlueprint() override;

    void addTerm(const IDocumentWeightAttribute::LookupKey & key, int32_t weight) {
        IDocumentWeightAttribute::LookupResult result = _attr.lookup(key, _dictionary_snapshot);
        HitEstimate childEst(result.posting_size, (result.posting_size == 0));
        if (!childEst.empty) {
            if (_estimate.empty) {
                _estimate = childEst;
            } else {
                _estimate.estHits += childEst.estHits;
            }
            setEstimate(_estimate);
            _weights.push_back(weight);
            _terms.push_back(result);
        }
    }

    SearchIterator::UP createLeafSearch(const TermFieldMatchDataArray &tfmda, bool) const override;

    std::unique_ptr<SearchIterator> createFilterSearch(bool strict, FilterConstraint constraint) const override;
    std::unique_ptr<queryeval::MatchingElementsSearch> create_matching_elements_search(const MatchingElementsFields &fields) const override {
        if (fields.has_field(_field_name)) {
            return queryeval::MatchingElementsSearch::create(_iattr, _dictionary_snapshot, vespalib::ConstArrayRef<IDocumentWeightAttribute::LookupResult>(_terms));
        } else {
            return {};
        }
    }
    void visitMembers(vespalib::ObjectVisitor& visitor) const override {
        LeafBlueprint::visitMembers(visitor);
        visit_attribute(visitor, _iattr);
    }
};

template <typename SearchType>
SearchIterator::UP
DirectWeightedSetBlueprint<SearchType>::createLeafSearch(const TermFieldMatchDataArray &tfmda, bool) const
{
    assert(tfmda.size() == 1);
    assert(getState().numFields() == 1);
    if (_terms.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    std::vector<DocumentWeightIterator> iterators;
    const size_t numChildren = _terms.size();
    iterators.reserve(numChildren);
    for (const IDocumentWeightAttribute::LookupResult &r : _terms) {
        _attr.create(r.posting_idx, iterators);
    }
    bool field_is_filter = getState().fields()[0].isFilter();
    return SearchType::create(*tfmda[0], field_is_filter, _weights, std::move(iterators));
}


template <typename SearchType>
DirectWeightedSetBlueprint<SearchType>::~DirectWeightedSetBlueprint() = default;

template <typename SearchType>
std::unique_ptr<SearchIterator>
DirectWeightedSetBlueprint<SearchType>::createFilterSearch(bool, FilterConstraint) const
{
    std::vector<DocumentWeightIterator> iterators;
    iterators.reserve(_terms.size());
    for (const IDocumentWeightAttribute::LookupResult &r : _terms) {
        _attr.create(r.posting_idx, iterators);
    }
    return attribute::DocumentWeightOrFilterSearch::create(std::move(iterators));
}

//-----------------------------------------------------------------------------

class DirectWandBlueprint : public queryeval::ComplexLeafBlueprint
{
private:
    HitEstimate                                         _estimate;
    mutable queryeval::SharedWeakAndPriorityQueue       _scores;
    const queryeval::wand::score_t                      _scoreThreshold;
    double                                              _thresholdBoostFactor;
    const uint32_t                                      _scoresAdjustFrequency;
    std::vector<int32_t>                                _weights;
    std::vector<IDocumentWeightAttribute::LookupResult> _terms;
    const IDocumentWeightAttribute                     &_attr;
    vespalib::datastore::EntryRef                       _dictionary_snapshot;

public:
    DirectWandBlueprint(const FieldSpec &field, const IDocumentWeightAttribute &attr, uint32_t scoresToTrack,
                        queryeval::wand::score_t scoreThreshold, double thresholdBoostFactor, size_t size_hint)
        : ComplexLeafBlueprint(field),
          _estimate(),
          _scores(scoresToTrack),
          _scoreThreshold(scoreThreshold),
          _thresholdBoostFactor(thresholdBoostFactor),
          _scoresAdjustFrequency(queryeval::DEFAULT_PARALLEL_WAND_SCORES_ADJUST_FREQUENCY),
          _weights(),
          _terms(),
          _attr(attr),
          _dictionary_snapshot(_attr.get_dictionary_snapshot())
    {
        _weights.reserve(size_hint);
        _terms.reserve(size_hint);
    }

    ~DirectWandBlueprint() override;

    void addTerm(const IDocumentWeightAttribute::LookupKey & key, int32_t weight) {
        IDocumentWeightAttribute::LookupResult result = _attr.lookup(key, _dictionary_snapshot);
        HitEstimate childEst(result.posting_size, (result.posting_size == 0));
        if (!childEst.empty) {
            if (_estimate.empty) {
                _estimate = childEst;
            } else {
                _estimate.estHits += childEst.estHits;
            }
            setEstimate(_estimate);
            _weights.push_back(weight);
            _terms.push_back(result);
        }
    }

    SearchIterator::UP createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override {
        assert(tfmda.size() == 1);
        if (_terms.empty()) {
            return std::make_unique<queryeval::EmptySearch>();
        }
        return queryeval::ParallelWeakAndSearch::create(*tfmda[0],
                queryeval::ParallelWeakAndSearch::MatchParams(_scores, _scoreThreshold,
                                                              _thresholdBoostFactor, _scoresAdjustFrequency)
                        .setDocIdLimit(get_docid_limit()),
                _weights, _terms, _attr, strict);
    }
    std::unique_ptr<SearchIterator> createFilterSearch(bool strict, FilterConstraint constraint) const override;
    bool always_needs_unpack() const override { return true; }
};

DirectWandBlueprint::~DirectWandBlueprint() = default;

std::unique_ptr<SearchIterator>
DirectWandBlueprint::createFilterSearch(bool, FilterConstraint constraint) const
{
    if (constraint == Blueprint::FilterConstraint::UPPER_BOUND) {
        std::vector<DocumentWeightIterator> iterators;
        iterators.reserve(_terms.size());
        for (const IDocumentWeightAttribute::LookupResult &r : _terms) {
            _attr.create(r.posting_idx, iterators);
        }
        return attribute::DocumentWeightOrFilterSearch::create(std::move(iterators));
    } else {
        return std::make_unique<queryeval::EmptySearch>();
    }
}

bool
AttributeFieldBlueprint::getRange(vespalib::string &from, vespalib::string &to) const {
    if (_type == INT) {
        Int64Range range = _search_context->getAsIntegerTerm();
        char buf[32];
        auto res = std::to_chars(buf, buf + sizeof(buf), range.lower(), 10);
        from = vespalib::stringref(buf, res.ptr - buf);
        res = std::to_chars(buf, buf + sizeof(buf), range.upper(), 10);
        to = vespalib::stringref(buf, res.ptr - buf);
        return true;
    } else if (_type == FLOAT) {
        DoubleRange range = _search_context->getAsDoubleTerm();
        from = vespalib::make_string("%g", range.lower());
        to = vespalib::make_string("%g", range.upper());
        return true;
    }
    return false;
}

//-----------------------------------------------------------------------------

class DirectAttributeBlueprint : public queryeval::SimpleLeafBlueprint
{
private:
    vespalib::string                        _attrName;
    const IAttributeVector                 &_iattr;
    const IDocumentWeightAttribute         &_attr;
    vespalib::datastore::EntryRef           _dictionary_snapshot;
    IDocumentWeightAttribute::LookupResult  _dict_entry;

public:
    DirectAttributeBlueprint(const FieldSpec &field, const vespalib::string & name,
                             const IAttributeVector &iattr,
                             const IDocumentWeightAttribute &attr,
                             const IDocumentWeightAttribute::LookupKey & key)
        : SimpleLeafBlueprint(field),
          _attrName(name),
          _iattr(iattr),
          _attr(attr),
          _dictionary_snapshot(_attr.get_dictionary_snapshot()),
          _dict_entry(_attr.lookup(key, _dictionary_snapshot))
    {
        setEstimate(HitEstimate(_dict_entry.posting_size, (_dict_entry.posting_size == 0)));
    }

    SearchIterator::UP createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override {
        assert(tfmda.size() == 1);
        if (_dict_entry.posting_size == 0) {
            return std::make_unique<queryeval::EmptySearch>();
        }
        if (tfmda[0]->isNotNeeded()) {
            auto bitvector_iterator = _attr.make_bitvector_iterator(_dict_entry.posting_idx, get_docid_limit(), *tfmda[0], strict);
            if (bitvector_iterator) {
                return bitvector_iterator;
            }
        }
        return std::make_unique<queryeval::DocumentWeightSearchIterator>(*tfmda[0], _attr, _dict_entry);
    }

    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
        (void) constraint; // We provide an iterator with exact results, so no need to take constraint into consideration.
        auto wrapper = std::make_unique<FilterWrapper>(getState().numFields());
        wrapper->wrap(createLeafSearch(wrapper->tfmda(), strict));
        return wrapper;
    }

    void visitMembers(vespalib::ObjectVisitor &visitor) const override {
        LeafBlueprint::visitMembers(visitor);
        visit_attribute(visitor, _iattr);
    }
    std::unique_ptr<queryeval::MatchingElementsSearch> create_matching_elements_search(const MatchingElementsFields &fields) const override {
        if (fields.has_field(_attrName)) {
            return queryeval::MatchingElementsSearch::create(_iattr, _dictionary_snapshot, vespalib::ConstArrayRef<IDocumentWeightAttribute::LookupResult>(&_dict_entry, 1));
        } else {
            return {};
        }
    }
};

//-----------------------------------------------------------------------------

bool check_valid_diversity_attr(const IAttributeVector *attr) {
    if ((attr == nullptr) || attr->hasMultiValue()) {
        return false;
    }
    return (attr->hasEnum() || attr->isIntegerType() || attr->isFloatingPointType());
}

//-----------------------------------------------------------------------------


/**
 * Determines the correct Blueprint to use.
 **/
class CreateBlueprintVisitor : public CreateBlueprintVisitorHelper
{
private:
    const FieldSpec &_field;
    const IAttributeVector &_attr;
    const IDocumentWeightAttribute *_dwa;
    vespalib::string _scratchPad;

public:
    CreateBlueprintVisitor(Searchable &searchable, const IRequestContext &requestContext,
                           const FieldSpec &field, const IAttributeVector &attr)
        : CreateBlueprintVisitorHelper(searchable, field, requestContext),
          _field(field),
          _attr(attr),
          _dwa(attr.asDocumentWeightAttribute()),
          _scratchPad()
    {
    }
    ~CreateBlueprintVisitor() override;

    template <class TermNode>
    void visitTerm(TermNode &n, bool simple = false) {
        if (simple && (_dwa != nullptr) && !_field.isFilter() && n.isRanked()) {
            NodeAsKey key(n, _scratchPad);
            setResult(std::make_unique<DirectAttributeBlueprint>(_field, _attr.getName(), _attr, *_dwa, key));
        } else {
            SearchContextParams scParams = createContextParams(_field.isFilter());
            const string stack = StackDumpCreator::create(n);
            setResult(std::make_unique<AttributeFieldBlueprint>(_field, _attr, stack, scParams));
        }
    }

    void visitLocation(LocationTerm &node) {
        setResult(make_location_blueprint(_field, _attr, node.getTerm(), createContextParams(_field.isFilter())));
    }

    void visitPredicate(PredicateQuery &query) {
        const auto *attr = dynamic_cast<const PredicateAttribute *>(&_attr);
        if (!attr) {
            Issue::report("Trying to apply a PredicateQuery node to a non-predicate attribute.");
            setResult(std::make_unique<queryeval::EmptyBlueprint>(_field));
        } else {
            setResult(std::make_unique<PredicateBlueprint>( _field, *attr, query));
        }
    }

    void visit(NumberTerm & n) override { visitTerm(n, true); }
    void visit(LocationTerm &n) override { visitLocation(n); }
    void visit(PrefixTerm & n) override { visitTerm(n); }

    void visit(RangeTerm &n) override {
        const string stack = StackDumpCreator::create(n);
        const string term = queryeval::termAsString(n);
        QueryTermSimple parsed_term(term, QueryTermSimple::Type::WORD);
        SearchContextParams scParams = createContextParams(_field.isFilter());
        if (parsed_term.getMaxPerGroup() > 0) {
            const IAttributeVector *diversity(getRequestContext().getAttribute(parsed_term.getDiversityAttribute()));
            if (check_valid_diversity_attr(diversity)) {
                scParams.diversityAttribute(diversity)
                        .diversityCutoffGroups(parsed_term.getDiversityCutoffGroups())
                        .diversityCutoffStrict(parsed_term.getDiversityCutoffStrict());
                setResult(std::make_unique<AttributeFieldBlueprint>(_field, _attr, stack, scParams));
            } else {
                setResult(std::make_unique<queryeval::EmptyBlueprint>(_field));
            }
        } else {
            setResult(std::make_unique<AttributeFieldBlueprint>(_field, _attr, stack, scParams));
        }
    }

    void visit(StringTerm & n) override { visitTerm(n, true); }
    void visit(SubstringTerm & n) override {
        query::SimpleRegExpTerm re(vespalib::RegexpUtil::make_from_substring(n.getTerm()),
                                   n.getView(), n.getId(), n.getWeight());
        visitTerm(re);
    }
    void visit(SuffixTerm & n) override {
        query::SimpleRegExpTerm re(vespalib::RegexpUtil::make_from_suffix(n.getTerm()),
                                   n.getView(), n.getId(), n.getWeight());
        visitTerm(re);
    }
    void visit(PredicateQuery &n) override { visitPredicate(n); }
    void visit(RegExpTerm & n) override { visitTerm(n); }

    template <typename WS>
    void createDirectWeightedSet(WS *bp, MultiTerm &n);

    template <typename WS>
    void createShallowWeightedSet(WS *bp, MultiTerm &n, const FieldSpec &fs, bool isInteger);

    static QueryTermSimple::UP
    extractTerm(vespalib::stringref term, bool isInteger) {
        if (isInteger) {
            return std::make_unique<QueryTermSimple>(term, QueryTermSimple::Type::WORD);
        }
        return std::make_unique<QueryTermUCS4>(term, QueryTermSimple::Type::WORD);
    }

    void visit(query::WeightedSetTerm &n) override {
        bool isSingleValue = !_attr.hasMultiValue();
        bool isString = (_attr.isStringType() && _attr.hasEnum());
        bool isInteger = _attr.isIntegerType();
        if (isSingleValue && (isString || isInteger)) {
            auto ws = std::make_unique<AttributeWeightedSetBlueprint>(_field, _attr);
            SearchContextParams scParams = createContextParams();
            for (size_t i = 0; i < n.getNumTerms(); ++i) {
                auto term = n.getAsString(i);
                ws->addToken(_attr.createSearchContext(extractTerm(term.first, isInteger), scParams), term.second.percent());
            }
            setResult(std::move(ws));
        } else {
            if (_dwa != nullptr) {
                auto *bp = new DirectWeightedSetBlueprint<queryeval::WeightedSetTermSearch>(_field, _attr, *_dwa, n.getNumTerms());
                createDirectWeightedSet(bp, n);
            } else {
                auto *bp = new WeightedSetTermBlueprint(_field);
                createShallowWeightedSet(bp, n, _field, _attr.isIntegerType());
            }
        }
    }

    void visit(query::DotProduct &n) override {
        if (_dwa != nullptr) {
            auto *bp = new DirectWeightedSetBlueprint<queryeval::DotProductSearch>(_field, _attr, *_dwa, n.getNumTerms());
            createDirectWeightedSet(bp, n);
        } else {
            auto *bp = new DotProductBlueprint(_field);
            createShallowWeightedSet(bp, n, _field, _attr.isIntegerType());
        }
    }

    void visit(query::WandTerm &n) override {
        if (_dwa != nullptr) {
            auto *bp = new DirectWandBlueprint(_field, *_dwa,
                                               n.getTargetNumHits(), n.getScoreThreshold(), n.getThresholdBoostFactor(),
                                               n.getNumTerms());
            createDirectWeightedSet(bp, n);
        } else {
            auto *bp = new ParallelWeakAndBlueprint(_field,
                    n.getTargetNumHits(),
                    n.getScoreThreshold(),
                    n.getThresholdBoostFactor());
            createShallowWeightedSet(bp, n, _field, _attr.isIntegerType());
        }
    }
    void fail_nearest_neighbor_term(query::NearestNeighborTerm&n, const vespalib::string& error_msg) {
        Issue::report("NearestNeighborTerm(%s, %s): %s. Returning empty blueprint",
                      _field.getName().c_str(), n.get_query_tensor_name().c_str(), error_msg.c_str());
        setResult(std::make_unique<queryeval::EmptyBlueprint>(_field));
    }
    void visit(query::NearestNeighborTerm &n) override {
        const auto* query_tensor = getRequestContext().get_query_tensor(n.get_query_tensor_name());
        if (query_tensor == nullptr) {
            return fail_nearest_neighbor_term(n, "Query tensor was not found in request context");
        }
        try {
            auto calc = tensor::DistanceCalculator::make_with_validation(_attr, *query_tensor);
            setResult(std::make_unique<queryeval::NearestNeighborBlueprint>(_field,
                                                                            std::move(calc),
                                                                            n.get_target_num_hits(),
                                                                            n.get_allow_approximate(),
                                                                            n.get_explore_additional_hits(),
                                                                            n.get_distance_threshold(),
                                                                            getRequestContext().get_attribute_blueprint_params().global_filter_lower_limit,
                                                                            getRequestContext().get_attribute_blueprint_params().global_filter_upper_limit,
                                                                            getRequestContext().getDoom()));
        } catch (const vespalib::IllegalArgumentException& ex) {
            return fail_nearest_neighbor_term(n, ex.getMessage());

        }
    }

    void visit(query::FuzzyTerm &n) override { visitTerm(n); }
};

template <typename WS>
void
CreateBlueprintVisitor::createDirectWeightedSet(WS *bp, MultiTerm &n) {
    Blueprint::UP result(bp);
    for (uint32_t i(0); i < n.getNumTerms(); i++) {
        bp->addTerm(LookupKey(n, i), n.weight(i).percent());
    }
    setResult(std::move(result));
}

template <typename WS>
void
CreateBlueprintVisitor::createShallowWeightedSet(WS *bp, MultiTerm &n, const FieldSpec &fs, bool isInteger) {
    Blueprint::UP result(bp);
    SearchContextParams scParams = createContextParams();
    bp->reserve(n.getNumTerms());
    for (uint32_t i(0); i < n.getNumTerms(); i++) {
        FieldSpec childfs = bp->getNextChildField(fs);
        auto term = n.getAsString(i);
        bp->addTerm(std::make_unique<AttributeFieldBlueprint>(childfs, _attr, extractTerm(term.first, isInteger), scParams.useBitVector(childfs.isFilter())), term.second.percent());
    }
    setResult(std::move(result));
}

CreateBlueprintVisitor::~CreateBlueprintVisitor() = default;

} // namespace

//-----------------------------------------------------------------------------

Blueprint::UP
AttributeBlueprintFactory::createBlueprint(const IRequestContext & requestContext,
                                           const FieldSpec &field,
                                           const query::Node &term)
{
    const IAttributeVector *attr(requestContext.getAttribute(field.getName()));
    if (attr == nullptr) {
        Issue::report("attribute not found: %s", field.getName().c_str());
        return std::make_unique<queryeval::EmptyBlueprint>(field);
    }
    try {
        CreateBlueprintVisitor visitor(*this, requestContext, field, *attr);
        const_cast<Node &>(term).accept(visitor);
        return visitor.getResult();
    } catch (const vespalib::UnsupportedOperationException &e) {
        Issue::report(e);
        return std::make_unique<queryeval::EmptyBlueprint>(field);
    }
}

}  // namespace search
