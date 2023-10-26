// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    blob.h
 * @brief   Definition of Blob class
 *
 */

#pragma once

#include <string.h>
#include <stdlib.h>

#include <string>

namespace fsa {

// {{{ class Blob

/**
 * @class Blob
 * @brief %Blob (binary large object) class.
 *
 * Representation of a blob (binary large object). Supports assign
 * method, access to size and data, and comparison operators.
 */
class Blob {
private:
  /** Size of data. */
  unsigned int  _size;
  /** Pointer to the data. */
  void*   _data;
public:

  /**
   * @brief Default constructor
   *
   * Creates an empty blob.
   */
  Blob() : _size(0), _data(NULL) {}

  /**
   * @brief Constructor
   *
   * Creates a blob from a character string. The string must be zero
   * terminated.
   *
   * @param str Pointer to input string.
   */
  Blob(const char *str) : _size(strlen(str)+1), _data((void*)strdup(str)) {}

  /**
   * @brief Constructor
   *
   * Creates a blob from arbitrary data.
   *
   * @param data Pointer to data.
   * @param size Size of the data.
   */
  Blob(const void *data, unsigned int size) : _size(size), _data(malloc(size))
  { memcpy(_data,data,_size); }

  /**
   * @brief Copy constructor
   *
   * @param b Blob to copy.
   */
  Blob(const Blob& b) : _size(b._size), _data(malloc(_size))
  { memcpy(_data,b._data,_size); }

  /**
   * @brief Constructor
   *
   * Creates a blob from std::string.
   *
   * @param s Reference to input string.
   */
  Blob(const std::string &s) : _size(s.size()), _data(malloc(_size))
  { s.copy((char*)_data,_size); }

  /** Destructor */
  ~Blob() { if(_data!=NULL) free(_data); }

  /**
   * @brief Get data size.
   *
   * @return Data size.
   */
  unsigned int size() const { return _size; }

  /**
   * @brief Get data.
   *
   * @return Pointer to data. Valid as long as the blob object exists
   *         and is not modified.
   */
  const void*  data() const { return _data; }

  /**
   * @brief Reassign the blob.
   *
   * @param s Input string
   */
  void assign(const std::string &s)
  {
    if(_data!=NULL) free(_data);
    _size=s.size();
    _data=malloc(s.size());
    s.copy((char*)_data,_size);
  }

  /**
   * @brief Less-than operator.
   *
   * @param b Blob to compare.
   */
  bool operator<(const Blob& b) const;

  /**
   * @brief Greater-than operator.
   *
   * @param b Blob to compare.
   */
  bool operator>(const Blob& b) const;

  /**
   * @brief Equals operator.
   *
   * @param b Blob to compare.
   */
  bool operator==(const Blob& b) const;

};

// }}}

} // namespace fsa

