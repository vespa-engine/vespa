// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    fsamanager.cpp
 * @brief
 *
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "fsamanager.h"

#ifdef HAVE_CURL
#include <stdio.h>
#include <unistd.h>
#include <curl/curl.h>
#include <curl/types.h>
#include <curl/easy.h>
#endif



namespace fsa {

// {{{ FSAManager::~FSAManager()

FSAManager::~FSAManager()
{
  for(LibraryIterator it=_library.begin(); it!=_library.end();++it){
    delete it->second;
  }
}

// }}}
// {{{ FSAManager::load()

bool FSAManager::load(const std::string &id, const std::string &url)
{
  std::string file=url;

#if ((__GNUG__ == 3 && __GNUC_MINOR__ >= 1) || __GNUG__ > 3)
  if(!url.compare(0,7,"http://"))
#else
  if(!url.compare("http://",0,7))
#endif
  {
    unsigned int pos=url.find_last_of('/');
    if(pos==url.size()-1) return false;
    _cacheLock.lock();
    file=_cacheDir;
    _cacheLock.unlock();
    if(file.size()>0 && file[file.size()-1]!='/') file+='/';
    file+=url.substr(pos+1);
    if(!getUrl(url,file)) return false;
  }

  FSA::Handle *newdict = new FSA::Handle(file);
  if(!newdict->isOk()){
    delete newdict;
    return false;
  }

  _lock.wrLock();
  {
    LibraryIterator it = _library.find(id);
    if(it!=_library.end()){
      delete it->second;
      it->second = newdict;
    }
    else
      _library.insert(Library::value_type(id,newdict));
  }
  _lock.unlock();

  return true;
}

// }}}
// {{{ FSAManager::getUrl()

bool FSAManager::getUrl(const std::string &url, const std::string &file)
{
#ifdef HAVE_CURL
  CURL *curl_handle;
  FILE *filehandle;
  long  response_code;

  filehandle = fopen(file.c_str(),"r");
  if(filehandle!=NULL){
    fclose(filehandle);
    return true;
  }

  filehandle = fopen(file.c_str(),"w");
  if(filehandle==NULL)
    return false;

  curl_handle  = curl_easy_init();

  curl_easy_setopt(curl_handle, CURLOPT_URL, url.c_str());
  curl_easy_setopt(curl_handle, CURLOPT_WRITEDATA, (void *)filehandle);
  curl_easy_setopt(curl_handle, CURLOPT_USERAGENT, "libfsa-url-agent/0.1");

  curl_easy_perform(curl_handle);

  curl_easy_getinfo(curl_handle, CURLINFO_RESPONSE_CODE, &response_code);

  curl_easy_cleanup(curl_handle);

  fclose(filehandle);

  if(response_code!=200){
    unlink(file.c_str());
    return false;
  }

  return true;
#else  // HAVE_CURL
  (void)url;(void)file;
  return false;
#endif // HAVE_CURL
}

// }}}
// {{{ FSAManager::get()

FSA::Handle* FSAManager::get(const std::string &id) const
{
  FSA::Handle *newhandle=NULL;
  _lock.rdLock();
  {
    LibraryConstIterator it = _library.find(id);
    if(it!=_library.end()){
      newhandle = new FSA::Handle(*(it->second));
    }
  }
  _lock.unlock();
  return newhandle;
}

// }}}
// {{{ FSAManager::drop()

void FSAManager::drop(const std::string &id)
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
// {{{ FSAManager::clear()

void FSAManager::clear()
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
// {{{ FSAManager::setCacheDir()

void FSAManager::setCacheDir(const std::string &dir)
{
  _cacheLock.lock();
  _cacheDir = dir;
  _cacheLock.unlock();
}

// }}}

} // namespace fsa
