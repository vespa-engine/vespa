package vespa

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
)

// DeleteService removes documents from a given schema
//
// See https://docs.vespa.ai/en/document-v1-api-guide.html
// for details.
type DeleteService struct {
	client HTTPClient

	namespace       string
	scheme          string
	id              string
	selection       string
	selectionClause whereParams
	timeout         string
	continuation    string
	cluster         string

	headers http.Header
}

// NewDeleteService will create and instance of DeleteService.
func NewDeleteService(client HTTPClient) *DeleteService {
	return &DeleteService{
		client:    client,
		namespace: DefaultNamespace,
	}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (s *DeleteService) Namespace(name string) *DeleteService {
	s.namespace = name
	return s
}

// Scheme sets the name of the scheme.
func (s *DeleteService) Scheme(scheme string) *DeleteService {
	s.scheme = scheme
	return s
}

// ID sets the id of the document
func (s *DeleteService) ID(id string) *DeleteService {
	s.id = id
	return s
}

// Continuation sets the continuation values to make a new request
func (s *DeleteService) Continuation(continuation string) *DeleteService {
	s.continuation = continuation
	return s
}

// Cluster sets the cluster value to make a new request
func (s *DeleteService) Cluster(cluster string) *DeleteService {
	s.cluster = cluster
	return s
}

// Selection sets the selection string for the update. Check
// https://docs.vespa.ai/en/reference/document-select-language.html for
// supported sintax.
func (s *DeleteService) Selection(where string, params ...interface{}) *DeleteService {
	s.selectionClause = whereParams{
		where:  where,
		params: params,
	}
	s.buildSelect()

	return s
}

// Timeout sets the Request timeout expresed in in seconds, or with optional
// ks, s, ms or µs unit.
func (s *DeleteService) Timeout(timeout string) *DeleteService {
	s.timeout = timeout
	return s
}

// Validate checks if the operation is valid.
func (s *DeleteService) Validate() error {
	var invalid []string
	if s.id == "" && s.selection == "" {
		invalid = append(invalid, "id/selection")
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

func (s *DeleteService) buildURL() (method, path string, parameters url.Values) {

	method = http.MethodDelete
	path = fmt.Sprintf("/document/v1/%s/%s/docid/%s", s.namespace, s.scheme, s.id)

	params := url.Values{}
	if s.timeout != "" {
		params.Set("timeout", s.timeout)
	}
	if s.selection != "" {
		params.Set("selection", s.selection)
	}

	if s.continuation != "" {
		params.Set("continuation", s.continuation)
	}

	if s.cluster != "" {
		params.Set("cluster", s.cluster)
	}

	return method, path, params

}

// Generate the YQL query
func (s *DeleteService) buildSelect() {
	var selection string
	var idx int

	if s.selectionClause.where != "" {
		selection = s.selectionClause.where

		for _, v := range []byte(selection) {
			if v == '?' && len(s.selectionClause.params) > idx {
				selection = VisitReplace(selection, s.selectionClause.params[idx])
				idx++
			}
		}
	}

	s.selection = selection
}

// Do executes the create operation. Returns a DeleteResponse or an error.
func (s *DeleteService) Do(ctx context.Context) (*DeleteResponse, error) {

	if err := s.Validate(); err != nil {
		return nil, err
	}

	var endResponse DeleteResponse
	var lastErr error

	for {
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

		var dr DeleteResponse
		endResponse.Status = res.StatusCode

		err = json.NewDecoder(res.Body).Decode(&dr)
		if err != nil {
			lastErr = err
			break
		}

		endResponse.DocumentCount += dr.DocumentCount // this could be 0.
		endResponse.ID = dr.ID
		endResponse.PathID = dr.PathID
		endResponse.Message = dr.Message

		if endResponse.Message != "" {
			lastErr = fmt.Errorf(endResponse.Message)
		}

		if dr.Continuation == "" {
			// when there is no continuation, the request is complete
			break
		}
		s.Continuation(dr.Continuation) // set continuation for the next loop

	}
	return &endResponse, lastErr

}

// DeleteResponse is the result of updating a document in Vespa.
type DeleteResponse struct {
	Status        int    `json:"status"`
	ID            string `json:"id,omitempty"`
	PathID        string `json:"pathId,omitempty"`
	Message       string `json:"message,omitempty"`
	Continuation  string `json:"continuation,omitempty"`
	DocumentCount int    `json:"documentCount,omitempty"`
}
