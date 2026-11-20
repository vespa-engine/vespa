// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_wand_blueprint.h"

#include "emptysearch.h"

#include <vespa/check_require.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/attribute/i_docid_with_float_weight_posting_store.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/typify.h>

#include <algorithm>

using vespalib::IllegalArgumentException;
using vespalib::make_string;
using vespalib::SharedStringRepo;
using vespalib::string_id;
using vespalib::typify_invoke;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;

namespace search::queryeval {

namespace {

void check_score_threshold(const IDocidWithFloatWeightPostingStore* posting_store, double score_threshold) {
    if (posting_store != nullptr && !(score_threshold >= 0.0)) {
        throw IllegalArgumentException(make_string(
            "Score threshold (%g) must not be negative for attribute with label posting lists", score_threshold));
    }
}

struct GetCell {
    template <typename CT> static double invoke(TypedCells cells, size_t idx) { return cells.typify<CT>()[idx]; }
};

double get_cell(TypedCells cells, size_t idx) {
    return typify_invoke<1, TypifyCellType, GetCell>(cells.type, cells, idx);
}

} // namespace

SparseTensorWandBlueprint::SparseTensorWandBlueprint(const FieldSpec&                field,
                                                     const tensor::ITensorAttribute& attr_tensor,
                                                     const vespalib::eval::Value& query_tensor, uint32_t target_hits,
                                                     double score_threshold)
    : ComplexLeafBlueprint(field),
      _attr_tensor(attr_tensor),
      _query_tensor(query_tensor),
      _interpreted_dot_product(),
      _dot_product(),
      _target_hits(target_hits),
      _score_threshold(score_threshold),
      _scores(target_hits),
      _matching_phase(MatchingPhase::FIRST_PHASE),
      _posting_store(nullptr),
      _posting_refs(),
      _query_weights() {
    const auto* posting_store = attr_tensor.label_posting_store();
    check_score_threshold(posting_store, score_threshold);
    _scores.set_min_score(score_threshold);
    // the interpreted function is faster when document tensors are fast values
    if (_attr_tensor.supports_get_tensor_ref()) {
        _interpreted_dot_product = SparseTensorWandSearch::make_dot_product(_attr_tensor.getTensorType());
    } else {
        _dot_product = std::make_unique<SparseTensorDotProduct>(query_tensor);
    }
    uint32_t est_hits = _attr_tensor.get_num_docs();
    /*
     * Documents without any of the query labels have a dot product of 0, so they can
     * only be hits when the score threshold is negative (rejected above). It is thus
     * sufficient to score the documents in the posting lists of the query labels.
     */
    if (posting_store != nullptr && posting_store->has_always_btree_iterator()) {
        _posting_store = posting_store;
        auto       snapshot = posting_store->get_dictionary_snapshot();
        uint64_t   posting_sizes = 0;
        string_id  label;
        string_id* label_ptr = &label;
        size_t     subspace = 0;
        auto       query_cells = query_tensor.cells();
        auto       view = query_tensor.index().create_view({});
        view->lookup({});
        while (view->next_result({&label_ptr, 1}, subspace)) {
            auto label_str = SharedStringRepo::Handle::string_from_id(label);
            auto result = posting_store->lookup(label_str, snapshot);
            if (result.posting_idx.valid()) {
                _posting_refs.push_back(result.posting_idx);
                _query_weights.push_back(get_cell(query_cells, subspace));
                posting_sizes += result.posting_size;
            }
        }
        est_hits = std::min(posting_sizes, static_cast<uint64_t>(est_hits));
        setEstimate(HitEstimate(est_hits, est_hits == 0));
    } else {
        setEstimate(HitEstimate(est_hits, false));
    }
}

SparseTensorWandBlueprint::~SparseTensorWandBlueprint() = default;

const tensor::ITensorAttribute& SparseTensorWandBlueprint::validate(const attribute::IAttributeVector& attr,
                                                                    const vespalib::eval::Value&       query_tensor,
                                                                    double score_threshold) {
    const tensor::ITensorAttribute* attr_tensor = attr.asTensorAttribute();
    if (attr_tensor == nullptr) {
        throw IllegalArgumentException("Attribute is not a tensor");
    }
    const auto& at_type = attr_tensor->getTensorType();
    if (!SparseTensorWandSearch::is_supported_type(at_type)) {
        throw IllegalArgumentException(
            make_string("Attribute tensor type (%s) is not a sparse tensor with one mapped dimension",
                        at_type.to_spec().c_str()));
    }
    const auto& qt_type = query_tensor.type();
    if (qt_type != at_type) {
        throw IllegalArgumentException(make_string("Attribute tensor type (%s) and query tensor type (%s) differ",
                                                   at_type.to_spec().c_str(), qt_type.to_spec().c_str()));
    }
    if (!attr_tensor->supports_get_serialized_tensor_ref() && !attr_tensor->supports_get_tensor_ref()) {
        throw IllegalArgumentException(make_string("Attribute tensor type (%s) does not support access by reference",
                                                   at_type.to_spec().c_str()));
    }
    check_score_threshold(attr_tensor->label_posting_store(), score_threshold);
    return *attr_tensor;
}

double SparseTensorWandBlueprint::sort(InFlow in_flow) {
    resolve_strict(in_flow);
    return abs_cost();
}

std::unique_ptr<SearchIterator>
SparseTensorWandBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda) const {
    CHECK(tfmda.size() == 1);
    fef::TermFieldMatchData&            tfmd = *tfmda[0]; // always search in only one field
    SparseTensorWandSearch::MatchParams match_params(_scores, wand::DEFAULT_PARALLEL_WAND_SCORES_ADJUST_FREQUENCY,
                                                     get_docid_limit());
    bool                                readonly_scores_heap = (_matching_phase != MatchingPhase::FIRST_PHASE);
    if (_posting_store != nullptr) {
        if (_posting_refs.empty()) {
            return std::make_unique<EmptySearch>();
        }
        if (strict() && _posting_refs.size() <= SparseTensorWandSearch::max_postings) {
            std::vector<DocidWithFloatWeightIterator> postings;
            postings.reserve(_posting_refs.size());
            for (auto ref : _posting_refs) {
                postings.push_back(_posting_store->create(ref));
            }
            if (_interpreted_dot_product) {
                return SparseTensorWandSearch::create_with_postings(tfmd, _attr_tensor, *_interpreted_dot_product,
                                                                    _query_tensor, match_params, readonly_scores_heap,
                                                                    std::move(postings), _query_weights);
            }
            return SparseTensorWandSearch::create_with_postings(tfmd, _attr_tensor, *_dot_product, match_params,
                                                                readonly_scores_heap, std::move(postings),
                                                                _query_weights);
        }
        // Non-strict (or too many posting lists): scoring documents directly is exact,
        // documents without query labels score 0 and are never hits.
    }
    if (_interpreted_dot_product) {
        return SparseTensorWandSearch::create(strict(), tfmd, _attr_tensor, *_interpreted_dot_product, _query_tensor,
                                              match_params, readonly_scores_heap);
    }
    return SparseTensorWandSearch::create(strict(), tfmd, _attr_tensor, *_dot_product, match_params,
                                          readonly_scores_heap);
}

void SparseTensorWandBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const {
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute_tensor", _attr_tensor.getTensorType().to_spec());
    visitor.visitString("query_tensor", _query_tensor.type().to_spec());
    visitor.visitInt("target_hits", _target_hits);
    visitor.visitFloat("score_threshold", _score_threshold);
    visitor.visitBool("uses_postings", uses_postings());
}

bool SparseTensorWandBlueprint::always_needs_unpack() const {
    return true;
}

void SparseTensorWandBlueprint::set_matching_phase(MatchingPhase matching_phase) noexcept {
    Blueprint::set_matching_phase(matching_phase);
    _matching_phase = matching_phase;
    if (matching_phase != MatchingPhase::FIRST_PHASE) {
        /*
         * During first phase matching, the scores heap is adjusted by
         * the iterators. The score threshold is raised when the
         * scores heap is full while handling a matching document with
         * a higher score than the worst existing one.
         *
         * During later matching phases, only the original score
         * threshold is used, and the heap is not updated by the
         * iterators. This ensures that all documents considered a hit
         * by the first phase matching will also be considered as hits
         * by the later matching phases.
         */
        _scores.set_min_score(_score_threshold);
    }
}

} // namespace search::queryeval
