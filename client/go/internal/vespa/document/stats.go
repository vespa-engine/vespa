// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package document

import (
	"time"
)

// Status of a document operation.
type Status int

const (
	// StatusSuccess indicates a successful document operation.
	StatusSuccess Status = iota
	// StatusConditionNotMet indicates that the document operation itself was successful, but did not satisfy its
	// test-and-set condition.
	StatusConditionNotMet
	// StatusVespaFailure indicates that Vespa failed to process the document operation.
	StatusVespaFailure
	// StatusTransportFailure indicates that there was failure in the transport layer error while sending the document
	// operation to Vespa.
	StatusTransportFailure
)

// Result represents the result of a feeding operation.
type Result struct {
	Err        error
	Id         Id
	Trace      string
	Body       []byte
	Status     Status
	HTTPStatus int
	Latency    time.Duration
	BytesSent  int64
	BytesRecv  int64
}

func (r Result) Success() bool {
	return r.HTTPStatus/100 == 2 || r.HTTPStatus == 404 || r.HTTPStatus == 412
}

// Stats represents feeding operation statistics.
type Stats struct {
	// Number of operations passed to the feeder by the user, not counting retries.
	Operations int64
	// Number of responses received, grouped by the HTTP status code. Requests that do not receive a response (i.e. no
	// status code) are not counted.
	ResponsesByCode map[int]int64
	// Number of requests made, including retries.
	Requests int64
	// Number of responses received.
	Responses int64
	// Number of transport layer errors.
	Errors int64
	// Number of requests currently in-flight.
	Inflight int64
	// Sum of response latency
	TotalLatency time.Duration
	// Lowest recorded response latency
	MinLatency time.Duration
	// Highest recorded response latency
	MaxLatency time.Duration
	// Total bytes sent
	BytesSent int64
	// Total bytes received
	BytesRecv int64
}

// AvgLatency returns the average latency for a request.
func (s Stats) AvgLatency() time.Duration {
	requests := s.Requests
	if requests == 0 {
		requests = 1
	}
	return s.TotalLatency / time.Duration(requests)
}

func (s Stats) Successful() int64 {
	if s.ResponsesByCode == nil {
		return 0
	}
	return s.ResponsesByCode[200]
}

func (s Stats) Unsuccessful() int64 { return s.Requests - s.Successful() }

func (s Stats) Clone() Stats {
	if s.ResponsesByCode != nil {
		mapCopy := make(map[int]int64)
		for k, v := range s.ResponsesByCode {
			mapCopy[k] = v
		}
		s.ResponsesByCode = mapCopy
	}
	return s
}

// Add statistics from result to this.
func (s *Stats) Add(result Result, retry bool) {
	if !retry {
		s.Operations++
	}
	s.Requests++
	if s.ResponsesByCode == nil {
		s.ResponsesByCode = make(map[int]int64)
	}
	if result.Err == nil {
		s.ResponsesByCode[result.HTTPStatus]++
		s.Responses++
	} else {
		s.Errors++
	}
	s.TotalLatency += result.Latency
	if result.Latency < s.MinLatency || s.MinLatency == 0 {
		s.MinLatency = result.Latency
	}
	if result.Latency > s.MaxLatency {
		s.MaxLatency = result.Latency
	}
	s.BytesSent += result.BytesSent
	s.BytesRecv += result.BytesRecv
}
