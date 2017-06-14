// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

/* These algorithms only work for standard C-type arrays, not generic
   iterators the way STL works.  To make them work for stl-type
   iterators, we need to create a set of traits classes, but that is
   kind of overkill until we need that functionality.  */

/* You can use these functions to customize the heap */

template<typename T>
inline bool
FastS_min(T a, T b)
{
    return a < b;
}

template<typename T>
inline bool
FastS_max(T a, T b)
{
    return b < a;
}

/* Push obj onto the heap first of length len.  len must be large
 * enough to include the new object.  For example if you have a heap
 * with 3 elements and want to push a new element onto the heap, len
 * should be 4.
 */

template <typename T, typename Comp>
inline void
FastS_push_heap(T *first, int len, T obj, Comp comp)
{
    int x = len - 1;
    int parent = (x - 1)/2;
    while (x > 0 && comp(*(first + parent), obj)) {
        *(first + x) = *(first + parent);
        x = parent;
        parent = (x - 1)/2;
    }
    *(first + x) = obj;
}

/* Pop the largest element off the heap, reducing the size of the heap
 * by 1. (Note: it is the responsibility of the caller to keep track
 * of the size of the heap.)
 */

template<typename T, typename Comp>
inline T
FastS_pop_heap(T *first, int len, Comp comp)
{
    /* The algorithm we use is a variation of the textbook algorithm.
       We first remove the first element, then instead of putting the
       last element at the top of the heap and heapify(), we propagate
       the "hole" left by the removed first element to the bottom.  Then
       we copy the last element into the hole and push this element
       upwards. Since the last element has a high probability of being
       pushed down to the bottom anyways, this reduces the number of
       comparisons we need to do. */
    T ret = *first;
    /* right child */
    int topidx = 0;
    int childidx = 2;
    /* while both right and left child exist.. */
    while (childidx < len) {
        /* compare right to left child */
        if (comp(*(first + childidx), *(first + childidx - 1)))
            childidx--;
        *(first + topidx) = *(first + childidx);
        topidx = childidx;
        childidx = 2 * (topidx + 1);
    }
    /* if only left child exists.. */
    if (childidx == len) {
        *(first + topidx) = *(first + childidx - 1);
        topidx = childidx - 1;
    }
    /* now topidx is the hole.. */
    FastS_push_heap(first, topidx + 1, *(first + len - 1), comp);
    return ret;
}

/* Pop the largest element off the heap, and push a new element on the
 * heap in the same operation.
 */

template<typename T, typename Comp>
inline T
FastS_pop_push_heap(T *first, int len, T obj, Comp comp)
{
    T ret = *first;
    /* right child */
    int topidx = 0;
    int childidx = 2;
    /* while both right and left child exist.. */
    while (childidx < len) {
        /* compare right to left child */
        if (comp(*(first + childidx), *(first + childidx - 1)))
            childidx--;
        *(first + topidx) = *(first + childidx);
        topidx = childidx;
        childidx = 2 * (topidx + 1);
    }
    /* if only left child exists.. */
    if (childidx == len) {
        *(first + topidx) = *(first + childidx - 1);
        topidx = childidx - 1;
    }
    /* now topidx is the hole.. */
    FastS_push_heap(first, topidx + 1, obj, comp);
    return ret;
}

/* Similar to FastS_pop_heap, this function, given a "hole" in the
 * heap, heapify()es the heap downwards.  It then inserts obj and
 * adjusts the heap upwards.
 */

template<typename T, typename Comp>
inline void
FastS__adjust_heap(T *first, int len, int hole, T obj, Comp comp)
{
    /* right child */
    int topidx = hole;
    int childidx = 2 * (hole + 1);
    /* while both right and left child exist.. */
    while (childidx < len) {
        /* compare right to left child */
        if (comp(*(first + childidx), *(first + childidx - 1)))
            childidx--;
        *(first + topidx) = *(first + childidx);
        topidx = childidx;
        childidx = 2 * (topidx + 1);
    }
    /* if only left child exists.. */
    if (childidx == len) {
        *(first + topidx) = *(first + childidx - 1);
        topidx = childidx - 1;
    }
    /* now first[topidx] is the hole.. */
    FastS_push_heap(first, topidx + 1, obj, comp);
}

template <typename T, typename Comp>
inline void
FastS_make_heap(T *first, int len, Comp comp)
{
    if (len < 2)
        return;
    int parent = (len - 2)/2;
    for (/**/; parent >= 0; parent--) {
        int holeidx = parent;
        T obj = *(first + parent);
        int childidx = 2 * (parent + 1);
        while (childidx < len) {
            if (comp(*(first + childidx), *(first + childidx - 1)))
                childidx--;
            if (comp(*(first + childidx), obj)) {
                *(first + holeidx) = obj;
                goto nextparent;
            } else {
                *(first + holeidx) = *(first + childidx);
                holeidx = childidx;
                childidx = 2* (holeidx + 1);
            }
        }
        if (childidx == len) {
            if (comp(*(first + childidx - 1), obj)) {
                *(first + holeidx) = obj;
            } else {
                *(first + holeidx) = *(first + childidx - 1);
                *(first + childidx - 1) = obj;
            }
        } else /* childidx > len */
            *(first + holeidx) = obj;
    nextparent:
        ;
    }
}

template <typename T, typename Comp>
inline void
FastS_sort_heap(T *first, int len, Comp comp)
{
    while (len > 0) {
        *(first + len - 1) = FastS_pop_heap(first, len, comp);
        len--;
    }
}

template <typename T, typename Comp>
inline bool
FastS_is_heap(T *first, int len, Comp comp)
{
    for (int i = 0; i < len; i++) {
        int left = 2 * i + 1;
        int right = 2 * i + 2;
        if (left < len && comp(*(first + i), *(first + left)))
            return false;
        if (right < len && comp(*(first + i), *(first + right)))
            return false;
    }
    return true;
}

////////////////////////////////////////////////////////
// Similar to the above, but without comparator support
////////////////////////////////////////////////////////

template <typename T>
inline void
FastS_push_heap(T *first, int len, T obj)
{
    int x = len - 1;
    int parent = (x - 1)/2;
    while (x > 0 && *(first + parent) < obj) {
        *(first + x) = *(first + parent);
        x = parent;
        parent = (x - 1)/2;
    }
    *(first + x) = obj;
}

/* Pop the largest element off the heap, reducing the size of the heap
 * by 1. (Note: it is the responsibility of the caller to keep track
 * of the size of the heap.)
 */

template<typename T>
inline T
FastS_pop_heap(T *first, int len)
{
    /* The algorithm we use is a variation of the textbook algorithm.
       We first remove the first element, then instead of putting the
       last element at the top of the heap and heapify(), we propagate
       the "hole" left by the removed first element to the bottom.  Then
       we copy the last element into the hole and push this element
       upwards. Since the last element has a high probability of being
       pushed down to the bottom anyways, this reduces the number of
       comparisons we need to do. */
    T ret = *first;
    /* right child */
    int topidx = 0;
    int childidx = 2;
    /* while both right and left child exist.. */
    while (childidx < len) {
        /* compare right to left child */
        if (*(first + childidx) < *(first + childidx - 1))
            childidx--;
        *(first + topidx) = *(first + childidx);
        topidx = childidx;
        childidx = 2 * (topidx + 1);
    }
    /* if only left child exists.. */
    if (childidx == len) {
        *(first + topidx) = *(first + childidx - 1);
        topidx = childidx - 1;
    }
    /* now topidx is the hole.. */
    FastS_push_heap(first, topidx + 1, *(first + len - 1));
    return ret;
}


/* Pop the largest element off the heap, and push a new element on the
 * heap in the same operation.
 */

template<typename T>
inline T
FastS_pop_push_heap(T *first, int len, T obj)
{
    T ret = *first;
    /* right child */
    int topidx = 0;
    int childidx = 2;
    /* while both right and left child exist.. */
    while (childidx < len) {
        /* compare right to left child */
        if (*(first + childidx) < *(first + childidx - 1))
            childidx--;
        *(first + topidx) = *(first + childidx);
        topidx = childidx;
        childidx = 2 * (topidx + 1);
    }
    /* if only left child exists.. */
    if (childidx == len) {
        *(first + topidx) = *(first + childidx - 1);
        topidx = childidx - 1;
    }
    /* now topidx is the hole.. */
    FastS_push_heap(first, topidx + 1, obj);
    return ret;
}


/* Similar to FastS_pop_heap, this function, given a "hole" in the
 * heap, heapify()es the heap downwards.  It then inserts obj and
 * adjusts the heap upwards.
 */

template<typename T>
inline void
FastS__adjust_heap(T *first, int len, int hole, T obj)
{
    /* right child */
    int topidx = hole;
    int childidx = 2 * (hole + 1);
    /* while both right and left child exist.. */
    while (childidx < len) {
        /* compare right to left child */
        if (*(first + childidx) < *(first + childidx - 1))
            childidx--;
        *(first + topidx) = *(first + childidx);
        topidx = childidx;
        childidx = 2 * (topidx + 1);
    }
    /* if only left child exists.. */
    if (childidx == len) {
        *(first + topidx) = *(first + childidx - 1);
        topidx = childidx - 1;
    }
    /* now first[topidx] is the hole.. */
    FastS_push_heap(first, topidx + 1, obj);
}

template <typename T>
inline void
FastS_make_heap(T *first, int len)
{
    if (len < 2)
        return;
    int parent = (len - 2)/2;
    for (/**/; parent >= 0; parent--) {
        int holeidx = parent;
        T obj = *(first + parent);
        int childidx = 2 * (parent + 1);
        while (childidx < len) {
            // Find largest of left, right child of holeidx, and object.
            if (*(first + childidx) < *(first + childidx - 1))
                childidx--;
            if (*(first + childidx) < obj) {
                *(first + holeidx) = obj;
                goto nextparent;
            } else {
                // If child is largest, put it at holeidx, and
                // look further down.
                *(first + holeidx) = *(first + childidx);
                holeidx = childidx;
                childidx = 2* (holeidx + 1);
            }
        }
        if (childidx == len) {
            // Only left child exists
            if (*(first + childidx - 1) < obj) {
                *(first + holeidx) = obj;
            } else {
                *(first + holeidx) = *(first + childidx - 1);
                *(first + childidx - 1) = obj;
            }
        } else /* childidx > len */
            *(first + holeidx) = obj;
    nextparent:
        ;
    }
}

template <typename T>
inline void
FastS_sort_heap(T *first, int len)
{
    while (len > 0) {
        *(first + len - 1) = FastS_pop_heap(first, len);
        len--;
    }
}

template <typename T>
inline bool
FastS_is_heap(T *first, int len)
{
    for (int i = 0; i < len; i++) {
        int left = 2 * i + 1;
        int right = 2 * i + 2;
        if (left < len && *(first + i) < *(first + left))
            return false;
        if (right < len && *(first + i) < *(first + right))
            return false;
    }
    return true;
}


