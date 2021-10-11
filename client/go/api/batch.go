package vespa

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"time"
)

// BatchService allows for batching batch requests to Vespa. It currently
// makes use of the sync api. In the future we expect this to work with the
// async api.
type BatchService struct {
	client HTTPClient

	namespace string
	scheme    string
	timeout   string
	headers   http.Header

	requests []BatchableRequest
}

// NewBatchService will create and instance of BatchService.
func NewBatchService(client HTTPClient) *BatchService {
	return &BatchService{
		client: client,
	}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (s *BatchService) Namespace(name string) *BatchService {
	s.namespace = name
	return s
}

// Scheme sets the name of the scheme.
func (s *BatchService) Scheme(scheme string) *BatchService {
	s.scheme = scheme
	return s
}

// Timeout sets the Request timeout expresed in in seconds, or with optional
// ks, s, ms or µs unit.
func (s *BatchService) Timeout(timeout string) *BatchService {
	s.timeout = timeout
	return s
}

// Add adds batchable requests, such as CreateBatchRequest
func (s *BatchService) Add(requests ...BatchableRequest) *BatchService {
	s.requests = append(s.requests, requests...)
	return s
}

// Len return the number of requests to send
func (s *BatchService) Len() int {
	return len(s.requests)
}

// Do will send a number of current goroutines to Vespa. It will reuse the connection
// pool of the HttpClient
func (s *BatchService) Do(ctx context.Context) *BatchResponse {
	now := time.Now()
	resCh := make(chan HTTPResponse)
	for _, item := range s.requests {
		go s.makeRequest(ctx, item, resCh)
	}

	var batchResponse BatchResponse

	for i := 0; i < s.Len(); i++ {
		res := <-resCh
		item, err := s.processResponse(res)
		if err {
			batchResponse.Errors = true
		}
		batchResponse.Items = append(batchResponse.Items, item)
	}

	batchResponse.Took = time.Since(now)

	return &batchResponse

}

func (s *BatchService) makeRequest(ctx context.Context, request BatchableRequest, out chan<- HTTPResponse) {
	if err := request.Validate(); err != nil {
		out <- HTTPResponse{
			res: nil,
			err: err,
		}
		return
	}

	// Get URL for request
	method, path, params := request.BuildURL()
	data := request.Source()

	// Get HTTP response
	response, err := s.client.PerformRequest(ctx, PerformRequest{
		Method:  method,
		Path:    path,
		Params:  params,
		Body:    data,
		Headers: s.headers,
	})
	if err != nil {
		out <- HTTPResponse{
			res: nil,
			err: err,
		}
		return
	}

	out <- HTTPResponse{
		res: response,
		err: err,
	}

}

func (s *BatchService) processResponse(res HTTPResponse) (BatchResponseItem, bool) {
	var item BatchResponseItem

	if res.err != nil {
		item.Message = res.err.Error()
		return item, true
	}

	defer func() {
		err := res.res.Body.Close()
		if err != nil {
			log.Print(err)
		}
	}()

	item.Status = res.res.StatusCode

	err := json.NewDecoder(res.res.Body).Decode(&item)
	if err != nil {
		item.Message = err.Error()
	}

	return item, false
}

// BatchResponse is the return of a batch Response.
type BatchResponse struct {
	Took   time.Duration
	Errors bool
	Items  []BatchResponseItem
}

// BatchResponseItem is one of the items under a batch response.
type BatchResponseItem struct {
	Status  int    `json:"status"`
	ID      string `json:"id,omitempty"`
	PathID  string `json:"pathId,omitempty"`
	Message string `json:"message,omitempty"`
}

// HTTPResponse groups a *httpResponse and an error to send through a channel
type HTTPResponse struct {
	res *http.Response
	err error
}
