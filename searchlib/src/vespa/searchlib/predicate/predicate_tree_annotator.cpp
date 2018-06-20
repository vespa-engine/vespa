// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_tree_annotator.h"
#include "predicate_index.h"
#include "predicate_range_expander.h"
#include "predicate_tree_analyzer.h"
#include <vespa/document/predicate/predicate.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.predicate.predicate_tree_annotator");

using document::Predicate;
using std::map;
using std::string;
using vespalib::slime::Inspector;
using vespalib::Memory;

namespace search {
namespace predicate {

using predicate::MIN_INTERVAL;
using predicate::MAX_INTERVAL;

namespace {

class PredicateTreeAnnotatorImpl {
    uint32_t _begin;
    uint32_t _end;
    uint32_t _left_weight;
    PredicateTreeAnnotations &_result;
    uint64_t _zStar_hash;
    bool _negated;
    bool _final_range_used;
    const std::map<std::string, int> &_size_map;
    TreeCrumbs _crumbs;
    int64_t _lower_bound;
    int64_t _upper_bound;
    uint16_t _interval_range;


    uint32_t makeMarker(uint32_t begin, uint32_t end) {
        return (begin << 16) | end;
    }
    uint32_t getCEnd() {
        if (!_final_range_used && _end == _interval_range) {
            _final_range_used = true;
            return _interval_range - 1;
        }
        return _left_weight + 1;
    }
    void addZstarIntervalIfNegated(uint32_t cEnd);

public:
    PredicateTreeAnnotatorImpl(const std::map<std::string, int> &size_map,
                               PredicateTreeAnnotations &result,
                               int64_t lower, int64_t upper, uint16_t interval_range);

    void assignIntervalMarkers(const vespalib::slime::Inspector &in);
};

void PredicateTreeAnnotatorImpl::addZstarIntervalIfNegated(uint32_t cEnd) {
    if (_negated) {
        auto it = _result.interval_map.find(_zStar_hash);
        if (it == _result.interval_map.end()) {
            it = _result.interval_map.insert(make_pair(
                            _zStar_hash, std::vector<Interval>())).first;
            _result.features.push_back(_zStar_hash);
        }
        auto &intervals = it->second;
        intervals.push_back(Interval{ makeMarker(cEnd, _begin - 1) });
        if (_end - cEnd != 1) {
            intervals.push_back(Interval{ makeMarker(0, _end) });
        }
        _left_weight += 1;
    }
}

PredicateTreeAnnotatorImpl::PredicateTreeAnnotatorImpl(
        const map<string, int> &size_map,
        PredicateTreeAnnotations &result,
        int64_t lower_bound, int64_t upper_bound, uint16_t interval_range)
    : _begin(MIN_INTERVAL),
      _end(interval_range),
      _left_weight(0),
      _result(result),
      _zStar_hash(PredicateIndex::z_star_compressed_hash),
      _negated(false),
      _final_range_used(false),
      _size_map(size_map),
      _crumbs(),
      _lower_bound(lower_bound),
      _upper_bound(upper_bound),
      _interval_range(interval_range) {
}

long getType(const Inspector &in, bool negated) {
    long type = in[Predicate::NODE_TYPE].asLong();
    if (negated) {
        if (type == Predicate::TYPE_CONJUNCTION) {
            return Predicate::TYPE_DISJUNCTION;
        } else if (type == Predicate::TYPE_DISJUNCTION) {
            return Predicate::TYPE_CONJUNCTION;
        }
    }
    return type;
}

void PredicateTreeAnnotatorImpl::assignIntervalMarkers(const Inspector &in) {
    switch (getType(in, _negated)) {
    case Predicate::TYPE_CONJUNCTION: {
        int crumb_size = _crumbs.size();
        uint32_t curr = _begin;
        size_t child_count = in[Predicate::CHILDREN].children();
        uint32_t begin = _begin;
        uint32_t end = _end;
        for (size_t i = 0; i < child_count; ++i) {
            _crumbs.setChild(i, 'a');
            if (i == child_count - 1) {  // Last child (may also be the only?)
                _begin = curr;
                _end = end;
                assignIntervalMarkers(in[Predicate::CHILDREN][i]);
                // No need to update/touch curr
            } else if (i == 0) {  // First child
                auto it = _size_map.find(_crumbs.getCrumb());
                assert (it != _size_map.end());
                uint32_t child_size = it->second;
                uint32_t next = _left_weight + child_size + 1;
                _begin = curr;
                _end = next - 1;
                assignIntervalMarkers(in[Predicate::CHILDREN][i]);
                curr = next;
            } else {  // Middle children
                auto it = _size_map.find(_crumbs.getCrumb());
                assert (it != _size_map.end());
                uint32_t child_size = it->second;
                uint32_t next = curr + child_size;
                _begin = curr;
                _end = next - 1;
                assignIntervalMarkers(in[Predicate::CHILDREN][i]);
                curr = next;
            }
            _crumbs.resize(crumb_size);
        }
        _begin = begin;
        break;
    }
    case Predicate::TYPE_DISJUNCTION: {
        // All OR children will have the same {begin, end} values, and
        // the values will be same as that of the parent OR node
        int crumb_size = _crumbs.size();
        for (size_t i = 0; i < in[Predicate::CHILDREN].children(); ++i) {
            _crumbs.setChild(i, 'o');
            assignIntervalMarkers(in[Predicate::CHILDREN][i]);
            _crumbs.resize(crumb_size);
        }
        break;
    }
    case Predicate::TYPE_FEATURE_SET: {
        uint32_t cEnd = _negated? getCEnd() : 0;
        Memory label_mem = in[Predicate::KEY].asString();
        string label(label_mem.data, label_mem.size);
        label.push_back('=');
        const size_t prefix_size = label.size();
        for (size_t i = 0; i < in[Predicate::SET].children(); ++i) {
            Memory value = in[Predicate::SET][i].asString();
            label.resize(prefix_size);
            label.append(value.data, value.size);
            uint64_t hash = PredicateHash::hash64(label);
            if (_result.interval_map.find(hash)
                == _result.interval_map.end()) {
                _result.features.push_back(hash);
            }
            _result.interval_map[hash].push_back(
                    { makeMarker(_begin, _negated? cEnd : _end) });
        }
        addZstarIntervalIfNegated(cEnd);
        _left_weight += 1;
        break;
    }
    case Predicate::TYPE_FEATURE_RANGE: {
        uint32_t cEnd = _negated? getCEnd() : 0;
        for (size_t i = 0; i < in[Predicate::HASHED_PARTITIONS].children();
             ++i) {
            uint64_t hash = in[Predicate::HASHED_PARTITIONS][i].asLong();
            _result.interval_map[hash].push_back(
                    { makeMarker(_begin, _negated? cEnd : _end) });
        }
        const Inspector& in_hashed_edges =
            in[Predicate::HASHED_EDGE_PARTITIONS];
        for (size_t i = 0; i < in_hashed_edges.children(); ++i){
            const Inspector& child = in_hashed_edges[i];
            uint64_t hash = child[Predicate::HASH].asLong();
            uint32_t payload = child[Predicate::PAYLOAD].asLong();
            _result.bounds_map[hash].push_back(
                    { makeMarker(_begin, _negated? cEnd : _end), payload });
        }
        uint32_t hash_count = in[Predicate::HASHED_PARTITIONS].children() +
                              in_hashed_edges.children();
        if (hash_count < 3) {  // three features takes more space than
                               // one stored range.
            for (size_t i = 0; i < in[Predicate::HASHED_PARTITIONS].children();
                 ++i) {
                _result.features.push_back(in[Predicate::HASHED_PARTITIONS][i]
                                           .asLong());
            }
            for (size_t i = 0; i < in_hashed_edges.children(); ++i) {
                _result.features.push_back(in_hashed_edges[i].asLong());
            }
        } else {
            bool has_min = in[Predicate::RANGE_MIN].valid();
            bool has_max = in[Predicate::RANGE_MAX].valid();
            _result.range_features.push_back(
                    {in[Predicate::KEY].asString(),
                     has_min? in[Predicate::RANGE_MIN].asLong() : _lower_bound,
                     has_max? in[Predicate::RANGE_MAX].asLong() : _upper_bound
                     });
        }
        addZstarIntervalIfNegated(cEnd);
        _left_weight += 1;
        break;
    }
    case Predicate::TYPE_NEGATION:
        _negated = !_negated;
        assignIntervalMarkers(in[Predicate::CHILDREN][0]);
        _negated = !_negated;
        break;
    }  // switch
}
}  // namespace


PredicateTreeAnnotations::PredicateTreeAnnotations(uint32_t mf, uint16_t ir)
    : min_feature(mf), interval_range(ir)
{}
PredicateTreeAnnotations::~PredicateTreeAnnotations(){}

void PredicateTreeAnnotator::annotate(const Inspector &in,
                                      PredicateTreeAnnotations &result,
                                      int64_t lower, int64_t upper) {
    PredicateTreeAnalyzer analyzer(in);
    uint32_t min_feature = static_cast<uint32_t>(analyzer.getMinFeature());
    // Size is as interval range (tree size is lower bound for interval range)
    int size = analyzer.getSize();
    assert(size <= UINT16_MAX && size > 0);
    uint16_t interval_range = static_cast<uint16_t>(size);

    PredicateTreeAnnotatorImpl
        annotator(analyzer.getSizeMap(), result, lower, upper, interval_range);
    annotator.assignIntervalMarkers(in);
    result.min_feature = min_feature;
    result.interval_range = interval_range;
}

}  // namespace predicate
}  // namespace search
