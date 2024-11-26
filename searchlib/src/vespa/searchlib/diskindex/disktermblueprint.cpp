// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disktermblueprint.h"
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/filter_wrapper.h>
#include <vespa/searchlib/queryeval/flow_tuning.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.disktermblueprint");

using search::BitVectorIterator;
using search::fef::TermFieldMatchDataArray;
using search::index::DictionaryLookupResult;
using search::index::Schema;
using search::queryeval::Blueprint;
using search::queryeval::BooleanMatchIteratorWrapper;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecBaseList;
using search::queryeval::LeafBlueprint;
using search::queryeval::SearchIterator;
using search::queryeval::flow::disk_index_cost;
using search::queryeval::flow::disk_index_strict_cost;

namespace search::diskindex {

namespace {

std::string
getName(uint32_t indexId)
{
    return vespalib::make_string("fieldId(%u)", indexId);
}

}

DiskTermBlueprint::DiskTermBlueprint(const FieldSpec & field,
                                     const FieldIndex& field_index,
                                     const std::string& query_term,
                                     DictionaryLookupResult lookupRes,
                                     bool useBitVector)
    : SimpleLeafBlueprint(field),
      _field(field),
      _field_index(field_index),
      _query_term(query_term),
      _lookupRes(std::move(lookupRes)),
      _bitvector_lookup_result(_field_index.lookup_bit_vector(_lookupRes)),
      _useBitVector(useBitVector),
      _fetchPostingsDone(false),
      _postingHandle(),
      _bitVector(),
      _mutex(),
      _late_bitvector()
{
    setEstimate(HitEstimate(_lookupRes.counts._numDocs,
                            _lookupRes.counts._numDocs == 0));
}

void
DiskTermBlueprint::log_bitvector_read() const
{
    auto range = _field_index.get_bitvector_file_range(_bitvector_lookup_result);
    LOG(debug, "DiskTermBlueprint::fetchPosting "
        "bitvector %s %s %" PRIu64 " %" PRIu64 " %" PRIu64 " %" PRIu32 " %" PRIu64 " %" PRIu64,
        _field.getName().c_str(), _query_term.c_str(), _field_index.get_file_id(),
        _lookupRes.wordNum, _lookupRes.counts._numDocs,
        _bitvector_lookup_result.idx,
        range.start_offset, range.size());

}

void
DiskTermBlueprint::log_posting_list_read() const
{
    auto range = _field_index.get_posting_list_file_range(_lookupRes);
    LOG(debug, "DiskTermBlueprint::fetchPosting "
        "posting %s %s %" PRIu64 " %" PRIu64 " %" PRIu64 " %" PRIu64 " %" PRIu64 " %" PRIu64 " %" PRIu64,
        _field.getName().c_str(), _query_term.c_str(), _field_index.get_file_id(),
        _lookupRes.wordNum, _lookupRes.counts._numDocs,
        _lookupRes.bitOffset, _lookupRes.counts._bitLength,
        range.start_offset, range.size());
}

void
DiskTermBlueprint::fetchPostings(const queryeval::ExecuteInfo &execInfo)
{
    (void) execInfo;
    if (!_fetchPostingsDone) {
        if (_useBitVector && _bitvector_lookup_result.valid()) {
            if (LOG_WOULD_LOG(debug)) [[unlikely]] {
                log_bitvector_read();
            }
            _bitVector = _field_index.read_bit_vector(_bitvector_lookup_result);
        }
        if (!_bitVector) {
            if (LOG_WOULD_LOG(debug)) [[unlikely]] {
                log_posting_list_read();
            }
            _postingHandle = _field_index.read_posting_list(_lookupRes);
        }
    }
    _fetchPostingsDone = true;
}

queryeval::FlowStats
DiskTermBlueprint::calculate_flow_stats(uint32_t docid_limit) const
{
    double rel_est = abs_to_rel_est(_lookupRes.counts._numDocs, docid_limit);
    return {rel_est, disk_index_cost(rel_est), disk_index_strict_cost(rel_est)};
}

const BitVector *
DiskTermBlueprint::get_bitvector() const
{
    if (_bitVector) {
        return _bitVector.get();
    }
    std::lock_guard guard(_mutex);
    if (!_late_bitvector) {
        if (LOG_WOULD_LOG(debug)) [[unlikely]] {
            log_bitvector_read();
        }
        _late_bitvector = _field_index.read_bit_vector(_bitvector_lookup_result);
        assert(_late_bitvector);
    }
    return _late_bitvector.get();
}

SearchIterator::UP
DiskTermBlueprint::createLeafSearch(const TermFieldMatchDataArray & tfmda) const
{
    if (_bitvector_lookup_result.valid() && (_useBitVector || tfmda[0]->isNotNeeded())) {
        LOG(debug, "Return BitVectorIterator: %s, wordNum(%" PRIu64 "), docCount(%" PRIu64 ")",
            getName(_field_index.get_field_id()).c_str(), _lookupRes.wordNum, _lookupRes.counts._numDocs);
        return BitVectorIterator::create(get_bitvector(), *tfmda[0], strict());
    }
    auto search(_field_index.create_iterator(_lookupRes, _postingHandle, tfmda));
    if (_useBitVector) {
        LOG(debug, "Return BooleanMatchIteratorWrapper: %s, wordNum(%" PRIu64 "), docCount(%" PRIu64 ")",
            getName(_field_index.get_field_id()).c_str(), _lookupRes.wordNum, _lookupRes.counts._numDocs);
        return std::make_unique<BooleanMatchIteratorWrapper>(std::move(search), tfmda);
    }
    LOG(debug, "Return posting list iterator: %s, wordNum(%" PRIu64 "), docCount(%" PRIu64 ")",
        getName(_field_index.get_field_id()).c_str(), _lookupRes.wordNum, _lookupRes.counts._numDocs);
    return search;
}

SearchIterator::UP
DiskTermBlueprint::createFilterSearch(FilterConstraint) const
{
    auto wrapper = std::make_unique<queryeval::FilterWrapper>(getState().numFields());
    auto & tfmda = wrapper->tfmda();
    if (_bitvector_lookup_result.valid()) {
        wrapper->wrap(BitVectorIterator::create(get_bitvector(), *tfmda[0], strict()));
    } else {
        wrapper->wrap(_field_index.create_iterator(_lookupRes, _postingHandle, tfmda));
    }
    return wrapper;
}

void
DiskTermBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    SimpleLeafBlueprint::visitMembers(visitor);
    visit(visitor, "field_name", _field.getName());
    visit(visitor, "query_term", _query_term);
}

} // namespace
