// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once


namespace search {

class Lid {
public:
    Lid() { memset(_lid, 0, sizeof(_lid)); }
    Lid(uint32_t l) { lid(l);}
    uint32_t lid() const
    {
        return (_lid[0] << 24) +
               (_lid[1] << 16) +
               (_lid[2] << 8) +
               _lid[3];
    }
    void lid(uint32_t v)
    {
       _lid[0] = (v >> 24) & 0xff;
       _lid[1] = (v >> 16) & 0xff;
       _lid[2] = (v >>  8) & 0xff;
       _lid[3] = v & 0xff;
    }
    int cmp(const Lid & b) const { return lid() - b.lid(); }
private:
    typedef unsigned char LidT[4];
    LidT _lid;
};

class Gid {
public:
    Gid() { memset(_gid, 0, sizeof(_gid)); }
    Gid(const char *g)           { memcpy(_gid, g, sizeof(_gid)); }
    const char * gid()     const { return _gid; }
    int cmp(const Gid & b) const { return memcmp(_gid, b._gid, sizeof(_gid)); }
private:
    typedef char GidT[12];
    GidT _gid;
};

inline bool operator <  (const Gid & a, const Gid & b) { return a.cmp(b) <  0; }
inline bool operator <= (const Gid & a, const Gid & b) { return a.cmp(b) <= 0; }
inline bool operator == (const Gid & a, const Gid & b) { return a.cmp(b) == 0; }
inline bool operator != (const Gid & a, const Gid & b) { return a.cmp(b) != 0; }
inline bool operator >  (const Gid & a, const Gid & b) { return a.cmp(b) >  0; }
inline bool operator >= (const Gid & a, const Gid & b) { return a.cmp(b) >= 0; }

}

