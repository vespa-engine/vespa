// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// generic utilities
package util

type ArrayList[E comparable] []E

func NewArrayList[E comparable](initialCapacity int) ArrayList[E] {
	return make([]E, 0, initialCapacity)
}

func ArrayListOf[E comparable](elems []E) ArrayList[E] {
	return ArrayList[E](elems)
}

func (arrayP *ArrayList[E]) Append(elem E) {
	*arrayP = append(*arrayP, elem)
}

func (arrayP *ArrayList[E]) AppendAll(elemsToAppend ...E) {
	firstLen := len(*arrayP)
	secondLen := len(elemsToAppend)
	totLen := firstLen + secondLen
	if totLen > cap(*arrayP) {
		res := make([]E, totLen, cap(*arrayP)+cap(elemsToAppend))
		copy(res, *arrayP)
		copy(res[firstLen:], elemsToAppend)
		*arrayP = res
	} else {
		res := (*arrayP)[0:totLen]
		copy(res[firstLen:], elemsToAppend)
		*arrayP = res
	}
}

func (arrayP *ArrayList[E]) Insert(index int, elem E) {
	cur := *arrayP
	oldLen := len(cur)
	result := append(cur, elem)
	if index != oldLen {
		copy(result[index+1:], cur[index:])
		result[index] = elem
	}
	*arrayP = result
}

func (arrayP *ArrayList[E]) InsertAll(index int, elemsToInsert ...E) {
	firstLen := len(*arrayP)
	secondLen := len(elemsToInsert)
	totLen := firstLen + secondLen
	var res []E
	if totLen > cap(*arrayP) {
		res = make([]E, totLen, cap(*arrayP)+cap(elemsToInsert))
		firstPart := (*arrayP)[:index]
		copy(res, firstPart)
	} else {
		res = (*arrayP)[0:totLen]
	}
	thirdPart := (*arrayP)[index:]
	dst := res[index+secondLen:]
	copy(dst, thirdPart)
	dst = res[index:]
	copy(dst, elemsToInsert)
	*arrayP = res
}

func (arrayP *ArrayList[E]) Contains(elem E) bool {
	for _, old := range *arrayP {
		if elem == old {
			return true
		}
	}
	return false
}

func (arrayP *ArrayList[E]) Each(f func(E)) {
	for _, elem := range *arrayP {
		f(elem)
	}
}
