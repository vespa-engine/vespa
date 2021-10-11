package vespa

import "net/url"

// BatchableRequest defines a struc that is able to
// be used by the BatchService. Wraps the method Source()
type BatchableRequest interface {
	Source() interface{}
	Validate() error
	BuildURL() (method, path string, parameters url.Values)
}
