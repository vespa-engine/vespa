package document

import (
	"fmt"
	"sync"
)

// Dispatcher dispatches documents from a queue to a Feeder.
type Dispatcher struct {
	concurrencyLevel int
	feeder           Feeder
	pending          chan Document
	closed           bool
	mu               sync.RWMutex
	wg               sync.WaitGroup
}

func NewDispatcher(feeder Feeder, concurrencyLevel int) *Dispatcher {
	if concurrencyLevel < 1 {
		concurrencyLevel = 1
	}
	d := &Dispatcher{
		concurrencyLevel: concurrencyLevel,
		feeder:           feeder,
		pending:          make(chan Document, 4*concurrencyLevel),
	}
	d.readPending()
	return d
}

func (d *Dispatcher) readPending() {
	for i := 0; i < d.concurrencyLevel; i++ {
		d.wg.Add(1)
		go func(n int) {
			defer d.wg.Done()
			for doc := range d.pending {
				d.feeder.Send(doc)
			}
		}(i)
	}
}

func (d *Dispatcher) Enqueue(doc Document) error {
	d.mu.RLock()
	defer d.mu.RUnlock()
	if d.closed {
		return fmt.Errorf("cannot enqueue document in closed dispatcher")
	}
	d.pending <- doc
	return nil
}

// Close closes the dispatcher and waits for all goroutines to return.
func (d *Dispatcher) Close() error {
	d.mu.Lock()
	defer d.mu.Unlock()
	if !d.closed {
		d.closed = true
		close(d.pending)
		d.wg.Wait()
	}
	return nil
}
