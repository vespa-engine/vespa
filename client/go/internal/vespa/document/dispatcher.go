package document

import (
	"container/list"
	"fmt"
	"io"
	"strings"
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

	started bool
	ready   chan Id
	results chan Result
	msgs    chan string

	inflight      map[string]*documentGroup
	inflightCount int64
	output        io.Writer
	verbose       bool

	listPool sync.Pool
	mu       sync.RWMutex
	workerWg sync.WaitGroup
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

func (g *documentGroup) add(op documentOp, first bool, listPool *sync.Pool) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.ops == nil {
		g.ops = listPool.Get().(*list.List)
	}
	if first {
		g.ops.PushFront(op)
	} else {
		g.ops.PushBack(op)
	}
}

func NewDispatcher(feeder Feeder, throttler Throttler, breaker CircuitBreaker, output io.Writer, verbose bool) *Dispatcher {
	d := &Dispatcher{
		feeder:         feeder,
		throttler:      throttler,
		circuitBreaker: breaker,
		inflight:       make(map[string]*documentGroup),
		output:         output,
		verbose:        verbose,
	}
	d.start()
	return d
}

func (d *Dispatcher) sendDocumentIn(group *documentGroup) {
	group.mu.Lock()
	first := group.ops.Front()
	if first == nil {
		panic("sending from empty document group, this should not happen")
	}
	op := group.ops.Remove(first).(documentOp)
	op.attempts++
	result := d.feeder.Send(op.document)
	d.results <- result
	d.releaseSlot()
	if group.ops.Front() == nil { // Empty list, release it back to the pool
		d.listPool.Put(group.ops)
		group.ops = nil
	}
	group.mu.Unlock()
	if d.shouldRetry(op, result) {
		d.enqueue(op)
	}
}

func (d *Dispatcher) shouldRetry(op documentOp, result Result) bool {
	if result.HTTPStatus/100 == 2 || result.HTTPStatus == 404 || result.HTTPStatus == 412 {
		if d.verbose {
			d.msgs <- fmt.Sprintf("feed: successfully fed %s with status %d", op.document.Id, result.HTTPStatus)
		}
		d.throttler.Success()
		d.circuitBreaker.Success()
		return false
	}
	if result.HTTPStatus == 429 || result.HTTPStatus == 503 {
		d.msgs <- fmt.Sprintf("feed: %s was throttled with status %d: retrying\n", op.document, result.HTTPStatus)
		d.throttler.Throttled(atomic.LoadInt64(&d.inflightCount))
		return true
	}
	if result.Err != nil || result.HTTPStatus == 500 || result.HTTPStatus == 502 || result.HTTPStatus == 504 {
		retry := op.attempts <= maxAttempts
		var msg strings.Builder
		msg.WriteString("feed: ")
		msg.WriteString(op.document.String())
		if result.Err != nil {
			msg.WriteString("error ")
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
	d.listPool.New = func() any { return list.New() }
	d.ready = make(chan Id, 4096)
	d.results = make(chan Result, 4096)
	d.msgs = make(chan string, 4096)
	d.started = true
	d.resultWg.Add(2)
	go d.sumStats()
	go d.printMessages()
}

func (d *Dispatcher) sumStats() {
	defer d.resultWg.Done()
	for result := range d.results {
		d.stats.Add(result.Stats)
	}
}

func (d *Dispatcher) printMessages() {
	defer d.resultWg.Done()
	for msg := range d.msgs {
		fmt.Fprintln(d.output, msg)
	}
}

func (d *Dispatcher) enqueue(op documentOp) error {
	d.mu.Lock()
	if !d.started {
		return fmt.Errorf("dispatcher is closed")
	}
	key := op.document.Id.String()
	group, ok := d.inflight[key]
	if !ok {
		group = &documentGroup{}
		d.inflight[key] = group
	}
	d.mu.Unlock()
	group.add(op, op.attempts > 0, &d.listPool)
	d.enqueueWithSlot(op.document.Id)
	return nil
}

func (d *Dispatcher) enqueueWithSlot(id Id) {
	d.acquireSlot()
	d.ready <- id
	d.throttler.Sent()
	d.dispatch()
}

func (d *Dispatcher) dispatch() {
	d.workerWg.Add(1)
	go func() {
		defer d.workerWg.Done()
		id := <-d.ready
		d.mu.RLock()
		group := d.inflight[id.String()]
		d.mu.RUnlock()
		d.sendDocumentIn(group)
	}()
}

func (d *Dispatcher) acquireSlot() {
	for atomic.LoadInt64(&d.inflightCount) >= d.throttler.TargetInflight() {
		time.Sleep(time.Millisecond)
	}
	atomic.AddInt64(&d.inflightCount, 1)
}

func (d *Dispatcher) releaseSlot() { atomic.AddInt64(&d.inflightCount, -1) }

func (d *Dispatcher) Enqueue(doc Document) error { return d.enqueue(documentOp{document: doc}) }

func (d *Dispatcher) Stats() Stats { return d.stats }

// Close closes the dispatcher and waits for all inflight operations to complete.
func (d *Dispatcher) Close() error {
	d.workerWg.Wait() // Wait for all inflight operations to complete
	d.mu.Lock()
	if d.started {
		close(d.results)
		close(d.msgs)
		d.started = false
	}
	d.mu.Unlock()
	d.resultWg.Wait() // Wait for results
	return nil
}
