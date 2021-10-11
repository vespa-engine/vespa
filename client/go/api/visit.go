package vespa

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strconv"
	"strings"
)

// VisitService iterates over and gets all documents, or a selection of
// documents, in chunks, using continuation tokens to track progress.
//
// See https://docs.vespa.ai/en/document-v1-api-guide.html
// for details.
type VisitService struct {
	client HTTPClient

	namespace           string
	scheme              string
	timeout             string
	continuation        string
	selection           string
	selectionClause     whereParams
	wantedDocumentCount int
	cluster             string
	headers             http.Header
	concurrency         int
}

// NewVisitService will create and instance of VisitService
func NewVisitService(client HTTPClient) *VisitService {
	return &VisitService{
		client:    client,
		namespace: DefaultNamespace,
	}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (v *VisitService) Namespace(name string) *VisitService {
	v.namespace = name
	return v
}

// Scheme sets the name of the scheme.
func (v *VisitService) Scheme(scheme string) *VisitService {
	v.scheme = scheme
	return v
}

// Continuation sets the continuation value to make a new request
func (v *VisitService) Continuation(continuation string) *VisitService {
	v.continuation = continuation
	return v
}

// Selection sets the selection value to make a new request
func (v *VisitService) Selection(where string, params ...interface{}) *VisitService {
	v.selectionClause = whereParams{
		where:  where,
		params: params,
	}
	v.buildSelect()

	return v
}

// WantedDocumentCount WantedDocumentCount defines the desired number of items
// returned in the request. It can be understood as the limit in a normal offset
// limit query. Use this in cases with limited amount of data inside of the
// Cluster. Vespa currently limits this value to around 1024.
// Note that the maximum value of wantedDocumentCount is bounded by an
// implementation-specific limit to prevent excessive resource usage.
func (v *VisitService) WantedDocumentCount(number int) *VisitService {
	v.wantedDocumentCount = number
	return v
}

// Cluster sets the cluster value to make a new request
func (v *VisitService) Cluster(cluster string) *VisitService {
	v.cluster = cluster
	return v
}

// Concurrency sets the numbers of workers asigned to the visit operation. Limit is 100
func (v *VisitService) Concurrency(concurrency int) *VisitService {
	v.concurrency = concurrency
	return v
}

// Timeout sets the Request timeout expresed in in seconds, or with optional ks, s, ms or µs unit.
func (v *VisitService) Timeout(timeout string) *VisitService {
	v.timeout = timeout
	return v
}

// Validate checks if the operation is valid.
func (v *VisitService) Validate() error {
	var invalid []string

	if v.scheme == "" {
		invalid = append(invalid, "scheme")
	}

	if v.namespace == "" {
		invalid = append(invalid, "namespace")
	}

	if len(invalid) > 0 {
		return fmt.Errorf("missing required fields for operation: %v", invalid)
	}

	return nil
}

// Generate the YQL query
func (v *VisitService) buildSelect() {
	var selection string
	var idx int

	if v.selectionClause.where != "" {
		selection = v.selectionClause.where

		for _, val := range []byte(selection) {
			if val == '?' && len(v.selectionClause.params) > idx {
				selection = VisitReplace(selection, v.selectionClause.params[idx])
				idx++
			}
		}
	}

	v.selection = selection
}

func (v *VisitService) buildURL() (method, path string, parameters url.Values) {

	method = http.MethodGet
	path = fmt.Sprintf("/document/v1/%s/%s/docid", v.namespace, v.scheme)

	params := url.Values{}
	if v.timeout != "" {
		params.Set("timeout", v.timeout)
	}

	if v.selection != "" {
		params.Set("selection", v.selection)
	}

	if v.continuation != "" {
		params.Set("continuation", v.continuation)
	}

	if v.wantedDocumentCount > 0 {
		strWantedDocCount := strconv.Itoa(v.wantedDocumentCount)
		params.Set("wantedDocumentCount", strWantedDocCount)
	}

	if v.concurrency > 0 {
		strConcurrency := strconv.Itoa(v.concurrency)
		params.Set("concurrency", strConcurrency)
	}

	if v.cluster != "" {
		path = "/document/v1"

		params.Set("cluster", v.cluster)
	}

	return method, path, params
}

// Do executes the get operation. Returns a VisitResponse or an error.
func (v *VisitService) Do(ctx context.Context) (*VisitResponse, error) {

	if err := v.Validate(); err != nil {
		return nil, err
	}

	// Get URL for request
	method, path, params := v.buildURL()

	// Get HTTP response
	res, err := v.client.PerformRequest(ctx, PerformRequest{
		Method:  method,
		Path:    path,
		Params:  params,
		Headers: v.headers,
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

	var vr VisitResponse
	vr.Status = res.StatusCode

	err = json.NewDecoder(res.Body).Decode(&vr)

	return &vr, err
}

// VisitResponse is the result for the visit endpoints from Vespa
type VisitResponse struct {
	Status        int             `json:"status,omitempty"`
	ID            string          `json:"id,omitempty"`
	Fields        json.RawMessage `json:"fields,omitempty"`
	PathID        string          `json:"pathId,omitempty"`
	Message       string          `json:"message,omitempty"`
	Continuation  string          `json:"continuation,omitempty"`
	Documents     json.RawMessage `json:"documents,omitempty"`
	DocumentCount int             `json:"documentCount,omitempty"`
}

// VisitReplace replaces ? entries with correct params for the Visit API
func VisitReplace(where string, in interface{}) string {
	switch t := in.(type) {
	case int, int32, int64:
		where = strings.Replace(where, "?", fmt.Sprintf("%d", in), 1)
	case float32, float64:
		where = strings.Replace(where, "?", fmt.Sprintf("%.2f", in), 1)
	case string:
		where = strings.Replace(where, "?", fmt.Sprintf("%q", in), 1)
	case bool:
		where = strings.Replace(where, "?", fmt.Sprintf("%t", in), 1)
	case []int, []int32, []int64, []float32, []float64, []string, []bool:
		params := anythingToSlice(in)
		values := VisitReplaceArray(params)
		where = strings.Replace(where, "?", values, 1)
	default:
		fmt.Print("Different type", t)
		where = strings.Replace(where, "?", fmt.Sprintf("%v", in), 1)
	}

	return where
}

// VisitReplaceArray replaces ? entries with correct params when the param is an array
func VisitReplaceArray(params []interface{}) string {
	arr := make([]string, 0, len(params))
	for _, param := range params {
		arr = append(arr, VisitReplace("?", param))
	}

	return strings.Join(arr, ",")
}
