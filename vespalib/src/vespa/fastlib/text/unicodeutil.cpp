// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unicodeutil.h"
#include <cstdlib>

#include "unicodeutil-charprops.cpp"

char *
Fast_UnicodeUtil::utf8ncopy(char *dst, const ucs4_t *src, int maxdst, int maxsrc) noexcept
{
    char * p = dst;
    char * edst = dst + maxdst;

    for (const ucs4_t *esrc(src + maxsrc); (src < esrc) && (*src != 0) && (p < edst); src++) {
        ucs4_t i(*src);
        if (i < 128)
            *p++ = i;
        else if (i < 0x800) {
            if (p + 1 >= edst)
                break;
            *p++ = (i >> 6) | 0xc0;
            *p++ = (i & 63) | 0x80;
        } else if (i < 0x10000) {
            if (p + 2 >= edst)
                break;
            *p++ = (i >> 12) | 0xe0;
            *p++ = ((i >> 6) & 63) | 0x80;
            *p++ = (i & 63) | 0x80;
        } else if (i < 0x200000) {
            if (p + 3 >= edst)
                break;
            *p++ = (i >> 18) | 0xf0;
            *p++ = ((i >> 12) & 63) | 0x80;
            *p++ = ((i >> 6) & 63) | 0x80;
            *p++ = (i & 63) | 0x80;
        } else if (i < 0x4000000) {
            if (p + 4 >= edst)
                break;
            *p++ = (i >> 24) | 0xf8;
            *p++ = ((i >> 18) & 63) | 0x80;
            *p++ = ((i >> 12) & 63) | 0x80;
            *p++ = ((i >> 6) & 63) | 0x80;
            *p++ = (i & 63) | 0x80;
        } else {
            if (p + 5 >= edst)
                break;
            *p++ = (i >> 30) | 0xfc;
            *p++ = ((i >> 24) & 63) | 0x80;
            *p++ = ((i >> 18) & 63) | 0x80;
            *p++ = ((i >> 12) & 63) | 0x80;
            *p++ = ((i >> 6) & 63) | 0x80;
            *p++ = (i & 63) | 0x80;
        }
    }
    if (p < edst)
        *p = 0;
    return p;
}


int
Fast_UnicodeUtil::utf8cmp(const char *s1, const ucs4_t *s2) noexcept
{
    ucs4_t i1;
    ucs4_t i2;

    const unsigned char *ps1 = reinterpret_cast<const unsigned char *>(s1);
    do {
        i1 = GetUTF8Char(ps1);
        i2 = *s2++;
    } while (i1 != 0 && i1 == i2);
    if (i1 > i2)
        return 1;
    if (i1 < i2)
        return -1;
    return 0;
}

size_t
Fast_UnicodeUtil::ucs4strlen(const ucs4_t *str) noexcept
{
    const ucs4_t *p = str;
    while (*p++ != 0) {
        /* Do nothing */
    }
    return p - 1 - str;
}

ucs4_t *
Fast_UnicodeUtil::ucs4copy(ucs4_t *dst, const char *src) noexcept
{
    ucs4_t i;
    ucs4_t *p;
    const unsigned char *psrc = reinterpret_cast<const unsigned char *>(src);

    p = dst;
    while ((i = GetUTF8Char(psrc)) != 0) {
        if (i != _BadUTF8Char)
            *p++ = i;
    }
    *p = 0;
    return p;
}

ucs4_t
Fast_UnicodeUtil::GetUTF8CharNonAscii(unsigned const char *&src) noexcept
{
    ucs4_t retval;

    if (*src >= 0xc0) {
        if (src[1] < 0x80 || src[1] >= 0xc0) {
            src++;
            return _BadUTF8Char;
        }
        if (*src >= 0xe0) {                       /* 0xe0..0xff */
            if (src[2] < 0x80 || src[2] >= 0xc0) {
                src += 2;
                return _BadUTF8Char;
            }
            if (*src >= 0xf0) {                     /* 0xf0..0xff */
                if (src[3] < 0x80 || src[3] >= 0xc0) {
                    src += 3;
                    return _BadUTF8Char;
                }
                if (*src >= 0xf8) {                   /* 0xf8..0xff */
                    if (src[4] < 0x80 || src[4] >= 0xc0) {
                        src += 4;
                        return _BadUTF8Char;
                    }
                    if (*src >= 0xfc) {                 /* 0xfc..0xff */
                        if (src[5] < 0x80 || src[5] >= 0xc0) {
                            src += 5;
                            return _BadUTF8Char;
                        }
                        if (*src >= 0xfe) {               /* 0xfe..0xff: INVALID */
                            src += 5;
                            return _BadUTF8Char;
                        } else {                          /* 0xfc..0xfd: 6 bytes */
                            retval = ((src[0] & 1) << 30) |
                                     ((src[1] & 63) << 24) |
                                     ((src[2] & 63) << 18) |
                                     ((src[3] & 63) << 12) |
                                     ((src[4] & 63) << 6) |
                                     (src[5] & 63);
                            if (retval < 0x4000000u) {      /* 6 bytes: >= 0x4000000 */
                                retval = _BadUTF8Char;
                            }
                            src += 6;
                            return retval;
                        }
                    } else {                            /* 0xf8..0xfb: 5 bytes */
                        retval = ((src[0] & 3) << 24) |
                                 ((src[1] & 63) << 18) |
                                 ((src[2] & 63) << 12) |
                                 ((src[3] & 63) << 6) |
                                 (src[4] & 63);
                        if (retval < 0x200000u) {         /* 5 bytes: >= 0x200000 */
                            retval = _BadUTF8Char;
                        }
                        src += 5;
                        return retval;
                    }
                } else {                              /* 0xf0..0xf7: 4 bytes */
                    retval = ((src[0] & 7) << 18) |
                             ((src[1] & 63) << 12) |
                             ((src[2] & 63) << 6) |
                             (src[3] & 63);
                    if (retval < 0x10000) {             /* 4 bytes: >= 0x10000 */
                        retval = _BadUTF8Char;
                    }
                    src += 4;
                    return retval;
                }
            } else {                                /* 0xe0..0xef: 3 bytes */
                retval = ((src[0] & 15) << 12) |
                         ((src[1] & 63) << 6) |
                         (src[2] & 63);
                if (retval < 0x800) {                 /* 3 bytes: >= 0x800 */
                    retval = _BadUTF8Char;
                }
                src += 3;
                return retval;
            }
        } else {                                  /* 0xc0..0xdf: 2 bytes */

            retval = ((src[0] & 31) << 6) |
                     (src[1] & 63);
            if (retval < 0x80) {                    /* 2 bytes: >= 0x80 */
                retval = _BadUTF8Char;
            }
            src += 2;
            return retval;
        }
    } else {                                    /* 0x80..0xbf: INVALID */
        src += 1;
        return _BadUTF8Char;
    }
}

ucs4_t
Fast_UnicodeUtil::GetUTF8Char(unsigned const char *&src) noexcept
{
    return (*src >= 0x80)
        ? GetUTF8CharNonAscii(src)
        : *src++;
}

/** Move forwards or backwards a number of characters within an UTF8 buffer
 * Modify pos to yield new position if possible
 * @param start A pointer to the start of the UTF8 buffer
 * @param length The length of the UTF8 buffer
 * @param pos A pointer to the current position within the UTF8 buffer,
 *            updated to reflect new position upon return. @param pos will
 *    point to the start of the offset'th character before or after the character
 *    currently pointed to.
 * @param offset An offset (+/-) in number of UTF8 characters.
 *        Offset 0 consequently yields a move to the start of the current character.
 * @return Number of bytes moved, or -1 if out of range.
 *        If -1 is returned, pos is unchanged.
 */

#define UTF8_STARTCHAR(c)  (!((c) & 0x80) || ((c) & 0x40))

int Fast_UnicodeUtil::UTF8move(unsigned const char* start, size_t length,
                               unsigned const char*& pos, off_t offset) noexcept
{
    int increment = offset > 0 ? 1 : -1;
    unsigned const char* p = pos;

    /* If running backward we first need to get to the start of
     * the current character, that's an extra step.
     * Similarly, if we are running forward an are at the start of a character,
     * we count that character as a step.
     */

    if (increment < 0)
    {
        // Already at start?
        if (p < start) return -1;
        if (!offset)
        {
            if (p > start + length) return -1;
        }
        else if (p == start) return -1;

        // Initially pointing to the first invalid char?
        if (p == start + length)
            p += increment;
        else
            offset += increment;
    }
    else if (p >= start + length)
        return -1;
    else if (UTF8_STARTCHAR(*p))
        offset += increment;


    for (; p >= start && p < start+length; p += increment)
    {
        /** Are we at start of a character? (both highest bits or none of them set) */
        if (UTF8_STARTCHAR(*p))
            offset -= increment; // We have "eaten" another character (independent of dir)
        if (offset == 0) break;
    }

    if (offset != 0)
    {
        offset -= increment;
        if (increment < 0)
            p -= increment;
    }

    if (offset == 0) // Enough room to make it..
    {
        int moved = std::abs(p - pos);
        pos = p;
        return moved;
    }
    else
        return -1;
}
