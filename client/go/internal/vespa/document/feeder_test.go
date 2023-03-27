package document

import (
	"reflect"
	"testing"
	"time"
)

func TestStatsAdd(t *testing.T) {
	var got Stats
	got.Add(Stats{Requests: 1})
	got.Add(Stats{Requests: 1})
	got.Add(Stats{Responses: 1})
	got.Add(Stats{Responses: 1})
	got.Add(Stats{ResponsesByCode: map[int]int64{200: 2}})
	got.Add(Stats{ResponsesByCode: map[int]int64{200: 2}})
	got.Add(Stats{MinLatency: 200 * time.Millisecond})
	got.Add(Stats{MaxLatency: 400 * time.Millisecond})
	got.Add(Stats{MinLatency: 100 * time.Millisecond})
	got.Add(Stats{MaxLatency: 500 * time.Millisecond})
	got.Add(Stats{MaxLatency: 300 * time.Millisecond})
	got.Add(Stats{})

	want := Stats{
		Requests:        2,
		Responses:       2,
		ResponsesByCode: map[int]int64{200: 4},
		MinLatency:      100 * time.Millisecond,
		MaxLatency:      500 * time.Millisecond,
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("got %+v, want %+v", got, want)
	}
}
