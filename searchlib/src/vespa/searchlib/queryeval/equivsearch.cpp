// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "equivsearch.h"
#include <vespa/vespalib/util/left_right_heap.h>

namespace search::queryeval {

template <bool strict, typename Parent>
class EquivImpl : public Parent
{
private:
    fef::MatchData::UP        _inputMatchData;
    fef::TermMatchDataMerger  _merger;
    bool                      _valid;

protected:
    void doUnpack(uint32_t docid) override;

public:
    /**
     * Create a new Equiv Search with the given children.
     *
     * @param children the search objects that should be equivalent
     **/
    EquivImpl(MultiSearch::Children children,
              fef::MatchData::UP inputMatchData,
              const fef::TermMatchDataMerger::Inputs &inputs,
              const fef::TermFieldMatchDataArray &outputs);
};

template<bool strict, typename Parent>
EquivImpl<strict, Parent>::EquivImpl(MultiSearch::Children children,
                                     fef::MatchData::UP inputMatchData,
                                     const fef::TermMatchDataMerger::Inputs &inputs,
                                     const fef::TermFieldMatchDataArray &outputs)

  : Parent(std::move(children), NoUnpack()),
    _inputMatchData(std::move(inputMatchData)),
    _merger(inputs, outputs),
    _valid(outputs.valid())
{
}

template<bool strict, typename Parent>
void
EquivImpl<strict, Parent>::doUnpack(uint32_t docid)
{
    if (_valid) {
        MultiSearch::doUnpack(docid);
        _merger.merge(docid);
    }
}

SearchIterator::UP
EquivSearch::create(Children children,
                    fef::MatchData::UP inputMatchData,
                    const fef::TermMatchDataMerger::Inputs &inputs,
                    const fef::TermFieldMatchDataArray &outputs,
                    bool strict)
{
    if (strict) {
        if (children.size() < 0x70) {
            using Parent = StrictHeapOrSearch<NoUnpack, vespalib::LeftArrayHeap, uint8_t>;
            return std::make_unique<EquivImpl<true, Parent>>(std::move(children), std::move(inputMatchData), inputs, outputs);
        } else {
            using Parent = StrictHeapOrSearch<NoUnpack, vespalib::LeftHeap, uint32_t>;
            return std::make_unique<EquivImpl<true, Parent>>(std::move(children), std::move(inputMatchData), inputs, outputs);
        }
    } else {
        using Parent = OrLikeSearch<false, NoUnpack>;
        return std::make_unique<EquivImpl<false, Parent>>(std::move(children), std::move(inputMatchData), inputs, outputs);
    }
}

}
