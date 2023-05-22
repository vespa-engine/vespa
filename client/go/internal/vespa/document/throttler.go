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
		minInflight = 16 * int64(connections)
		maxInflight = 256 * minInflight // 4096 max streams per connection on the server side
	)
	t := &dynamicThrottler{
		minInflight: minInflight,
		maxInflight: maxInflight,

		throughputs: make([]float64, 128),

		start: nowFunc(),
		now:   nowFunc,
	}
	t.targetInflight.Store(8 * minInflight)
	t.targetTimesTen.Store(10 * maxInflight)
	return t
}

func NewThrottler(connections int) Throttler { return newThrottler(connections, time.Now) }

func (t *dynamicThrottler) Sent() {
	currentInflight := t.targetInflight.Load()
	t.sent++
	if t.sent*t.sent*t.sent < 100*currentInflight*currentInflight {
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
