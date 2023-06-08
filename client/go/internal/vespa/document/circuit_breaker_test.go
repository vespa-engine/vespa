package document

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestCircuitBreaker(t *testing.T) {
	clock := &manualClock{}
	breaker := NewCircuitBreaker(time.Second, time.Minute)
	breaker.now = clock.now

	assert.Equal(t, CircuitClosed, breaker.State(), "Initial state is closed")

	clock.advance(100 * time.Second)
	assert.Equal(t, CircuitClosed, breaker.State(), "State is closed after some time without activity")

	breaker.Success()
	assert.Equal(t, CircuitClosed, breaker.State(), "State is closed after a success")

	clock.advance(100 * time.Second)
	assert.Equal(t, CircuitClosed, breaker.State(), "State is closed some time after a success")

	breaker.Failure()
	assert.Equal(t, CircuitClosed, breaker.State(), "State is closed right after a failure")

	clock.advance(time.Second)
	assert.Equal(t, CircuitClosed, breaker.State(), "State is closed until grace duration has passed")

	clock.advance(time.Millisecond)
	assert.Equal(t, CircuitHalfOpen, breaker.State(), "State is half-open when grace duration has passed")

	breaker.Success()
	assert.Equal(t, CircuitClosed, breaker.State(), "State is closed after a new success")

	breaker.Failure()
	clock.advance(time.Minute)
	assert.Equal(t, CircuitHalfOpen, breaker.State(), "State is half-open until doom duration has passed")

	clock.advance(time.Millisecond)
	assert.Equal(t, CircuitOpen, breaker.State(), "State is open when doom duration has passed")

	breaker.Success()
	assert.Equal(t, CircuitOpen, breaker.State(), "State remains open in spite of new successes")
}
