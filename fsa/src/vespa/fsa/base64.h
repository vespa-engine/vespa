// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    base64.h
 * @brief   Definition of Base64 class
 *
 */

#pragma once

#include <string>

namespace fsa {

/**
 * @class Base64
 * @brief Base64 encoding and decoding.
 *
 * Encode and decode arbitrary binary strings to %Base64.
 */
class Base64 {
private:
  /** Encoing table */
  static const unsigned char _table[];
  /** Padding character */
  static const unsigned char _padding;

  /** Decode one symbol */
  static inline int b2n(int b);
  /** Encode one symbol */
  static inline int n2b(int n);

public:

  /**
   * @brief Decode a %Base64 encoded string.
   *
   * @param src Source %Base64 encoded string.
   * @param dest Destination to hold the decoded string.
   * @return Size of destination string.
   */
  static int decode(const std::string &src, std::string &dest);

  /**
   * @brief Decode a %Base64 encoded string.
   *
   * @param src Source string.
   * @param dest Destination to hold %Base64 encoded string.
   * @return Size of destination string.
   */
  static int encode(const std::string &src, std::string &dest);

};

} // namespace fsa

