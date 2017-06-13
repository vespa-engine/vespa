// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <vector>

namespace search
{

class Hit
{
 public:
  Hit(uint32_t pos_, uint32_t context_, int32_t weight_) :
    _position(pos_ | (context_<<24)),
    _weight(weight_)
  { }
  int32_t weight() const { return _weight; }
  uint32_t pos()     const { return _position; }
  uint32_t wordpos() const { return _position & 0xffffff; }
  uint32_t context() const { return _position >> 24; }
  bool operator < (const Hit & b) const { return cmp(b) < 0; }
 private:
  int cmp(const Hit & b) const { return _position - b._position; }
  uint32_t _position;
  int32_t  _weight;
};

typedef std::vector<Hit> HitList;

}

