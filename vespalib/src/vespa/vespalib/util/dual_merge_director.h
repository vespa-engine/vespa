// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rendezvous.h"

namespace vespalib {

/**
 * Utility used to direct multi-threaded merging of 2 separate sources
 * of information. Each thread calls the dualMerge function with its
 * own thread id and sources. The first source of each thread is
 * ultimately merged into the first source of thread 0. The second
 * sources are handled the same way and the result ends up in the
 * second source of thread 0. The sources may be of different
 * types. Note that the dualMerge function will return when the
 * director no longer needs the calling thread. In order to wait for
 * the completion of the overall merge operation, external
 * synchronization is needed.
 **/
class DualMergeDirector
{
public:
    struct Source {
        virtual void merge(Source &rhs) = 0;
        virtual ~Source() {}
    };

private:
    struct TaggedSource {
        size_t thread_id;
        Source *source;
        TaggedSource(size_t t, Source &s) : thread_id(t), source(&s) {}
        TaggedSource() : thread_id(-1), source(0) {}
    };

    struct MergeState {
        enum State { MIXED, TYPEA, TYPEB, LAST, EMPTY } state;
        TaggedSource first;
        TaggedSource second;
        MergeState() : state(EMPTY), first(), second() {}
        MergeState(State s, const TaggedSource &a)
            : state(s), first(a), second() {}
        MergeState(State s, const TaggedSource &a, const TaggedSource &b)
            : state(s), first(a), second(b) {}
        bool merge();
    };

    struct MixedMergeStateExchanger : Rendezvous<MergeState, MergeState> {
        bool last;
        MixedMergeStateExchanger(bool v) :
            Rendezvous<MergeState, MergeState>(2), last(v) {}
        void mingle() override;
    };

    struct MergeStateExchanger : Rendezvous<MergeState, MergeState> {
        size_t remaining;
        MergeStateExchanger(size_t r) :
            Rendezvous<MergeState, MergeState>(2), remaining(r) {}
        void mingle() override;
    };

    size_t                   _num_threads;
    MixedMergeStateExchanger _mixedExchanger;
    MergeStateExchanger      _typeAExchanger;
    MergeStateExchanger      _typeBExchanger;

public:
    DualMergeDirector(size_t num_threads);
    ~DualMergeDirector();
    void dualMerge(size_t thread_id, Source &typeA, Source &typeB);
};

} // namespace vespalib

