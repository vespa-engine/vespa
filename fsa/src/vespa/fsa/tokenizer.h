// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    tokenizer.h
 * @brief   Generic tokenizer class.
 */

#pragma once

#include <iostream>
#include <vector>
#include <string>
#include <algorithm>


namespace fsa {

// {{{ class Tokenizer

/**
 * @class Tokenizer
 * @brief Generic tokenizer class.
 *
 * Generic interface to various tokenizer implementations.
 */
class Tokenizer {

public:

  /**
   * @brief Constructor.
   */
  Tokenizer() {}

  /**
   * @brief Destructor.
   */
  virtual ~Tokenizer() {}

  /**
   * @brief Initialize the tokenizer.
   *
   * @param text Input text.
   * @return True on success.
   */
  virtual bool         init(const std::string &text) = 0;

  /**
   * @brief Check if there are more tokens available.
   *
   * @return True if there are more tokens.
   */
  virtual bool         hasMore() = 0;

  /**
   * @brief Get next token.
   *
   * @return Next token, or empty string if there are no more tokens left.
   */
  virtual std::string  getNext() = 0;

};

// }}}

} // namespace fsa

