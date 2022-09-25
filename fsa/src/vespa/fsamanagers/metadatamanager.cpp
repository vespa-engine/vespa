// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    metadatamanager.cpp
 * @brief   Metadata manager class implementation.
 *
 */

#include "metadatamanager.h"

namespace fsa {

// {{{ MetaDataManager::~MetaDataManager()

MetaDataManager::~MetaDataManager()
{
  for(LibraryIterator it=_library.begin(); it!=_library.end();++it){
    delete it->second;
  }
}

// }}}

// {{{ MetaDataManager::load()

bool MetaDataManager::load(const std::string &id, const std::string &datafile)
{
  MetaData::Handle *newmd = new MetaData::Handle(datafile.c_str());

  if(newmd==NULL || !(*newmd)->isOk()){
    delete newmd;
    return false;
  }

  _lock.wrLock();
  {
    LibraryIterator it = _library.find(id);
    if(it!=_library.end()){
      delete it->second;
      it->second = newmd;
    }
    else
      _library.insert(Library::value_type(id,newmd));
  }
  _lock.unlock();

  return true;
}

// }}}
// {{{ MetaDataManager::get()

MetaData::Handle* MetaDataManager::get(const std::string &id) const
{
  MetaData::Handle *newhandle=NULL;
  _lock.rdLock();
  {
    LibraryConstIterator it = _library.find(id);
    if(it!=_library.end()){
      newhandle = new MetaData::Handle(*(it->second));
    }
  }
  _lock.unlock();
  return newhandle;
}

// }}}
// {{{ MetaDataManager::drop()

void MetaDataManager::drop(const std::string &id)
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
// {{{ MetaDataManager::clear()

void MetaDataManager::clear()
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
