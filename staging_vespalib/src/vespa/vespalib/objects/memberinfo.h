// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

#define MI_TYPEID(t) MemberInfo::##t##_id
#define MI_M_OFFSET(c, m) static_cast<long>(&static_cast<const c *>(0)->m)
#define MI_M_SIZEOF(c, m) sizeof(static_cast<const c *>(0)->m)
#define MI_BASE_OFFSET(c,b) static_cast<long>(static_cast<const b *>(static_cast<const * c>(1)) - 1)

#define MI_DECLARE \
  static const MemberInfo _memberInfo[]; \
  static const unsigned   _memberCount;

#define MI_IMPL_BEGIN(c) const MemberInfo c::_memberInfo[] = { \
#define MI_IMPL_MEM(c, t, m) {#m, MI_TYPEID(t), MI_M_OFFSET(c, m), sizeof(m) }
#define MI_IMPL_END(c) }; const unsigned c::_memberCount = sizeof(cl::__memberDescription)/sizeof(cl::__memberDescription[0])


class MemberInfo {
 public:
  enum TypeId {
    bool_id = 1,
    char_id,
    int8_t_id,
    uint8_t_id,
    int16_t_id,
    uint16_t_id,
    int32_t_id,
    uint32_t_id,
    int64_t_id,
    uint64_t_id,
    float_id,
    double_id,
    std_string_id,
    vespalib_Identifiable_id
  };
  const char *         _name;
  TypeId               _type;
  size_t               _offset
  size_t               _sizeof;
};

}

