// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/util/exceptions.h>
#include <limits.h>

namespace search {
namespace attribute {

Config::Config() :
    _basicType(BasicType::NONE),
    _type(CollectionType::SINGLE),
    _fastSearch(false),
    _huge(false),
    _enableBitVectors(false),
    _enableOnlyBitVector(false),
    _isFilter(false),
    _fastAccess(false),
    _growStrategy(),
    _compactionStrategy(),
    _arity(8),
    _lower_bound(LLONG_MIN),
    _upper_bound(LLONG_MAX),
    _dense_posting_list_threshold(0.4),
    _tensorType(vespalib::eval::ValueType::error_type())
{
}

Config::Config(BasicType bt,
               CollectionType ct,
               bool fastSearch_,
               bool huge_)
    : _basicType(bt),
      _type(ct),
      _fastSearch(fastSearch_),
      _huge(huge_),
      _enableBitVectors(false),
      _enableOnlyBitVector(false),
      _isFilter(false),
      _fastAccess(false),
      _growStrategy(),
      _compactionStrategy(),
      _arity(8),
      _lower_bound(LLONG_MIN),
      _upper_bound(LLONG_MAX),
      _dense_posting_list_threshold(0.4),
      _tensorType(vespalib::eval::ValueType::error_type())
{
}

}
}
