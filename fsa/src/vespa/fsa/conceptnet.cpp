// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    conceptnet.cpp
 * @brief   Concept network class implementation.
 *
 */

#include "conceptnet.h"
#include "fstream"

#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/mman.h> // for ::mmap()
#include <sys/time.h>
#include <sys/resource.h> // for getrlimit(), setrlimit(), etc.

// define this at your own risk...
#undef NO_RANGE_CHECK

namespace fsa {

// {{{ constants

const uint32_t ConceptNet::MAGIC;

// }}}

// {{{ ConceptNet::ConceptNet()

ConceptNet::ConceptNet(const char *fsafile, const char *datafile, FileAccessMethod fam) :
  _mmap_addr(NULL), _mmap_length(0),
  _unit_fsa(fsafile,fam),
  _index_size(0), _index(NULL),
  _info_size(0), _info(NULL),
  _catindex_size(0), _catindex(NULL),
  _strings_size(0), _strings(NULL),
  _ok(false)
{
  _ok = _unit_fsa.isOk();
  if(_ok && datafile!=NULL)
    _ok = read(datafile,fam);
}

ConceptNet::ConceptNet(const std::string &fsafile, const std::string &datafile, FileAccessMethod fam) :
  _mmap_addr(NULL), _mmap_length(0),
  _unit_fsa(fsafile,fam),
  _index_size(0), _index(NULL),
  _info_size(0), _info(NULL),
  _catindex_size(0), _catindex(NULL),
  _strings_size(0), _strings(NULL),
  _ok(false)
{
  _ok = _unit_fsa.isOk();
  if(_ok)
    _ok = read(datafile.c_str(),fam);
}

// }}}
// {{{ ConceptNet::~ConceptNet()

ConceptNet::~ConceptNet()
{
  reset();
}

// }}}

// {{{ ConceptNet::reset()

void ConceptNet::reset()
{
  if(_mmap_addr!=NULL && _mmap_addr!=MAP_FAILED){
    munmap(_mmap_addr,_mmap_length);
  }
  else{
    delete[] _index;
    delete[] _info;
    delete[] _catindex;
    delete[] _strings;
  }
  _mmap_addr=NULL; _mmap_length=0;
  // leave _unit_fsa alone
  _index_size=0; _index=NULL;
  _info_size=0; _info=NULL;
  _catindex_size=0; _catindex=NULL;
  _strings_size=0; _strings=NULL;
  _ok=false;
}

// }}}
// {{{ ConceptNet::read()

bool ConceptNet::read(const char *datafile, FileAccessMethod fam)
{
  Header header;

  size_t r;

  reset(); //WATCHOUT: if reset() ever changes to unref _unit_fsa, we can't use it since the FSA is read in the constructor before we get here

  if(fam==FILE_ACCESS_UNDEF)
    fam=_default_file_access_method;

  if(datafile==NULL)
    return false;

  int fd = ::open(datafile,O_RDONLY);
  if(fd<0)
    return false;

  r=::read(fd,&header,sizeof(header));
  if(r!=sizeof(header) || header._magic!=ConceptNet::MAGIC){
    ::close(fd);
    return false;
  }

  _index_size = header._index_size;
  _info_size = header._info_size;
  _catindex_size = header._catindex_size;
  _strings_size = header._strings_size;

  if(fam==FILE_ACCESS_MMAP || fam==FILE_ACCESS_MMAP_WITH_MLOCK){
    _mmap_length =
      sizeof(header) +
      _index_size*sizeof(UnitData) +
      _info_size*sizeof(uint32_t) +
      _catindex_size*sizeof(uint32_t) +
      _strings_size*sizeof(char);
    _mmap_addr = ::mmap((void*)0, _mmap_length, PROT_READ, MAP_SHARED, fd, 0);
    if(_mmap_addr==MAP_FAILED){
      ::close(fd);
      reset();
      return false;
    }
    if(fam==FILE_ACCESS_MMAP_WITH_MLOCK){
      if(mlock(_mmap_addr, _mmap_length)<0) {
        /* try to increase RLIMIT_MEMLOCK then mlock() again */
        struct rlimit rl;
        if(getrlimit(RLIMIT_MEMLOCK, &rl) >= 0) {
          rl.rlim_cur += _mmap_length + getpagesize();
          rl.rlim_max += _mmap_length + getpagesize();
          if(setrlimit(RLIMIT_MEMLOCK, &rl) >= 0)
            mlock(_mmap_addr, _mmap_length);
        }
      }
    }
  }

  // read _index
  if(_mmap_addr==NULL){
    _index = new UnitData[_index_size];
    r=::read(fd,_index,_index_size*sizeof(UnitData));
    if(r!=_index_size*sizeof(UnitData)){
      ::close(fd);
      reset();
      return false;
    }
  }
  else {
    _index = (UnitData*)(void *)((uint8_t*)_mmap_addr + sizeof(header));
  }

  // read _info
  if(_mmap_addr==NULL){
    _info = new uint32_t[_info_size];
    r=::read(fd,_info,_info_size*sizeof(uint32_t));
    if(r!=_info_size*sizeof(uint32_t)){
      ::close(fd);
      reset();
      return false;
    }
  }
  else {
    _info = (uint32_t*)(void *)((uint8_t*)_index + _index_size*sizeof(UnitData));
  }

  // read _catindex
  if(_mmap_addr==NULL){
    _catindex = new uint32_t[_catindex_size];
    r=::read(fd,_catindex,_catindex_size*sizeof(uint32_t));
    if(r!=_catindex_size*sizeof(uint32_t)){
      ::close(fd);
      reset();
      return false;
    }
  }
  else {
    _catindex = (uint32_t*)(void *)((uint8_t*)_info + _info_size*sizeof(uint32_t));
  }

  // read _strings
  if(_mmap_addr==NULL){
    _strings = new char[_strings_size];
    r=::read(fd,_strings,_strings_size*sizeof(char));
    if(r!=_strings_size*sizeof(char)){
      ::close(fd);
      reset();
      return false;
    }
  }
  else {
    _strings = (char*)((uint8_t*)_catindex + _catindex_size*sizeof(uint32_t));
  }

  ::close(fd);

  return true;
}

// }}}

// {{{ ConceptNet::lookup()

int ConceptNet::lookup(const char *unit) const
{
  FSA::HashedState hs(_unit_fsa);
  hs.start(unit);
  if(hs.isFinal()){
    return (int)hs.hash();
  }
  return -1;
}

const char * ConceptNet::lookup(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return NULL;
  }
#endif
  return _strings+_index[idx]._term;
}

// }}}
// {{{ ConceptNet::frq()

int ConceptNet::frq(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
#endif
  return _index[idx]._frq;
}

int ConceptNet::frq(const char *unit) const
{
  return frq(lookup(unit));
}

// }}}
// {{{ ConceptNet::cFrq()

int ConceptNet::cFrq(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
#endif
  return _index[idx]._cfrq;
}

int ConceptNet::cFrq(const char *unit) const
{
  return cFrq(lookup(unit));
}

// }}}
// {{{ ConceptNet::qFrq()

int ConceptNet::qFrq(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
#endif
  return _index[idx]._qfrq;
}

int ConceptNet::qFrq(const char *unit) const
{
  return qFrq(lookup(unit));
}

// }}}
// {{{ ConceptNet::sFrq()

int ConceptNet::sFrq(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
#endif
  return _index[idx]._sfrq;
}

int ConceptNet::sFrq(const char *unit) const
{
  return sFrq(lookup(unit));
}

// }}}
// {{{ ConceptNet::score()

double ConceptNet::score(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1.0;
  }
#endif
  return 100.0*(double)_index[idx]._cfrq/(double)_index[idx]._qfrq;
}

double ConceptNet::score(const char *unit) const
{
  return score(lookup(unit));
}

// }}}
// {{{ ConceptNet::strength()

double ConceptNet::strength(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1.0;
  }
#endif
  return 100.0*(double)_index[idx]._qfrq/(double)_index[idx]._sfrq;
}

double ConceptNet::strength(const char *unit) const
{
  return strength(lookup(unit));
}

// }}}
// {{{ ConceptNet::numExt()

int ConceptNet::numExt(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
#endif
  if(_index[idx]._exts==0){
    return 0;
  }
  return (int)_info[_index[idx]._exts];
}

// }}}
// {{{ ConceptNet::numAssoc()

int ConceptNet::numAssoc(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
#endif
  if(_index[idx]._assocs==0){
    return 0;
  }
  return (int)_info[_index[idx]._assocs];
}

// }}}
// {{{ ConceptNet::numCat()

int ConceptNet::numCat(int idx) const
{
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
#endif
  if(_index[idx]._cats==0){
    return 0;
  }
  return (int)_info[_index[idx]._cats];
}

// }}}
// {{{ ConceptNet::ext()

int ConceptNet::ext(int idx, int j) const
{
  assert(j>=0);
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
  if(_index[idx]._exts==0){
    return -1;
  }
  if((uint32_t)j>=_info[_index[idx]._exts]){
    return -1;
  }
#endif
  return (int)_info[_index[idx]._exts+1+2*j];
}

// }}}
// {{{ ConceptNet::extFrq()

int ConceptNet::extFrq(int idx, int j) const
{
  assert(j>=0);
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
  if(_index[idx]._exts==0){
    return -1;
  }
  if((uint32_t)j>=_info[_index[idx]._exts]){
    return -1;
  }
#endif
  return (int)_info[_index[idx]._exts+1+2*j+1];
}

// }}}
// {{{ ConceptNet::assoc()

int ConceptNet::assoc(int idx, int j) const
{
  assert(j>=0);
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
  if(_index[idx]._assocs==0){
    return -1;
  }
  if((uint32_t)j>=_info[_index[idx]._assocs]){
    return -1;
  }
#endif
  return (int)_info[_index[idx]._assocs+1+2*j];
}

// }}}
// {{{ ConceptNet::assocFrq()

int ConceptNet::assocFrq(int idx, int j) const
{
  assert(j>=0);
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
  if(_index[idx]._assocs==0){
    return -1;
  }
  if((uint32_t)j>=_info[_index[idx]._assocs]){
    return -1;
  }
#endif
  return (int)_info[_index[idx]._assocs+1+2*j+1];
}

// }}}
// {{{ ConceptNet::cat()

int ConceptNet::cat(int idx, int j) const
{
  assert(j>=0);
#ifndef NO_RANGE_CHECK
  if(idx<0 || (uint32_t)idx>=_index_size){
    return -1;
  }
  if(_index[idx]._cats==0){
    return -1;
  }
  if((uint32_t)j>=_info[_index[idx]._cats]){
    return -1;
  }
#endif
  return (int)_info[_index[idx]._cats+1+j];
}

// }}}
// {{{ ConceptNet::catName()

const char *ConceptNet::catName(int catIdx) const
{
  if(catIdx<0 || (uint32_t)catIdx>=_catindex_size){
    return NULL;
  }
  return _strings+_catindex[catIdx];

}

// }}}

} // namespace fsa
