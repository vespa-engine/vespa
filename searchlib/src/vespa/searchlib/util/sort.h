// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <vespa/searchlib/util/inline.h>

namespace search {
/* Various sorting-related functions */

template <typename T, typename Compare>
inline always_inline__ T *
median3(T *a, T *b, T *c, Compare *compobj)
{
    return Compare::Compare(compobj, *a, *b) < 0 ?
        (Compare::Compare(compobj, *b, *c) < 0 ? b : Compare::Compare(compobj, *a, *c) < 0 ? c : a) :
        (Compare::Compare(compobj, *b, *c) > 0 ? b : Compare::Compare(compobj, *a, *c) > 0 ? c : a);
}


template <typename T, typename Compare>
void
insertion_sort(T a[], unsigned int n, Compare *compobj)
{
    unsigned int i, j;
    T _swap;

    for (i=1; i<n ; i++)
    {
        _swap = a[i];
        j = i;
        while (Compare::Compare(compobj, _swap, a[j-1]) < 0)
        {
            a[j] = a[j-1];
            if(!(--j)) break;
        }
        a[j] = _swap;
    }
}

template <int InsertSortLevel, int Median9Level, typename T,
          typename Compare>
void
qsort(T *a, unsigned int n, Compare *compobj)
{
    for (;;) {
        if (n < InsertSortLevel) {
            insertion_sort<T, Compare>(a, n, compobj);
            return;
        }
        T *middle = a + (n/2);
        T *left = a;
        T *right = a + n - 1;
        if (n > Median9Level) {
            size_t s = n/8;
            left = median3<T, Compare>
                   (left, left + s, left + 2*s, compobj);
            middle = median3<T, Compare>
                     (middle - s, middle, middle+s, compobj);
            right = median3<T, Compare>
                    (right - 2*s, right - s, right, compobj);
        }
        middle = median3<T, Compare>(left, middle, right, compobj);
        T *pa, *pb, *pc, *pd;
        pa = pb = a;
        pc = pd = a + n - 1;
        T swap;
        T pivot = *middle;
        int r;
        for (;;) {
            while (pb <= pc && (r = Compare::Compare(compobj, *pb, pivot)) <= 0) {
                if (r == 0) {
                    swap = *pa;
                    *pa = *pb;
                    *pb = swap;
                    pa++;
                }
                pb++;
            }
            while (pb <= pc && (r = Compare::Compare(compobj, *pc, pivot)) >= 0) {
                if (r == 0) {
                    swap = *pc;
                    *pc = *pd;
                    *pd = swap;
                    pd--;
                }
                pc--;
            }
            if (pb > pc)
                break;
            swap = *pb;
            *pb = *pc;
            *pc = swap;
            pb++;
            pc--;
        }
        right = a + n;
        int s = std::min(pa - a, pb - pa);
        T *swapa = a;
        T *swapb = pb-s;
        T *swapaend = a + s;
        while (swapa < swapaend) {
            T tmp = *swapa;
            *swapa++ = *swapb;
            *swapb++ = tmp;
        }
        s = std::min(pd - pc, right - pd - 1);
        swapa = pb;
        swapb = right - s;
        swapaend = pb + s;
        while (swapa < swapaend) {
            T tmp = *swapa;
            *swapa++ = *swapb;
            *swapb++ = tmp;
        }
        // Recurse on the smaller partition.
        if (pb - pa < pd - pc) {
            if ((s = pb - pa) > 1)
                qsort<InsertSortLevel, Median9Level, T, Compare>
                    (a, s, compobj);
            if ((s = pd - pc) > 1) {
                a = right - s;
                n = s;
                continue;
            }
        } else {
            if ((s = pd - pc) > 1)
                qsort<InsertSortLevel, Median9Level, T, Compare>
                    (right - s, s, compobj);
            if ((s = pb - pa) > 1) {
                n = s;
                continue;
            }
        }
        break;
    }
}

}

