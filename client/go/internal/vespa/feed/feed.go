package feed

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
	// StatusError is a catch-all status for any other error that might occur.
	StatusError
)

// Result represents the result of a feeding operation.
type Result struct {
	Id      DocumentId
	Status  Status
	Message string
	Trace   string
	Err     error
}

// Success returns whether status s is considered a success.
func (s Status) Success() bool { return s == StatusSuccess || s == StatusConditionNotMet }

// Feeder is the interface for code that perform a document operation and return its result.
type Feeder interface {
	Send(Document) Result
}
