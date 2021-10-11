package vespa

import (
	"fmt"
	"net/http"
	"net/url"
)

// UpdateBatchRequest is a request to update document in Vespa.
// Implements the Batcheable interface so it can be used with
// the Batch Service.
type UpdateBatchRequest struct {
	BatchableRequest
	namespace string
	scheme    string
	id        string
	data      map[string]PartialUpdateItem
	headers   http.Header
}

// NewUpdateBatchRequest creates a new instance of UpdateBatchRequest
func NewUpdateBatchRequest() *UpdateBatchRequest {
	return &UpdateBatchRequest{}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (s *UpdateBatchRequest) Namespace(name string) *UpdateBatchRequest {
	s.namespace = name
	return s
}

// Scheme sets the name of the scheme.
func (s *UpdateBatchRequest) Scheme(scheme string) *UpdateBatchRequest {
	s.scheme = scheme
	return s
}

// ID sets the id of the document
func (s *UpdateBatchRequest) ID(id string) *UpdateBatchRequest {
	s.id = id
	return s
}

// Field sets a single field to be updated. Can be called multiple times.
func (s *UpdateBatchRequest) Field(key string, value interface{}) *UpdateBatchRequest {
	if s.data == nil {
		s.data = make(map[string]PartialUpdateItem)
	}

	s.data[key] = PartialUpdateItem{
		Assign: value,
	}

	return s
}

// Fields sets the fields to update
func (s *UpdateBatchRequest) Fields(items ...PartialUpdateField) *UpdateBatchRequest {
	if s.data == nil {
		s.data = make(map[string]PartialUpdateItem)
	}

	for _, item := range items {
		s.data[item.Key] = PartialUpdateItem{
			Assign: item.Value,
		}
	}

	return s
}

// Validate checks if the operation is valid.
func (s *UpdateBatchRequest) Validate() error {
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
func (s *UpdateBatchRequest) BuildURL() (method, path string, parameters url.Values) {

	method = http.MethodPut
	path = fmt.Sprintf("/document/v1/%s/%s/docid/%s", s.namespace, s.scheme, s.id)

	params := url.Values{}

	return method, path, params

}

// Source returns the body of the request we will make to create job
// endpoint
func (s *UpdateBatchRequest) Source() interface{} {
	payload := make(map[string]interface{})

	payload["fields"] = s.data

	return payload
}
