// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/12/17
 * @version $Id$
 * @file    metadata.h
 * @brief   Generic metadata associated with perfect hash values. The
 *          data structutre is completely up to the user, but it is
 *          usually an array of fixed size records indexed by the
 *          perfect hash value, or it contains an index which maps the
 *          perfect hash values to variable size records.
 *
 */

#pragma once

#include <stdlib.h>
#include "fsa.h"


namespace fsa {

// {{{ class MetaData

/**
 * @class MetaData
 * @brief Class for representing generic metadata.
 *
 * Generic metadata associated with perfect hash values. The data
 * structutre is completely up to the user, but it is usually an array
 * of fixed size records indexed by the perfect hash value, or it
 * contains an index which maps the perfect hash values to variable
 * size records.
 */
class MetaData {

public:

  class Handle; // defined in metadatahandle.h

private:
  static const uint32_t MAGIC = 0x873EA98B;    /**< Magic number identifying metadata net files. */

  static const FileAccessMethod _default_file_access_method = FILE_ACCESS_MMAP;   /**< Default file access method (read/mmap). */

  /**
   * @struct Header
   * @brief Concept net data file header.
   */
  struct Header {
    uint32_t _magic;             /**< Magic number.                        */
    uint32_t _version;           /**< Version number. (currently not used) */
    uint32_t _checksum;          /**< Checksum. (currently not used)       */
    uint32_t _size;              /**< Size of the data.                    */
    uint32_t _reserved[10];      /**< Reserved for later use.              */
    uint32_t _user[50];          /**< User defined fields.                 */
  };

  void   *_mmap_addr;            /**< mmap address, NULL is file has not been mmapped.   */
  size_t  _mmap_length;          /**< mmap length.                                       */

  bool    _ok;                   /**< Flag indicating successful initialization. */
  Header  _header;
  void   *_data;

  /**
   * @brief Reset the object.
   *
   * Resets the object to an empty %MetaData, and releases allocated memory.
   */
  void reset();

  /**
   * @brief Read the metadata file from disk.
   *
   * @param datafile Name of the metadata file.
   * @param fam File access mode (read or mmap). If not set, the
   *            global default access mode will be used.
   * @return True on success.
   */
  bool read(const char *datafile, FileAccessMethod fam = FILE_ACCESS_UNDEF);

  /**
   * @brief Unimplemented private default constructor.
   */
  MetaData();
  /**
   * @brief Unimplemented private copy constructor.
   */
  MetaData(const MetaData&);
  /**
   * @brief Unimplemented private assignment operator.
   */
  const MetaData& operator=(const MetaData&);

public:

  /**
   * @brief Constructor.
   *
   * @param datafile Metadata file.
   * @param fam File access mode (read or mmap). If not set, the
   *            global default access mode will be used.
   */
  MetaData(const char *datafile, FileAccessMethod fam = FILE_ACCESS_UNDEF);
  MetaData(const std::string &datafile, FileAccessMethod fam = FILE_ACCESS_UNDEF);

  /**
   * @brief Destructor.
   */
  virtual ~MetaData();

  /**
   * @brief Check if initialization was successful.
   *
   * @return True if the initialization of the object succeeded.
   */
  bool isOk() const
  {
    return _ok;
  }

  /**
   * @brief Get user defined header field
   *
   * @param idx Field index
   * @return Header field value.
   */
  uint32_t user(unsigned int idx) const
  {
    if(_ok && idx<50)
      return _header._user[idx];
    else
      return 0;
  }

  uint32_t getUIntEntry(uint32_t idx) const
  {
    if(_ok){
      return ((const uint32_t*)_data)[idx];
    }
    else
      return 0;
  }

  const void *getDirectRecordEntry(uint32_t idx, uint32_t size) const
  {
    if(_ok)
      return (const void*)((const uint8_t*)_data+idx*size);
    else
      return NULL;
  }

  const void *getIndirectRecordEntry(uint32_t idx) const
  {
    if(_ok){
      uint32_t offset=((const uint32_t*)_data)[idx];
      return (const void*)((const uint8_t*)_data+offset);
    }
    else
      return NULL;
  }

  const char *getCharPtrEntry(uint32_t offset) const
  {
    if(_ok)
      return ((const char*)_data)+offset;
    else
      return NULL;
  }

};

// }}}

} // namespace fsa

