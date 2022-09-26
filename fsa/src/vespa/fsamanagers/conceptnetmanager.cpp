// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    conceptnetmanager.cpp
 * @brief   Concept network manager class implementation.
 *
 */

#include "conceptnetmanager.h"

namespace fsa {

// {{{ ConceptNetManager::~ConceptNetManager()

ConceptNetManager::~ConceptNetManager()
{
  for(LibraryIterator it=_library.begin(); it!=_library.end();++it){
    delete it->second;
  }
}

// }}}

// {{{ ConceptNetManager::load()

bool ConceptNetManager::load(const std::string &id, const std::string &fsafile, const std::string &datafile)
{
  ConceptNet::Handle *newcn = new ConceptNet::Handle(fsafile.c_str(), datafile.length()>0?datafile.c_str():NULL);

  if(newcn==NULL || !(*newcn)->isOk()){
    delete newcn;
    return false;
  }

  _lock.wrLock();
  {
    LibraryIterator it = _library.find(id);
    if(it!=_library.end()){
      delete it->second;
      it->second = newcn;
    }
    else
      _library.insert(Library::value_type(id,newcn));
  }
  _lock.unlock();

  return true;
}

// }}}
// {{{ ConceptNetManager::get()

ConceptNet::Handle* ConceptNetManager::get(const std::string &id) const
{
  ConceptNet::Handle *newhandle=NULL;
  _lock.rdLock();
  {
    LibraryConstIterator it = _library.find(id);
    if(it!=_library.end()){
      newhandle = new ConceptNet::Handle(*(it->second));
    }
  }
  _lock.unlock();
  return newhandle;
}

// }}}
// {{{ ConceptNetManager::drop()

void ConceptNetManager::drop(const std::string &id)
{
  _lock.wrLock();
  {
    LibraryIterator it = _library.find(id);
    if(it!=_library.end()){
      delete it->second;
      _library.erase(it);
    }
  }
  _lock.unlock();
}

// }}}
// {{{ ConceptNetManager::clear()

void ConceptNetManager::clear()
{
  _lock.wrLock();
  {
    for(LibraryIterator it = _library.begin(); it!=_library.end(); ++it)
      delete it->second;
    _library.clear();
  }
  _lock.unlock();
}

// }}}

} // namespace fsa
