package document

import (
	"fmt"
	"sync"
)

const maxAttempts = 10

// Dispatcher dispatches documents from a queue to a Feeder.
type Dispatcher struct {
	workers  int
	feeder   Feeder
	ready    chan Id
	inflight map[string]*documentGroup
	mu       sync.RWMutex
	wg       sync.WaitGroup
	closed   bool
}

// documentGroup holds document operations which share their ID, and must be dispatched in order.
type documentGroup struct {
	id         Id
	failed     bool
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

func NewDispatcher(feeder Feeder, workers int) *Dispatcher {
	if workers < 1 {
		workers = 1
	}
	d := &Dispatcher{
		workers:  workers,
		feeder:   feeder,
		inflight: make(map[string]*documentGroup),
	}
	d.start()
	return d
}

func (d *Dispatcher) dispatchAll(g *documentGroup) int {
	g.mu.Lock()
	defer g.mu.Unlock()
	failCount := len(g.operations)
	for i := 0; !g.failed && i < len(g.operations); i++ {
		op := g.operations[i]
		ok := false
		for op.attempts <= maxAttempts && !ok {
			op.attempts += 1
			// TODO(mpolden): Extract function which does throttling/circuit-breaking
			result := d.feeder.Send(op.document)
			ok = result.Status.Success()
		}
		if ok {
			failCount--
		} else {
			g.failed = true
			failCount = len(g.operations) - i
		}
	}
	g.operations = nil
	return failCount
}

func (d *Dispatcher) start() {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.closed = false
	d.ready = make(chan Id, 4*d.workers)
	for i := 0; i < d.workers; i++ {
		d.wg.Add(1)
		go func() {
			defer d.wg.Done()
			for id := range d.ready {
				d.mu.RLock()
				group := d.inflight[id.String()]
				d.mu.RUnlock()
				if group != nil {
					failedDocs := d.dispatchAll(group)
					d.feeder.AddStats(Stats{Errors: int64(failedDocs)})
				}
			}
		}()
	}
}

func (d *Dispatcher) Enqueue(doc Document) error {
	d.mu.Lock()
	defer d.mu.Unlock()
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
	d.ready <- doc.Id
	return nil
}

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
