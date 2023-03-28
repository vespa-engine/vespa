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
	feeder         Feeder
	throttler      Throttler
	circuitBreaker CircuitBreaker
	stats          Stats

	closed        bool
	ready         chan Id
	results       chan Result
	inflight      map[string]*documentGroup
	inflightCount atomic.Int64

	mu       sync.RWMutex
	wg       sync.WaitGroup
	resultWg sync.WaitGroup
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

func NewDispatcher(feeder Feeder, throttler Throttler, breaker CircuitBreaker) *Dispatcher {
	d := &Dispatcher{
		feeder:         feeder,
		throttler:      throttler,
		circuitBreaker: breaker,
		inflight:       make(map[string]*documentGroup),
	}
	d.start()
	return d
}

func (d *Dispatcher) dispatchAll(g *documentGroup) {
	g.mu.Lock()
	defer g.mu.Unlock()
	for i := 0; i < len(g.operations); i++ {
		op := g.operations[i]
		ok := false
		for !ok {
			op.attempts++
			result := d.feeder.Send(op.document)
			d.results <- result
			ok = result.Status.Success()
			if !d.shouldRetry(op, result) {
				break
			}
		}
		d.releaseSlot()
	}
	g.operations = nil
}

func (d *Dispatcher) shouldRetry(op documentOp, result Result) bool {
	if result.HTTPStatus/100 == 2 || result.HTTPStatus == 404 || result.HTTPStatus == 412 {
		d.throttler.Success()
		d.circuitBreaker.Success()
		return false
	}
	if result.HTTPStatus == 429 || result.HTTPStatus == 503 {
		d.throttler.Throttled(d.inflightCount.Load())
		return true
	}
	if result.HTTPStatus == 500 || result.HTTPStatus == 502 || result.HTTPStatus == 504 {
		d.circuitBreaker.Error(fmt.Errorf("request failed with status %d", result.HTTPStatus))
		if op.attempts <= maxAttempts {
			return true
		}
	}
	return false
}

func (d *Dispatcher) start() {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.ready = make(chan Id, 4096)
	d.results = make(chan Result, 4096)
	d.closed = false
	d.wg.Add(1)
	go func() {
		defer d.wg.Done()
		d.readDocuments()
	}()
	d.resultWg.Add(1)
	go func() {
		defer d.resultWg.Done()
		d.readResults()
	}()
}

func (d *Dispatcher) readDocuments() {
	for id := range d.ready {
		d.mu.RLock()
		group := d.inflight[id.String()]
		d.mu.RUnlock()
		if group != nil {
			d.wg.Add(1)
			go func() {
				defer d.wg.Done()
				d.dispatchAll(group)
			}()
		}
	}
}

func (d *Dispatcher) readResults() {
	for result := range d.results {
		d.stats.Add(result.Stats)
	}
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

func (d *Dispatcher) Stats() Stats { return d.stats }

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

func closeAndWait[T any](ch chan T, wg *sync.WaitGroup, d *Dispatcher, markClosed bool) {
	d.mu.Lock()
	if !d.closed {
		close(ch)
		if markClosed {
			d.closed = true
		}
	}
	d.mu.Unlock()
	wg.Wait()
}

// Close closes the dispatcher and waits for all inflight operations to complete.
func (d *Dispatcher) Close() error {
	closeAndWait(d.ready, &d.wg, d, false)
	closeAndWait(d.results, &d.resultWg, d, true)
	return nil
}
