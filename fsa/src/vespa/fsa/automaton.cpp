// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdio.h>
#include <fcntl.h>
#include <unistd.h> // for ;:read(), ::write(), etc.
#include <sys/stat.h>

#include "fsa.h"
#include "automaton.h"
#include "checksum.h"
#include "unaligned.h"

namespace fsa {

// {{{ constants

const uint32_t Automaton::PackedAutomaton::_ALLOC_CELLS;
const uint32_t Automaton::PackedAutomaton::_ALLOC_BLOB;
const uint32_t Automaton::PackedAutomaton::_BACKCHECK;

const Blob Automaton::EMPTY_BLOB("");

// }}}

// {{{ Automaton::TransitionList::operator<()

bool Automaton::TransitionList::operator<(const Automaton::TransitionList& tl) const
{
  if(this==&tl) return false;
  if(_size<tl._size) return true;
  if(_size>tl._size) return false;
  for(unsigned int i=0; i<_size;i++){
    if(_trans[i]._symbol<tl._trans[i]._symbol) return true;
    if(_trans[i]._symbol>tl._trans[i]._symbol) return false;
    if(_trans[i]._state<tl._trans[i]._state) return true;
    if(_trans[i]._state>tl._trans[i]._state) return false;
  }
  return false;
}

// }}}
// {{{ Automaton::TransitionList::operator>()

bool Automaton::TransitionList::operator>(const Automaton::TransitionList& tl) const
{
  if(this==&tl) return false;
  if(_size>tl._size) return true;
  if(_size<tl._size) return false;
  for(unsigned int i=0; i<_size;i++){
    if(_trans[i]._symbol>tl._trans[i]._symbol) return true;
    if(_trans[i]._symbol<tl._trans[i]._symbol) return false;
    if(_trans[i]._state>tl._trans[i]._state) return true;
    if(_trans[i]._state<tl._trans[i]._state) return false;
  }
  return false;
}

// }}}
// {{{ Automaton::TransitionList::operator==()

bool Automaton::TransitionList::operator==(const Automaton::TransitionList& tl) const
{
  if(this==&tl) return true;
  if(_size!=tl._size) return false;
  for(unsigned int i=0; i<_size;i++){
    if(_trans[i]._symbol!=tl._trans[i]._symbol) return false;
    if(_trans[i]._state!=tl._trans[i]._state) return false;
  }
  return true;
}

// }}}

// {{{ Automaton::PackedAutomaton::reset()

void Automaton::PackedAutomaton::reset()
{
  _packable = false;
  _pack_map.clear();
  _blob_map.clear();
  if(_packed_ptr!=nullptr){
    free(_packed_ptr);
    _packed_ptr=nullptr;
  }
  if(_packed_idx!=nullptr){
    if(sizeof(State*)!=sizeof(state_t)){
      free(_packed_idx);
    }
    _packed_idx=nullptr;
  }
  if(_symbol!=nullptr){
    free(_symbol);
    _symbol=nullptr;
  }
  if(_used!=nullptr){
    free(_used);
    _used=nullptr;
  }
  if(_perf_hash!=nullptr){
    free(_perf_hash);
    _perf_hash=nullptr;
  }
  if(_totals!=nullptr){
    free(_totals);
    _totals=nullptr;
  }
  _packed_size=0;
  _last_packed=0;
  if(_blob!=nullptr){
    free(_blob);
    _blob=nullptr;
  }
  _blob_size=0;
  _blob_used=0;
  _blob_type=FSA::DATA_VARIABLE;
  _fixed_blob_size=0;
  _start_state=0;
}

// }}}
// {{{ Automaton::PackedAutomaton::init()

void Automaton::PackedAutomaton::init()
{
  reset();

  _packed_ptr = (State**)malloc(_ALLOC_CELLS*sizeof(State*));
  if(sizeof(State*)!=sizeof(state_t)){
    _packed_idx = (state_t*)malloc(_ALLOC_CELLS*sizeof(state_t));
  }
  else {
    _packed_idx = (state_t*)_packed_ptr;
  }
  _symbol = (symbol_t*)malloc(_ALLOC_CELLS*sizeof(symbol_t));
  _used = (bool*)malloc(_ALLOC_CELLS*sizeof(bool));
  _packed_size  = _ALLOC_CELLS;

  assert(_packed_ptr!=nullptr && _packed_idx!=nullptr && _symbol!=nullptr && _used!=nullptr);

  for(uint32_t i=0;i<_packed_size;i++){
    _used[i] = false;
    _symbol[i] = FSA::EMPTY_SYMBOL;
    _packed_ptr[i] = nullptr;
    if(sizeof(State*)!=sizeof(state_t)){
      _packed_idx[i] = 0;
    }
  }

  _blob = (data_t*)malloc(_ALLOC_BLOB);
  _blob_size = _ALLOC_BLOB;

  assert(_blob!=nullptr);

  _packable = true;
}

// }}}
// {{{ Automaton::PackedAutomaton::expandCells()

void Automaton::PackedAutomaton::expandCells()
{
  uint32_t i;

  _packed_ptr = (State**)realloc(_packed_ptr,(_packed_size+_ALLOC_CELLS)*sizeof(State*));
  if(sizeof(State*)!=sizeof(state_t)){
    _packed_idx = (state_t*)realloc(_packed_idx,(_packed_size+_ALLOC_CELLS)*sizeof(state_t));
  }
  else {
    _packed_idx = (state_t*)_packed_ptr;
  }
  _symbol = (symbol_t*)realloc(_symbol,(_packed_size+_ALLOC_CELLS)*sizeof(symbol_t));
  _used = (bool*)realloc(_used,(_packed_size+_ALLOC_CELLS)*sizeof(bool));

  assert(_packed_ptr!=nullptr && _packed_idx!=nullptr && _symbol!=nullptr && _used!=nullptr);

  for(i=_packed_size;i<_packed_size+_ALLOC_CELLS;i++){
    _used[i] = false;
    _symbol[i] = FSA::EMPTY_SYMBOL;
    _packed_ptr[i] = nullptr;
    if(sizeof(State*)!=sizeof(state_t)){
      _packed_idx[i] = 0;
    }
  }
  _packed_size  += _ALLOC_CELLS;
}

// }}}
// {{{ Automaton::PackedAutomaton::expandBlob()

void Automaton::PackedAutomaton::expandBlob(uint32_t minExpand)
{
  uint32_t expand=(minExpand/_ALLOC_BLOB+1)*_ALLOC_BLOB;

  _blob = (data_t*)realloc(_blob,_blob_size+expand);

  assert(_blob!=nullptr);

  _blob_size  += expand;
}

// }}}
// {{{ Automaton::PackedAutomaton::getEmptyCell()

uint32_t Automaton::PackedAutomaton::getEmptyCell()
{
  unsigned int cell = _last_packed>_BACKCHECK?_last_packed-_BACKCHECK:1;
  while(_used[cell]){
    cell++;
    if(cell+256>=_packed_size)
      expandCells();
  }

  _used[cell] = true;

  return cell;
}

// }}}
// {{{ Automaton::PackedAutomaton::getCell()

uint32_t Automaton::PackedAutomaton::getCell(Automaton::SymList t)
{
  SymListIterator tit;
  uint32_t cell = _last_packed>_BACKCHECK?_last_packed-_BACKCHECK:1;
  bool found = false;
  while(!found){
    if(!_used[cell]){
      if(cell+256>=_packed_size)
        expandCells();
      for(tit=t.begin();tit!=t.end();++tit){
        if(_symbol[cell+*tit]!=FSA::EMPTY_SYMBOL)
          break;
      }
      if(tit==t.end())
        found=true;
    }
    if(!found){
      cell++;
      if(cell>=_packed_size)
          expandCells();
    }
  }
  _used[cell] = true;
  for(tit=t.begin();tit!=t.end();++tit){
    _symbol[cell+*tit] = *tit;
  }

  return cell;
}

// }}}
// {{{ Automaton::PackedAutomaton::packStartState()

bool Automaton::PackedAutomaton::packStartState(const Automaton::State *s)
{
  return packState(s,true);
}

// }}}
// {{{ Automaton::PackedAutomaton::packState()

bool Automaton::PackedAutomaton::packState(const Automaton::State *s, bool start)
{
  SymList transitions;
  uint32_t cell;
  size_t i;

  if(_packable){
    if(s->getTransitionList().size()==0){
      cell = getEmptyCell();
    }
    else{
      for(i=0; i<s->getTransitionList().size(); i++){
        transitions.push_back(s->getTransitionList()[i]._symbol);
      }
      transitions.sort();
      cell = getCell(transitions);
      for(i=0; i<s->getTransitionList().size(); i++){
        if(s->getTransitionList()[i]._symbol==FSA::FINAL_SYMBOL){
          _packed_idx[cell+FSA::FINAL_SYMBOL] =
            packBlob(s->getTransitionList()[i]._state->getBlob());
        }
        else{
          _packed_ptr[cell+s->getTransitionList()[i]._symbol] =
            s->getTransitionList()[i]._state;
        }
      }
    }

    _pack_map[s] = cell;
    if(cell>_last_packed)
      _last_packed = cell;
    if(start)
      _start_state=(state_t)cell;

    return true;
  }

  return false;

}

// }}}
// {{{ Automaton::PackedAutomaton::packBlob()

static const Blob nullBlob;

uint32_t Automaton::PackedAutomaton::packBlob(const Blob *b)
{
  PackMapIterator pi = _blob_map.find(b);
  if(pi!=_blob_map.end()){
    return pi->second;
  }
  else {
    uint32_t cell=_blob_used;
    _blob_map[b]=cell;
    if(b==nullptr){
      b=&nullBlob;
    }
    uint32_t size=b->size();
    if(_blob_used+size+sizeof(uint32_t)>_blob_size)
      expandBlob(size+sizeof(uint32_t));
    memcpy(_blob+_blob_used,&size,sizeof(uint32_t));
    memcpy(_blob+_blob_used+sizeof(uint32_t),b->data(),size);
    _blob_used += size+sizeof(uint32_t);

    return cell;
  }
}

// }}}
// {{{ Automaton::PackedAutomaton::finalize()

void Automaton::PackedAutomaton::finalize()
{
  if(_packable){
    for(uint32_t i=0;i<_last_packed+256;i++){
      if(i>=_packed_size) // this shouldn't happen anymore, but check anyway
        expandCells();
      if(_symbol[i]!=FSA::EMPTY_SYMBOL && _symbol[i]!=FSA::FINAL_SYMBOL){
        _packed_idx[i] = _pack_map[_packed_ptr[i]];
      }
    }
    if (_blob_used == 0) {
        _packable = false;
        return;
    }

    // compact blobs if the size is constant
    std::map<uint32_t,uint32_t> bcomp;
    std::map<uint32_t,uint32_t>::iterator bcomp_it;
    bcomp[0]=0;
    uint32_t lastsize = *((uint32_t*)(void *)_blob), currsize;
    uint32_t i=lastsize+sizeof(uint32_t);
    uint32_t j=lastsize;
    bool fixedsize = true;
    while(i<_blob_used){
        currsize = Unaligned<uint32_t>::at(_blob+i);
      if(currsize!=lastsize){
        fixedsize = false;
        break;
      }
      bcomp[i]=j;
      i+=currsize+sizeof(uint32_t);
      j+=currsize;
    }
    if(fixedsize){
      _blob_type = FSA::DATA_FIXED;
      _fixed_blob_size = lastsize;
      _blob_used = j;
      for(i=0;i<_last_packed+256;i++){
        if(_symbol[i]==FSA::FINAL_SYMBOL){
          _packed_idx[i] = bcomp[_packed_idx[i]];
        }
      }

      for(bcomp_it = bcomp.begin(); bcomp_it!=bcomp.end(); ++bcomp_it){
        memmove(_blob+(bcomp_it->second),_blob+(bcomp_it->first+sizeof(uint32_t)),lastsize);
      }
    }

    _packable = false;
  }
}

// }}}
// {{{ Automaton::PackedAutomaton::computePerfectHash()

hash_t Automaton::PackedAutomaton::computePerfectHash(state_t state)
{
  symbol_t s;
  hash_t count;

  if(_totals[state]!=0){
    return _totals[state];
  }

  count = (_symbol[state+FSA::FINAL_SYMBOL]==FSA::FINAL_SYMBOL) ? 1 : 0;

  for(s=1;s<=254;s++){
    if(_symbol[state+s]==s){
      _perf_hash[state+s] = count;
      count += computePerfectHash(_packed_idx[state+s]);
    }
  }

  _totals[state] = count;

  return count;
}

// }}}
// {{{ Automaton::PackedAutomaton::addPerfectHash()

void Automaton::PackedAutomaton::addPerfectHash()
{
  if(_last_packed==0 || _packable){
    // do nothing with an empty automaton or one which has not been finalized
    return;
  }

  uint32_t size = _last_packed+256;

  _perf_hash = (hash_t*)malloc(size*sizeof(hash_t));
  _totals    = (hash_t*)malloc(size*sizeof(hash_t));

  assert(_perf_hash!=nullptr && _totals!=nullptr);

  for(unsigned int i=0;i<size;i++){
    _perf_hash[i] = 0;
    _totals[i] = 0;
  }

  computePerfectHash(_start_state);

  free(_totals); _totals=nullptr;
}

// }}}
// {{{ Automaton::PackedAutomaton::lookup()

const data_t* Automaton::PackedAutomaton::lookup(const char *input) const
{
  if(_packable || _start_state==0){
    return nullptr;
  }
  state_t state = _start_state;
  const char *p=input;
  while(*p){
    if(_symbol[state+*p]==*p){
      state=_packed_idx[state+*p];
      p++;
    }
    else{
      return nullptr;
    }
  }
  if(_symbol[state+FSA::FINAL_SYMBOL]==FSA::FINAL_SYMBOL){
    return _blob+_packed_idx[state+FSA::FINAL_SYMBOL];
  }
  return nullptr;
}

// }}}

namespace {

void checkWrite(int fd, const void *buf, size_t len, bool &success) {
    ssize_t writeRes = ::write(fd, buf, len);
    if (writeRes != static_cast<ssize_t>(len)) {
        success = false;
    }
}

}

// {{{ Automaton::PackedAutomaton::write()

bool Automaton::PackedAutomaton::write(const char *filename, uint32_t serial)
{
  if(_packable || _packed_size==0) // must be non-empty and finalized
    return false;

  FSA::Header header;

  header._magic            = FSA::MAGIC;
  header._version          = FSA::VER;
  header._checksum         = 0;
  header._size             = _last_packed+256;
  header._start            = _start_state;
  header._data_size        = _blob_used;
  header._data_type        = _blob_type;
  header._fixed_data_size  = _fixed_blob_size;
  header._has_perfect_hash = (_perf_hash==nullptr) ? 0 : 1;
  header._serial           = serial;
  memset(&(header._reserved), 0, sizeof(header._reserved));

  int fd = open(filename,O_CREAT|O_TRUNC|O_RDWR,S_IRUSR|S_IWUSR|S_IRGRP|S_IROTH);
  if(fd<0) return false;

  header._checksum += Checksum::compute(_symbol,header._size*sizeof(symbol_t));
  header._checksum += Checksum::compute(_packed_idx,header._size*sizeof(state_t));
  header._checksum += Checksum::compute(_blob,_blob_used);
  if(header._has_perfect_hash){
    header._checksum += Checksum::compute(_perf_hash,header._size*sizeof(hash_t));
  }

  bool success = true;
  checkWrite(fd,&header,sizeof(header), success);
  checkWrite(fd,_symbol,header._size*(sizeof(symbol_t)), success);
  checkWrite(fd,_packed_idx,header._size*(sizeof(state_t)), success);
  checkWrite(fd,_blob,_blob_used, success);
  if(header._has_perfect_hash){
      checkWrite(fd,_perf_hash,header._size*(sizeof(hash_t)), success);
  }
  close(fd);

  return success;
}

// }}}

namespace {

void checkRead(int fd, void *buf, size_t len, bool &success) {
    ssize_t readRes = ::read(fd, buf, len);
    if (readRes != static_cast<ssize_t>(len)) {
        success = false;
    }
}

}

// {{{ Automaton::PackedAutomaton::read()

bool Automaton::PackedAutomaton::read(const char *filename)
{
  FSA::Header header;
  size_t r;

  reset();
  int fd = ::open(filename,O_RDONLY);
  if(fd<0){
    return false;
  }
  r=::read(fd,&header,sizeof(header));
  if(r<sizeof(header) || header._magic!=FSA::MAGIC){
    ::close(fd);
    return false;
  }

  _packable = false;
  _packed_size = header._size;
  _last_packed = _packed_size-256;
  _blob_size = header._data_size;
  _blob_used = header._data_size;
  _blob_type = header._data_type;
  _fixed_blob_size = header._fixed_data_size;
  _start_state = header._start;

  _symbol = (symbol_t*)malloc(_packed_size*sizeof(symbol_t));
  assert(_symbol!=nullptr);
  bool success = true;
  checkRead(fd,_symbol,_packed_size*(sizeof(symbol_t)), success);
  _packed_idx = (state_t*)malloc(_packed_size*sizeof(state_t));
  assert(_packed_idx!=nullptr);
  checkRead(fd,_packed_idx,_packed_size*(sizeof(state_t)), success);
  _blob = (data_t*)malloc(_blob_used);
  assert(_blob!=nullptr);
  checkRead(fd,_blob,_blob_used, success);
  if(header._has_perfect_hash){
    _perf_hash = (hash_t*)malloc(_packed_size*sizeof(hash_t));
    assert(_perf_hash!=nullptr);
    checkRead(fd,_perf_hash,_packed_size*(sizeof(hash_t)), success);
  }

  ::close(fd);

  return success;
}

// }}}
// {{{ Automaton::PackedAutomaton::getFSA()

bool Automaton::PackedAutomaton::getFSA(FSA::Descriptor &d)
{
  if(_packable || _packed_size==0) // must be non-empty and finalized
    return false;

  uint32_t size = _last_packed+256;

  _symbol = (symbol_t*)realloc(_symbol,size*sizeof(symbol_t));
  _packed_idx = (state_t*)realloc(_packed_idx,size*sizeof(state_t));
  _blob = (data_t*)realloc(_blob,_blob_used);
  if(_perf_hash!=nullptr){
    _perf_hash = (hash_t*)realloc(_perf_hash,size*sizeof(hash_t));
  }

  d._version = FSA::VER;
  d._serial = 0;
  d._state = Unaligned<state_t>::ptr(_packed_idx);
  d._symbol = _symbol;
  d._size = size;
  d._data = _blob;
  d._data_size = _blob_used;
  d._data_type = _blob_type;
  d._fixed_data_size = _fixed_blob_size;
  d._perf_hash = Unaligned<hash_t>::ptr(_perf_hash);
  d._start = _start_state;

  _symbol = nullptr;
  _packed_idx = nullptr;
  if(sizeof(State*)==sizeof(state_t)){ // _packed_idx and _packed_ptr are overlayed
    _packed_ptr=nullptr;
  }
  _blob = nullptr;
  _perf_hash = nullptr;
  reset();

  return true;
}

// }}}

// {{{ Automaton::cleanUp()

void Automaton::cleanUp()
{
  if(_q0!=nullptr){
    finalize(); // make sure all states are in _register
    for(BlobRegisterIterator bi = _blob_register.begin(); bi!=_blob_register.end(); ++bi){
      delete bi->second;
    }
    _blob_register.clear(); // clear _blob_register
    // clear _register and remove all states
    for(RegisterIterator ri = _register.begin(); ri!=_register.end(); ++ri){
      delete ri->second;
    }
    _register.clear();
    delete _q0;
    _q0 = nullptr;
    _previous_input.clear();
  }
}

// }}}
// {{{ Automaton::~Automaton()

Automaton::~Automaton()
{
  cleanUp();
}

// }}}
// {{{ Automaton::getCPLength()

unsigned int Automaton::getCPLength(const char *input)
{
  if(_q0==nullptr) return 0;

  unsigned int l=0;
  State* state = _q0;
  State* next;
  while(input[l]!=0){
    next = state->child(input[l]);
    if(next==nullptr) return l;
    state=next;
    l++;
  }
  return l;
}

// }}}
// {{{ Automaton::getCPLastState()

Automaton::State* Automaton::getCPLastState(const char *input)
{
  if(_q0==nullptr) return nullptr;

  unsigned int l=0;
  State* state = _q0;
  State* next;
  while(input[l]!=0){
    next = state->child(input[l]);
    if(next==nullptr) return state;
    state=next;
    l++;
  }
  return state;
}

// }}}
// {{{ Automaton::addSuffix()

void Automaton::addSuffix(State* state, const char *suffix, const Blob *b)
{
  State* current = state;
  State* child;

  while(*suffix != 0){
    child = current->addEmptyChild(*suffix);
    current = child;
    suffix++;
  }
  BlobRegisterIterator bi;
  if(b!=nullptr)
    bi = _blob_register.find(*b);
  else
    bi = _blob_register.find(EMPTY_BLOB);
  if(bi!=_blob_register.end()){
    child = bi->second;
    current->addChild(FSA::FINAL_SYMBOL,child);
  }
  else {
    const Blob *bcopy = (b==nullptr) ? new Blob(EMPTY_BLOB) : new Blob(*b);
    assert(bcopy!=nullptr);
    child = current->addEmptyChild(FSA::FINAL_SYMBOL,bcopy);
    _blob_register[*bcopy] = child;
  }
}

// }}}
// {{{ Automaton::init()

void Automaton::init()
{
  cleanUp();
  _q0 = new State();
  assert(_q0!=nullptr);
  _finalized = false;

  _packed.init();
}

// }}}
// {{{ Automaton::finalize()

void Automaton::finalize()
{
  if(!_finalized && _q0!=nullptr){
    replaceOrRegister(_q0);
    _packed.packStartState(_q0);
    _packed.finalize();
    _finalized = true;
  }

}

// }}}
// {{{ Automaton::addPerfectHash()

void Automaton::addPerfectHash()
{
  if(_finalized){
    _packed.addPerfectHash();
  }
}

// }}}
// {{{ Automaton::write()

bool Automaton::write(const char *file, uint32_t serial)
{
  if(!_finalized){
    finalize();
  }
  return _packed.write(file,serial);
}

// }}}
// {{{ Automaton::getFSA()

FSA* Automaton::getFSA()
{
  if(!_finalized){
    finalize();
  }

  FSA::Descriptor d;

  if(!_packed.getFSA(d))
    return nullptr;

  FSA *fsa = new FSA(d);

  cleanUp();

  return fsa;
}

// }}}
// {{{ Automaton::insertSortedString()

void Automaton::insertSortedString(const std::string &input)
{
  insertSortedString(input.c_str());
}

void Automaton::insertSortedString(const std::string &input, const std::string &meta)
{
  Blob b(meta);
  insertSortedString(input.c_str(),&b);
}

void Automaton::insertSortedString(const char *input, const Blob& b)
{
  insertSortedString(input,&b);
}

void Automaton::insertSortedString(const char *input, const Blob* b)
{
  if(_q0==nullptr || _finalized) return;

  State* lastState = getCPLastState(input);
  const char* currentSuffix = input + getCPLength(input);

  if(lastState->hasChildren()){
    replaceOrRegister(lastState);
  }
  addSuffix(lastState,currentSuffix,b);
}

// }}}
// {{{ Automaton::replaceOrRegister()

void Automaton::replaceOrRegister(Automaton::State* state)
{
  State* child = state->lastChild();
  if(child!=nullptr){
    if(child->hasChildren()){
      replaceOrRegister(child);
    }
    RegisterIterator ri = _register.find(TListPtr(&(child->getTransitionList())));
    if(ri!=_register.end() && ri->second!=child){
      state->updateLastChild(ri->second);
      delete child;
    }
    else {
      _register[TListPtr(&(child->getTransitionList()))] = child;
      _packed.packState(child);
    }
  }
}

// }}}

} // namespace fsa
