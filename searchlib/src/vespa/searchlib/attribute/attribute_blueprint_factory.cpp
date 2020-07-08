// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_blueprint_factory.h"
#include "attribute_weighted_set_blueprint.h"
#include "i_document_weight_attribute.h"
#include "iterator_pack.h"
#include "predicate_attribute.h"
#include "attribute_blueprint_params.h"
#include "document_weight_or_filter_search.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/common/locationiterators.h>
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
#include <vespa/searchlib/queryeval/nearest_neighbor_blueprint.h>
#include <vespa/searchlib/queryeval/orlikesearch.h>
#include <vespa/searchlib/queryeval/predicate_blueprint.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_blueprint.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_search.h>
#include <vespa/searchlib/queryeval/weighted_set_term_blueprint.h>
#include <vespa/searchlib/queryeval/weighted_set_term_search.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/vespalib/util/regexp.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attribute_blueprint_factory");

using search::attribute::IAttributeVector;
using search::attribute::ISearchContext;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::fef::TermFieldMatchDataPosition;
using search::query::Location;
using search::query::LocationTerm;
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
using vespalib::geo::ZCurve;
using vespalib::make_string;
using vespalib::string;
using vespalib::tensor::DenseTensorView;

namespace search {
namespace {

//-----------------------------------------------------------------------------

/**
 * Blueprint for creating regular, stack-based attribute iterators.
 **/
class AttributeFieldBlueprint : public SimpleLeafBlueprint
{
private:
    ISearchContext::UP _search_context;

    AttributeFieldBlueprint(const FieldSpec &field, const IAttributeVector &attribute,
                            QueryTermSimple::UP term, const attribute::SearchContextParams &params)
        : SimpleLeafBlueprint(field),
          _search_context(attribute.createSearchContext(std::move(term), params))
    {
        uint32_t estHits = _search_context->approximateHits();
        HitEstimate estimate(estHits, estHits == 0);
        setEstimate(estimate);
    }

    AttributeFieldBlueprint(const FieldSpec &field, const IAttributeVector &attribute,
                            const string &query_stack, const attribute::SearchContextParams &params)
        : AttributeFieldBlueprint(field, attribute, QueryTermDecoder::decodeTerm(query_stack), params)
    { }

public:
    AttributeFieldBlueprint(const FieldSpec &field, const IAttributeVector &attribute, QueryTermSimple::UP term)
        : AttributeFieldBlueprint(field, attribute, std::move(term),
                                  attribute::SearchContextParams().useBitVector(field.isFilter()))
    {}
    AttributeFieldBlueprint(const FieldSpec &field, const IAttributeVector &attribute, const string &query_stack)
        : AttributeFieldBlueprint(field, attribute, QueryTermDecoder::decodeTerm(query_stack))
    {}

    AttributeFieldBlueprint(const FieldSpec &field, const IAttributeVector &attribute,
                            const IAttributeVector &diversity, const string &query_stack,
                            size_t diversityCutoffGroups, bool diversityCutoffStrict)
        : AttributeFieldBlueprint(field, attribute, query_stack,
                                  attribute::SearchContextParams()
                                      .diversityAttribute(&diversity)
                                      .useBitVector(field.isFilter())
                                      .diversityCutoffGroups(diversityCutoffGroups)
                                      .diversityCutoffStrict(diversityCutoffStrict))
    {}

    SearchIterator::UP createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override {
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
};

void
AttributeFieldBlueprint::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    LeafBlueprint::visitMembers(visitor);
    visit(visitor, "attribute", _search_context->attributeName());
}

//-----------------------------------------------------------------------------

template <bool is_strict>
struct LocationPreFilterIterator : public OrLikeSearch<is_strict, NoUnpack>
{
    LocationPreFilterIterator(OrSearch::Children children)
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
    LocationPreFilterBlueprint(const FieldSpec &field, const IAttributeVector &attribute, const ZCurve::RangeVector &rangeVector)
        : ComplexLeafBlueprint(field),
          _attribute(attribute),
          _rangeSearches(),
          _should_use(false)
    {
        uint64_t estHits(0);
        const IAttributeVector &attr(_attribute);
        for (auto it(rangeVector.begin()), mt(rangeVector.end()); it != mt; it++) {
            const ZCurve::Range &r(*it);
            query::Range qr(r.min(), r.max());
            query::SimpleRangeTerm rt(qr, "", 0, query::Weight(0));
            string stack(StackDumpCreator::create(rt));
            _rangeSearches.push_back(attr.createSearchContext(QueryTermDecoder::decodeTerm(stack),
                                                              attribute::SearchContextParams()));
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

    bool should_use() const { return _should_use; }

    SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override
    {
        OrSearch::Children children;
        for (auto it(_rangeSearches.begin()), mt(_rangeSearches.end()); it != mt; it++) {
            children.push_back((*it)->createIterator(tfmda[0], strict));
        }
        if (strict) {
            return std::make_unique<LocationPreFilterIterator<true>>(std::move(children));
        } else {
            return std::make_unique<LocationPreFilterIterator<false>>(std::move(children));
        }
    }

    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override {
        for (size_t i(0); i < _rangeSearches.size(); i++) {
            _rangeSearches[i]->fetchPostings(execInfo);
        }
    }
};

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
          _location()
    {
        _location.setVec(attribute);
        _location.parse(loc.getLocationString());
        uint32_t estHits = _attribute.getNumDocs();
        LOG(info, "location %s in attribute with numdocs %u", loc.getLocationString().c_str(), estHits);
        HitEstimate estimate(estHits, estHits == 0);
        setEstimate(estimate);
    }

    const common::Location &location() const { return _location; }

    SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &, bool strict) const override
    {
        return FastS_AllocLocationIterator(_attribute.getNumDocs(), strict, _location);
    }
};

//-----------------------------------------------------------------------------

Blueprint::UP
make_location_blueprint(const FieldSpec &field, const IAttributeVector &attribute, const Location &loc) {
    auto post_filter = std::make_unique<LocationPostFilterBlueprint>(field, attribute, loc);
    const common::Location &location = post_filter->location();
    if (location.getMinX() > location.getMaxX() ||
        location.getMinY() > location.getMaxY())
    {
        return std::make_unique<queryeval::EmptyBlueprint>(field);
    }
    ZCurve::RangeVector rangeVector = ZCurve::find_ranges(
            location.getMinX(), location.getMinY(),
            location.getMaxX(), location.getMaxY());
    auto pre_filter = std::make_unique<LocationPreFilterBlueprint>(field, attribute, rangeVector);
    if (!pre_filter->should_use()) {
        return post_filter;
    }
    auto root = std::make_unique<AndBlueprint>();
    root->addChild(std::move(pre_filter));
    root->addChild(std::move(post_filter));
    return root;
}

//-----------------------------------------------------------------------------

template <typename SearchType>
class DirectWeightedSetBlueprint : public ComplexLeafBlueprint
{
private:
    HitEstimate                                         _estimate;
    std::vector<int32_t>                                _weights;
    std::vector<IDocumentWeightAttribute::LookupResult> _terms;
    const IDocumentWeightAttribute                     &_attr;

public:
    DirectWeightedSetBlueprint(const FieldSpec &field, const IDocumentWeightAttribute &attr, size_t size_hint)
        : ComplexLeafBlueprint(field),
          _estimate(),
          _weights(),
          _terms(),
          _attr(attr)
    {
        set_allow_termwise_eval(true);
        _weights.reserve(size_hint);
        _terms.reserve(size_hint);
    }

    void addTerm(const vespalib::string &term, int32_t weight) {
        IDocumentWeightAttribute::LookupResult result = _attr.lookup(term);
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

    SearchIterator::UP createLeafSearch(const TermFieldMatchDataArray &tfmda, bool) const override
    {
        assert(tfmda.size() == 1);
        if (_terms.size() == 0) {
            return std::make_unique<queryeval::EmptySearch>();
        }
        std::vector<DocumentWeightIterator> iterators;
        const size_t numChildren = _terms.size();
        iterators.reserve(numChildren);
        for (const IDocumentWeightAttribute::LookupResult &r : _terms) {
            _attr.create(r.posting_idx, iterators);
        }
        return SearchType::create(*tfmda[0], _weights, std::move(iterators));
    }

    std::unique_ptr<SearchIterator> createFilterSearch(bool strict, FilterConstraint constraint) const override;
};

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
          _attr(attr)
    {
        _weights.reserve(size_hint);
        _terms.reserve(size_hint);
    }

    void addTerm(const vespalib::string &term, int32_t weight) {
        IDocumentWeightAttribute::LookupResult result = _attr.lookup(term);
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
        if (_terms.size() == 0) {
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

//-----------------------------------------------------------------------------

class DirectAttributeBlueprint : public queryeval::SimpleLeafBlueprint
{
private:
    vespalib::string                        _attrName;
    const IDocumentWeightAttribute         &_attr;
    IDocumentWeightAttribute::LookupResult  _dict_entry;

public:
    DirectAttributeBlueprint(const FieldSpec &field, const vespalib::string & name,
                             const IDocumentWeightAttribute &attr, const vespalib::string &term)
        : SimpleLeafBlueprint(field),
          _attrName(name),
          _attr(attr),
          _dict_entry(_attr.lookup(term))
    {
        setEstimate(HitEstimate(_dict_entry.posting_size, (_dict_entry.posting_size == 0)));
    }

    SearchIterator::UP createLeafSearch(const TermFieldMatchDataArray &tfmda, bool) const override {
        assert(tfmda.size() == 1);
        if (_dict_entry.posting_size == 0) {
            return std::make_unique<queryeval::EmptySearch>();
        }
        return std::make_unique<queryeval::DocumentWeightSearchIterator>(*tfmda[0], _attr, _dict_entry);
    }

    void visitMembers(vespalib::ObjectVisitor &visitor) const override {
        LeafBlueprint::visitMembers(visitor);
        visit(visitor, "attribute", _attrName);
    }
};

//-----------------------------------------------------------------------------

bool check_valid_diversity_attr(const IAttributeVector *attr) {
    if ((attr == nullptr) || attr->hasMultiValue()) {
        return false;
    }
    return (attr->hasEnum() || attr->isIntegerType() || attr->isFloatingPointType());
}

bool
is_compatible_for_nearest_neighbor(const vespalib::eval::ValueType& lhs,
                                   const vespalib::eval::ValueType& rhs)
{
    return (lhs.dimensions() == rhs.dimensions());
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

public:
    CreateBlueprintVisitor(Searchable &searchable, const IRequestContext &requestContext,
                           const FieldSpec &field, const IAttributeVector &attr)
        : CreateBlueprintVisitorHelper(searchable, field, requestContext),
          _field(field),
          _attr(attr),
          _dwa(attr.asDocumentWeightAttribute())
    {
    }

    template <class TermNode>
    void visitTerm(TermNode &n, bool simple = false) {
        if (simple && (_dwa != nullptr) && !_field.isFilter() && n.isRanked()) {
            vespalib::string term = queryeval::termAsString(n);
            setResult(std::make_unique<DirectAttributeBlueprint>(_field, _attr.getName(), *_dwa, term));
        } else {
            const string stack = StackDumpCreator::create(n);
            setResult(std::make_unique<AttributeFieldBlueprint>(_field, _attr, stack));
        }
    }

    void visitLocation(LocationTerm &node) {
        Location loc(node.getTerm());
        setResult(make_location_blueprint(_field, _attr, loc));
    }

    void visitPredicate(PredicateQuery &query) {
        const PredicateAttribute *attr = dynamic_cast<const PredicateAttribute *>(&_attr);
        if (!attr) {
            LOG(warning, "Trying to apply a PredicateQuery node to a non-predicate attribute.");
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
        QueryTermSimple parsed_term(term, QueryTermSimple::WORD);
        if (parsed_term.getMaxPerGroup() > 0) {
            const IAttributeVector *diversity(getRequestContext().getAttribute(parsed_term.getDiversityAttribute()));
            if (check_valid_diversity_attr(diversity)) {
                setResult(std::make_unique<AttributeFieldBlueprint>(_field, _attr, *diversity, stack,
                                                                    parsed_term.getDiversityCutoffGroups(),
                                                                    parsed_term.getDiversityCutoffStrict()));
            } else {
                setResult(std::make_unique<queryeval::EmptyBlueprint>(_field));
            }
        } else {
            setResult(std::make_unique<AttributeFieldBlueprint>(_field, _attr, stack));
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

    template <typename WS, typename NODE>
    void createDirectWeightedSet(WS *bp, NODE &n) {
        Blueprint::UP result(bp);
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            const query::Node &node = *n.getChildren()[i];
            vespalib::string term = queryeval::termAsString(node);
            uint32_t weight = queryeval::getWeightFromNode(node).percent();
            bp->addTerm(term, weight);
        }
        setResult(std::move(result));
    }

    static QueryTermSimple::UP
    extractTerm(const query::Node &node, bool isInteger) {
        vespalib::string term = queryeval::termAsString(node);
        if (isInteger) {
            return std::make_unique<QueryTermSimple>(term, QueryTermSimple::WORD);
        }
        return std::make_unique<QueryTermUCS4>(term, QueryTermSimple::WORD);
    }

    template <typename WS, typename NODE>
    void createShallowWeightedSet(WS *bp, NODE &n, const FieldSpec &fs, bool isInteger) {
        Blueprint::UP result(bp);
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            const query::Node &node = *n.getChildren()[i];
            uint32_t weight = queryeval::getWeightFromNode(node).percent();
            FieldSpec childfs = bp->getNextChildField(fs);
            bp->addTerm(std::make_unique<AttributeFieldBlueprint>(childfs, _attr, extractTerm(node, isInteger)), weight);
        }
        setResult(std::move(result));
    }

    void visit(query::WeightedSetTerm &n) override {
        bool isSingleValue = !_attr.hasMultiValue();
        bool isString = (_attr.isStringType() && _attr.hasEnum());
        bool isInteger = _attr.isIntegerType();
        if (isSingleValue && (isString || isInteger)) {
            auto ws = std::make_unique<AttributeWeightedSetBlueprint>(_field, _attr);
            for (size_t i = 0; i < n.getChildren().size(); ++i) {
                const query::Node &node = *n.getChildren()[i];
                uint32_t weight = queryeval::getWeightFromNode(node).percent();
                ws->addToken(_attr.createSearchContext(extractTerm(node, isInteger), attribute::SearchContextParams()), weight);
            }
            setResult(std::move(ws));
        } else {
            if (_dwa != nullptr) {
                auto *bp = new DirectWeightedSetBlueprint<queryeval::WeightedSetTermSearch>(_field, *_dwa, n.getChildren().size());
                createDirectWeightedSet(bp, n);
            } else {
                auto *bp = new WeightedSetTermBlueprint(_field);
                createShallowWeightedSet(bp, n, _field, _attr.isIntegerType());
            }
        }
    }

    void visit(query::DotProduct &n) override {
        if (_dwa != nullptr) {
            auto *bp = new DirectWeightedSetBlueprint<queryeval::DotProductSearch>(_field, *_dwa, n.getChildren().size());
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
                                               n.getChildren().size());
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
        LOG(warning, "NearestNeighborTerm(%s, %s): %s. Returning empty blueprint",
            _field.getName().c_str(), n.get_query_tensor_name().c_str(), error_msg.c_str());
        setResult(std::make_unique<queryeval::EmptyBlueprint>(_field));
    }
    void visit(query::NearestNeighborTerm &n) override {
        if (_attr.asTensorAttribute() == nullptr) {
            return fail_nearest_neighbor_term(n, "Attribute is not a tensor");
        }
        const auto* dense_attr_tensor = dynamic_cast<const DenseTensorAttribute*>(_attr.asTensorAttribute());
        if (dense_attr_tensor == nullptr) {
            return fail_nearest_neighbor_term(n, make_string("Attribute is not a dense tensor (type=%s)",
                                                             _attr.asTensorAttribute()->getTensorType().to_spec().c_str()));
        }
        if (dense_attr_tensor->getTensorType().dimensions().size() != 1) {
            return fail_nearest_neighbor_term(n, make_string("Attribute tensor type (%s) is not of order 1",
                                                             dense_attr_tensor->getTensorType().to_spec().c_str()));
        }
        auto query_tensor = getRequestContext().get_query_tensor(n.get_query_tensor_name());
        if (query_tensor.get() == nullptr) {
            return fail_nearest_neighbor_term(n, "Query tensor was not found in request context");
        }
        auto* dense_query_tensor = dynamic_cast<DenseTensorView*>(query_tensor.get());
        if (dense_query_tensor == nullptr) {
            return fail_nearest_neighbor_term(n, make_string("Query tensor is not a dense tensor (type=%s)",
                                                             query_tensor->type().to_spec().c_str()));
        }
        if (!is_compatible_for_nearest_neighbor(dense_attr_tensor->getTensorType(), dense_query_tensor->type())) {
            return fail_nearest_neighbor_term(n, make_string("Attribute tensor type (%s) and query tensor type (%s) are not compatible",
                                                             dense_attr_tensor->getTensorType().to_spec().c_str(), dense_query_tensor->type().to_spec().c_str()));
        }
        std::unique_ptr<DenseTensorView> dense_query_tensor_up(dense_query_tensor);
        query_tensor.release();
        setResult(std::make_unique<queryeval::NearestNeighborBlueprint>(_field, *dense_attr_tensor,
                                                                        std::move(dense_query_tensor_up),
                                                                        n.get_target_num_hits(),
                                                                        n.get_allow_approximate(),
                                                                        n.get_explore_additional_hits(),
                                                                        getRequestContext().get_attribute_blueprint_params().nearest_neighbor_brute_force_limit));
    }
};

} // namespace

//-----------------------------------------------------------------------------

Blueprint::UP
AttributeBlueprintFactory::createBlueprint(const IRequestContext & requestContext,
                                           const FieldSpec &field,
                                           const query::Node &term)
{
    const IAttributeVector *attr(requestContext.getAttribute(field.getName()));
    if (attr == nullptr) {
        return std::make_unique<queryeval::EmptyBlueprint>(field);
    }
    CreateBlueprintVisitor visitor(*this, requestContext, field, *attr);
    const_cast<Node &>(term).accept(visitor);
    return visitor.getResult();
}

}  // namespace search
