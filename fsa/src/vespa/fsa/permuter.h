// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    permuter.h
 * @brief   Permuter class.
 */

#pragma once

#include <vector>
#include <map>
#include <string>


namespace fsa {

// {{{ class Permuter

/**
 * @class Permuter
 * @brief Permuter class.
 */
class Permuter {
private:

  static const unsigned int MAX_UNIT_LENGTH = 6;

  using PermTab = std::vector<std::string>;
  using PermTabIterator = std::vector<std::string>::iterator;
  using PermMap = std::map<std::string,unsigned int>;
  using PermMapIterator = std::map<std::string,unsigned int>::iterator;
  using PermMapConstIterator = std::map<std::string,unsigned int>::const_iterator;

  PermTab        _permtab;
  PermMap        _permmap;
  unsigned int   _size;
  std::string    _seed;

  void initRec(const std::string &input, std::string tail);
	
public:
  /**
   * @brief Default constructor.
   */
  Permuter();

  /**
   * @brief Destructor.
   */
  ~Permuter();

  std::string getPerm(unsigned int id) const { return _permtab[id]; }
  int getPermId(const std::string &perm) const;

  static unsigned int firstComb(unsigned int n, unsigned int m);
  static unsigned int nextComb(unsigned int c, unsigned int m);

};

// }}}

} // namespace fsa

