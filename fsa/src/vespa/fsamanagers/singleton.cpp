// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/05
 * @version $Id$
 * @file    singleton.cpp
 * @brief   Singleton pattern.
 */

#include <stdlib.h>

#include "singleton.h"


namespace fsa {

// {{{ SingletonExitHandler::_instance

SingletonExitHandler* SingletonExitHandler::_instance = NULL;

// }}}

// {{{ SingletonExitHandler::SingletonExitHandler()

SingletonExitHandler::SingletonExitHandler()
  : _functionList()
{
    /*
     * This won't work as part of plugins.  When library is unloaded, the
     * registration remains, and the program will crash when trying to
     * exit.
     */
  atexit(&atExit);
}

// }}}
// {{{ SingletonExitHandler::~SingletonExitHandler()

SingletonExitHandler::~SingletonExitHandler()
{
}

// }}}
// {{{ SingletonExitHandler::instance()

SingletonExitHandler* SingletonExitHandler::instance()
{
  if (_instance == NULL) {
    _instance = new SingletonExitHandler();
  }
  return _instance;
}

// }}}
// {{{ SingletonExitHandler::registerSingletonDestroyer()

void SingletonExitHandler::registerSingletonDestroyer(void (*p)())
{
  _functionList.push_front(p);
}

// }}}
// {{{ SingletonExitHandler::atExit()

void SingletonExitHandler::atExit()
{
  SingletonExitHandler::instance()->destroy();
  delete SingletonExitHandler::instance();
}

// }}}
// {{{ SingletonExitHandler::destroy()

void SingletonExitHandler::destroy()
{
  for(FunctionListIterator iterator=_functionList.begin();
      iterator!=_functionList.end(); ++iterator) {
    (*iterator)();
  }

}

// }}}

} // namespace fsa
