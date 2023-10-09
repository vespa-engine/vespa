// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/07
 * @version $Id$
 * @file    fsamanager.h
 * @brief   Class definition of the %FSA manager
 *
 */

#pragma once

#include <string>
#include <map>

#include "singleton.h"
#include "rwlock.h"
#include "fsahandle.h"

namespace fsa {

// {{{ class FSAManager

/**
 * @class FSAManager
 * @brief Class for managing finite state automata.
 *
 * This class provides a single point of access to all finite state
 * automata used by the applications. Supports loading fsa files and
 * downloading from the net if libcurl support is built in, in which
 * case the files are cached in a local cache directory. FSAManager is
 * implemented as a singleton.
 */
class FSAManager : public Singleton<FSAManager> {

protected:
  friend class Singleton<FSAManager>;

  /** Default constructor. Protected to avoid accidental creation */
  FSAManager() : _library(), _lock(), _cacheDir(), _cacheLock() {}

private:

  /** Private unimplemented copy constructor */
  FSAManager(const FSAManager&);
  /** Private unimplemented assignment operator */
  FSAManager& operator=(const FSAManager&);

  /** %FSA library type */
  using Library = std::map<std::string,FSA::Handle*>;
  /** %FSA library iterator type */
  using LibraryIterator = std::map<std::string,FSA::Handle*>::iterator;
  /** %FSA library const iterator type */
  using LibraryConstIterator = std::map<std::string,FSA::Handle*>::const_iterator;

  Library           _library;    /**< Library of automata.                         */
  mutable RWLock    _lock;       /**< Read-write lock for library synchronization. */
  std::string       _cacheDir;   /**< Cache directory.                             */
  mutable Mutex     _cacheLock;  /**< Mutex for cache synchronization.             */

  /**
   * @brief Fetch an automaton from the net.
   *
   * @param url URL to automaton.
   * @param file Name of local file to store automaton.
   * @return True on success.
   */
  bool getUrl(const std::string &url, const std::string &file);

public:

  /** Destructor */
  ~FSAManager();

  /**
   * @brief Load automaton from file or fetch from the net.
   *
   * Load automaton from file or fetch from the net. If the url begins
   * with "http://", and libcurl support is compiled in, the automaton
   * is downloaded from the net an stored in the local cache, unless
   * an automaton with that filename already exist in the cache, in which
   * case the local copy is used. This behaviour is expected to change
   * in the future, and it will use the serial number from the fsa
   * header to decide whether an update is needed.
   *
   * If an automaton is already registered with the given ID, the old
   * one is dropped as soon as the new is loaded. This does not
   * effects handles to the old automaton which were acquired
   * previously, as the old automaton will stay in memory until all
   * handles are deleted.
   *
   * @param id Automaton ID (name) used by the application.
   * @param url File name or URL (the latter if it begins with "http://").
   * @return True on success.
   */
  bool         load(const std::string &id, const std::string &url);

  /**
   * @brief Get a handle to an automaton.
   *
   * @param id Automaton ID (name).
   * @return Pointer to a new handle to the automaton, or NULL if not found.
   *         The handle must be deleted when it is not needed
   *         anymore. (In fact it should be deleted and re-requested
   *         on a regular basis if automaton updates may be performed.)
   */
  FSA::Handle* get(const std::string &id) const;

  /**
   * @brief Drop an automaton from the library.
   *
   * Drop the automaton from the library. All new requests for the
   * given ID will receive a NULL handle after this operation (unless
   * an automaton with the same ID is later loaded again).
   *
   * @param id Automaton ID
   */
  void         drop(const std::string &id);

  /**
   * @brief Drop all automatons from the library.
   */
  void         clear();

  /**
   * @brief Set the local cache directory.
   *
   * Set the local cache directory (default is empty, which
   * corresponds to the CWD (current working directory).
   *
   * @param dir Cache directory.
   */
  void         setCacheDir(const std::string &dir);

};

// }}}

} // namespace fsa

