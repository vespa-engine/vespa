package vespa

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
)

// UpdateService adds or updates a typed JSON document in a specified schema,
// making it searchable.
//
// See https://docs.vespa.ai/en/document-v1-api-guide.html
// for details.
type UpdateService struct {
	client HTTPClient

	namespace       string
	scheme          string
	id              string
	selection       string
	selectionClause whereParams
	data            map[string]PartialUpdateItem
	timeout         string
	continuation    string
	cluster         string

	headers http.Header
}

// NewUpdateService will create and instance of UpdateService.
func NewUpdateService(client HTTPClient) *UpdateService {
	return &UpdateService{
		client:    client,
		namespace: DefaultNamespace,
	}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (s *UpdateService) Namespace(name string) *UpdateService {
	s.namespace = name
	return s
}

// Scheme sets the name of the scheme.
func (s *UpdateService) Scheme(scheme string) *UpdateService {
	s.scheme = scheme
	return s
}

// ID sets the id of the document
func (s *UpdateService) ID(id string) *UpdateService {
	s.id = id
	return s
}

// Continuation sets the continuation value to make a new request
func (s *UpdateService) Continuation(continuation string) *UpdateService {
	s.continuation = continuation
	return s
}

// Cluster sets the cluster value to make a new request
func (s *UpdateService) Cluster(cluster string) *UpdateService {
	s.cluster = cluster
	return s
}

// Selection sets the selection string for the update. Check
// https://docs.vespa.ai/en/reference/document-select-language.html for
// supported sintax.
func (s *UpdateService) Selection(where string, params ...interface{}) *UpdateService {
	s.selectionClause = whereParams{
		where:  where,
		params: params,
	}
	s.buildSelect()

	return s
}

// Field sets a single field to be updated. Can be called multiple times.
func (s *UpdateService) Field(key string, value interface{}) *UpdateService {
	if s.data == nil {
		s.data = make(map[string]PartialUpdateItem)
	}

	s.data[key] = PartialUpdateItem{
		Assign: value,
	}

	return s
}

// Fields sets the fields to update
func (s *UpdateService) Fields(items ...PartialUpdateField) *UpdateService {
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

// Timeout sets the Request timeout expresed in in seconds, or with optional
// ks, s, ms or µs unit.
func (s *UpdateService) Timeout(timeout string) *UpdateService {
	s.timeout = timeout
	return s
}

// Validate checks if the operation is valid.
func (s *UpdateService) Validate() error {
	var invalid []string
	if s.id == "" && s.selection == "" {
		invalid = append(invalid, "id/selection")
	}
	if s.scheme == "" {
		invalid = append(invalid, "scheme")
	}
	if s.data == nil || len(s.data) == 0 {
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

func (s *UpdateService) buildURL() (method, path string, parameters url.Values) {

	method = http.MethodPut
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
func (s *UpdateService) buildSelect() {
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

// Source returns the body of the request we will make to create job
// endpoint
func (s *UpdateService) Source() interface{} {
	payload := make(map[string]interface{})

	payload["fields"] = s.data

	return payload
}

// Do executes the create operation. Returns a UpdateResponse or an error.
func (s *UpdateService) Do(ctx context.Context) (*UpdateResponse, error) {

	if err := s.Validate(); err != nil {
		return nil, err
	}

	var endResponse UpdateResponse
	var lastErr error

	for {
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

		var ur UpdateResponse
		endResponse.Status = res.StatusCode

		err = json.NewDecoder(res.Body).Decode(&ur)
		if err != nil {
			lastErr = err
			break
		}

		endResponse.DocumentCount += ur.DocumentCount // this could be 0.
		endResponse.ID = ur.ID
		endResponse.PathID = ur.PathID
		endResponse.Message = ur.Message

		if endResponse.Message != "" {
			lastErr = fmt.Errorf(endResponse.Message)
		}

		if ur.Continuation == "" {
			// when there is no continuation, the request is complete
			break
		}
		s.Continuation(ur.Continuation) // set continuation for the next loop

	}

	return &endResponse, lastErr

}

// UpdateResponse is the result of updating a document in Vespa.
type UpdateResponse struct {
	Status        int    `json:"status"`
	ID            string `json:"id,omitempty"`
	PathID        string `json:"pathId,omitempty"`
	Message       string `json:"message,omitempty"`
	Continuation  string `json:"continuation,omitempty"`
	DocumentCount int    `json:"documentCount,omitempty"`
}
