// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "generationholder.h"

namespace vespalib {

GenerationHeldBase::~GenerationHeldBase(void)
{
}

GenerationHeldMalloc::GenerationHeldMalloc(size_t size, void *data)
    : GenerationHeldBase(size),
      _data(data)
{
}

GenerationHeldMalloc::~GenerationHeldMalloc(void)
{
    free(_data);
};

GenerationHolder::GenerationHolder(void)
    : _hold1List(),
      _hold2List()
{
}

GenerationHolder::~GenerationHolder(void)
{
    assert(_hold1List.empty());
    assert(_hold2List.empty());
}

void
GenerationHolder::hold(GenerationHeldBase::UP data)
{
    _hold1List.push_back(GenerationHeldBase::SP(data.release()));
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
        _hold2List.erase(_hold2List.begin());
    }
}

void
GenerationHolder::clearHoldLists(void)
{
    _hold1List.clear();
    _hold2List.clear();
}

size_t
GenerationHolder::getHeldBytes(void) const
{
    size_t ret = 0;
    HoldList::const_iterator it(_hold1List.begin());
    HoldList::const_iterator ite(_hold1List.end());
    for (; it != ite; ++it) {
        assert((*it)->_generation == 0u);
        ret += (*it)->getSize();
    }
    it =_hold2List.begin();
    ite = _hold2List.end();
    for (; it != ite; ++it) {
        ret += (*it)->getSize();
    }
    return ret;
}

}
