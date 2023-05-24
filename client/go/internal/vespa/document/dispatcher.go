package document

import (
	"fmt"
	"io"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// maxAttempts controls the maximum number of times a document operation is attempted before giving up.
const maxAttempts = 10

// Feeder is the interface for a consumer of documents.
type Feeder interface{ Send(Document) Result }

// Dispatcher dispatches documents from a queue to a Feeder.
type Dispatcher struct {
	feeder         Feeder
	throttler      Throttler
	circuitBreaker CircuitBreaker
	stats          Stats

	started bool
	results chan documentOp
	msgs    chan string

	inflight      map[string]*Queue[documentOp]
	inflightCount atomic.Int64
	output        io.Writer
	verbose       bool

	mu         sync.Mutex
	statsMu    sync.Mutex
	wg         sync.WaitGroup
	inflightWg sync.WaitGroup
}

// documentOp represents a document operation and the number of times it has been attempted.
type documentOp struct {
	document Document
	result   Result
	attempts int
}

func (op documentOp) resetResult() documentOp {
	op.result = Result{}
	return op
}

func NewDispatcher(feeder Feeder, throttler Throttler, breaker CircuitBreaker, output io.Writer, verbose bool) *Dispatcher {
	d := &Dispatcher{
		feeder:         feeder,
		throttler:      throttler,
		circuitBreaker: breaker,
		inflight:       make(map[string]*Queue[documentOp]),
		output:         output,
		verbose:        verbose,
	}
	d.start()
	return d
}

func (d *Dispatcher) shouldRetry(op documentOp, result Result) bool {
	if result.Trace != "" {
		d.msgs <- fmt.Sprintf("feed: trace for %s:\n%s", op.document, result.Trace)
	}
	if result.Success() {
		if d.verbose {
			d.msgs <- fmt.Sprintf("feed: %s succeeded with status %d", op.document, result.HTTPStatus)
		}
		d.throttler.Success()
		d.circuitBreaker.Success()
		return false
	}
	if result.HTTPStatus == 429 || result.HTTPStatus == 503 {
		d.msgs <- fmt.Sprintf("feed: %s was throttled with status %d: retrying", op.document, result.HTTPStatus)
		d.throttler.Throttled(d.inflightCount.Load())
		return true
	}
	if result.Err != nil || result.HTTPStatus == 500 || result.HTTPStatus == 502 || result.HTTPStatus == 504 {
		retry := op.attempts < maxAttempts
		var msg strings.Builder
		msg.WriteString("feed: ")
		msg.WriteString(op.document.String())
		msg.WriteString(" failed: ")
		if result.Err != nil {
			msg.WriteString(result.Err.Error())
		} else {
			msg.WriteString(fmt.Sprintf("status %d", result.HTTPStatus))
		}
		if retry {
			msg.WriteString(": retrying")
		} else {
			msg.WriteString(fmt.Sprintf(": giving up after %d attempts", maxAttempts))
		}
		d.msgs <- msg.String()
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
	d.results = make(chan documentOp, 4096)
	d.msgs = make(chan string, 4096)
	d.started = true
	d.wg.Add(2)
	go d.processResults()
	go d.printMessages()
}

func (d *Dispatcher) dispatch(op documentOp) {
	if !d.acceptDocument() {
		d.msgs <- fmt.Sprintf("refusing to dispatch document %s: too many errors", op.document.Id.String())
		d.results <- op.resetResult()
		return
	}
	go func() {
		op.attempts++
		op.result = d.feeder.Send(op.document)
		d.results <- op
	}()
}

func (d *Dispatcher) processResults() {
	defer d.wg.Done()
	for op := range d.results {
		d.statsMu.Lock()
		d.stats.Add(op.result)
		d.statsMu.Unlock()
		if d.shouldRetry(op, op.result) {
			d.enqueue(op.resetResult(), true)
		} else {
			op.document.Reset()
			d.inflightWg.Done()
		}
		d.dispatchNext(op.document.Id)
	}
}

func (d *Dispatcher) dispatchNext(id Id) {
	d.mu.Lock()
	defer d.mu.Unlock()
	k := id.String()
	q, ok := d.inflight[k]
	if !ok {
		panic("no queue exists for " + id.String() + ": this should not happen")
	}
	hasNext := q != nil
	if hasNext {
		if next, ok := q.Poll(); ok {
			// we have more operations with this ID: dispatch the next one
			d.dispatch(next)
		} else {
			hasNext = false
		}
	}
	if !hasNext {
		// no more operations with this ID: release slot
		delete(d.inflight, k)
		d.releaseSlot()
	}
}

func (d *Dispatcher) printMessages() {
	defer d.wg.Done()
	for msg := range d.msgs {
		fmt.Fprintln(d.output, msg)
	}
}

func (d *Dispatcher) enqueue(op documentOp, isRetry bool) error {
	d.mu.Lock()
	if !d.started {
		d.mu.Unlock()
		return fmt.Errorf("dispatcher is closed")
	}
	if !d.acceptDocument() {
		d.mu.Unlock()
		return fmt.Errorf("refusing to enqueue document %s: too many errors", op.document.Id.String())
	}
	k := op.document.Id.String()
	q, ok := d.inflight[k]
	if !ok {
		d.inflight[k] = nil // track operation, but defer allocating queue until needed
	} else {
		if q == nil {
			q = NewQueue[documentOp]()
			d.inflight[k] = q
		}
		q.Add(op, isRetry)
	}
	if !isRetry {
		d.inflightWg.Add(1)
	}
	d.mu.Unlock()
	if !ok && !isRetry {
		// first operation with this ID: acquire slot and dispatch
		d.acquireSlot()
		d.dispatch(op)
		d.throttler.Sent()
	}
	return nil
}

func (d *Dispatcher) acceptDocument() bool {
	switch d.circuitBreaker.State() {
	case CircuitClosed:
		return true
	case CircuitHalfOpen:
		time.Sleep(time.Second)
		return true
	case CircuitOpen:
		return false
	}
	panic("invalid circuit state")
}

func (d *Dispatcher) acquireSlot() {
	for d.inflightCount.Load() >= d.throttler.TargetInflight() {
		time.Sleep(time.Millisecond)
	}
	d.inflightCount.Add(1)
}

func (d *Dispatcher) releaseSlot() { d.inflightCount.Add(-1) }

func (d *Dispatcher) Enqueue(doc Document) error { return d.enqueue(documentOp{document: doc}, false) }

func (d *Dispatcher) Stats() Stats {
	d.statsMu.Lock()
	defer d.statsMu.Unlock()
	statsCopy := d.stats.Clone()
	statsCopy.Inflight = d.inflightCount.Load()
	return statsCopy
}

// Close waits for all inflight operations to complete and closes the dispatcher.
func (d *Dispatcher) Close() error {
	d.inflightWg.Wait() // Wait for all inflight operations to complete
	d.mu.Lock()
	if d.started {
		close(d.results)
		close(d.msgs)
		d.started = false
	}
	d.mu.Unlock()
	d.wg.Wait() // Wait for all channel readers to return
	return nil
}
