package document

import (
	"fmt"
	"sync"
	"sync/atomic"
	"time"
)

const maxAttempts = 10

// Dispatcher dispatches documents from a queue to a Feeder.
type Dispatcher struct {
	feeder    Feeder
	throttler Throttler

	closed        bool
	ready         chan Id
	inflight      map[string]*documentGroup
	inflightCount atomic.Int64

	mu sync.RWMutex
	wg sync.WaitGroup
}

// documentGroup holds document operations which share their ID, and must be dispatched in order.
type documentGroup struct {
	id         Id
	operations []documentOp
	mu         sync.Mutex
}

type documentOp struct {
	document Document
	attempts int
}

func (g *documentGroup) append(op documentOp) {
	g.mu.Lock()
	defer g.mu.Unlock()
	g.operations = append(g.operations, op)
}

func NewDispatcher(feeder Feeder, throttler Throttler) *Dispatcher {
	d := &Dispatcher{
		feeder:    feeder,
		throttler: throttler,
		inflight:  make(map[string]*documentGroup),
	}
	d.start()
	return d
}

func (d *Dispatcher) dispatchAll(g *documentGroup) int {
	g.mu.Lock()
	defer g.mu.Unlock()
	failCount := 0
	for i := 0; i < len(g.operations); i++ {
		op := g.operations[i]
		ok := false
		for op.attempts <= maxAttempts && !ok {
			op.attempts += 1
			result := d.feeder.Send(op.document)
			d.releaseSlot()
			ok = result.Status.Success()
			if !d.shouldRetry(op, result) {
				break
			}
		}
		if !ok {
			failCount++
		}
	}
	g.operations = nil
	return failCount
}

func (d *Dispatcher) shouldRetry(op documentOp, result Result) bool {
	if result.HTTPStatus/100 == 2 || result.HTTPStatus == 404 || result.HTTPStatus == 412 {
		d.throttler.Success()
		return false
	}
	if result.HTTPStatus == 429 || result.HTTPStatus == 503 {
		d.throttler.Throttled(d.inflightCount.Load())
		return true
	}
	if result.HTTPStatus == 500 || result.HTTPStatus == 502 || result.HTTPStatus == 504 {
		// TODO(mpolden): Trigger circuit-breaker
	}
	return false
}

func (d *Dispatcher) start() {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.closed = false
	d.ready = make(chan Id, 4096)
	d.wg.Add(1)
	go func() {
		defer d.wg.Done()
		for id := range d.ready {
			d.mu.RLock()
			group := d.inflight[id.String()]
			d.mu.RUnlock()
			if group != nil {
				d.wg.Add(1)
				go func() {
					defer d.wg.Done()
					failedDocs := d.dispatchAll(group)
					d.feeder.AddStats(Stats{Errors: int64(failedDocs)})
				}()
			}
		}
	}()
}

func (d *Dispatcher) Enqueue(doc Document) error {
	d.mu.Lock()
	if d.closed {
		return fmt.Errorf("dispatcher is closed")
	}
	group, ok := d.inflight[doc.Id.String()]
	if ok {
		group.append(documentOp{document: doc})
	} else {
		group = &documentGroup{
			id:         doc.Id,
			operations: []documentOp{{document: doc}},
		}
		d.inflight[doc.Id.String()] = group
	}
	d.mu.Unlock()
	d.enqueueWithSlot(doc.Id)
	return nil
}

func (d *Dispatcher) enqueueWithSlot(id Id) {
	d.acquireSlot()
	d.ready <- id
	d.throttler.Sent()
}

func (d *Dispatcher) acquireSlot() {
	for d.inflightCount.Load() >= d.throttler.TargetInflight() {
		time.Sleep(time.Millisecond)
	}
	d.inflightCount.Add(1)
}

func (d *Dispatcher) releaseSlot() { d.inflightCount.Add(-1) }

// Close closes the dispatcher and waits for all inflight operations to complete.
func (d *Dispatcher) Close() error {
	d.mu.Lock()
	if !d.closed {
		close(d.ready)
		d.closed = true
	}
	d.mu.Unlock()
	d.wg.Wait()
	return nil
}
