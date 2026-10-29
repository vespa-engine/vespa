// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_wand_blueprint.h"

#include <vespa/check_require.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace search::queryeval {

SparseTensorWandBlueprint::SparseTensorWandBlueprint(const FieldSpec&                field,
                                                     const tensor::ITensorAttribute& attr_tensor,
                                                     const vespalib::eval::Value& query_tensor, uint32_t target_hits,
                                                     double score_threshold)
    : ComplexLeafBlueprint(field),
      _attr_tensor(attr_tensor),
      _query_tensor(query_tensor),
      _dot_product(query_tensor),
      _target_hits(target_hits),
      _score_threshold(score_threshold),
      _scores(target_hits),
      _matching_phase(MatchingPhase::FIRST_PHASE) {
    _scores.set_min_score(score_threshold);
    uint32_t est_hits = _attr_tensor.get_num_docs();
    setEstimate(HitEstimate(est_hits, false));
}

SparseTensorWandBlueprint::~SparseTensorWandBlueprint() = default;

const tensor::ITensorAttribute& SparseTensorWandBlueprint::validate(const attribute::IAttributeVector& attr,
                                                                    const vespalib::eval::Value&       query_tensor) {
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
    return *attr_tensor;
}

double SparseTensorWandBlueprint::sort(InFlow in_flow) {
    resolve_strict(in_flow);
    return abs_cost();
}

std::unique_ptr<SearchIterator>
SparseTensorWandBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda) const {
    CHECK(tfmda.size() == 1);
    fef::TermFieldMatchData& tfmd = *tfmda[0]; // always search in only one field
    return SparseTensorWandSearch::create(
        strict(), tfmd, _attr_tensor, _dot_product,
        SparseTensorWandSearch::MatchParams(_scores, wand::DEFAULT_PARALLEL_WAND_SCORES_ADJUST_FREQUENCY,
                                            get_docid_limit()),
        _matching_phase != MatchingPhase::FIRST_PHASE);
}

void SparseTensorWandBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const {
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute_tensor", _attr_tensor.getTensorType().to_spec());
    visitor.visitString("query_tensor", _query_tensor.type().to_spec());
    visitor.visitInt("target_hits", _target_hits);
    visitor.visitFloat("score_threshold", _score_threshold);
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
