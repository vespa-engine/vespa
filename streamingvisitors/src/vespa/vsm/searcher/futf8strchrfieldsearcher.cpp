// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "futf8strchrfieldsearcher.h"
#ifdef __x86_64__
#include "fold.h"
#endif
#include <vespa/vespalib/util/size_literals.h>

using search::byte;
using search::streaming::QueryTerm;
using search::v16qi;
using vespalib::Optimized;

namespace vsm {

std::unique_ptr<FieldSearcher>
FUTF8StrChrFieldSearcher::duplicate() const
{
    return std::make_unique<FUTF8StrChrFieldSearcher>(*this);
}

FUTF8StrChrFieldSearcher::FUTF8StrChrFieldSearcher()
    : UTF8StrChrFieldSearcher(),
      _folded(4_Ki)
{ }
FUTF8StrChrFieldSearcher::FUTF8StrChrFieldSearcher(FieldIdT fId)
    : UTF8StrChrFieldSearcher(fId),
      _folded(4_Ki)
{ }
FUTF8StrChrFieldSearcher::~FUTF8StrChrFieldSearcher() = default;

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
  unalignedStart = (size_t(toFold) & 0xF);
#ifdef __x86_64__
  bool retval(true);
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
#else
  return ansiFold(toFold, sz, folded + unalignedStart);
#endif
}

bool
FUTF8StrChrFieldSearcher::lfoldua(const char * toFold, size_t sz, char * folded, size_t & alignedStart)
{
  alignedStart =  0xF - (size_t(folded + 0xF) % 0x10);
#ifdef __x86_64__
  bool retval(true);

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
#else
  return ansiFold(toFold, sz, folded + alignedStart);
#endif
}

namespace {

#ifdef __x86_64__
constexpr v16qi G_zero  = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
inline const char * advance(const char * n)
{
    uint32_t charMap = 0;
    unsigned zeroCountSum = 0;
    do { // find first '\0' character (the end of the word)
        v16qi tmpCurrent = __builtin_ia32_lddqu(n+zeroCountSum);
        v16qi tmp0       = tmpCurrent == G_zero;
        charMap = __builtin_ia32_pmovmskb128(tmp0); // 1 in charMap equals to '\0' in input buffer
        zeroCountSum += 16;
    } while (!charMap);
    int charCount = Optimized::lsbIdx(charMap); // number of word characters in last 16 bytes
    uint32_t zeroMap = ((~charMap) & 0xffff) >> charCount;

    int zeroCounter = Optimized::lsbIdx(zeroMap); // number of non-characters ('\0') in last 16 bytes
    int sum = zeroCountSum - 16 + charCount + zeroCounter;
    if (!zeroMap) { // only '\0' in last 16 bytes (no new word found)
        do { // find first word character (the next word)
            v16qi tmpCurrent = __builtin_ia32_lddqu(n+zeroCountSum);
            tmpCurrent  = tmpCurrent > G_zero;
            zeroMap = __builtin_ia32_pmovmskb128(tmpCurrent); // 1 in zeroMap equals to word character in input buffer
            zeroCountSum += 16;
        } while(!zeroMap);
        zeroCounter = Optimized::lsbIdx(zeroMap);
        sum = zeroCountSum - 16 + zeroCounter;
    }
    return n + sum;
}
#else
inline const char* advance(const char* n)
{
    const char* p = n;
    const char* zero = static_cast<const char *>(memchr(p, 0, 64_Ki));
    while (zero == nullptr) {
        p += 64_Ki;
        zero = static_cast<const char *>(memchr(p, 0, 64_Ki));
    }
    p = zero;
    while (*p == '\0') {
        ++p;
    }
    return p;
}
#endif

}

size_t FUTF8StrChrFieldSearcher::match(const char *folded, size_t sz, QueryTerm & qt)
{
  termcount_t words(0);
  const char * term;
  termsize_t tsz = qt.term(term);
  const char *et=term+tsz;
  const char * n = folded;
  const char *e = n + sz;

  while (!*n) n++;
  while (true) {
    if (n>=e) break;

    const char *tt = term;
    while ((tt < et) && (*tt == *n)) { tt++; n++; }
    if ((tt == et) && (prefix() || qt.isPrefix() || !*n)) {
      addHit(qt, words);
    }
    words++;
    n = advance(n);
  }
  return words;
}

size_t FUTF8StrChrFieldSearcher::match(const char *folded, size_t sz, size_t mintsz, QueryTerm ** qtl, size_t qtlSize)
{
  (void) mintsz;
  termcount_t words(0);
  const char * n = folded;
  const char *e = n + sz;
  while (!*n) n++;
  for( ; ; ) {
    if (n>=e) break;
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
    words++;
    n = advance(n);
  }
  return words;
}

size_t FUTF8StrChrFieldSearcher::matchTerm(const FieldRef & f, QueryTerm & qt)
{
  _folded.reserve(f.size()+16*3);  //Enable fulle xmm0 store
  size_t unalignedStart(0);
  bool ascii7Bit = lfoldua(f.data(), f.size(), &_folded[0], unalignedStart);
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
  bool ascii7Bit = lfoldua(f.data(), f.size(), &_folded[0], unalignedStart);
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
