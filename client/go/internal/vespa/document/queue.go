package document

import (
	"container/list"
	"sync"
)

// Queue wraps a doubly linked list. It attempts to re-use lists through a sync.Pool to reduce GC pressure.
type Queue[T any] struct {
	items    *list.List
	listPool *sync.Pool
}

func NewQueue[T any](listPool *sync.Pool) *Queue[T] {
	if listPool.New == nil {
		listPool.New = func() any { return list.New() }
	}
	return &Queue[T]{listPool: listPool}
}

func (q *Queue[T]) Add(item T, front bool) {
	if q.items == nil {
		q.items = q.listPool.Get().(*list.List)
	}
	if front {
		q.items.PushFront(item)
	} else {
		q.items.PushBack(item)
	}
}

func (q *Queue[T]) Poll() (T, bool) {
	if q.items == nil || q.items.Front() == nil {
		var empty T
		return empty, false
	}
	item := q.items.Remove(q.items.Front()).(T)
	if q.items.Front() == nil { // Emptied queue, release list back to pool
		q.listPool.Put(q.items)
		q.items = nil
	}
	return item, true
}
