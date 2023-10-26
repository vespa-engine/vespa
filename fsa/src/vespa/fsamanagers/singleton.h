// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/05
 * @version $Id$
 * @file    singleton.h
 * @brief   Singleton pattern.
 */


#pragma once

#include <list>

#include "mutex.h"


namespace fsa {

// {{{ class SingletonExitHandler

/**
 * @class SingletonExitHandler
 * @brief %Singleton exit handler.
 *
 * %Singleton exit handler. Uses the atexit() librarary call to
 * destory all Singleton objects in reverse order as they were
 * created. It is also a singleton self.
 */
class SingletonExitHandler
{
private:

  /** Default constructor */
  SingletonExitHandler();

  /** Method to call at exit, destroys all Singletons. */
  static void atExit();

  /** Instance pointer */
  static SingletonExitHandler* _instance;

  /** Destroy method -  does the dirty work */
  void destroy();


  using FunctionList = std::list<void(*)()>;
  using FunctionListIterator = std::list<void(*)()>::iterator;

  /** List of Singleton destroy functions */
  FunctionList _functionList;

public:

  /** Destructor */
  virtual ~SingletonExitHandler();

  /**
   * @brief Get instance pointer.
   *
   * @return pointer to instance.
   */
  static SingletonExitHandler* instance();

  /**
   * @brief Register a singleton.
   *
   * @param p Pointer to destroy function of the singleton.
   */
  void registerSingletonDestroyer(void (*p)());

};

// }}}

// {{{ class Singleton

/**
 * @class Singleton
 * @brief %Singleton template.
 *
 * %Singleton template (from Design Patterns by Gamma et al.). To use
 * it, subclass as follows, and make constructors private:
 *
 *  class MyClass : public Singleton<MyClass> {
 *    friend class Singleton<MyClass>;
 *  private:
 *    MyClass();
 *  public:
 *    void MyMethod();
 *  ...
 *  }
 *
 * and then call MyMethod as:
 *
 *   MyClass::instance().MyMethod();
 *
 */
template<typename T>
class Singleton
{
  /** SingletonExitHandler handles destruction. */
  friend class SingletonExitHandler;

public:
  /** Destructor */
  virtual ~Singleton();

  /**
   * @brief Get reference to the instance.
   *
   * Get reference to the instance. The first call of this method will
   * create the instance, and register the destroy function with the
   * exit handler.
   *
   * @return Reference to the instance.
   */
  static T& instance();

protected:

  /** Explicit constructor (to avoid implicit conversion). */
  explicit Singleton();

private:

  /** Copy constructor (unimplemented) */
  Singleton(const Singleton&);
  /** Assignment operator (unimplemented) */
  Singleton& operator=(const Singleton&);

  /** Destroy function - this will be registered with the exit handler. */
  static void destroy();

  static Mutex _lock;  /**< Mutex for synchronization. */

  static T* _instance; /**< Instance pointer.          */
};


template<typename T> Singleton<T>::Singleton() {}

template<typename T> Singleton<T>::~Singleton() {}

template<typename T> void Singleton<T>::destroy()
{
  delete _instance;
  _instance = NULL;
}

template<typename T> T& Singleton<T>::instance()
{
  if (_instance == NULL) {
    _lock.lock();
    if (_instance == NULL) {
      SingletonExitHandler::instance()->registerSingletonDestroyer(&destroy);
      _instance = new T();
    }
    _lock.unlock();
  }

  return *_instance;
}

template<typename T> T* Singleton<T>::_instance = NULL;

template<typename T> Mutex Singleton<T>::_lock;

// }}}

} // namespace fsa

