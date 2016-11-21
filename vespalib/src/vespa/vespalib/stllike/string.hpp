#include "string.h"

namespace vespalib {

template <uint32_t StackSize>
void small_string<StackSize>::assign_slower(const void * s, size_type sz)
{   
    reset();
    append(s, sz);
}

template <uint32_t StackSize>
void small_string<StackSize>::init_slower(const void *s)
{   
    _bufferSize = _sz+1;
    _buf = (char *) malloc(_bufferSize);
    memcpy(_buf, s, _sz);
    _buf[_sz] = '\0';
}

template <uint32_t StackSize>
void small_string<StackSize>::appendAlloc(const void * s, size_type addSz)
{   
    size_type newBufferSize = roundUp2inN(_sz+addSz+1);
    char * buf = (char *) malloc(newBufferSize);
    memcpy(buf, buffer(), _sz);
    if (isAllocated()) {
        free(_buf);
    }
    memcpy(buf+_sz, s, addSz);
    _buf = buf; 
    _bufferSize = newBufferSize;
    _sz += addSz;
    _buf[_sz] = '\0';
}

template <uint32_t StackSize>
small_string<StackSize> &
small_string<StackSize>::insert(size_type start, const void * v, size_type sz)
{
    if (start < size()) {
        if ((static_cast<const char *>(v)+sz < c_str()) || (c_str()+size() < v)) {
            reserve(size() + sz);
            memmove(begin() + start + sz, c_str() + start, size() - start + 1);
            memcpy(begin() + start, v, sz);
            _sz += sz;
        } else {
            small_string n;
            n.reserve(size() + sz);
            n.append(c_str(), start);
            n.append(v, sz);
            n.append(c_str() + start, size() - start);
            swap(n);
        }
    } else {
        append(v, sz);
    }
    return *this;
}


template <uint32_t StackSize>
small_string<StackSize> &
small_string<StackSize>::append(const void * s, size_type addSz)
{
    if (needAlloc(addSz)) {
        appendAlloc(s, addSz);
    } else {
        char * buf(buffer());
        memmove(buf+_sz, s, addSz);
        _sz += addSz;
        buf[_sz] = '\0';
    }
    return *this;
}

}
