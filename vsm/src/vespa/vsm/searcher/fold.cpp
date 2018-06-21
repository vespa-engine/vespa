// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#include "fold.h"

namespace vsm {

const unsigned char * sse2_foldaa(const unsigned char * toFoldOrg, size_t sz, unsigned char * foldedOrg)
{
  typedef char v16qi __attribute__ ((__vector_size__(16)));
  typedef long long v2di  __attribute__ ((__vector_size__(16)));
  static v16qi _G_0    = { '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1 };
  static v16qi _G_9    = { '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9' };
  static v16qi _G_a    = { 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1 };
  static v16qi _G_z    = { 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z' };
  static v16qi _G_8bit = { (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4,
                           (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4 };
  static v2di  _G_lowCase = { 0x2020202020202020ULL, 0x2020202020202020ULL };
  const v16qi *toFold = reinterpret_cast<const v16qi *>(toFoldOrg);
  v2di * folded =  reinterpret_cast<v2di *>(foldedOrg);
  size_t i=0;
  for (size_t m=sz/16; i < m; i++)
  {
#ifndef __INTEL_COMPILER
    int nonAscii = __builtin_ia32_pmovmskb128(toFold[i]);
    if (nonAscii)
    {
      v16qi non8Mask = __builtin_ia32_pcmpgtb128(_G_8bit, toFold[i]);
      int non8bit = __builtin_ia32_pmovmskb128(non8Mask);
      if (non8bit)
      {
        break;
      }
      break;
    }
    v16qi _0     = __builtin_ia32_pcmpgtb128(toFold[i], _G_0);
    v16qi _z     = __builtin_ia32_pcmpgtb128(toFold[i], _G_z);
    v2di  _0_z   = __builtin_ia32_pxor128(v2di(_0), v2di(_z));
    v2di  toLow  = __builtin_ia32_pand128(_0_z, v2di(toFold[i]));
    v16qi low    = v16qi(__builtin_ia32_por128(toLow, _G_lowCase));
    _0           = __builtin_ia32_pcmpgtb128(low, _G_0);
    v16qi _9     = __builtin_ia32_pcmpgtb128(low, _G_9);
    v16qi _a     = __builtin_ia32_pcmpgtb128(low, _G_a);
    _z           = __builtin_ia32_pcmpgtb128(low, _G_z);
    v2di  _0_9_m = __builtin_ia32_pxor128(v2di(_0), v2di(_9));
    v2di  _a_z_m = __builtin_ia32_pxor128(v2di(_a), v2di(_z));
    v2di  _0_9   = __builtin_ia32_pand128(_0_9_m, v2di(low));
    v2di  _a_z   = __builtin_ia32_pand128(_a_z_m, v2di(low));
    folded[i]    = __builtin_ia32_por128(_0_9, _a_z);
#else
#   warning "Intel's icc compiler does not like __builtin_ia32_pxor128"
    LOG_ABORT("should not be reached");
#endif
  }
  return toFoldOrg+i*16;
}

const unsigned char * sse2_foldua(const unsigned char * toFoldOrg, size_t sz, unsigned char * foldedOrg)
{
  typedef char v16qi __attribute__ ((__vector_size__(16)));
  typedef long long v2di  __attribute__ ((__vector_size__(16)));
  static v16qi _G_0    = { '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1, '0'-1 };
  static v16qi _G_9    = { '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9' };
  static v16qi _G_a    = { 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1, 'a'-1 };
  static v16qi _G_z    = { 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z', 'z' };
  static v16qi _G_8bit = { (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4,
                           (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4, (char)0xc4 };
  static v2di  _G_lowCase = { 0x2020202020202020ULL, 0x2020202020202020ULL };
  v2di * folded =  reinterpret_cast<v2di *>(foldedOrg);
  size_t i=0;
  for (size_t m=sz/16; i < m; i++)
  {
#ifndef __INTEL_COMPILER
    v16qi current = __builtin_ia32_loaddqu(reinterpret_cast<const char *>(&toFoldOrg[i*16]));
    int nonAscii = __builtin_ia32_pmovmskb128(current);
    if (nonAscii)
    {
      v16qi non8Mask = __builtin_ia32_pcmpgtb128(_G_8bit, current);
      int non8bit = __builtin_ia32_pmovmskb128(non8Mask);
      if (non8bit)
      {
        break;
      }
      break;
    }
    v16qi _0     = __builtin_ia32_pcmpgtb128(current, _G_0);
    v16qi _z     = __builtin_ia32_pcmpgtb128(current, _G_z);
    v2di  _0_z   = __builtin_ia32_pxor128(v2di(_0), v2di(_z));
    v2di  toLow  = __builtin_ia32_pand128(_0_z, v2di(current));
    v16qi low    = v16qi(__builtin_ia32_por128(toLow, _G_lowCase));
    _0           = __builtin_ia32_pcmpgtb128(low, _G_0);
    v16qi _9     = __builtin_ia32_pcmpgtb128(low, _G_9);
    v16qi _a     = __builtin_ia32_pcmpgtb128(low, _G_a);
    _z           = __builtin_ia32_pcmpgtb128(low, _G_z);
    v2di  _0_9_m = __builtin_ia32_pxor128(v2di(_0), v2di(_9));
    v2di  _a_z_m = __builtin_ia32_pxor128(v2di(_a), v2di(_z));
    v2di  _0_9   = __builtin_ia32_pand128(_0_9_m, v2di(low));
    v2di  _a_z   = __builtin_ia32_pand128(_a_z_m, v2di(low));
    folded[i]    = __builtin_ia32_por128(_0_9, _a_z);
#else
#   warning "Intel's icc compiler does not like __builtin_ia32_pxor128"
    LOG_ABORT("should not be reached");
#endif
  }
  return toFoldOrg+i*16;
}

}
