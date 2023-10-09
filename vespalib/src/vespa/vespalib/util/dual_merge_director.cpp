// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dual_merge_director.h"

namespace vespalib {

bool
DualMergeDirector::MergeState::merge()
{
    if (second.thread_id < first.thread_id) {
        std::swap(first, second);
    }
    first.source->merge(*second.source);
    second = TaggedSource();
    return (state != LAST);
}

void
DualMergeDirector::MixedMergeStateExchanger::mingle()
{
    out(0) = MergeState(last ? MergeState::LAST : MergeState::TYPEA,
                        in(0).first, in(1).first);
    out(1) = MergeState(last ? MergeState::LAST : MergeState::TYPEB,
                        in(0).second, in(1).second);
}

void
DualMergeDirector::MergeStateExchanger::mingle()
{
    bool last = (--remaining == 0);
    size_t dst = (in(0).first.thread_id < in(1).first.thread_id) ? 0 : 1;
    out(dst) = MergeState(last ? MergeState::LAST : in(0).state,
                          in(dst).first, in(1 - dst).first);
}

DualMergeDirector::DualMergeDirector(size_t num_threads)
    : _num_threads(num_threads),
      _mixedExchanger(num_threads == 2),
      _typeAExchanger((num_threads - 1) / 2),
      _typeBExchanger((num_threads - 1) / 2)
{
}

DualMergeDirector::~DualMergeDirector() {}

void
DualMergeDirector::dualMerge(size_t thread_id, Source &typeA, Source &typeB)
{
    if (_num_threads == 1) {
        return;
    }
    if (((_num_threads % 2) == 1) && ((thread_id + 1) == _num_threads)) {
        _typeAExchanger.rendezvous(MergeState(MergeState::TYPEA,
                                              TaggedSource(thread_id, typeA)));
        _typeBExchanger.rendezvous(MergeState(MergeState::TYPEB,
                                              TaggedSource(thread_id, typeB)));
        return;
    }
    MergeState state(MergeState::MIXED, TaggedSource(thread_id, typeA),
                     TaggedSource(thread_id, typeB));
    state = _mixedExchanger.rendezvous(state);
    MergeStateExchanger &exchanger = (state.state == MergeState::TYPEA) ?
                                     _typeAExchanger : _typeBExchanger;
    while (state.merge()) {
        state = exchanger.rendezvous(state);
        if (state.state == MergeState::EMPTY) {
            return;
        }
    }
}

} // namespace vespalib
