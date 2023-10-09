// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package document

import (
	"container/list"
)

// Queue is a generic wrapper around a doubly linked list.
type Queue[T any] struct{ items *list.List }

func NewQueue[T any]() *Queue[T] { return &Queue[T]{items: list.New()} }

func (q *Queue[T]) Add(item T, front bool) {
	if front {
		q.items.PushFront(item)
	} else {
		q.items.PushBack(item)
	}
}

func (q *Queue[T]) Poll() (T, bool) {
	front := q.items.Front()
	if front == nil {
		var empty T
		return empty, false
	}
	return q.items.Remove(front).(T), true
}
