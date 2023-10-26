// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    conceptnetmanager.h
 * @brief   Concept network manager class definition.
 *
 */

#pragma once

#include <string>
#include <map>

#include "singleton.h"
#include "rwlock.h"
#include "conceptnethandle.h"

namespace fsa {

// {{{ class ConceptNetManager

/**
 * @class ConceptNetManager
 * @brief Class for managing concept networks.
 *
 * This class provides a single point of access to all concept networks
 * used by the applications.
 */
class ConceptNetManager : public Singleton<ConceptNetManager> {

protected:
  friend class Singleton<ConceptNetManager>;

  /** Default constructor. Protected to avoid accidental creation */
  ConceptNetManager() : _library(), _lock() {}

private:

  /** Private unimplemented copy constructor */
  ConceptNetManager(const ConceptNetManager&);
  /** Private unimplemented assignment operator */
  ConceptNetManager& operator=(const ConceptNetManager&);

  /** %ConceptNet library type */
  using Library = std::map<std::string,ConceptNet::Handle*>;
  /** %ConceptNet library iterator type */
  using LibraryIterator = std::map<std::string,ConceptNet::Handle*>::iterator;
  /** %ConceptNet library const iterator type */
  using LibraryConstIterator = std::map<std::string,ConceptNet::Handle*>::const_iterator;

  Library           _library;    /**< Library of concept networks.                 */
  mutable RWLock    _lock;       /**< Read-write lock for library synchronization. */

public:

  /** Destructor */
  ~ConceptNetManager();

  /**
   * @brief Load a concept network into memory.
   *
   * @param id Concept network id (to be used in later get() or drop() calls).
   * @param fsafile Concept net %FSA file name
   * @param datafile Concept net data file name (defaults to empty
   *                 string which means use the fsa file name but
   *                 replace .fsa extension with .dat).
   */
  bool                load(const std::string &id,
                           const std::string &fsafile,
                           const std::string &datafile=std::string(""));

  /**
   * @brief Get a handle to a concept net.
   *
   * @param id Concept net id.
   * @return Newly allocated handle, must be deleted by the
   *         caller. (NULL if no concept net with the given id was found.)
   */
  ConceptNet::Handle* get(const std::string &id) const;

  /**
   * @brief Drop a concept net from the library.
   *
   * Drop a concept net from the library. The concept net object will
   * be deleted automagically when there are no more handles referring
   * to it.
   *
   * @param id Concept net id.
   */
  void                drop(const std::string &id);

  /**
   * @brief Drop all concept nets from the library.
   */
  void                clear();

};

// }}}

} // namespace fsa

