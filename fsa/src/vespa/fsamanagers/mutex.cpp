// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/07
 * @version $Id$
 * @file    mutex.cpp
 * @brief   Mutex.
 *
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifndef DISABLE_THREADS
#include <pthread.h>
#include <sched.h>
#include <cassert>
#endif

#include "mutex.h"

namespace fsa {

// {{{ class Mutex::Impl

struct Mutex::Impl
{
#ifndef DISABLE_THREADS
  pthread_mutex_t _mutex; /**< lock */
#else
  int _mutex;
#endif
};

// }}}

Mutex::Mutex(void) : _impl(new Impl)
{
#ifndef DISABLE_THREADS
  int rc;
  rc = pthread_mutex_init(&(_impl->_mutex),NULL);
  assert(rc == 0);
#endif
}

Mutex::~Mutex(void)
{
#ifndef DISABLE_THREADS
  pthread_mutex_destroy(&(_impl->_mutex));
#endif
  delete _impl;
}

bool Mutex::tryLock (void)
{
#ifndef DISABLE_THREADS
  return pthread_mutex_trylock(&(_impl->_mutex)) == 0;
#else
  return true;
#endif
}

bool Mutex::lock (void)
{
#ifndef DISABLE_THREADS
  return pthread_mutex_lock(&(_impl->_mutex)) == 0;
#else
  return true;
#endif
}

bool Mutex::unlock (void)
{
#ifndef DISABLE_THREADS
  return pthread_mutex_unlock(&(_impl->_mutex)) == 0;
#else
  return true;
#endif
}

} // namespace fsa
