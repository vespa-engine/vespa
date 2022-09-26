// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    fsa.cpp
 * @brief   Implementation of FSA methods (not inlined)
 *
 */

#include "fsa.h"
#include "checksum.h"

#include <map>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h> // for ::read(), ::close()
#include <sys/types.h>
#include <sys/mman.h> // for ::mmap()
#include <sys/time.h>
#include <sys/resource.h> // for getrlimit(), setrlimit(), etc.



namespace fsa {

// {{{ constants
const uint32_t FSA::MAGIC;
const uint32_t FSA::VER;
const symbol_t FSA::EMPTY_SYMBOL;
const symbol_t FSA::FINAL_SYMBOL;
// }}}


// {{{ FSA::iterator::operator++()

FSA::iterator& FSA::iterator::operator++()
{
  state_t next;
  unsigned int depth;

  if(_item._symbol==0xff || _item._fsa==NULL)
    return *this;

  if(_item._symbol==0 && _item._state==0)
    _item._state=_item._fsa->start();

  while(1){
    _item._symbol++;
    if(_item._symbol<0xff){
      next=_item._fsa->delta(_item._state,_item._symbol);
      if(next){
        _item._string += _item._symbol;
        _item._stack.push_back(_item._state);
        _item._state = next;
        _item._symbol = 0;
        if(_item._fsa->isFinal(next))
          break;
      }
    }
    else { // bactrack
      if((depth=_item._string.size())>0){
        _item._symbol = _item._string[depth-1];
        _item._string.resize(depth-1);
        _item._state = _item._stack.back();
        _item._stack.pop_back();
      }
      else{
        _item._state=0;
        break;
      }
    }
  }
  return *this;
}

// }}}
// {{{ FSA::libVER()

uint32_t FSA::libVER()
{
  return VER;
}

// }}}
// {{{ MetaData::MetaData()

FSA::FSA(const char *file, FileAccessMethod fam) :
  _mmap_addr(NULL), _mmap_length(0),
  _version(0), _serial(0),
  _state(NULL), _symbol(NULL), _size(0),
  _data(NULL), _data_size(0), _data_type(DATA_VARIABLE), _fixed_data_size(0),
  _has_perfect_hash(false),_perf_hash(NULL),
  _start(0), _ok(false)
{
  _ok = read(file, fam);
}

FSA::FSA(const std::string &file, FileAccessMethod fam) :
  _mmap_addr(NULL), _mmap_length(0),
  _version(0), _serial(0),
  _state(NULL), _symbol(NULL), _size(0),
  _data(NULL), _data_size(0), _data_type(DATA_VARIABLE), _fixed_data_size(0),
  _has_perfect_hash(false),_perf_hash(NULL),
  _start(0), _ok(false)
{
  _ok = read(file.c_str(), fam);
}

// }}}
// {{{ FSA::~FSA()

FSA::~FSA()
{
  if(_mmap_addr!=NULL && _mmap_addr!=MAP_FAILED){
    munmap(_mmap_addr,_mmap_length);
  }
  else{
    if(_state!=NULL) free(_state);
    if(_symbol!=NULL) free(_symbol);
    if(_data!=NULL) free(_data);
    if(_perf_hash!=NULL) free(_perf_hash);
  }
}

// }}}
// {{{ FSA::reset()

void FSA::reset()
{
  _version = 0;
  _serial = 0;
  if(_mmap_addr!=NULL && _mmap_addr!=MAP_FAILED){
    munmap(_mmap_addr,_mmap_length);
  }
  else{
    if(_state!=NULL) free(_state);
    if(_symbol!=NULL) free(_symbol);
    if(_data!=NULL) free(_data);
    if(_perf_hash!=NULL) free(_perf_hash);
  }
  _mmap_addr=NULL; _mmap_length=0;
  _state=NULL; _symbol=NULL; _size=0;
  _data=NULL; _data_size=0; _data_type=DATA_VARIABLE; _fixed_data_size=0;
  _has_perfect_hash=false; _perf_hash=NULL;
  _start=0;
}

// }}}
// {{{ FSA::read()

bool FSA::read(const char *file, FileAccessMethod fam)
{
  Header header;
  size_t r;
  uint32_t checksum=0;

  reset();

  if(fam==FILE_ACCESS_UNDEF)
    fam=_default_file_access_method;

  if(file==NULL)
    return false;

  int fd = ::open(file,O_RDONLY);
  if(fd<0)
    return false;

  r=::read(fd,&header,sizeof(header));
  if(r<sizeof(header) || header._magic!=MAGIC || header._version<1000){
    ::close(fd);       // no fsa had version number below 0.1.0
    return false;
  }

  _version = header._version;
  _serial = header._serial;
  _size = header._size;
  _data_size = header._data_size;
  _data_type = header._data_type;
  _fixed_data_size = header._fixed_data_size;
  _start = header._start;

  if(fam==FILE_ACCESS_MMAP || fam==FILE_ACCESS_MMAP_WITH_MLOCK){
    _mmap_length =
      sizeof(header) +
      _size*sizeof(symbol_t) +
      _size*sizeof(state_t) +
      _data_size +
      (header._has_perfect_hash?_size*sizeof(hash_t):0);
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
    _symbol = (symbol_t*)malloc(_size*sizeof(symbol_t));
    r=::read(fd,_symbol,_size*sizeof(symbol_t));
    if(r!=_size*sizeof(symbol_t)){
      ::close(fd);
      reset();
      return false;
    }
  }
  else {
    _symbol = (symbol_t*)((uint8_t*)_mmap_addr + sizeof(header));
  }
  checksum += Checksum::compute(_symbol,_size*sizeof(symbol_t));

  if(_mmap_addr==NULL){
    _state = Unaligned<state_t>::ptr(malloc(_size*sizeof(state_t)));
    r=::read(fd,_state,_size*sizeof(state_t));
    if(r!=_size*sizeof(state_t)){
      ::close(fd);
      reset();
      return false;
    }
  }
  else {
    _state = Unaligned<state_t>::ptr((uint8_t*)_mmap_addr + sizeof(header) +
                                     _size*sizeof(symbol_t));
  }
  checksum += Checksum::compute(_state,_size*sizeof(state_t));

  if(_mmap_addr==NULL){
    _data = (data_t*)malloc(_data_size);
    r=::read(fd,_data,_data_size);
    if(r!=_data_size){
      ::close(fd);
      reset();
      return false;
    }
  }
  else {
    _data = (data_t*)((uint8_t*)_mmap_addr + sizeof(header) +
                                             _size*sizeof(symbol_t) +
                                             _size*sizeof(state_t));
  }
  checksum += Checksum::compute(_data,_data_size);

  if(header._has_perfect_hash){
    if(_mmap_addr==NULL){
      _perf_hash = Unaligned<hash_t>::ptr(malloc(_size*sizeof(hash_t)));
      r=::read(fd,_perf_hash,_size*sizeof(hash_t));
      if(r!=_size*sizeof(hash_t)){
        ::close(fd);
        reset();
        return false;
      }
    }
    else {
      _perf_hash = Unaligned<hash_t>::ptr((uint8_t*)_mmap_addr + sizeof(header) +
                                          _size*sizeof(symbol_t) +
                                          _size*sizeof(state_t) +
                                          _data_size);
    }
    checksum += Checksum::compute(_perf_hash,_size*sizeof(hash_t));
    _has_perfect_hash = true;
  }

  ::close(fd);

  if(_version>=2000 && checksum!=header._checksum){
    reset();    // use checksum since version 0.2.0
    return false;
  }

  return true;
}
// }}}
// {{{ FSA::revLookup()

std::string FSA::revLookup(hash_t hash) const
{
  state_t state = start();
  state_t next,last_next, current_next;
  hash_t current = 0,d,last_d;
  std::string current_string;
  symbol_t symbol,last_symbol,current_symbol;

  if(!hasPerfectHash())
    return std::string();
  last_symbol=current_symbol=0;

  while(current<hash){
    last_symbol=current_symbol=0;
    last_next=current_next=0;
    d=last_d=0;
    for(symbol=1;symbol<=254;symbol++){
      next=delta(state,symbol);
      if(next){
        last_symbol=current_symbol;
        current_symbol=symbol;
        last_next=current_next;
        current_next=next;
        last_d=d;
        d=hashDelta(state,symbol);
        if(current+d>=hash)
          break;
      }
    }
    if(current_symbol==0)
      return std::string();
    if(current+d<=hash){
      current_string+=(char)current_symbol;
      state=current_next;
      current+=d;
    }
    else{
      current_string+=(char)last_symbol;
      state=last_next;
      current+=last_d;
    }
  }

  while(!isFinal(state)){
    for(symbol=1;symbol<=254;symbol++){
      next=delta(state,symbol);
      if(next){
        current_string+=(char)symbol;
        state=next;
        break;
      }
    }
    if(symbol==255)
      return std::string();
  }

  return current_string;
}

// }}}

// {{{ FSA::printDot()

void FSA::printDot(std::ostream &out) const
{
  state_t start,state,next;
  symbol_t symbol;
  std::list<state_t> state_stack;
  std::list<symbol_t> symbol_stack;
  std::map<state_t,bool> visited;
  bool v;


  symbol=0;
  start=state=this->start();

  out << "digraph fsa {\n";
  out << "  node [label=\"\",shape=circle]\n";
  out << "  start [label=start]\n";

  while(1){
    symbol++;
    if(symbol<0xff){
      next=delta(state,symbol);
      if(next){
        v=visited[next];
        if(!v && isFinal(next))
          out << "  n" << next << " [shape=doublecircle]\n";
        out << "  ";
        if(state==start)
          out << "start";
        else
          out << "n" << state;
        out << " -> n" << next << " [label=\"" << char(symbol) << "\"]\n";
        if(!v){
          visited[next]=true;
          symbol_stack.push_back(symbol);
          state_stack.push_back(state);
          state = next;
          symbol = 0;
        }
      }
    }
    else { // bactrack
      if(state_stack.size()>0){
        symbol = symbol_stack.back();
        symbol_stack.pop_back();
        state = state_stack.back();
        state_stack.pop_back();
      }
      else{
        break;
      }
    }
  }

  out << "}\n";

}
// }}}

} // namespace fsa
