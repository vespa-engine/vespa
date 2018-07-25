// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config.h"

namespace search::attribute {

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
    _predicateParams(),
    _tensorType(vespalib::eval::ValueType::error_type())
{
}

Config::Config(BasicType bt, CollectionType ct, bool fastSearch_, bool huge_)
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
      _predicateParams(),
      _tensorType(vespalib::eval::ValueType::error_type())
{
}

Config::Config(const Config &) = default;
Config & Config::operator = (const Config &) = default;
Config::~Config() = default;

}
