// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "equivsearch.h"

namespace search::queryeval {

template <bool strict>
class EquivImpl : public OrLikeSearch<strict, NoUnpack>
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
    EquivImpl(const MultiSearch::Children &children,
              fef::MatchData::UP inputMatchData,
              const fef::TermMatchDataMerger::Inputs &inputs,
              const fef::TermFieldMatchDataArray &outputs);
};

template<bool strict>
EquivImpl<strict>::EquivImpl(const MultiSearch::Children &children,
                             fef::MatchData::UP inputMatchData,
                             const fef::TermMatchDataMerger::Inputs &inputs,
                             const fef::TermFieldMatchDataArray &outputs)

    : OrLikeSearch<strict, NoUnpack>(children, NoUnpack()),
      _inputMatchData(std::move(inputMatchData)),
      _merger(inputs, outputs),
      _valid(outputs.valid())
{
}

template<bool strict>
void
EquivImpl<strict>::doUnpack(uint32_t docid)
{
    if (_valid) {
        MultiSearch::doUnpack(docid);
        _merger.merge(docid);
    }
}

SearchIterator *
EquivSearch::create(const Children &children,
                    fef::MatchData::UP inputMatchData,
                    const fef::TermMatchDataMerger::Inputs &inputs,
                    const fef::TermFieldMatchDataArray &outputs,
                    bool strict)
{
    if (strict) {
        return new EquivImpl<true>(children, std::move(inputMatchData), inputs, outputs);
    } else {
        return new EquivImpl<false>(children, std::move(inputMatchData), inputs, outputs);
    }
}

}
