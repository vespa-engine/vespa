// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wordchartokenizer.h"
#include "unicode.h"

#include <string.h>


namespace fsa {

const bool WordCharTokenizer::_punctuation_table[] = {
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
  0, 1, 0, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 1, 0,
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1,
  1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0,
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0,
};


bool WordCharTokenizer::init(const std::string &text)
{
  _tokens.clear();
  _current = 0;

  char *dup;
  if(_lowercase)
      dup = Unicode::strlowdupUTF8(text.c_str());
  else
      dup = Unicode::strdupUTF8(text.c_str());

  char *tmp = dup;
  char *tok,*end;
  ucs4_t ch=0;
  bool  need_punct=false, added_punct=false;

  while(*tmp) {
    tok=NULL;
    while((tok=tmp,*tmp) &&
          (ch=Unicode::getUTF8Char(tmp),
           _punctuation==PUNCTUATION_WHITESPACEONLY?Unicode::isSpaceChar(ch):!Unicode::isWordChar(ch))){
      if(_punctuation!=PUNCTUATION_DISCARD && _punctuation!=PUNCTUATION_WHITESPACEONLY){
        if(ch<128 && _punctuation_table[ch] && need_punct && !added_punct){
          _tokens.push_back(_punctuation_token);
          added_punct=true;
        }
      }
    }

    while((end=tmp,*tmp) &&
          (ch=Unicode::getUTF8Char(tmp),
           _punctuation==PUNCTUATION_WHITESPACEONLY?!Unicode::isSpaceChar(ch):Unicode::isWordChar(ch)));

    if(*end) {
      *end=0;
    }
    if(*tok){
      _tokens.push_back(std::string((char *)tok));
      added_punct = false;
      need_punct = true;
      if(_punctuation!=PUNCTUATION_DISCARD && _punctuation!=PUNCTUATION_WHITESPACEONLY){
        if(ch<128 && _punctuation_table[ch]){
          if(_punctuation==PUNCTUATION_FULL || ch!='.' || strlen(tok)>1){
            _tokens.push_back(_punctuation_token);
            added_punct=true;
          }
        }
      }
    }
  }

  if(added_punct) {  // The last token is a puctuation, drop it
    _tokens.pop_back();
  }

  free(dup);
  return true;
}


bool WordCharTokenizer::hasMore()
{
  return _tokens.size()>_current;
}

std::string WordCharTokenizer::getNext()
{
  if(_tokens.size()>_current){
    return _tokens[_current++];
  }
  else{
    return std::string();
  }
}

} // namespace fsa
