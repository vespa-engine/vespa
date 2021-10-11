package vespa

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
)

// GetService retrieves the document by ID
type GetService struct {
	client HTTPClient

	namespace string
	scheme    string
	id        string
	timeout   string
	headers   http.Header
}

// NewGetService will create an instance of GetService
func NewGetService(client HTTPClient) *GetService {
	return &GetService{
		client:    client,
		namespace: DefaultNamespace,
	}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (s *GetService) Namespace(name string) *GetService {
	s.namespace = name
	return s
}

// Scheme sets the name of the scheme.
func (s *GetService) Scheme(scheme string) *GetService {
	s.scheme = scheme
	return s
}

// ID sets the id of the document
func (s *GetService) ID(id string) *GetService {
	s.id = id
	return s
}

// Timeout sets the Request timeout expresed in in seconds, or with optional ks, s, ms or µs unit.
func (s *GetService) Timeout(timeout string) *GetService {
	s.timeout = timeout
	return s
}

// Validate checks if the operation is valid.
func (s *GetService) Validate() error {
	var invalid []string
	if s.id == "" {
		invalid = append(invalid, "id")
	}
	if s.scheme == "" {
		invalid = append(invalid, "scheme")
	}
	if s.namespace == "" {
		invalid = append(invalid, "namespace")
	}

	if len(invalid) > 0 {
		return fmt.Errorf("missing required fields for operation: %v", invalid)
	}

	return nil
}

func (s *GetService) buildURL() (method, path string, parameters url.Values) {

	method = http.MethodGet
	path = fmt.Sprintf("/document/v1/%s/%s/docid/%s", s.namespace, s.scheme, s.id)

	params := url.Values{}
	if s.timeout != "" {
		params.Set("timeout", s.timeout)
	}

	return method, path, params

}

// Do executes the get operation. Returns a GetResponse or an error.
func (s *GetService) Do(ctx context.Context) (*GetResponse, error) {

	if err := s.Validate(); err != nil {
		return nil, err
	}

	// Get URL for request
	method, path, params := s.buildURL()

	// Get HTTP response
	res, err := s.client.PerformRequest(ctx, PerformRequest{
		Method:  method,
		Path:    path,
		Params:  params,
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

	var gr GetResponse
	gr.Status = res.StatusCode

	err = json.NewDecoder(res.Body).Decode(&gr)

	return &gr, err

}

// GetResponse is the result of retrieve a specific document in Vespa.
type GetResponse struct {
	Status  int             `json:"status"`
	ID      string          `json:"id,omitempty"`
	PathID  string          `json:"pathId,omitempty"`
	Message string          `json:"message,omitempty"`
	Fields  json.RawMessage `json:"fields,omitempty"`
}
