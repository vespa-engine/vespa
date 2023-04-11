package document

import (
	"time"
)

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
	Id         Id
	Status     Status
	HTTPStatus int
	Message    string
	Trace      string
	Err        error
	Stats      Stats
}

func (r Result) Success() bool {
	return r.Err == nil && (r.Status == StatusSuccess || r.Status == StatusConditionNotMet)
}

// Stats represents feeding operation statistics.
type Stats struct {
	Requests        int64
	Responses       int64
	ResponsesByCode map[int]int64
	Errors          int64
	Inflight        int64
	TotalLatency    time.Duration
	MinLatency      time.Duration
	MaxLatency      time.Duration
	BytesSent       int64
	BytesRecv       int64
}

// AvgLatency returns the average latency for a request.
func (s Stats) AvgLatency() time.Duration {
	requests := s.Requests
	if requests == 0 {
		requests = 1
	}
	return s.TotalLatency / time.Duration(requests)
}

func (s Stats) Successes() int64 {
	if s.ResponsesByCode == nil {
		return 0
	}
	return s.ResponsesByCode[200]
}

// Add all statistics contained in other to this.
func (s *Stats) Add(other Stats) {
	s.Requests += other.Requests
	s.Responses += other.Responses
	if s.ResponsesByCode == nil && other.ResponsesByCode != nil {
		s.ResponsesByCode = make(map[int]int64)
	}
	for code, count := range other.ResponsesByCode {
		_, ok := s.ResponsesByCode[code]
		if ok {
			s.ResponsesByCode[code] += count
		} else {
			s.ResponsesByCode[code] = count
		}
	}
	s.Errors += other.Errors
	s.Inflight += other.Inflight
	s.TotalLatency += other.TotalLatency
	if s.MinLatency == 0 || (other.MinLatency > 0 && other.MinLatency < s.MinLatency) {
		s.MinLatency = other.MinLatency
	}
	if other.MaxLatency > s.MaxLatency {
		s.MaxLatency = other.MaxLatency
	}
	s.BytesSent += other.BytesSent
	s.BytesRecv += other.BytesRecv
}

// Feeder is the interface for a consumer of documents.
type Feeder interface{ Send(Document) Result }
