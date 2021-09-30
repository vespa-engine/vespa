// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id: */

#pragma once

#include <string>
#include <set>
#include "querynode.h"

class Matcher;
class key_occ;
class MatchCandidate;

/* Sequential ordering of elements */
template <typename _Elem>
struct sequential_elem
{
    inline bool operator()(_Elem m1, _Elem m2) const
    {
        return m1->starttoken() < m2->starttoken();
    }
};
typedef std::set<key_occ*, sequential_elem<key_occ*> > keylist;

class MatchElement
{
public:
    MatchElement(off_t startpos, off_t starttoken);
    virtual ~MatchElement() {}
    virtual void set_valid() = 0; // Mark this element and its subelements as valid
    virtual void add_to_keylist(keylist& kl) = 0;
    virtual void dump(std::string& s) = 0;
    virtual size_t length() const = 0;
    virtual size_t word_length() const = 0;
    virtual bool complete() = 0;
    virtual off_t endpos() const = 0;
    virtual off_t endtoken() const = 0;

    // Word/token position of the first token in this match element
    inline off_t starttoken() const { return _starttoken; }

    // byte position of the start of the first token in this match element
    inline off_t startpos() const { return _startpos; }

    // Set if this match element is part of a valid match
    inline bool valid() const { return _valid; }

    virtual MatchCandidate* Complex() { return NULL; }
protected:
    off_t _starttoken;  // The token number at which this element starts
    off_t _startpos;    // The byte number (byte pos) at which this element starts
    bool  _valid;       // tag set if this match element is part of a valid match
};

