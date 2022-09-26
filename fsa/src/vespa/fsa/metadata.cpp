// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    metadata.cpp
 * @brief   Generic meta data class implementation.
 *
 */

#include "metadata.h"
#include "fstream"

#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/mman.h> // for ::mmap()
#include <sys/time.h>
#include <sys/resource.h> // for getrlimit(), setrlimit(), etc.

namespace fsa {

// {{{ constants

const uint32_t MetaData::MAGIC;

// }}}

// {{{ MetaData::MetaData()

MetaData::MetaData(const char *datafile, FileAccessMethod fam) : _mmap_addr(NULL), _mmap_length(0), _ok(false), _header(), _data(NULL)
{
  _ok = read(datafile,fam);
}

MetaData::MetaData(const std::string &datafile, FileAccessMethod fam) : _mmap_addr(NULL), _mmap_length(0), _ok(false), _header(), _data(NULL)
{
  _ok = read(datafile.c_str(),fam);
}

// }}}
// {{{ MetaData::~MetaData()

MetaData::~MetaData()
{
  reset();
}

// }}}

// {{{ MetaData::reset()

void MetaData::reset()
{
  if(_mmap_addr!=NULL && _mmap_addr!=MAP_FAILED){
    munmap(_mmap_addr,_mmap_length);
  }
  else{
    if(_data!=NULL) free(_data);
  }
  _mmap_addr=NULL; _mmap_length=0;
  _ok=false;
  _data=NULL;
}

// }}}
// {{{ MetaData::read()

bool MetaData::read(const char *datafile, FileAccessMethod fam)
{
  size_t r;

  reset();

  if(fam==FILE_ACCESS_UNDEF)
    fam=_default_file_access_method;

  if(datafile==NULL)
    return false;

  int fd = ::open(datafile,O_RDONLY);
  if(fd<0)
    return false;

  r=::read(fd,&_header,sizeof(_header));
  if(r!=sizeof(_header) || _header._magic!=MetaData::MAGIC){
    ::close(fd);
    return false;
  }

  if(fam==FILE_ACCESS_MMAP || fam==FILE_ACCESS_MMAP_WITH_MLOCK){
    _mmap_length = sizeof(_header) + _header._size;
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

  if(_mmap_addr==NULL){
    _data = malloc(_header._size);
    r=::read(fd,_data,_header._size);
    if(r!=_header._size){
      ::close(fd);
      reset();
      return false;
    }
  }
  else {
    _data = (void*)((uint8_t*)_mmap_addr + sizeof(_header));
  }

  ::close(fd);

  return true;
}

// }}}

} // namespace fsa
