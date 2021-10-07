// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "keyocc.h"

key_occ::key_occ(const char* term_, off_t spos, off_t stoken, int len) :
    MatchElement(spos, stoken),
    tokenlen(len),
    _term(term_)
{ }


void key_occ::set_valid()
{
    _valid = true;
}

void key_occ::add_to_keylist(keylist& kl)
{
    key_occ* k = this;
    kl.insert(k);
}


void key_occ::dump(std::string& s)
{
    s.append(term());
}
