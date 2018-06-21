// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "futf8strchrfieldsearcher.h"
#include "fold.h"

using vespalib::Optimized;
using search::byte;
using search::QueryTerm;
using search::v16qi;

namespace vsm {

IMPLEMENT_DUPLICATE(FUTF8StrChrFieldSearcher);

FUTF8StrChrFieldSearcher::FUTF8StrChrFieldSearcher()
    : UTF8StrChrFieldSearcher(),
      _folded(4096)
{ }
FUTF8StrChrFieldSearcher::FUTF8StrChrFieldSearcher(FieldIdT fId)
    : UTF8StrChrFieldSearcher(fId),
      _folded(4096)
{ }
FUTF8StrChrFieldSearcher::~FUTF8StrChrFieldSearcher() {}

bool
FUTF8StrChrFieldSearcher::ansiFold(const char * toFold, size_t sz, char * folded)
{
  bool retval(true);
  for(size_t i=0; i < sz; i++) {
    byte c = toFold[i];
    if (c>=128) { retval = false; break; }
    folded[i] = FieldSearcher::_foldLowCase[c];
  }
  return retval;
}

bool
FUTF8StrChrFieldSearcher::lfoldaa(const char * toFold, size_t sz, char * folded, size_t & unalignedStart)
{
  bool retval(true);
  unalignedStart = (size_t(toFold) & 0xF);
  size_t unalignedsz = std::min(sz, (16 - unalignedStart) & 0xF);

  size_t foldedUnaligned = (size_t(folded) & 0xF);
  unalignedStart = (foldedUnaligned < unalignedStart) ? (unalignedStart-foldedUnaligned) : unalignedStart + 16 - foldedUnaligned;
  size_t alignedStart = unalignedStart+unalignedsz;

  size_t alignedsz = sz - unalignedsz;
  size_t alignsz16 = alignedsz & 0xFFFFFFF0;
  size_t rest = alignedsz - alignsz16;

  if (unalignedStart) {
    retval = ansiFold(toFold, unalignedsz, folded + unalignedStart);
  }
  if (alignsz16 && retval) {
    const byte * end = sse2_foldaa(reinterpret_cast<const byte *>(toFold+unalignedsz), alignsz16, reinterpret_cast<byte *>(folded+alignedStart));
    retval = (end == reinterpret_cast<const byte *>(toFold+unalignedsz+alignsz16));
  }
  if(rest && retval) {
    retval = ansiFold(toFold + unalignedsz + alignsz16, rest, folded+alignedStart+alignsz16);
  }
  return retval;
}

bool
FUTF8StrChrFieldSearcher::lfoldua(const char * toFold, size_t sz, char * folded, size_t & alignedStart)
{
  bool retval(true);

  alignedStart =  0xF - (size_t(folded + 0xF) % 0x10);

  size_t alignsz16 = sz & 0xFFFFFFF0;
  size_t rest = sz - alignsz16;

  if (alignsz16) {
    const byte * end = sse2_foldua(reinterpret_cast<const byte *>(toFold), alignsz16, reinterpret_cast<byte *>(folded+alignedStart));
    retval = (end == reinterpret_cast<const byte *>(toFold+alignsz16));
  }
  if(rest && retval) {
    retval = ansiFold(toFold + alignsz16, rest, folded+alignedStart+alignsz16);
  }
  return retval;
}

namespace {

inline const char * advance(const char * n, const v16qi zero)
{
    uint32_t charMap = 0;
    unsigned zeroCountSum = 0;
    do { // find first '\0' character (the end of the word)
#ifndef __INTEL_COMPILER
        v16qi tmpCurrent = __builtin_ia32_loaddqu(n+zeroCountSum);
        v16qi tmp0       = __builtin_ia32_pcmpeqb128(tmpCurrent, reinterpret_cast<v16qi>(zero));
        charMap = __builtin_ia32_pmovmskb128(tmp0); // 1 in charMap equals to '\0' in input buffer
#else
#   warning "Intel's icc compiler does not like __builtin_ia32_xxxxx"
    LOG_ABORT("should not be reached");
#endif
        zeroCountSum += 16;
    } while (!charMap);
    int charCount = Optimized::lsbIdx(charMap); // number of word characters in last 16 bytes
    uint32_t zeroMap = ((~charMap) & 0xffff) >> charCount;

    int zeroCounter = Optimized::lsbIdx(zeroMap); // number of non-characters ('\0') in last 16 bytes
    int sum = zeroCountSum - 16 + charCount + zeroCounter;
    if (!zeroMap) { // only '\0' in last 16 bytes (no new word found)
        do { // find first word character (the next word)
#ifndef __INTEL_COMPILER
            v16qi tmpCurrent = __builtin_ia32_loaddqu(n+zeroCountSum);
            tmpCurrent  = __builtin_ia32_pcmpgtb128(tmpCurrent, reinterpret_cast<v16qi>(zero));
            zeroMap = __builtin_ia32_pmovmskb128(tmpCurrent); // 1 in zeroMap equals to word character in input buffer
#else
#   warning "Intel's icc compiler does not like __builtin_ia32_xxxxx"
    LOG_ABORT("should not be reached");
#endif
            zeroCountSum += 16;
        } while(!zeroMap);
        zeroCounter = Optimized::lsbIdx(zeroMap);
        sum = zeroCountSum - 16 + zeroCounter;
    }
    return n + sum;
}

}

size_t FUTF8StrChrFieldSearcher::match(const char *folded, size_t sz, QueryTerm & qt)
{
  const v16qi _G_zero  = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
  termcount_t words(0);
  const char * term;
  termsize_t tsz = qt.term(term);
  const char *et=term+tsz;
  const char * n = folded;
  const char *e = n + sz;

  while (!*n) n++;
  while (true) {
    if (n>=e) break;

#if 0
    v16qi current = __builtin_ia32_loaddqu(n);
    current = __builtin_ia32_pcmpeqb128(current, _qtlFast[0]);
    unsigned eqMap = __builtin_ia32_pmovmskb128(current);
    unsigned neqMap = ~eqMap;
    unsigned numEq = Optimized::lsbIdx(neqMap);
    /* if (eqMap)*/ {
      if (numEq >= 16) {
        const char *tt = term+16;
        const char *p = n+16;
        while ( (*tt == *p) && (tt < et)) { tt++; p++; numEq++; }
      }
      if ((numEq >= tsz) && (prefix() || qt.isPrefix() || !n[tsz])) {
        addHit(qt, words);
      }
    }
#else
    const char *tt = term;
    while ((tt < et) && (*tt == *n)) { tt++; n++; }
    if ((tt == et) && (prefix() || qt.isPrefix() || !*n)) {
      addHit(qt, words);
    }
#endif
    words++;
    n = advance(n, _G_zero);
  }
  return words;
}

size_t FUTF8StrChrFieldSearcher::match(const char *folded, size_t sz, size_t mintsz, QueryTerm ** qtl, size_t qtlSize)
{
  (void) mintsz;
  const v16qi _G_zero  = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
  termcount_t words(0);
  const char * n = folded;
  const char *e = n + sz;
  while (!*n) n++;
  for( ; ; ) {
    if (n>=e) break;
#if 0
    v16qi current = __builtin_ia32_loaddqu(n);
    for(size_t i=0; i < qtlSize; i++) {
      v16qi tmpEq = __builtin_ia32_pcmpeqb128(current, _qtlFast[i]);
      unsigned eqMap = __builtin_ia32_pmovmskb128(tmpEq);
      /* if (eqMap) */ {
        QueryTerm & qt = *qtl[i];
        unsigned neqMap = ~eqMap;
        unsigned numEq = Optimized::lsbIdx(neqMap);
        termsize_t tsz = qt.termLen();
        if (numEq >= 16) {
          const char *tt = qt.term() + 16;
          const char *et=tt+tsz;
          const char *p = n+16;
          while ( (*tt == *p) && (tt < et)) { tt++; p++; numEq++; }
        }
        if ((numEq >= tsz) && (prefix() || qt.isPrefix() || !n[tsz])) {
          addHit(qt, words);
        }
      }
    }
#else
    for(QueryTerm ** it=qtl, ** mt=qtl+qtlSize; it != mt; it++) {
      QueryTerm & qt = **it;
      const char * term;
      termsize_t tsz = qt.term(term);

      const char *et=term+tsz;
      const char *fnt;
      for (fnt = n; (term < et) && (*term == *fnt); term++, fnt++);
      if ((term == et) && (prefix() || qt.isPrefix() || !*fnt)) {
        addHit(qt, words);
      }
    }
#endif
    words++;
    n = advance(n, _G_zero);
  }
  return words;
}

size_t FUTF8StrChrFieldSearcher::matchTerm(const FieldRef & f, QueryTerm & qt)
{
  _folded.reserve(f.size()+16*3);  //Enable fulle xmm0 store
  size_t unalignedStart(0);
  bool ascii7Bit = lfoldua(f.c_str(), f.size(), &_folded[0], unalignedStart);
  if (ascii7Bit) {
    char * folded = &_folded[unalignedStart];
    /// Add the pattern 00 01 00 to avoid multiple eof tests of falling off the edge.
    folded[f.size()] = 0;
    folded[f.size()+1] = 0x01;
    memset(folded + f.size() + 2, 0, 16); // initialize padding data to avoid valgrind complaining about uninitialized values
    return match(folded, f.size(), qt);
    NEED_CHAR_STAT(addPureUsAsciiField(f.size()));
  } else {
    return UTF8StrChrFieldSearcher::matchTerm(f, qt);
  }
}

size_t FUTF8StrChrFieldSearcher::matchTerms(const FieldRef & f, const size_t mintsz)
{
  _folded.reserve(f.size()+16*3);  //Enable fulle xmm0 store
  size_t unalignedStart(0);
  bool ascii7Bit = lfoldua(f.c_str(), f.size(), &_folded[0], unalignedStart);
  if (ascii7Bit) {
    char * folded = &_folded[unalignedStart];
    /// Add the pattern 00 01 00 to avoid multiple eof tests of falling off the edge.
    folded[f.size()] = 0;
    folded[f.size()+1] = 0x01;
    memset(folded + f.size() + 2, 0, 16); // initialize padding data to avoid valgrind complaining about uninitialized values
    return match(folded, f.size(), mintsz, &_qtl[0], _qtl.size());
    NEED_CHAR_STAT(addPureUsAsciiField(f.size()));
  } else {
    return UTF8StrChrFieldSearcher::matchTerms(f, mintsz);
  }
}

}
