// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#include "fold.h"

using search::v16qi;
using search::v2di;

namespace vsm {
namespace {

constexpr v16qi G_0    = { '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1 };
constexpr v16qi G_9    = { '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9' };
constexpr v16qi G_a    = { 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1 };
constexpr v16qi G_z    = { 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z' };
constexpr v16qi G_8bit = { (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4,
                            (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4 };
constexpr v2di  G_lowCase = { 0x2020202020202020ULL, 0x2020202020202020ULL };
}
const unsigned char * sse2_foldaa(const unsigned char * toFoldOrg, size_t sz, unsigned char * foldedOrg)
{
  const auto * toFold = reinterpret_cast<const v16qi *>(toFoldOrg);
  v2di * folded =  reinterpret_cast<v2di *>(foldedOrg);
  size_t i=0;
  for (size_t m=sz/16; i < m; i++)
  {
    int nonAscii = __builtin_ia32_pmovmskb128(toFold[i]);
    if (nonAscii)
    {
      v16qi non8Mask = G_8bit > toFold[i];

      int non8bit = __builtin_ia32_pmovmskb128(non8Mask);
      if (non8bit)
      {
        break;
      }
      break;
    }
    v16qi _0     = toFold[i] > G_0;
    v16qi _z     = toFold[i] > G_z;
    v2di  _0_z   = v2di(_0) ^ v2di(_z);
    v2di  toLow  = _0_z & v2di(toFold[i]);
    auto  low    = v16qi(toLow | G_lowCase);
    _0           = low > G_0;
    v16qi _9     = low > G_9;
    v16qi _a     = low > G_a;
    _z           = low > G_z;
    v2di  _0_9_m = v2di(_0) ^ v2di(_9);
    v2di  _a_z_m = v2di(_a) ^ v2di(_z);
    v2di  _0_9   = _0_9_m & v2di(low);
    v2di  _a_z   = _a_z_m & v2di(low);
    folded[i]    = _0_9 | _a_z;
  }
  return toFoldOrg+i*16;
}

const unsigned char * sse2_foldua(const unsigned char * toFoldOrg, size_t sz, unsigned char * foldedOrg)
{
  v2di * folded =  reinterpret_cast<v2di *>(foldedOrg);
  size_t i=0;
  for (size_t m=sz/16; i < m; i++)
  {
    v16qi current = __builtin_ia32_lddqu(reinterpret_cast<const char *>(&toFoldOrg[i*16]));
    int nonAscii = __builtin_ia32_pmovmskb128(current);
    if (nonAscii)
    {
      v16qi non8Mask = G_8bit > current;
      int non8bit = __builtin_ia32_pmovmskb128(non8Mask);
      if (non8bit)
      {
        break;
      }
      break;
    }
    v16qi _0     = current > G_0;
    v16qi _z     = current > G_z;
    v2di  _0_z   = v2di(_0) ^ v2di(_z);
    v2di  toLow  = _0_z & v2di(current);
    auto  low    = v16qi(toLow | G_lowCase);
    _0           = low > G_0;
    v16qi _9     = low > G_9;
    v16qi _a     = low > G_a;
    _z           = low > G_z;
    v2di  _0_9_m = v2di(_0) ^ v2di(_9);
    v2di  _a_z_m = v2di(_a) ^ v2di(_z);
    v2di  _0_9   = _0_9_m & v2di(low);
    v2di  _a_z   = _a_z_m & v2di(low);
    folded[i]    = _0_9 | _a_z;
  }
  return toFoldOrg+i*16;
}

}
