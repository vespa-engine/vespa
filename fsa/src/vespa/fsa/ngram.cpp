// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    ngram.cpp
 * @brief   n-gram class for tokenized text.
 */

#include "ngram.h"
#include "wordchartokenizer.h"

#include <ctype.h>
#include <iostream>

namespace fsa {

// {{{ NGram::NGram()

NGram::NGram(const char *text, unsigned int from, int length) : _tokens()
{
  append(text,from,length);
}

NGram::NGram(const char *text, Tokenizer &tokenizer, unsigned int from, int length) : _tokens()
{
  append(text,tokenizer,from,length);
}

NGram::NGram(const NGram &g, unsigned int from, int length) : _tokens()
{
  append(g,from,length);
}

NGram::NGram(const NGram &g, const Selector &select) : _tokens()
{
  append(g,select);
}

NGram::NGram(const NGram &g, const Permuter &p, unsigned int id) : _tokens()
{
  append(g,p,id);
}

NGram::NGram(const std::string &s, unsigned int from, int length) : _tokens()
{
  append(s,from,length);
}

NGram::NGram(const std::string &s, Tokenizer &tokenizer, unsigned int from, int length) : _tokens()
{
  append(s,tokenizer,from,length);
}

// }}}
// {{{ NGram::set()

void NGram::set(const char *text, unsigned int from, int length)
{
  clear();
  append(text,from,length);
}

void NGram::set(const char *text, Tokenizer &tokenizer, unsigned int from, int length)
{
  clear();
  append(text,tokenizer,from,length);

}

void NGram::set(const NGram &g, unsigned int from, int length)
{
  if(this==&g){
    set(NGram(g),from,length);
  }
  else{
    clear();
    append(g,from,length);
  }
}

void NGram::set(const NGram &g, const Selector &select)
{
  if(this==&g){
    set(NGram(g),select);
  }
  else{
    clear();
    append(g,select);
  }
}

void NGram::set(const NGram &g, const Permuter &p, unsigned int id)
{
  if(this==&g){
    set(NGram(g),p,id);
  }
  else{
    clear();
    append(g,p,id);
  }
}

void NGram::set(const std::string &s, unsigned int from, int length)
{
  clear();
  append(s,from,length);
}

void NGram::set(const std::string &s, Tokenizer &tokenizer, unsigned int from, int length)
{
  clear();
  append(s,tokenizer,from,length);
}

// }}}
// {{{ NGram::setOne()

void NGram::setOne(const std::string &s)
{
  clear();
  appendOne(s);
}

// }}}
// {{{ NGram::append()

void NGram::append(const char *text, unsigned int from, int length)
{
  WordCharTokenizer tokenizer;
  append(text,tokenizer,from,length);
}

void NGram::append(const char *text, Tokenizer &tokenizer, unsigned int from, int length)
{
  append(std::string(text),tokenizer,from,length);
}


void NGram::append(const NGram &g, unsigned int from, int length)
{
  if(this==&g){
    append(NGram(g),from,length);
    return;
  }

  if(length<0 || from+length>g._tokens.size()) length=g._tokens.size()-from;

  if(length>0){
    for(unsigned int i=from; i<from+length; i++){
      _tokens.push_back(g._tokens[i]);
    }
  }
}

void NGram::append(const NGram &g, const Selector &select)
{
  if(this==&g){
    append(NGram(g),select);
    return;
  }

  for(unsigned int i=0; i<g._tokens.size()&&i<select.size(); i++){
    if(select[i])
      _tokens.push_back(g._tokens[i]);
  }
}

void NGram::append(const NGram &g, const Permuter &p, unsigned int id)
{
  if(this==&g){
    append(NGram(g),p,id);
    return;
  }

  std::string perm=p.getPerm(id);

  for(unsigned int i=0;i<perm.length();i++){
    if(perm[i]>0 && perm[i]<=(int)g._tokens.size()){
      _tokens.push_back(g._tokens[perm[i]-1]);
    }
  }
}

void NGram::append(const std::string &s, unsigned int from, int length)
{
  WordCharTokenizer tokenizer;
  append(s,tokenizer,from,length);
}

void NGram::append(const std::string &s, Tokenizer &tokenizer, unsigned int from, int length)
{
  tokenizer.init(s);
  unsigned int i=0;
  while(i<from && tokenizer.hasMore()){
    tokenizer.getNext();
    i++;
  }

  i=0;
  while(tokenizer.hasMore() && (length<0 || (int)i<length)){
    appendOne(tokenizer.getNext());
    i++;
  }
}

// }}}
// {{{ NGram::appendOne()

void NGram::appendOne(const std::string &s)
{
  _tokens.push_back(s);
}

// }}}
// {{{ NGram::uniq()

unsigned int NGram::uniq()
{
  std::vector<std::string>::iterator  pos;

  pos = std::unique(_tokens.begin(),_tokens.end());
  _tokens.erase(pos,_tokens.end());
  return _tokens.size();
}

// }}}
// {{{ NGram::join()

std::string NGram::join(const std::string &separator, unsigned int from, int length) const
{
  unsigned int to = _tokens.size();
  if(length!=-1 && from+length<to)
    to=from+length;

  std::string dest;
  if(to>from)
    dest=_tokens[from];
  for(unsigned i=from+1;i<to;i++){
    dest+=separator;
    dest+=_tokens[i];
  }

  return dest;
}

// }}}
// {{{ NGram::getPermIdTo()

int NGram::getPermIdTo(const NGram &g, const Permuter &p) const
{
  if(_tokens.size()!=g._tokens.size())
    return -1;

  std::string perm(_tokens.size(),'\0');
  for(unsigned int i=0;i<_tokens.size();i++){
    for(unsigned int j=0;j<g._tokens.size();j++){
      if(_tokens[i]==g._tokens[j]){
        perm[j]=i+1;
      }
    }
  }
  return p.getPermId(perm);
}

// }}}

// {{{ operator<<

std::ostream& operator<<(std::ostream &out, const NGram &g)
{
  for(unsigned int i=0;i<g._tokens.size();i++){
    if(i>0) out<<" ";
    out<<g._tokens[i];
  }
  return out;
}

// }}}

} // namespace fsa
