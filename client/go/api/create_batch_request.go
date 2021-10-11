package vespa

import (
	"fmt"
	"net/http"
	"net/url"
)

// CreateBatchRequest is a request to insert document into vespa.
// Implements the Batcheable interface so it can be used with
// the Batch Service.
type CreateBatchRequest struct {
	BatchableRequest
	namespace string
	scheme    string
	id        string
	data      interface{}
	headers   http.Header
}

// NewCreateBatchRequest returns a new CreateBatchRequest object.
func NewCreateBatchRequest() *CreateBatchRequest {
	return &CreateBatchRequest{}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (s *CreateBatchRequest) Namespace(name string) *CreateBatchRequest {
	s.namespace = name
	return s
}

// Scheme sets the name of the scheme.
func (s *CreateBatchRequest) Scheme(scheme string) *CreateBatchRequest {
	s.scheme = scheme
	return s
}

// ID sets the id of the document
func (s *CreateBatchRequest) ID(id string) *CreateBatchRequest {
	s.id = id
	return s
}

// Body sets the documents to insert. It needs to be a JSON serializable object.
func (s *CreateBatchRequest) Body(body interface{}) *CreateBatchRequest {
	s.data = body
	return s
}

// Validate checks if the operation is valid.
func (s *CreateBatchRequest) Validate() error {
	var invalid []string
	if s.id == "" {
		invalid = append(invalid, "id")
	}
	if s.scheme == "" {
		invalid = append(invalid, "scheme")
	}
	if s.data == nil {
		invalid = append(invalid, "data")
	}
	if s.namespace == "" {
		invalid = append(invalid, "namespace")
	}

	if len(invalid) > 0 {
		return fmt.Errorf("missing required fields for operation: %v", invalid)
	}

	return nil
}

// BuildURL returns the values that combined can be used to build the target
// url for the batch operation.
func (s *CreateBatchRequest) BuildURL() (method, path string, params url.Values) {

	method = http.MethodPost
	path = fmt.Sprintf("/document/v1/%s/%s/docid/%s", s.namespace, s.scheme, s.id)

	params = url.Values{}

	return method, path, params

}

// Source returns the body of the request we will make to create job
// endpoint
func (s *CreateBatchRequest) Source() interface{} {
	payload := make(map[string]interface{})

	payload["fields"] = s.data

	return payload
}
