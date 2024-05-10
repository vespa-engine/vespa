// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package document

import (
	"testing"
	"time"
)

func TestThrottler(t *testing.T) {
	clock := &manualClock{tick: time.Second}
	tr := newThrottler(8, clock.now)
	if got, want := tr.TargetInflight(), int64(16); got != want {
		t.Errorf("got TargetInflight() = %d, but want %d", got, want)
	}
	for tr.TargetInflight() < int64(18) {
		tr.Sent()
	}
	tr.Throttled(34)
	if got, want := tr.TargetInflight(), int64(17); got != want {
		t.Errorf("got TargetInflight() = %d, but want %d", got, want)
	}
}
