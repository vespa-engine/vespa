// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <cstring>
#include <cstdint>
#include <cstdlib>

#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))

namespace vespalib {

/**
 * This class holds a reference to an external chunk of memory.
 * It behaves like a string in many respects.
 * It is the responsibility of the programmer to ensure that the
 * memory referenced is valid and preferably unchanged for the
 * lifetime of the stringref; said lifetime should generally be short.
 **/
class stringref
{
public:
    using const_iterator = const char *;
    using size_type = size_t;
    static constexpr size_type npos = static_cast<size_type>(-1);
    constexpr stringref() noexcept : _s(""), _sz(0) { }
    stringref(const char * s) noexcept : _s(s), _sz(strlen(s)) { }
    constexpr stringref(const char * s, size_type sz) noexcept : _s(s), _sz(sz) { }
    constexpr stringref(const std::string & s) noexcept : _s(s.c_str()), _sz(s.size()) { }
    stringref(const stringref &) noexcept = default;
    stringref & operator =(const stringref &) noexcept = default;
    stringref(stringref &&) noexcept = default;
    stringref & operator =(stringref &&) noexcept = default;

    /**
     * return a pointer to the data held, or nullptr.
     * Note that the data may not be zero terminated, and a default
     * constructed stringref will give a nullptr pointer back.  If you
     * need to make sure data() gives a valid zero-terminated string
     * you should make a string from the stringref.
     **/
    [[nodiscard]] const char *   data() const noexcept { return _s; }
    [[nodiscard]] size_type      size() const noexcept { return _sz; }
    [[nodiscard]] size_type    length() const noexcept { return size(); }
    [[nodiscard]] bool          empty() const noexcept { return _sz == 0; }
    [[nodiscard]] const char *  begin() const noexcept { return data(); }
    [[nodiscard]] const char *    end() const noexcept { return begin() + size(); }
    [[nodiscard]] const char * rbegin() const noexcept { return end() - 1; }
    [[nodiscard]] const char *   rend() const noexcept { return begin() - 1; }
    [[nodiscard]] stringref substr(size_type start, size_type sz=npos) const noexcept {
        if (start < size()) {
            return {data() + start, std::min(sz, size()-start)};
        }
        return {};
    }

    /**
     * Find the first occurrence of a string, searching from @c start
     *
     * @param s characters to search for. Must be zero terminated to make sense.
     * @param start index at which the search will be started
     * @return index from the start of the string at which the character
     *     was found, or npos if the character could not be located
     */
    size_type find(const char * s, size_type start=0) const noexcept {
        const char *buf = begin()+start;
        const char *found = (const char *)strstr(buf, s);
        return (found != nullptr) ? (found - begin()) : (size_type)npos;
    }
    /**
     * Find the first occurrence of a string, searching from @c start
     *
     * @param s characters to search for. Must be zero terminated to make sense.
     * @param start index at which the search will be started
     * @return index from the start of the string at which the character
     *     was found, or npos if the character could not be located
     */
    [[nodiscard]] size_type find(stringref s, size_type start=0) const noexcept;
    /**
     * Find the first occurrence of a character, searching from @c start
     *
     * @param c character to search for
     * @param start index at which the search will be started
     * @return index from the start of the string at which the character
     *     was found, or npos if the character could not be located
     */
    [[nodiscard]] size_type find(char c, size_type start=0) const noexcept {
        const char *buf = begin()+start;
        const char *found = (const char *)memchr(buf, c, _sz-start);
        return (found != nullptr) ? (found - begin()) : (size_type)npos;
    }
    /**
     * Find the last occurrence of a substring, starting at e and
     * searching in reverse order.
     *
     * @param s substring to search for
     * @param e index from which the search will be started
     * @return index from the start of the string at which the substring
     *     was found, or npos if the substring could not be located
     */
    [[nodiscard]] size_type rfind(char c, size_type e=npos) const noexcept {
        if (!empty()) {
            const char *b = begin();
            for (size_type i(std::min(size()-1, e) + 1); i > 0;) {
                --i;
                if (c == b[i]) {
                    return i;
                }
            }
        }
        return npos;
    }

    /**
     * Find the last occurrence of a substring, starting at e and
     * searching in reverse order.
     *
     * @param s substring to search for
     * @param e index from which the search will be started
     * @return index from the start of the string at which the substring
     *     was found, or npos if the substring could not be located
     */
    size_type rfind(const char * s, size_type e=npos) const noexcept;
    [[nodiscard]] int compare(stringref s) const noexcept { return compare(s.data(), s.size()); }

    /**
     * Returns true iff input string is a prefix of this string.
     */
    [[nodiscard]] bool starts_with(stringref prefix) const noexcept {
        if (prefix.size() > size()) {
            return false;
        }
        return (memcmp(data(), prefix.data(), prefix.size()) == 0);
    }

    const char & operator [] (size_t i) const noexcept { return _s[i]; }
    operator std::string () const { return {_s, _sz}; }
    std::strong_ordering operator <=>(const char * s) const noexcept { return strong_compare(s, strlen(s)); }
    std::strong_ordering operator <=>(const std::string & s) const noexcept { return strong_compare(s.data(), s.size()); }
    std::strong_ordering operator <=>(stringref s) const noexcept { return strong_compare(s.data(), s.size()); }
    bool operator ==(const char * s) const noexcept { return strong_compare(s, strlen(s)) == std::strong_ordering::equal; }
    bool operator ==(const std::string & s) const noexcept { return strong_compare(s.data(), s.size()) == std::strong_ordering::equal; }
    bool operator ==(stringref s) const noexcept { return strong_compare(s.data(), s.size()) == std::strong_ordering::equal; }
    friend std::ostream & operator << (std::ostream & os, stringref v);
private:
    std::strong_ordering strong_compare(const char *s, size_type sz) const noexcept {
        int res = compare(s, sz);
        return (res < 0)
            ? std::strong_ordering::less
            : (res > 0)
                ? std::strong_ordering::greater
                : std::strong_ordering::equal;
    }
    int compare(const char *s, size_type sz) const noexcept {
        int diff(memcmp(_s, s, std::min(sz, size())));
        return (diff != 0) ? diff : (size() - sz);
    }
    const char *_s;
    size_type   _sz;
};


/**
 * class intended as a mostly-drop-in replacement for std::string
 * optimized for good multi-core performance using the well-known
 * "small-string optimization" where a small chunk of memory is
 * allocated internally in the object; as long as only small strings
 * are used the internal chunk will be used and no extra allocation
 * will happen.  The template parameter StackSize must be positive,
 * should be at least 8 and preferably a multiple of 8 for good
 * performance.  The size of strings is currently limited to 4GB, but
 * no checking is done - if a string grows too big the size will just
 * wrap.
 **/
template <uint32_t StackSize>
class small_string
{
public:
    using size_type = size_t;
    using iterator = char *;
    using const_iterator = const char *;
    using reverse_iterator = char *;
    using const_reverse_iterator = const char *;
    static constexpr size_type npos = static_cast<size_type>(-1);
    constexpr small_string() noexcept : _buf(_stack), _sz(0), _bufferSize(StackSize) { _stack[0] = '\0'; }
    small_string(const char * s) noexcept : _buf(_stack), _sz(s ? strlen(s) : 0) { init(s); }
    small_string(const void * s, size_type sz) noexcept : _buf(_stack), _sz(sz) { init(s); }
    small_string(stringref s) noexcept : _buf(_stack), _sz(s.size()) { init(s.data()); }
    small_string(const std::string & s) noexcept : _buf(_stack), _sz(s.size()) { init(s.data()); }
    small_string(small_string && rhs) noexcept
        : _sz(rhs.size()), _bufferSize(rhs._bufferSize)
    {
        move(std::move(rhs));
    }
    small_string(const small_string & rhs) noexcept : _buf(_stack), _sz(rhs.size()) { init(rhs.data()); }
    small_string(const small_string & rhs, size_type pos, size_type sz=npos) noexcept
        : _buf(_stack), _sz(std::min(sz, rhs.size()-pos))
    {
        init(rhs.data()+pos);
    }
    small_string(size_type sz, char c) noexcept
        : _buf(_stack), _sz(0), _bufferSize(StackSize)
    {
        reserve(sz);
        memset(buffer(), c, sz);
        _sz = sz;
        *end() = '\0';
    }

    template<typename Iterator>
    small_string(Iterator s, Iterator e) noexcept;

    ~small_string() {
        if (__builtin_expect(isAllocated(), false)) {
            free(buffer());
        }
    }

    small_string& operator= (small_string && rhs) noexcept {
        reset();
        _sz = rhs._sz;
        _bufferSize = rhs._bufferSize;
        move(std::move(rhs));
        return *this;
    }
    small_string& operator= (const small_string &rhs) noexcept {
        return assign(rhs.data(), rhs.size());
    }
    small_string & operator= (stringref rhs) noexcept {
        return assign(rhs.data(), rhs.size());
    }
    small_string& operator= (const char *s) noexcept {
        return assign(s);
    }
    small_string& operator= (const std::string &rhs) noexcept {
        return operator= (stringref(rhs));
    }
    void swap(small_string & rhs) noexcept {
        std::swap(*this, rhs);
    }
    operator std::string () const { return std::string(c_str(), size()); }
    operator stringref () const noexcept { return stringref(c_str(), size()); }
    [[nodiscard]] char at(size_t i) const noexcept { return buffer()[i]; }
    char & at(size_t i) noexcept { return buffer()[i]; }
    const char & operator [] (size_t i) const noexcept { return buffer()[i]; }
    char & operator [] (size_t i) noexcept { return buffer()[i]; }

    /** if there is a newline at the end of the string, remove it and return true */
    bool chomp() noexcept {
        if (size() > 0 && *rbegin() == '\n') {
            _resize(size() - 1);
            return true;
        }
        return false;
    }

    /**
     * Remove the last character of the string
     */
    void pop_back() noexcept {
      _resize(size() - 1);
    }

    /**
     * Returns true iff input string is a prefix of this string.
     */
    [[nodiscard]] bool starts_with(stringref prefix) const noexcept {
        if (prefix.size() > size()) {
            return false;
        }
        return (memcmp(buffer(), prefix.data(), prefix.size()) == 0);
    }

    /**
     * Find the last occurrence of a substring, starting at e and
     * searching in reverse order.
     *
     * @param s substring to search for
     * @param e index from which the search will be started
     * @return index from the start of the string at which the substring
     *     was found, or npos if the substring could not be located
     */
    size_type rfind(const char * s, size_type e=npos) const noexcept;

    /**
     * Find the last occurrence of a character, starting at e and
     * searching in reverse order.
     *
     * @param c character to search for
     * @param e index at which the search will be started
     * @return index from the start of the string at which the character
     *     was found, or npos if the character could not be located
     */
    [[nodiscard]] size_type rfind(char c, size_type e=npos) const noexcept {
        size_type sz = std::min(size()-1, e)+1;
        const char *b = buffer();
        while (sz-- > 0) {
            if (c == b[sz]) {
                return sz;
            }
        }
        return npos;
    }
    [[nodiscard]] size_type find_last_of(char c, size_type e=npos) const noexcept {
        return rfind(c, e);
    }
    [[nodiscard]] size_type find_first_of(char c, size_type start=0) const noexcept {
        return find(c, start);
    }

    [[nodiscard]] size_type find_first_not_of(char c, size_type start=0) const noexcept {
        size_t p(start);
        const char *buf = buffer();
        for(size_t m(size()); (p < m) && (buf[p] == c); p++);
        return (p < size()) ? p : (size_type)npos;
    }

    /**
     * Find the first occurrence of a substring, searching from @c start
     *
     * @param s substring to search for
     * @param start index from which the search will be started
     * @return index from the start of the string at which the substring
     *     was found, or npos if the substring could not be located
     */
    [[nodiscard]] size_type find(const small_string & s, size_type start=0) const noexcept {
        return find(s.c_str(), start);
    }

    /**
     * Find the first occurrence of a substring, searching from @c start
     *
     * @param s substring to search for
     * @param start index at which the search will be started
     * @return index from the start of the string at which the substring
     *     was found, or npos if the substring could not be located
     */
    size_type find(const char * s, size_type start=0) const noexcept {
        const char *buf = buffer()+start;
        const char *found = strstr(buf, s);
        return (found != nullptr) ? (found - buffer()) : (size_type)npos;
    }

    /**
     * Find the first occurrence of a character, searching from @c start
     *
     * @param s character to search for
     * @param start index at which the search will be started
     * @return index from the start of the string at which the character
     *     was found, or npos if the character could not be located
     */
    [[nodiscard]] size_type find(char c, size_type start=0) const noexcept {
        const char *buf = buffer()+start;
        const char *found = (const char *)memchr(buf, c, _sz-start);
        return (found != nullptr) ? (found - buffer()) : (size_type)npos;
    }
    small_string & assign(const char * s) noexcept { return assign(s, strlen(s)); }
    small_string & assign(const void * s, size_type sz) noexcept;
    small_string & assign(stringref s, size_type pos, size_type sz) noexcept {
        return assign(s.data() + pos, sz);
    }
    small_string & assign(stringref rhs) noexcept {
        if (data() != rhs.data()) assign(rhs.data(), rhs.size());
        return *this;
    }
    small_string & push_back(char c)              noexcept { return append(&c, 1); }
    small_string & append(char c)                 noexcept { return append(&c, 1); }
    small_string & append(const char * s)         noexcept { return append(s, strlen(s)); }
    small_string & append(stringref s)            noexcept { return append(s.data(), s.size()); }
    small_string & append(const std::string & s)  noexcept { return append(s.data(), s.size()); }
    small_string & append(const small_string & s) noexcept { return append(s.data(), s.size()); }
    small_string & append(const void * s, size_type sz) noexcept;
    small_string & operator += (char c)                 noexcept { return append(c); }
    small_string & operator += (const char * s)         noexcept { return append(s); }
    small_string & operator += (stringref s)            noexcept { return append(s); }
    small_string & operator += (const std::string & s)  noexcept { return append(s); }
    small_string & operator += (const small_string & s) noexcept { return append(s); }

    /**
     * Return a new string comprised of the contents of a sub-range of this
     * string, starting at start and spanning sz characters.
     *
     * @param start position at which the first character of the substring is to start
     * @param sz    length of substring. If start+sz is beyond the
     *     end of the string, only the remaining part will be returned.
     * @return a substring of *this
     */
    [[nodiscard]] small_string substr(size_type start, size_type sz=npos) const noexcept {
        if (start < size()) {
            const char *s = c_str();
            return small_string(s + start, std::min(sz, size()-start));
        }
        return small_string();
    }

    small_string & insert(iterator p, const_iterator f, const_iterator l) noexcept { return insert(p-c_str(), f, l-f); }
    small_string & insert(size_type start, stringref v) noexcept { return insert(start, v.data(), v.size()); }
    small_string & insert(size_type start, const void * v, size_type sz) noexcept;

    /**
     * Erases the content of the string, leaving it zero-length.
     * Does not alter string capacity.
     */
    void clear() noexcept {
        _sz = 0;
        buffer()[0] = 0;
    }

    /**
     * Frees any heap-allocated storage for the string and erases its content,
     * leaving it zero-length. Capacity is reset to the original small
     * string stack size
     */
    void reset() noexcept {
        if (isAllocated()) {
            free(buffer());
            _bufferSize = StackSize;
            _buf = _stack;
        }
        clear();
    }
    [[nodiscard]] const_iterator begin() const noexcept { return buffer(); }
    [[nodiscard]] const_iterator   end() const noexcept { return buffer() + size(); }
    iterator begin() noexcept { return buffer(); }
    iterator   end() noexcept { return buffer() + size(); }
    [[nodiscard]] const_reverse_iterator rbegin() const noexcept { return end() - 1; }
    [[nodiscard]] const_reverse_iterator   rend() const noexcept { return begin() - 1; }
    reverse_iterator rbegin() noexcept { return end() - 1; }
    reverse_iterator   rend() noexcept { return begin() - 1; }
    [[nodiscard]] const char * c_str() const noexcept { return buffer(); }
    [[nodiscard]] const char * data()  const noexcept { return buffer(); }
    [[nodiscard]] size_type size()     const noexcept { return _sz; }
    [[nodiscard]] size_type length()   const noexcept { return size(); }
    [[nodiscard]] bool empty()         const noexcept { return _sz == 0; }

    /**
     * at position p1, replace n1 characters with the contents of s
     *
     * @param p1 the position where the replacement is put, must be inside old string
     * @param n1 how many old characters should be replaced, cannot go outside old string
     * @param s  new replacement content
     **/
    small_string& replace (size_t p1, size_t n1, const small_string& s ) noexcept {
        return replace(p1, n1, s.c_str(), s.size());
    }

    /**
     * at position p1, replace n1 characters with
     * the n2 characters of s starting at position p2
     *
     * @param p1 the position where the replacement is put, must be inside old string
     * @param n1 how many old characters should be replaced, cannot go outside old string
     * @param s  where to get new replacement content
     * @param p2 position in s where replacement content starts
     * @param n2 how many new characters to use
     **/
    small_string& replace (size_t p1, size_t n1, const small_string& s, size_t p2, size_t n2) noexcept;

    /**
     * at position p1, replace n1 characters with
     * the n2 first characters of s
     *
     * @param p1 the position where the replacement is put, must be inside old string
     * @param n1 how many old characters should be replaced, cannot go outside old string
     * @param s  pointer to new content
     * @param n2 how many new characters to use
     **/
    small_string& replace (size_t p1, size_t n1, const char *s, size_t n2) noexcept;

    /**
     * at position p1, replace n1 characters with the contents of s
     *
     * @param p1 the position where the replacement is put, must be inside old string
     * @param n1 how many old characters should be replaced, cannot go outside old string
     * @param s  pointer to new replacement content
     **/
    small_string& replace (size_t p1, size_t n1, const char* s ) noexcept {
        return replace(p1, n1, s, strlen(s));
    }

    /* not implemented?
    small_string& replace ( size_t p1, size_t n1, size_t n2, char c );
    */

    std::strong_ordering operator <=>(const char * s) const noexcept { return strong_compare(s, strlen(s)); }
    std::strong_ordering operator <=>(const std::string & s) const noexcept { return strong_compare(s.data(), s.size()); }
    std::strong_ordering operator <=>(const small_string & s) const noexcept { return strong_compare(s.data(), s.size()); }
    std::strong_ordering operator <=>(stringref s) const noexcept { return strong_compare(s.data(), s.size()); }
    bool operator ==(const char * s) const noexcept { return strong_compare(s, strlen(s)) == std::strong_ordering::equal; }
    bool operator ==(const std::string & s) const noexcept { return strong_compare(s.data(), s.size()) == std::strong_ordering::equal; }
    bool operator ==(const small_string & s) const noexcept { return strong_compare(s.data(), s.size()) == std::strong_ordering::equal; }
    bool operator ==(stringref s) const noexcept { return strong_compare(s.data(), s.size()) == std::strong_ordering::equal; }

    template<typename T> bool operator != (const T& s) const noexcept { return ! operator == (s); }

    [[nodiscard]] int compare(const small_string & s) const noexcept { return compare(s.c_str(), s.size()); }
    int compare(const char *s, size_t sz) const noexcept {
        int diff(memcmp(buffer(), s, std::min(sz, size())));
        return (diff != 0) ? diff : (size() - sz);
    }

    [[nodiscard]] size_type capacity() const noexcept { return _bufferSize - 1; }

    /**
     * Make string exactly newSz in length removing characters at
     * the end as required or padding with pad character.
     *
     * @param newSz new size of string. Must be less than string capacity
     * @param c default character to use when initializing uninitialized memory.
     */

    void resize(size_type newSz, char padding = '\0') noexcept {
        if (newSz > capacity()) {
            reserve(newSz);
        }
        if (newSz > size()) {
          memset(buffer()+size(), padding, newSz - size());
        }
        _resize(newSz);
    }

    /**
     * Will extend the string within its current buffer. Assumes memory is already initialized.
     * Can not extend beyond capacity.
     * Note this is non-STL.
     *
     * @param newSz new size of string.
     */
    void append_from_reserved(size_type sz) noexcept;

    /**
     * Ensure string has at least newCapacity characters of available
     * storage. If newCapacity is beyond the initial small string
     * stack size, heap storage will be used instead.
     *
     * @param newCapacity new minimum capacity of string
     */
    void reserve(size_type newCapacity) noexcept {
        reserveBytes(newCapacity + 1);
    }

    [[nodiscard]] size_t count_allocated_memory() const noexcept {
        return sizeof(small_string) + (isAllocated() ? _bufferSize : 0);
    }
    [[nodiscard]] size_t count_used_memory() const noexcept {
        return sizeof(small_string) - StackSize + size();
    }
private:
    std::strong_ordering strong_compare(const char *s, size_type sz) const noexcept {
        int res = compare(s, sz);
        return (res < 0)
               ? std::strong_ordering::less
               : (res > 0)
                 ? std::strong_ordering::greater
                 : std::strong_ordering::equal;
    }
    void assign_slower(const void * s, size_type sz) noexcept __attribute((noinline));
    void init_slower(const void *s) noexcept __attribute((noinline));
    void _reserveBytes(size_type newBufferSize) noexcept;
    void reserveBytes(size_type newBufferSize) noexcept {
        if (newBufferSize > _bufferSize) {
            _reserveBytes(newBufferSize);
        }
    }
    void move(small_string && rhs) noexcept {
        if (rhs.isAllocated()) {
            _buf = rhs._buf;
            rhs._buf = rhs._stack;
            rhs._sz = 0;
            rhs._bufferSize = sizeof(rhs._stack);
            rhs._stack[0] = 0;
        } else {
            _buf = _stack;
            memcpy(_stack, rhs._stack, sizeof(_stack));
            rhs._sz = 0;
            rhs._stack[0] = 0;
        }
    }
    using isize_type = uint32_t;
    [[nodiscard]] bool needAlloc(isize_type add) const noexcept { return (add + _sz + 1) > _bufferSize; }
    [[nodiscard]] bool isAllocated() const noexcept { return _buf != _stack; }
    char * buffer() noexcept { return _buf; }
    [[nodiscard]] const char * buffer() const noexcept { return _buf; }
    VESPA_DLL_LOCAL void appendAlloc(const void * s, size_type sz) noexcept __attribute__((noinline));
    void init(const void *s) noexcept {
        if (__builtin_expect(_sz < StackSize, true)) {
            _bufferSize = StackSize;
            if (s) {
                memcpy(_stack, s, _sz);
            }
            _stack[_sz] = '\0';
        } else {
            init_slower(s);
        }
    }
    void _resize(size_type newSz) noexcept {
        _sz = newSz;
        *end() = '\0';
    }
    char     * _buf;
    isize_type _sz;
    isize_type _bufferSize;
    char       _stack[StackSize];
    template <uint32_t SS>
    friend std::ostream & operator << (std::ostream & os, const small_string<SS> & v);
    template <uint32_t SS>
    friend std::istream & operator >> (std::istream & is, small_string<SS> & v);
};

template <uint32_t StackSize>
template<typename Iterator>
small_string<StackSize>::small_string(Iterator s, Iterator e) noexcept :
    _buf(_stack),
    _sz(0),
    _bufferSize(StackSize)
{
    _stack[0] = '\0';
    for(; s != e; s++) {
        append(*s);
    }
}

using string = small_string<48>;

template<uint32_t StackSize>
small_string<StackSize>
operator + (const small_string<StackSize> & a, const small_string<StackSize> & b) noexcept;

template<uint32_t StackSize>
small_string<StackSize>
operator + (const small_string<StackSize> & a, stringref b) noexcept;

template<uint32_t StackSize>
small_string<StackSize>
operator + (stringref a, const small_string<StackSize> & b) noexcept;

template<uint32_t StackSize>
small_string<StackSize>
operator + (const small_string<StackSize> & a, const char * b) noexcept;

template<uint32_t StackSize>
small_string<StackSize>
operator + (const char * a, const small_string<StackSize> & b) noexcept;

string operator + (stringref a, stringref b) noexcept;
string operator + (const char * a, stringref b) noexcept;
string operator + (stringref a, const char * b) noexcept;

inline bool contains(stringref text, stringref key) noexcept {
    return text.find(key) != stringref::npos;
}

inline bool starts_with(stringref text, stringref key) noexcept {
    if (text.size() >= key.size()) {
        return memcmp(text.begin(), key.begin(), key.size()) == 0;
    }
    return false;
}

inline bool ends_with(stringref text, stringref key) noexcept {
    if (text.size() >= key.size()) {
        return memcmp(text.end()-key.size(), key.begin(), key.size()) == 0;
    }
    return false;
}

// returns a reference to a shared empty string
const string &empty_string() noexcept;

/**
 * Utility function to format an unsigned integer into a new
 * string instance.
 **/
static inline string stringify(uint64_t number)
{
    char digits[64];
    int numdigits = 0;
    do {
        digits[numdigits++] = '0' + (number % 10);
        number /= 10;
    } while (number > 0);
    string retval;
    while (numdigits > 0) {
        retval.append(digits[--numdigits]);
    }
    return retval;
}

} // namespace vespalib

