// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stddef.h>
#include <stdlib.h>
#include <stdio.h>
#include <map>
#include <set>
#include <tr1/unordered_set>
#include <vector>
#include <algorithm>
#include <vespa/vespalib/stllike/hash_set.hpp>

template <typename T>
class RoundRobinAllocator
{
public:
    typedef size_t     size_type;
    typedef ptrdiff_t  difference_type;
    typedef T *        pointer;
    typedef const T *  const_pointer;
    typedef T &        reference;
    typedef const T &  const_reference;
    typedef T          value_type;

    template<typename _Tp1>
    struct rebind {
        typedef RoundRobinAllocator<_Tp1> other;
    };
    RoundRobinAllocator() { }
    template<typename _Tp1>
    RoundRobinAllocator(const RoundRobinAllocator<_Tp1>&) throw() { }

    void construct(pointer p, const T& val) { new(static_cast<void*>(p)) T(val); }
    void destroy(pointer p) {
         p->~T();
    }
    pointer allocate(size_type n, const_pointer hint = 0) {
        (void) hint;
        if ((_w + n) < _sz) {
            pointer p(_memory + _w);
            _w += n;
            return p;
        }
        throw std::bad_alloc();
    }

    void deallocate(pointer p, size_type n) {
        if ((p - _memory) == long(_r)) {
            _r += n;
        }
    }
    size_type max_size() const throw() { return _sz; }

private:
    static size_t _r;
    static size_t _w;
    static size_t _sz;
    static T * _memory;
};

template <typename T>
size_t RoundRobinAllocator<T>::_r = 0;
template <typename T>
size_t RoundRobinAllocator<T>::_w = 0;
template <typename T>
size_t RoundRobinAllocator<T>::_sz = 10000000;
template <typename T>
T * RoundRobinAllocator<T>::_memory = static_cast<T *> (malloc(10000000*sizeof(T)));

class Gid
{
public:
  struct hash {
      size_t operator () (const Gid & g) const { return g.getGid()[0]; }
  };
  Gid(unsigned int v=0) : _gid() { _gid[0] = _gid[1] = _gid[2] = v; }
  const unsigned int * getGid() const { return _gid; }
  int cmp(const Gid & b) const { return memcmp(_gid, b._gid, sizeof(_gid)); }
  bool operator < (const Gid & b) const { return cmp(b) < 0; }
  bool operator == (const Gid & b) const { return cmp(b) == 0; }
private:
  unsigned int _gid[3];
};

class Slot
{
public:
  Slot(unsigned int v=0) : _gid(v) { }
  const Gid & getGid() const { return _gid; }
  int cmp(const Slot & b) const { return _gid.cmp(b.getGid()); }
private:
  Gid _gid;
};

struct IndirectCmp : public std::binary_function<Slot*, Slot*, bool> {
    bool operator()(const Slot* s1, const Slot* s2) {
        return s1->cmp(*s2) < 0;
    }
};

size_t benchMap(const std::vector<Slot *> & v)
{
    size_t uniq(0);
    typedef std::set<Gid> M;
    M set;
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        if (set.find(s.getGid()) == set.end()) {
            set.insert(s.getGid());
            uniq++;
        }
    }
    return uniq;
}

size_t benchMapIntelligent(const std::vector<Slot *> & v)
{
    size_t uniq(0);
    typedef std::set<Gid> M;
    M set;
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        std::pair<M::iterator, bool> r = set.insert(s.getGid());
        if (r.second) {
            uniq++;
        }
    }
    return uniq;
}

size_t benchHashStl(const std::vector<Slot *> & v)
{
    size_t uniq(0);
    typedef std::tr1::unordered_set< Gid, Gid::hash > M;
    M set(v.size());
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        if (set.find(s.getGid()) == set.end()) {
            set.insert(s.getGid());
            uniq++;
        }
    }
    return uniq;
}

size_t benchHashStlIntelligent(const std::vector<Slot *> & v)
{
    size_t uniq(0);
    typedef std::tr1::unordered_set< Gid, Gid::hash > M;
    M set(v.size());
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        std::pair<M::iterator, bool> r = set.insert(s.getGid());
        if (r.second) {
            uniq++;
        }
    }
    return uniq;
}

size_t benchHashStlFastAlloc(const std::vector<Slot *> & v)
{
    size_t uniq(0);
    std::tr1::unordered_set< Gid, Gid::hash, std::equal_to<Gid>, RoundRobinAllocator<Gid> > set(v.size());
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        if (set.find(s.getGid()) == set.end()) {
            set.insert(s.getGid());
            uniq++;
        }
    }
    return uniq;
}

size_t benchHashVespaLib(const std::vector<Slot *> & v)
{
    size_t uniq(0);
    typedef vespalib::hash_set< Gid, Gid::hash > M;
    M set(v.size()*2);
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        if (set.find(s.getGid()) == set.end()) {
            set.insert(s.getGid());
            uniq++;
        }
    }
    return uniq;
}

size_t benchHashVespaLibIntelligent(const std::vector<Slot *> & v)
{
    size_t uniq(0);
    typedef vespalib::hash_set< Gid, Gid::hash > M;
    M set(v.size()*2);
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        std::pair<M::iterator, bool> r = set.insert(s.getGid());
        if (r.second) {
            uniq++;
        }
    }
    return uniq;
}

size_t benchHashVespaLibIntelligentAndFast(const std::vector<Slot *> & v)
{
    size_t uniq(0);
    typedef vespalib::hash_set< Gid, Gid::hash, std::equal_to<Gid>, vespalib::hashtable_base::and_modulator > M;
    M set(v.size()*2);
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        std::pair<M::iterator, bool> r = set.insert(s.getGid());
        if (r.second) {
            uniq++;
        }
    }
    return uniq;
}

size_t benchSort(const std::vector<Slot *> & vOrg)
{
    IndirectCmp iCmp;
    std::vector<Slot *> v(vOrg);
    std::sort(v.begin(), v.end(), iCmp);
    Gid prev(0);
    size_t count(0);
    for(size_t i(0), m(v.size()); i < m; i++) {
        const Slot & s = *v[i];
        if (s.getGid().cmp(prev) != 0) {
            v[count++] = v[i];
            prev = s.getGid();
        }
    }
    v.resize(count);
    return count;
}

static char _type;

void*
runBenchMark(const std::vector<Slot *> * indirectSlotVector)
{
    int uniq(0);
    switch (_type) {
        case 'm': uniq = benchMap(*indirectSlotVector); break;
        case 'M': uniq = benchMapIntelligent(*indirectSlotVector); break;
        case 'v': uniq = benchSort(*indirectSlotVector); break;
        case 'h': uniq = benchHashStl(*indirectSlotVector); break;
        case 'H': uniq = benchHashStlIntelligent(*indirectSlotVector); break;
        case 'a': uniq = benchHashStlFastAlloc(*indirectSlotVector); break;
        case 'g': uniq = benchHashVespaLib(*indirectSlotVector); break;
        case 'G': uniq = benchHashVespaLibIntelligent(*indirectSlotVector); break;
        case 'J': uniq = benchHashVespaLibIntelligentAndFast(*indirectSlotVector); break;
        default: break;
    }
    return reinterpret_cast<void *>(uniq);
}

int main(int argc, char *argv[])
{
    typedef void* (*VFUNC)(void*);
    size_t count(10000000);
    size_t rep(10);
    size_t numThreads(0);
    char type('m');
    if (argc >= 2) {
        type = argv[1][0];
    }
    if (argc >= 3) {
        count = strtoul(argv[2], NULL, 0);
    }
    if (argc >= 4) {
        rep = strtoul(argv[3], NULL, 0);
    }
    if (argc >= 5) {
        numThreads = strtoul(argv[4], NULL, 0);
    }
    std::vector<Slot> slotVector(count);
    for (size_t i(0), m(slotVector.size()); i < m; i++) {
        slotVector[i] = Slot(rand());
    }
    std::vector<Slot *> indirectSlotVector(slotVector.size());
    for (size_t i(0), m(slotVector.size()); i < m; i++) {
        indirectSlotVector[i] = &slotVector[i];
    }
    std::vector<const char *> description(256);
    description['m'] = "std::set";
    description['M'] = "std::set with intelligent insert";
    description['v'] = "std::sort";
    description['h'] = "std::hash_set";
    description['H'] = "std::hash_set with intelligent insert";
    description['a'] = "std::hash_set with special allocator. Not threadsafe and hence not usable.";
    description['g'] = "vespalib::hash_set";
    description['G'] = "vespalib::hash_set with intelligent insert";
    description['J'] = "vespalib::hash_set with intelligent insert and fast modulator";
    size_t uniq(0);
    for (size_t i(0); i < rep; i++) {
        switch (type) {
           case 'm':
           case 'M':
           case 'v':
           case 'h':
           case 'H':
           case 'a':
           case 'g':
           case 'G':
           case 'J':
               _type = type;
               if (numThreads == 0) {
                   runBenchMark(&indirectSlotVector);
               } else {
                   std::vector<pthread_t> threads(numThreads);
                   for (size_t j(0); j < numThreads; j++) {
                        pthread_create(&threads[j], NULL, (VFUNC)runBenchMark, &indirectSlotVector);
                   }
                   for (size_t j(0); j < numThreads; j++) {
                       pthread_join(threads[j], NULL);
                   }
               }
           break;
        default:
            printf("'m' = %s\n", description[type]);
            printf("'M' = %s\n", description[type]);
            printf("'v' = %s\n", description[type]);
            printf("'h' = %s\n", description[type]);
            printf("'a' = %s\n", description[type]);
            printf("'H' = %s\n", description[type]);
            printf("'g' = %s\n", description[type]);
            printf("'G' = %s\n", description[type]);
            printf("'J' = %s\n", description[type]);
            printf("Unspecified type %c. Running map benchmark\n", type);
            exit(1);
            break;
        }
    }
    printf("Running test '%c' = %s, result = %ld unique values\n", type, description[type], uniq);
}

