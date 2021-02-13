// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generationholder.h"
#include <cassert>

namespace vespalib {

GenerationHeldBase::~GenerationHeldBase() = default;

GenerationHolder::GenerationHolder()
    : _hold1List(),
      _hold2List(),
      _heldBytes(0)
{ }

GenerationHolder::~GenerationHolder()
{
    assert(_hold1List.empty());
    assert(_hold2List.empty());
    assert(_heldBytes == 0);
}

void
GenerationHolder::hold(GenerationHeldBase::UP data)
{
    _hold1List.push_back(GenerationHeldBase::SP(data.release()));
    _heldBytes += _hold1List.back()->getSize();
}

void
GenerationHolder::transferHoldListsSlow(generation_t generation)
{
    HoldList::iterator it(_hold1List.begin());
    HoldList::iterator ite(_hold1List.end());
    HoldList &hold2List = _hold2List;
    for (; it != ite; ++it) {
        assert((*it)->_generation == 0u);
        (*it)->_generation = generation;
        hold2List.push_back(*it);
    }
    _hold1List.clear();
}

void
GenerationHolder::trimHoldListsSlow(generation_t usedGen)
{
    for (;;) {
        if (_hold2List.empty())
            break;
        GenerationHeldBase &first = *_hold2List.front();
        if (static_cast<sgeneration_t>(first._generation - usedGen) >= 0)
            break;
        _heldBytes -= first.getSize();
        _hold2List.erase(_hold2List.begin());
    }
}

void
GenerationHolder::clearHoldLists()
{
    _hold1List.clear();
    _hold2List.clear();
    _heldBytes = 0;
}

}
