// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threelevelcountbuffers.h"

namespace search::diskindex {

ThreeLevelCountWriteBuffers::ThreeLevelCountWriteBuffers(EC &sse, EC &spe, EC &pe)
    : _ss(sse),
      _sp(spe),
      _p(pe)
{
}

ThreeLevelCountWriteBuffers::~ThreeLevelCountWriteBuffers() = default;

void
ThreeLevelCountWriteBuffers::flush()
{
    _ss.flush();
    _sp.flush();
    _p.flush();
}

void
ThreeLevelCountWriteBuffers::startPad(uint32_t ssHeaderLen, uint32_t spHeaderLen, uint32_t pHeaderLen)
{
    _ss.start_pad(ssHeaderLen);
    _sp.start_pad(spHeaderLen);
    _p.start_pad(pHeaderLen);
}

ThreeLevelCountReadBuffers::ThreeLevelCountReadBuffers(DC &ssd, DC &spd, DC &pd, const ThreeLevelCountWriteBuffers &wb)
    : _ss(ssd, wb._ss),
      _sp(spd, wb._sp),
      _p(pd, wb._p)
{
}

ThreeLevelCountReadBuffers::~ThreeLevelCountReadBuffers() = default;

}
