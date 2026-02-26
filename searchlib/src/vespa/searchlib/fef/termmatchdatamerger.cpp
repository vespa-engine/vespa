// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termmatchdatamerger.h"
#include <vespa/searchlib/queryeval/element_id_extractor.h>
#include <algorithm>

using search::queryeval::ElementIdExtractor;

namespace search::fef {

TermMatchDataMerger::TermMatchDataMerger(const Inputs &allinputs,
                                         TermFieldMatchDataArray outputs)
    : _inputs(),
      _output(std::move(outputs)),
      _scratch()
{
    for (size_t i = 0; i < _output.size(); ++i) {
        Inputs inputs_for_i;
        uint32_t fieldId = _output[i]->getFieldId();

        for (size_t j = 0; j < allinputs.size(); ++j) {
            if (allinputs[j].matchData->getFieldId() == fieldId) {
                inputs_for_i.push_back(allinputs[j]);
            }
        }
        _inputs.push_back(inputs_for_i);
    }
}

TermMatchDataMerger::~TermMatchDataMerger() = default;

void
TermMatchDataMerger::merge(uint32_t docid)
{
    for (size_t i = 0; i < _output.size(); ++i) {
        merge(docid, _inputs[i], *(_output[i]));
    }
}

void
TermMatchDataMerger::merge(uint32_t docid,
                           const Inputs &in,
                           TermFieldMatchData &out)
{
    _scratch.clear();
    bool wasMatch = false;
    bool needs_normal_features = out.needs_normal_features();
    bool needs_interleaved_features = out.needs_interleaved_features();
    uint32_t num_occs = 0u;
    uint16_t field_length = 0u;
    for (size_t i = 0; i < in.size(); ++i) {
        const TermFieldMatchData *md = in[i].matchData;
        if (md->has_data(docid)) {
            if (needs_normal_features) {
                for (const TermFieldMatchDataPosition &iter : *md) {
                    double exactness = in[i].exactness * iter.getMatchExactness();
                    _scratch.push_back(iter);
                    _scratch.back().setMatchExactness(exactness);
                }
            }
            if (needs_interleaved_features) {
                num_occs += md->getNumOccs();
                field_length = std::max(field_length, md->getFieldLength());
            }
            wasMatch = true;
        }
    }
    if (wasMatch) {
        out.reset(docid);
        if (needs_normal_features) {
            num_occs = 0;
            if (_scratch.size() > 0) {
                std::sort(_scratch.begin(), _scratch.end(),
                          TermFieldMatchDataPosition::compareWithExactness);
                TermFieldMatchDataPosition prev = _scratch[0];
                for (size_t i = 1; i < _scratch.size(); ++i) {
                    const TermFieldMatchDataPosition &curr = _scratch[i];
                    if (prev.key() < curr.key()) {
                        out.appendPosition(prev);
                        prev = curr;
                        ++num_occs;
                    }
                }
                out.appendPosition(prev);
                ++num_occs;
            }
        }
        if (needs_interleaved_features) {
            constexpr uint32_t max_num_occs = std::numeric_limits<uint16_t>::max();
            uint16_t capped_num_occs = std::min(num_occs, max_num_occs);
            out.setNumOccs(std::min(capped_num_occs, field_length));
            out.setFieldLength(field_length);
        }
    }
}

}
