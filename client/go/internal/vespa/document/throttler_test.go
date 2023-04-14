package document

import (
	"testing"
	"time"
)

func TestThrottler(t *testing.T) {
	clock := &manualClock{tick: time.Second}
	tr := newThrottler(8, clock.now)
	for i := 0; i < 100; i++ {
		tr.Sent()
	}
	if got, want := tr.TargetInflight(), int64(1024); got != want {
		t.Errorf("got TargetInflight() = %d, but want %d", got, want)
	}
	tr.Throttled(5)
	if got, want := tr.TargetInflight(), int64(128); got != want {
		t.Errorf("got TargetInflight() = %d, but want %d", got, want)
	}
}
