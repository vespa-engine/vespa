// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    wordchartokenizer.h
 * @brief   Tokenizer based on the unicode WORDCHAR property.
 */

#pragma once

#include "tokenizer.h"

#include <iostream>
#include <vector>
#include <string>
#include <algorithm>


namespace fsa {

// {{{ class WordCharTokenizer

/**
 * @class WordCharTokenizer
 * @brief Tokenizer based on the Unicode WORDCHAR property.
 */
class WordCharTokenizer : public Tokenizer {

public:
  /**
   * @brief Enumareted type for specifying puctuation removal strategy.
   *
   * Enumareted type for specifying puctuation removal strategy. The
   * following strategies are currently supported:
   *   - PUNCTUATION_DISCARD: discard all punctuation.
   *   - PUNCTUATION_FULL: honour all punctuation and insert
   *     punctuation token.
   *   - PUNCTUATION_SMART: same as PUNCTUATION_FULL, with some
   *     heuristics to not break acronyms and names.
   *   - PUNCTUATION_WHITESPACEONLY: treat everything (including
   *     punctuation) as word characters, except white space.
   */
  enum Punctuation {
    PUNCTUATION_DISCARD = 0,
    PUNCTUATION_FULL,
    PUNCTUATION_SMART,
    PUNCTUATION_WHITESPACEONLY
  };

private:

  static const bool _punctuation_table[];   /**< Table used for punctuation tests.              */

  std::vector<std::string>  _tokens;        /**< Vector holding the tokens.                     */
  unsigned int _current;                    /**< Index of current token.                        */
  Punctuation _punctuation;                 /**< Punctuation strategy.                          */
  std::string _punctuation_token;           /**< Special token for marking punctuation.         */
  bool _lowercase;                          /**< Indicator whether tokens should be lowercased. */

public:

  WordCharTokenizer(Punctuation punct = PUNCTUATION_DISCARD, const std::string &punct_token = ".") :
    _tokens(),
    _current(0),
    _punctuation(punct),
    _punctuation_token(punct_token),
    _lowercase(true)
  {}

  virtual ~WordCharTokenizer() {}

  Punctuation getPunctuation() const { return _punctuation; }
  void setPunctuation(Punctuation punct) { _punctuation=punct; }
  std::string getPunctuationToken() const { return _punctuation_token; }
  void setPunctuationToken(const std::string &punct_token) { _punctuation_token=punct_token; }
  void rewind() { _current=0; }
  void setLowerCase(bool lc) { _lowercase = lc; }
  bool getLowerCase() const { return _lowercase; }

  /**
   * @brief Initialize the tokenizer.
   *
   * @param text Input text.
   * @return True on success.
   */
  bool         init(const std::string &text) override;


  /**
   * @brief Check if there are more tokens available.
   *
   * @return True if there are more tokens.
   */
  bool         hasMore() override;

  /**
   * @brief Get next token.
   *
   * @return Next token, or empty string if there are no more tokens left.
   */
  std::string  getNext() override;

};

// }}}

} // namespace fsa

