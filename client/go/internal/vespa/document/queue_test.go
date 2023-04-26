package document

import (
	"testing"
)

func TestQueue(t *testing.T) {
	q := NewQueue[int]()
	assertPoll(t, q, 0, false)
	q.Add(1, false)
	q.Add(2, false)
	assertPoll(t, q, 1, true)
	assertPoll(t, q, 2, true)
	q.Add(3, false)
	q.Add(4, true)
	assertPoll(t, q, 4, true)
	assertPoll(t, q, 3, true)
}

func assertPoll(t *testing.T, q *Queue[int], want int, wantOk bool) {
	got, ok := q.Poll()
	if ok != wantOk {
		t.Fatalf("got ok=%t, want %t", ok, wantOk)
	}
	if got != want {
		t.Fatalf("got v=%d, want %d", got, want)
	}
}
