// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threelevelcountbuffers.h"

namespace search::diskindex {

ThreeLevelCountWriteBuffers::
ThreeLevelCountWriteBuffers(EC &sse, EC &spe, EC &pe)
    : _sse(sse),
      _spe(spe),
      _pe(pe),
      _wcsse(sse),
      _wcspe(spe),
      _wcpe(pe),
      _ssHeaderLen(0u),
      _spHeaderLen(0u),
      _pHeaderLen(0u),
      _ssFileBitSize(0u),
      _spFileBitSize(0u),
      _pFileBitSize(0u)
{
    _wcsse.allocComprBuf();
    _sse.setWriteContext(&_wcsse);
    _sse.setupWrite(_wcsse);
    assert(_sse.getWriteOffset() == 0);

    _wcspe.allocComprBuf();
    _spe.setWriteContext(&_wcspe);
    _spe.setupWrite(_wcspe);
    assert(_spe.getWriteOffset() == 0);

    _wcpe.allocComprBuf();
    _pe.setWriteContext(&_wcpe);
    _pe.setupWrite(_wcpe);
    assert(_pe.getWriteOffset() == 0);
}

ThreeLevelCountWriteBuffers::~ThreeLevelCountWriteBuffers() = default;

void
ThreeLevelCountWriteBuffers::flush()
{
    _ssFileBitSize = _sse.getWriteOffset();
    _spFileBitSize = _spe.getWriteOffset();
    _pFileBitSize = _pe.getWriteOffset();
    _sse.padBits(128);
    _sse.flush();
    _spe.padBits(128);
    _spe.flush();
    _pe.padBits(128);
    _pe.flush();
}


void
ThreeLevelCountWriteBuffers::startPad(uint32_t ssHeaderLen,
                                      uint32_t spHeaderLen,
                                      uint32_t pHeaderLen)
{
    _sse.padBits(ssHeaderLen * 8);
    _spe.padBits(spHeaderLen * 8);
    _pe.padBits(pHeaderLen * 8);
    _ssHeaderLen = ssHeaderLen;
    _spHeaderLen = spHeaderLen;
    _pHeaderLen = pHeaderLen;
}


ThreeLevelCountReadBuffers::ThreeLevelCountReadBuffers(DC &ssd, DC &spd, DC &pd, ThreeLevelCountWriteBuffers &wb)
    : _ssd(ssd),
      _spd(spd),
      _pd(pd),
      _rcssd(ssd),
      _rcspd(spd),
      _rcpd(pd),
      _ssHeaderLen(wb._ssHeaderLen),
      _spHeaderLen(wb._spHeaderLen),
      _pHeaderLen(wb._pHeaderLen),
      _ssFileBitSize(wb._ssFileBitSize),
      _spFileBitSize(wb._spFileBitSize),
      _pFileBitSize(wb._pFileBitSize)
{
    ssd.setReadContext(&_rcssd);
    spd.setReadContext(&_rcspd);
    pd.setReadContext(&_rcpd);
    _rcssd.referenceWriteContext(wb._wcsse);
    _rcspd.referenceWriteContext(wb._wcspe);
    _rcpd.referenceWriteContext(wb._wcpe);
    ssd.skipBits(_ssHeaderLen * 8);
    spd.skipBits(_spHeaderLen * 8);
    pd.skipBits(_pHeaderLen * 8);
}


ThreeLevelCountReadBuffers::ThreeLevelCountReadBuffers(DC &ssd, DC &spd, DC &pd)
    : _ssd(ssd),
      _spd(spd),
      _pd(pd),
      _rcssd(ssd),
      _rcspd(spd),
      _rcpd(pd),
      _ssHeaderLen(0u),
      _spHeaderLen(0u),
      _pHeaderLen(0u),
      _ssFileBitSize(0u),
      _spFileBitSize(0u),
      _pFileBitSize(0u)
{
    ssd.setReadContext(&_rcssd);
    spd.setReadContext(&_rcspd);
    pd.setReadContext(&_rcpd);
}

ThreeLevelCountReadBuffers::~ThreeLevelCountReadBuffers() = default;

}
