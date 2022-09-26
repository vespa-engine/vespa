// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/07
 * @version $Id$
 * @file    mutex.cpp
 * @brief   Mutex.
 *
 */

#include <pthread.h>
#include <sched.h>
#include <cassert>

#include "mutex.h"

namespace fsa {

// {{{ class Mutex::Impl

struct Mutex::Impl
{
  pthread_mutex_t _mutex; /**< lock */
};

// }}}

Mutex::Mutex(void) : _impl(new Impl)
{
  int rc;
  rc = pthread_mutex_init(&(_impl->_mutex),NULL);
  assert(rc == 0);
}

Mutex::~Mutex(void)
{
  pthread_mutex_destroy(&(_impl->_mutex));
  delete _impl;
}

bool Mutex::tryLock (void)
{
  return pthread_mutex_trylock(&(_impl->_mutex)) == 0;
}

bool Mutex::lock (void)
{
  return pthread_mutex_lock(&(_impl->_mutex)) == 0;
}

bool Mutex::unlock (void)
{
  return pthread_mutex_unlock(&(_impl->_mutex)) == 0;
}

} // namespace fsa
