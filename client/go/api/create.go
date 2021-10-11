package vespa

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
)

const (
	// DefaultNamespace defines the default namespace documents live in when we the
	// user does not specifies one namespace
	DefaultNamespace = "default"
)

// CreateService adds or updates a typed JSON document in a specified schema,
// making it searchable.
//
// See https://docs.vespa.ai/en/document-v1-api-guide.html
// for details.
type CreateService struct {
	client HTTPClient

	namespace string
	scheme    string
	id        string
	data      interface{}
	timeout   string
	headers   http.Header
}

// HTTPClient is the interface that wraps the PerformRequest operation
// which makes requests to Vespa through http.
type HTTPClient interface {
	PerformRequest(ctx context.Context, opt PerformRequest) (*http.Response, error)
}

// NewCreateService will create and instance of CreateService.
func NewCreateService(client HTTPClient) *CreateService {
	return &CreateService{
		client:    client,
		namespace: DefaultNamespace,
	}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (s *CreateService) Namespace(name string) *CreateService {
	s.namespace = name
	return s
}

// Scheme sets the name of the scheme.
func (s *CreateService) Scheme(scheme string) *CreateService {
	s.scheme = scheme
	return s
}

// ID sets the id of the document
func (s *CreateService) ID(id string) *CreateService {
	s.id = id
	return s
}

// Body sets the documents to insert. It needs to be a JSON serializable object.
func (s *CreateService) Body(body interface{}) *CreateService {
	s.data = body
	return s
}

// Timeout sets the Request timeout expresed in in seconds, or with optional
// ks, s, ms or µs unit.
func (s *CreateService) Timeout(timeout string) *CreateService {
	s.timeout = timeout
	return s
}

// Validate checks if the operation is valid.
func (s *CreateService) Validate() error {
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

func (s *CreateService) buildURL() (method, path string, params url.Values) {

	method = http.MethodPost
	path = fmt.Sprintf("/document/v1/%s/%s/docid/%s", s.namespace, s.scheme, s.id)

	params = url.Values{}
	if s.timeout != "" {
		params.Set("timeout", s.timeout)
	}

	return method, path, params

}

// Source returns the body of the request we will make to create job
// endpoint
func (s *CreateService) Source() interface{} {
	payload := make(map[string]interface{})

	payload["fields"] = s.data

	return payload
}

// Do executes the create operation. Returns a CreateResponse or an error.
func (s *CreateService) Do(ctx context.Context) (*CreateResponse, error) {

	if err := s.Validate(); err != nil {
		return nil, err
	}

	// Get URL for request
	method, path, params := s.buildURL()
	data := s.Source()

	// Get HTTP response
	res, err := s.client.PerformRequest(ctx, PerformRequest{
		Method:  method,
		Path:    path,
		Params:  params,
		Body:    data,
		Headers: s.headers,
	})
	if err != nil {
		return nil, err
	}
	defer func() {
		err := res.Body.Close()
		if err != nil {
			log.Print(err)
		}
	}()

	var cr CreateResponse
	cr.Status = res.StatusCode

	err = json.NewDecoder(res.Body).Decode(&cr)

	return &cr, err

}

// CreateResponse is the result of creating a document in Vespa.
type CreateResponse struct {
	Status  int    `json:"status"`
	ID      string `json:"id,omitempty"`
	PathID  string `json:"pathId,omitempty"`
	Message string `json:"message,omitempty"`
}
