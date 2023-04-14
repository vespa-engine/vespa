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
	targetInflight int64
	targetTimesTen int64

	throughputs []float64
	ok          int64
	sent        int64

	start time.Time
	now   func() time.Time
}

func newThrottler(connections int, nowFunc func() time.Time) *dynamicThrottler {
	var (
		minInflight = 16 * int64(connections)
		maxInflight = 256 * minInflight // 4096 max streams per connection on the server side
	)
	return &dynamicThrottler{
		minInflight:    minInflight,
		maxInflight:    maxInflight,
		targetInflight: 8 * minInflight,
		targetTimesTen: 10 * maxInflight,

		throughputs: make([]float64, 128),

		start: nowFunc(),
		now:   nowFunc,
	}
}

func NewThrottler(connections int) Throttler { return newThrottler(connections, time.Now) }

func (t *dynamicThrottler) Sent() {
	currentInflight := atomic.LoadInt64(&t.targetInflight)
	t.sent++
	if t.sent*t.sent*t.sent < 100*currentInflight*currentInflight {
		return
	}
	t.sent = 0
	now := t.now()
	elapsed := now.Sub(t.start)
	t.start = now
	currentThroughput := float64(atomic.SwapInt64(&t.ok, 0)) / float64(elapsed)

	// Use buckets for throughput over inflight, along the log-scale, in [minInflight, maxInflight).
	index := int(float64(len(t.throughputs)) * math.Log(max(1, min(255, float64(currentInflight)/float64(t.minInflight)))) / math.Log(256))
	t.throughputs[index] = currentThroughput

	// Loop over throughput measurements and pick the one which optimises throughput and latency.
	choice := float64(currentInflight)
	maxObjective := float64(-1)
	for i := len(t.throughputs) - 1; i >= 0; i-- {
		if t.throughputs[i] == 0 {
			continue // Skip unknown values
		}
		inflight := float64(t.minInflight) * math.Pow(256, (float64(i)+0.5)/float64(len(t.throughputs)))
		objective := t.throughputs[i] * math.Pow(inflight, throttlerWeight-1) // Optimise throughput (weight), but also latency (1 - weight)
		if objective > maxObjective {
			maxObjective = objective
			choice = inflight
		}
	}
	target := int64((rand.Float64()*0.20 + 0.92) * choice) // Random walk, skewed towards increase
	atomic.StoreInt64(&t.targetInflight, max(t.minInflight, min(t.maxInflight, target)))
}

func (t *dynamicThrottler) Success() {
	atomic.AddInt64(&t.targetTimesTen, 1)
	atomic.AddInt64(&t.ok, 1)
}

func (t *dynamicThrottler) Throttled(inflight int64) {
	atomic.StoreInt64(&t.targetTimesTen, max(inflight*5, t.minInflight*10))
}

func (t *dynamicThrottler) TargetInflight() int64 {
	staticTargetInflight := min(t.maxInflight, atomic.LoadInt64(&t.targetTimesTen)/10)
	targetInflight := atomic.LoadInt64(&t.targetInflight)
	return min(staticTargetInflight, targetInflight)
}

type number interface{ float64 | int64 }

func min[T number](x, y T) T {
	if x < y {
		return x
	}
	return y
}

func max[T number](x, y T) T {
	if x > y {
		return x
	}
	return y
}
