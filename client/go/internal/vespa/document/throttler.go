// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package document

import (
	"math"
	"math/rand"
	"sync/atomic"
	"time"
)

const throttlerWeight = 0.7

type Throttler interface {
	// Sent notifies the the throttler that a document has been sent.
	Sent()
	// Success notifies the throttler that document operation succeeded.
	Success()
	// Throttled notifies the throttler that a throttling event occured while count documents were in-flight.
	Throttled(count int64)
	// TargetInflight returns the ideal number of documents to have in-flight now.
	TargetInflight() int64
}

type dynamicThrottler struct {
	minInflight    int64
	maxInflight    int64
	targetInflight atomic.Int64
	targetTimesTen atomic.Int64

	throughputs []float64
	ok          atomic.Int64
	sent        int64

	start time.Time
	now   func() time.Time
}

func newThrottler(connections int, nowFunc func() time.Time) *dynamicThrottler {
	var (
		minInflight = 2 * int64(connections)
		maxInflight = 256 * minInflight // 512 max streams per connection on the server side
	)
	t := &dynamicThrottler{
		minInflight: minInflight,
		maxInflight: maxInflight,

		throughputs: make([]float64, 128),

		start: nowFunc(),
		now:   nowFunc,
	}
	t.targetInflight.Store(minInflight)
	t.targetTimesTen.Store(10 * maxInflight)
	return t
}

func NewThrottler(connections int) Throttler { return newThrottler(connections, time.Now) }

func (t *dynamicThrottler) Sent() {
	currentInflight := t.TargetInflight()
	t.sent++
	if t.sent*t.sent*t.sent < 1000*currentInflight*currentInflight {
		return
	}
	t.sent = 0
	now := t.now()
	elapsed := now.Sub(t.start)
	t.start = now
	currentThroughput := float64(t.ok.Swap(0)) / float64(elapsed)

	// Use buckets for throughput over inflight, along the log-scale, in [minInflight, maxInflight).
	index := int(float64(len(t.throughputs)) * math.Log(max(1, min(255, float64(currentInflight)/float64(t.minInflight)))) / math.Log(256))
	t.throughputs[index] = currentThroughput

	// Loop over throughput measurements and pick the one which optimises throughput and latency.
	best := float64(currentInflight)
	maxObjective := float64(-1)
	choice := 0
	j := -1
	k := -1
	s := 0.0
	for i := len(t.throughputs) - 1; i >= 0; i-- {
		if t.throughputs[i] == 0 {
			continue // Skip unknown values
		}
		inflight := float64(t.minInflight) * math.Pow(256, (float64(i)+0.5)/float64(len(t.throughputs)))
		objective := t.throughputs[i] * math.Pow(inflight, throttlerWeight-1) // Optimise throughput (weight), but also latency (1 - weight)
		if objective > maxObjective {
			maxObjective = objective
			best = inflight
			choice = i
		}
		// Additionally, smooth the throughput values, to reduce the impact of noise, and reduce jumpiness
		if j != -1 {
			u := t.throughputs[j]
			if k != -1 {
				t.throughputs[j] = (18*u + t.throughputs[i] + s) / 20
			}
			s = u
		}
		k = j
		j = i
	}
	target := int64((rand.Float64()*0.40+0.84)*best + rand.Float64()*4 - 1) // Random walk, skewed towards increase
	// If the best inflight is at the high end of the known, we override the random walk to speed up upwards exploration
	if choice == j && choice+1 < len(t.throughputs) {
		target = int64(1 + float64(t.minInflight)*math.Pow(256, (float64(choice)+1.5)/float64(len(t.throughputs))))
	}
	t.targetInflight.Store(max(t.minInflight, min(t.maxInflight, target)))
}

func (t *dynamicThrottler) Success() {
	t.targetTimesTen.Add(1)
	t.ok.Add(1)
}

func (t *dynamicThrottler) Throttled(inflight int64) {
	t.targetTimesTen.Store(max(inflight*5, t.minInflight*10))
}

func (t *dynamicThrottler) TargetInflight() int64 {
	staticTargetInflight := min(t.maxInflight, t.targetTimesTen.Load()/10)
	targetInflight := t.targetInflight.Load()
	return min(staticTargetInflight, targetInflight)
}
