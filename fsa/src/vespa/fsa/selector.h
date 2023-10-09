// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    selector.h
 * @brief   Selector class.
 */

#pragma once

#include <vector>

namespace fsa {

// {{{ class Selector


/**
 * @class Selector
 * @brief Simple (bitmap-like) selector class.
 */
class Selector {

 private:

  /** Selector */
  std::vector<bool> _selector;

 public:

  /**
   * @brief Default constructor.
   */
  Selector() : _selector() {}

  /**
   * @brief Copy constructor.
   *
   * @param s Selector to copy.
   */
  Selector(const Selector &s) : _selector(s._selector) {}

  /**
   * @brief Constructor.
   *
   * Set the selector from a bitmask.
   *
   * @param c Bitmask.
   */
  Selector(unsigned int c) : _selector() { set(c); }

  /**
   * @brief Destructor.
   */
  ~Selector() {}


  /**
   * @brief Clear the selector.
   */
  void clear();

  /**
   * @brief Set selector from bitmask.
   *
   * @param c Bitmask.
   */
  void set(unsigned int c);

  /**
   * @brief Get size of selector.
   *
   * @return Size.
   */
  unsigned int size() const { return _selector.size(); }

  /**
   * @brief Set an item in the selector.
   *
   * @param i Index.
   */
  void select(unsigned int i);

  /**
   * @brief Unset an item in the selector.
   *
   * @param i Index.
   */
  void unselect(unsigned int i);

  /**
   * @brief Get an item.
   *
   * @param i Index.
   * @return Item.
   */
  bool  operator[](unsigned int i) const;

};

// }}}

} // namespace fsa

