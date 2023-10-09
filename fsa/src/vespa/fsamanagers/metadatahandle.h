// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    metadatamanager.h
 * @brief   Metadata handle class definition.
 *
 */

#pragma once

#include <string>

#include "refcountable.h"
#include <vespa/fsa/metadata.h>

namespace fsa {

// {{{ class MetaData::Handle

/**
 * @class Handle
 * @brief MetaData handle.
 *
 * A Handle looks like a MetaData, but copies are cheap; the actual
 * MetaData objects are refcounted and Handle copies merely copy the
 * MetaData pointer and increment the refcount.
 */
class MetaData::Handle {

private:

  /**
   * @brief Unimplemented private default constructor.
   */
  Handle();
  /**
   * @brief Unimplemented private assignment operator.
   */
  Handle& operator=(const Handle&);

  class RefCountableMetaData: public MetaData, public RefCountable<MetaData> {
  public:
    RefCountableMetaData(const char *datafile, FileAccessMethod fam = FILE_ACCESS_UNDEF) : MetaData(datafile,fam) {}
  };

  RefCountableMetaData *_metaData; /**< The MetaData object itself. */

public:

  /**
   * @brief Copy constructor.
   *
   * Duplicate a handle (and add new reference to the MetaData object.
   *
   * @param h Reference to existing Metadata::Handle.
   */
  Handle(const Handle& h) : _metaData(h._metaData)
  {
    _metaData->addReference();
  }

  /**
   * @brief Constructor.
   *
   * Create a new MetaData object (loaded from file) and add reference.
   *
   * @param datafile Name of the file containing the metadata.
   * @param fam File access mode (read or mmap). If not set, the
   *            global preferred access mode will be used.
   */
  Handle(const char *datafile, FileAccessMethod fam = FILE_ACCESS_UNDEF) :
    _metaData(new RefCountableMetaData(datafile,fam))
  {
    _metaData->addReference();
  }

  /**
   * @brief Constructor.
   *
   * Create a new MetaData object (loaded from file) and add reference.
   *
   * @param datafile Name of the file containing the metadata.
   * @param fam File access mode (read or mmap). If not set, the
   *            global preferred access mode will be used.
   */
  Handle(const std::string &datafile, FileAccessMethod fam = FILE_ACCESS_UNDEF) :
    _metaData(new RefCountableMetaData(datafile.c_str(),fam))
  {
    _metaData->addReference();
  }

  /**
   * @brief Destructor.
   */
  ~Handle(void)
  {
    _metaData->removeReference();
  }

  /**
   * @brief Dereference operator, provides access to Metadata
   *        methods.
   *
   * @return Reference to the Metadata object.
   */
  const MetaData& operator*() const { return *_metaData; }

  /**
   * @brief Dereference operator, provides access to Metadata
   *        methods.
   *
   * @return Pointer the Metadata object.
   */
  const MetaData* operator->() const { return _metaData; }

  /**
   * @brief Proxy methods
   */
  uint32_t user(unsigned int idx) const
  {
    return _metaData->user(idx);
  }
};

// }}}

} // namespace fsa

