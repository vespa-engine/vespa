package document

import (
	"math"
	"sync/atomic"
	"time"
)

type CircuitState int

const (
	// CircuitClosed represents a closed circuit. Documents are processed successfully
	CircuitClosed CircuitState = iota
	// CircuitHalfOpen represents a half-open circuit. Some errors have happend, but processing may still recover
	CircuitHalfOpen
	// CircuitOpen represents a open circuit. Something is broken. We should no longer process documents
	CircuitOpen
)

type CircuitBreaker interface {
	Success()
	Failure()
	State() CircuitState
}

type timeCircuitBreaker struct {
	graceDuration time.Duration
	doomDuration  time.Duration

	failingSinceMillis atomic.Int64
	halfOpen           atomic.Bool
	open               atomic.Bool

	now func() time.Time
}

func (b *timeCircuitBreaker) Success() {
	b.failingSinceMillis.Store(math.MaxInt64)
	if !b.open.Load() {
		b.halfOpen.CompareAndSwap(true, false)
	}
}

func (b *timeCircuitBreaker) Failure() {
	b.failingSinceMillis.CompareAndSwap(math.MaxInt64, b.now().UnixMilli())
}

func (b *timeCircuitBreaker) State() CircuitState {
	failingDuration := b.now().Sub(time.UnixMilli(b.failingSinceMillis.Load()))
	if failingDuration > b.graceDuration {
		b.halfOpen.CompareAndSwap(false, true)
	}
	if b.doomDuration > 0 && failingDuration > b.doomDuration {
		b.open.CompareAndSwap(false, true)
	}
	if b.open.Load() {
		return CircuitOpen
	} else if b.halfOpen.Load() {
		return CircuitHalfOpen
	}
	return CircuitClosed
}

func NewCircuitBreaker(graceDuration, doomDuration time.Duration) *timeCircuitBreaker {
	b := &timeCircuitBreaker{
		graceDuration: graceDuration,
		doomDuration:  doomDuration,
		now:           time.Now,
	}
	b.failingSinceMillis.Store(math.MaxInt64)
	b.open.Store(false)
	b.halfOpen.Store(false)
	return b
}
