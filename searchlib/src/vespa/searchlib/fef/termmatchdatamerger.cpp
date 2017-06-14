// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termmatchdatamerger.h"

namespace search {
namespace fef {

TermMatchDataMerger::TermMatchDataMerger(const Inputs &allinputs,
                                         const TermFieldMatchDataArray &outputs)
    : _inputs(),
      _output(outputs),
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

TermMatchDataMerger::~TermMatchDataMerger() {}

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
    for (size_t i = 0; i < in.size(); ++i) {
        const TermFieldMatchData *md = in[i].matchData;
        if (md->getDocId() == docid) {
            for (const TermFieldMatchDataPosition &iter : *md) {
                double exactness = in[i].exactness * iter.getMatchExactness();
                _scratch.push_back(iter);
                _scratch.back().setMatchExactness(exactness);
            }
            wasMatch = true;
        }
    }
    if (wasMatch) {
        out.reset(docid);
        if (_scratch.size() > 0) {
            std::sort(_scratch.begin(), _scratch.end(),
                      TermFieldMatchDataPosition::compareWithExactness);
            TermFieldMatchDataPosition prev = _scratch[0];
            for (size_t i = 1; i < _scratch.size(); ++i) {
                const TermFieldMatchDataPosition &curr = _scratch[i];
                if (prev.key() < curr.key()) {
                    out.appendPosition(prev);
                    prev = curr;
                }
            }
            out.appendPosition(prev);
        }
    }
}

} // namespace fef
} // namespace search
