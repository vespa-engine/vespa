// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/07
 * @version $Id$
 * @file    mutex.h
 * @brief   Mutex.
 *
 */

#pragma once

// {{{ class Mutex

namespace fsa {

/**
 * @class Mutex
 * @brief Mutex.
 *
 * Simple mutex class based on POSIX pthread_mutex_t.
 */
class Mutex
{
 protected:
  struct Impl;
  Impl *_impl;

 public:
  /**
   * @brief Constructor
   */
  Mutex(void);

  /**
   * @brief Destructor
   */
  ~Mutex(void);

  /**
   * @brief Try to get a lock.
   *
   * Try to get a lock. This method is non-blocking, and
   * returns true if locking was succesful.
   *
   * @return True if locking was successful.
   */
  bool tryLock (void);

  /**
   * @brief Get a lock.
   *
   * Get a read (shared) lock. This method blocks until a
   * lock is available (that is no other thread holds a
   * lock on the object.)
   *
   * @return True if locking was successful.
   */
  bool lock (void);

  /**
   * @brief Release a lock.
   *
   * @return True if unlocking was successful.
   */
  bool unlock (void);

};

// }}}

} // namespace fsa

