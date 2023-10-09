// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    ngram.h
 * @brief   n-gram class for tokenized text.
 */

#pragma once

#include <iostream>
#include <vector>
#include <string>
#include <algorithm>

#include "unicode.h"
#include "selector.h"
#include "permuter.h"
#include "tokenizer.h"

namespace fsa {

// {{{ class NGram

/**
 * @class NGram
 * @brief Class for representing n-grams.
 *
 * Supports tokenization and various manipulation methods, such as
 * join, sort, uniq, etc.
 */
class NGram {

public:

private:
  std::vector<std::string>  _tokens;        /**< Vector holding the tokens.             */

public:
  /**
   * @brief Default constructor, creates empty NGram.
   */
  NGram() : _tokens() {}

  /**
   * @brief Constructor.
   *
   * Creates an NGram object from a utf-8 encoded character
   * string. The string must be zero terminated. The string is
   * tokenized using unicode wordchar property. For certain puctuation
   * strategies, a special puctuation token is inserted if a puctuation
   * character is found.
   *
   * @param text Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  NGram(const char *text,
        unsigned int from=0, int length=-1);

  /**
   * @brief Constructor.
   *
   * Creates an NGram object from a utf-8 encoded character
   * string. The string must be zero terminated. The string is
   * tokenized using the supplied tokienizer.
   *
   * @param text Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   * @param tokenizer Tokenizer.
   */
  NGram(const char *text,
        Tokenizer &tokenizer,
        unsigned int from=0, int length=-1);

  /**
   * @brief (Sort of) Copy constructor.
   *
   * @param g NGram object to copy.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  NGram(const NGram &g, unsigned int from=0, int length=-1);

  /**
   * @brief (Sort of) Copy constructor.
   *
   * Copy selected tokens from an NGram objects.
   *
   * @param g NGram object to copy.
   * @param select Selector indicating which tokens to copy.
   */
  NGram(const NGram &g, const Selector &select);

  /**
   * @brief (Sort of) Copy constructor.
   *
   * Create a new NGram and permute the tokens.
   *
   * @param g NGram object to copy.
   * @param p Permuter object.
   * @param id Permutation ID.
   */
  NGram(const NGram &g, const Permuter &p, unsigned int id);

  /**
   * @brief Constructor.
   *
   * Creates an NGram object from a utf-8 encoded std::string. The
   * string is tokenized using unicode wordchar property. For certain
   * puctuation strategies, a special puctuation token is inserted if
   * a puctuation character is found.
   *
   * @param s Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  NGram(const std::string &s,
        unsigned int from=0, int length=-1);

  /**
   * @brief Constructor.
   *
   * Creates an NGram object from a utf-8 encoded std::string. The
   * string is tokenized using the supplied tokenizer.
   *
   * @param s Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   * @param tokenizer Tokenizer.
   */
  NGram(const std::string &s,
        Tokenizer &tokenizer,
        unsigned int from=0, int length=-1);

  /**
   * @brief Set the object.
   *
   * Reinitalizes the NGram object from a utf-8 encoded character
   * string. The string must be zero terminated. The string is
   * tokenized using unicode wordchar property. For certain puctuation
   * strategies, a special puctuation token is inserted if a puctuation
   * character is found.
   *
   * @param text Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  void set(const char *text,
           unsigned int from=0, int length=-1);

  /**
   * @brief Set the object.
   *
   * Reinitalizes the NGram object from a utf-8 encoded character
   * string. The string must be zero terminated. The string is
   * tokenized using the supplied tokenizer.
   *
   * @param text Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   * @param tokenizer Tokenizer.
   */
  void set(const char *text,
           Tokenizer &tokenizer,
           unsigned int from=0, int length=-1);

  /**
   * @brief Set the object.
   *
   * @param g NGram object to copy.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  void set(const NGram &g, unsigned int from=0, int length=-1);

  /**
   * @brief Set the object.
   *
   * Copy selected tokens from an NGram objects.
   *
   * @param g NGram object to copy.
   * @param select Selector indicating which tokens to copy.
   */
  void set(const NGram &g, const Selector &select);

  /**
   * @brief Set the object.
   *
   * Set the object from another NGram with permuting the tokens.
   *
   * @param g NGram object to copy.
   * @param p Permuter object.
   * @param id Permutation ID.
   */
  void set(const NGram &g, const Permuter &p, unsigned int id);

  /**
   * @brief Set the object.
   *
   * Reinitalizes the NGram object from a utf-8 encoded
   * std::string. The string is tokenized using unicode wordchar
   * property. For certain puctuation strategies, a special puctuation
   * token is inserted if a puctuation character is found.
   *
   * @param s Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  void set(const std::string &s,
           unsigned int from=0, int length=-1);

  /**
   * @brief Set the object.
   *
   * Reinitalizes the NGram object from a utf-8 encoded
   * std::string. The string is tokenized using the supplied tokenizer.
   *
   * @param s Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   * @param tokenizer Tokenizer.
   */
  void set(const std::string &s,
           Tokenizer &tokenizer,
           unsigned int from=0, int length=-1);

  /**
   * @brief Set the object.
   *
   * Reinitalizes the object from an std::string, as a single token.
   *
   * @param s Input string.
   */
  void setOne(const std::string &s);

  /**
   * @brief Append tokens to the object.
   *
   * Appends tokens to the NGram object from a utf-8 encoded character
   * string. The string must be zero terminated. The string is
   * tokenized using unicode wordchar property. For certain puctuation
   * strategies, a special puctuation token is inserted if a
   * puctuation character is found.
   *
   * @param text Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  void append(const char *text,
              unsigned int from=0, int length=-1);

  /**
   * @brief Append tokens to the object.
   *
   * Appends tokens to the NGram object from a utf-8 encoded character
   * string. The string must be zero terminated. The string is
   * tokenized using the supplied tokenizer.
   *
   * @param text Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   * @param tokenizer Tokenizer.
   */
  void append(const char *text,
              Tokenizer &tokenizer,
              unsigned int from=0, int length=-1);

  /**
   * @brief Append tokens to the object.
   *
   * @param g NGram object to append.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  void append(const NGram &g, unsigned int from=0, int length=-1);

  /**
   * @brief Append tokens to the object.
   *
   * Append selected tokens from an NGram objects.
   *
   * @param g NGram object to append.
   * @param select Selector indicating which tokens to copy.
   */
  void append(const NGram &g, const Selector &select);

  /**
   * @brief Append tokens to the object.
   *
   * Append a permuted NGram.
   *
   * @param g NGram object to append.
   * @param p Permuter object.
   * @param id Permutation ID.
   */
  void append(const NGram &g, const Permuter &p, unsigned int id);

  /**
   * @brief Append tokens to the object.
   *
   * Appends tokens to the NGram object from a utf-8 encoded
   * std::string. The string is tokenized using unicode wordchar
   * property. For certain puctuation strategies, a special puctuation
   * token is inserted if a puctuation character is found.
   *
   * @param s Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   */
  void append(const std::string &s,
              unsigned int from=0, int length=-1);

  /**
   * @brief Append tokens to the object.
   *
   * Appends tokens to the NGram object from a utf-8 encoded
   * std::string. The string is tokenized using the supplied tokenizer.
   *
   * @param s Input text.
   * @param from Starting token to keep (preceeding tokens are ignored).
   * @param length Number of tokens to keep.
   * @param tokenizer Tokenizer.
   */
  void append(const std::string &s,
              Tokenizer &tokenizer,
              unsigned int from=0, int length=-1);

  /**
   * @brief Append a single token to the object.
   *
   * Appends a single token from an std::string.
   *
   * @param s Input string.
   */
  void appendOne(const std::string &s);


  /**
   * @brief Reset the object.
   */
  void clear() { _tokens.clear(); }

  /**
   * @brief Get the size of the n-gram (number of tokens).
   *
   * @return Number of tokens in n-gram.
   */
  unsigned int size() const { return _tokens.size(); }

  /**
   * @brief Get the length (size) of the n-gram (number of tokens).
   *
   * @return Number of tokens in n-gram.
   */
  unsigned int length() const { return _tokens.size(); }

  /**
   * @brief Sort the tokens lexicograpically.
   */
  void sort() { std::sort(_tokens.begin(),_tokens.end()); }

  /**
   * @brief Remove duplicate tokens from a sorted n-gram.
   */
  unsigned int uniq();

  /**
   * @brief Reverse the order of the tokens.
   */
  void reverse() { std::reverse(_tokens.begin(),_tokens.end()); }

  /**
   * @brief Join the whole or parts of the n-gram to single string.
   *
   * @param separator Separator string.
   * @param from Starting token (default 0).
   * @param length Number of tokens (default -1 which means all).
   * @return Joined tokens.
   */
  std::string join(const std::string &separator = " ",
                   unsigned int from=0, int length=-1) const;

  /**
   * @brief Index operator.
   *
   * Provides access a token directly. The index must be in the range
   * of 0..length()-1, this is not checked.
   *
   * @param i Index.
   * @return Reference to token.
   */
  std::string& operator[](unsigned int i) { return _tokens[i]; }

  /**
   * @brief Index operator.
   *
   * Provides const access a token directly. The index must be in the
   * range of 0..length()-1, this is not checked.
   *
   * @param i Index.
   * @return Const reference to token.
   */
  const std::string& operator[](unsigned int i) const { return _tokens[i]; }

  /**
   * @brief Get permutation ID to another n-gram.
   *
   * Get permutation ID to another n-gram. The other n-gram should
   * consist of the same tokens in different order.
   *
   * @param g The other n-gram.
   * @param p Permuter object.
   * @return Permutation ID.
   */
  int getPermIdTo(const NGram &g, const Permuter &p) const;

  /**
   * @brief Output operator.
   *
   * @param out Reference to output stream.
   * @param g n-gram.
   * @return Reference to output stream.
   */
  friend std::ostream& operator<<(std::ostream &out, const NGram &g);
};

// }}}

} // namespace fsa

