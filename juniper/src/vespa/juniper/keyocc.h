// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastos/fastos.h>
#include "multiset.h"
#include <vector>
#include "matchelem.h"
#include "querynode.h"

typedef key_occ* key_occ_ptr;
typedef std::vector<key_occ_ptr> key_occ_vector;


class key_occ : public MatchElement
{
public:
    virtual void set_valid();
    virtual void add_to_keylist(keylist& kl);
    virtual void dump(std::string& s);
    virtual size_t length() const { return tokenlen; }
    inline const char* term() { return _term; }
    inline size_t word_length() const { return 1; }
    inline bool complete() { return true; }
    virtual inline off_t endpos() const { return _startpos + tokenlen; }
    virtual inline off_t endtoken() const { return _starttoken + 1; }

    int tokenlen;
    key_occ(const char* term, off_t posi, off_t tpos, int len);

private:
    const char* _term; // Pointer into first match (for debugging purposes only)

    key_occ(key_occ &);
    key_occ &operator=(key_occ &);
};


