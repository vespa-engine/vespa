// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    metadatamanager.h
 * @brief   Metadata manager class definition.
 *
 */

#pragma once

#include <string>
#include <map>

#include "singleton.h"
#include "rwlock.h"
#include "metadatahandle.h"

namespace fsa {

// {{{ class MetaDataManager

/**
 * @class MetaDataManager
 * @brief Class for managing generic metadata.
 *
 * This class provides a single point of access to all metadata
 * used by the applications.
 */
class MetaDataManager : public Singleton<MetaDataManager> {

protected:
  friend class Singleton<MetaDataManager>;

  /** Default constructor. Protected to avoid accidental creation */
  MetaDataManager() : _library(), _lock() {}

private:

  /** Private unimplemented copy constructor */
  MetaDataManager(const MetaDataManager&);
  /** Private unimplemented assignment operator */
  MetaDataManager& operator=(const MetaDataManager&);

  /** %MetaData library type */
  using Library = std::map<std::string,MetaData::Handle*>;
  /** %MetaData library iterator type */
  using LibraryIterator = std::map<std::string,MetaData::Handle*>::iterator;
  /** %MetaData library const iterator type */
  using LibraryConstIterator = std::map<std::string,MetaData::Handle*>::const_iterator;

  Library           _library;    /**< Library of MetaData objects.                 */
  mutable RWLock    _lock;       /**< Read-write lock for library synchronization. */

public:

  /** Destructor */
  ~MetaDataManager();

  /**
   * @brief Load a metadata file into memory.
   *
   * @param id MetaData id (to be used in later get() or drop() calls).
   * @param datafile Metadata file name
   */
  bool              load(const std::string &id, const std::string &datafile);

  /**
   * @brief Get a handle to metadata.
   *
   * @param id Metadata id.
   * @return Newly allocated handle, must be deleted by the
   *         caller. (NULL if no metadata with the given id was found.)
   */
  MetaData::Handle* get(const std::string &id) const;

  /**
   * @brief Drop a metadata from the library.
   *
   * Drop a metadata from the library. The metadata object will
   * be deleted automagically when there are no more handles referring
   * to it.
   *
   * @param id MetaData id.
   */
  void              drop(const std::string &id);

  /**
   * @brief Drop all metadatas from the library.
   */
  void              clear();

};

// }}}

} // namespace fsa

