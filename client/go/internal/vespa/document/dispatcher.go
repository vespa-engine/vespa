package document

import (
	"container/list"
	"fmt"
	"io"
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

	started       bool
	ready         chan Id
	results       chan Result
	inflight      map[string]*documentGroup
	inflightCount int64
	errWriter     io.Writer

	mu       sync.RWMutex
	wg       sync.WaitGroup
	resultWg sync.WaitGroup
}

// documentOp represents a document operation and the number of times it has been attempted.
type documentOp struct {
	document Document
	attempts int
}

// documentGroup holds document operations which share an ID, and must be dispatched in order.
type documentGroup struct {
	ops *list.List
	mu  sync.Mutex
}

func (g *documentGroup) add(op documentOp, first bool) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.ops == nil {
		g.ops = list.New()
	}
	if first {
		g.ops.PushFront(op)
	} else {
		g.ops.PushBack(op)
	}
}

func NewDispatcher(feeder Feeder, throttler Throttler, breaker CircuitBreaker, errWriter io.Writer) *Dispatcher {
	d := &Dispatcher{
		feeder:         feeder,
		throttler:      throttler,
		circuitBreaker: breaker,
		inflight:       make(map[string]*documentGroup),
		errWriter:      errWriter,
	}
	d.start()
	return d
}

func (d *Dispatcher) sendDocumentIn(group *documentGroup) {
	group.mu.Lock()
	defer group.mu.Unlock()
	defer d.releaseSlot()
	first := group.ops.Front()
	if first == nil {
		panic("sending from empty document group, this should not happen")
	}
	op := group.ops.Remove(first).(documentOp)
	op.attempts++
	result := d.feeder.Send(op.document)
	d.results <- result
	if d.shouldRetry(op, result) {
		d.enqueue(op)
	}
}

func (d *Dispatcher) shouldRetry(op documentOp, result Result) bool {
	if result.HTTPStatus/100 == 2 || result.HTTPStatus == 404 || result.HTTPStatus == 412 {
		d.throttler.Success()
		d.circuitBreaker.Success()
		return false
	}
	if result.HTTPStatus == 429 || result.HTTPStatus == 503 {
		fmt.Fprintf(d.errWriter, "feed: %s was throttled with status %d: retrying\n", op.document, result.HTTPStatus)
		d.throttler.Throttled(atomic.LoadInt64(&d.inflightCount))
		return true
	}
	if result.Err != nil || result.HTTPStatus == 500 || result.HTTPStatus == 502 || result.HTTPStatus == 504 {
		retry := op.attempts <= maxAttempts
		msg := "feed: " + op.document.String() + " failed with "
		if result.Err != nil {
			msg += "error " + result.Err.Error()
		} else {
			msg += fmt.Sprintf("status %d", result.HTTPStatus)
		}
		if retry {
			msg += ": retrying"
		} else {
			msg += fmt.Sprintf(": giving up after %d attempts", maxAttempts)
		}
		fmt.Fprintln(d.errWriter, msg)
		d.circuitBreaker.Error(fmt.Errorf("request failed with status %d", result.HTTPStatus))
		if retry {
			return true
		}
	}
	return false
}

func (d *Dispatcher) start() {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.started {
		return
	}
	d.ready = make(chan Id, 4096)
	d.results = make(chan Result, 4096)
	d.started = true
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
		d.wg.Add(1)
		go func() {
			defer d.wg.Done()
			d.sendDocumentIn(group)
		}()
	}
}

func (d *Dispatcher) readResults() {
	for result := range d.results {
		d.stats.Add(result.Stats)
	}
}

func (d *Dispatcher) enqueue(op documentOp) error {
	d.mu.Lock()
	if !d.started {
		return fmt.Errorf("dispatcher is closed")
	}
	group, ok := d.inflight[op.document.Id.String()]
	if !ok {
		group = &documentGroup{}
		d.inflight[op.document.Id.String()] = group
	}
	d.mu.Unlock()
	group.add(op, op.attempts > 0)
	d.enqueueWithSlot(op.document.Id)
	return nil
}

func (d *Dispatcher) enqueueWithSlot(id Id) {
	d.acquireSlot()
	d.ready <- id
	d.throttler.Sent()
}

func (d *Dispatcher) acquireSlot() {
	for atomic.LoadInt64(&d.inflightCount) >= d.throttler.TargetInflight() {
		time.Sleep(time.Millisecond)
	}
	atomic.AddInt64(&d.inflightCount, 1)
}

func (d *Dispatcher) releaseSlot() { atomic.AddInt64(&d.inflightCount, -1) }

func closeAndWait[T any](ch chan T, wg *sync.WaitGroup, d *Dispatcher, markClosed bool) {
	d.mu.Lock()
	if d.started {
		close(ch)
		if markClosed {
			d.started = false
		}
	}
	d.mu.Unlock()
	wg.Wait()
}

func (d *Dispatcher) Enqueue(doc Document) error { return d.enqueue(documentOp{document: doc}) }

func (d *Dispatcher) Stats() Stats { return d.stats }

// Close closes the dispatcher and waits for all inflight operations to complete.
func (d *Dispatcher) Close() error {
	closeAndWait(d.ready, &d.wg, d, false)
	closeAndWait(d.results, &d.resultWg, d, true)
	return nil
}
