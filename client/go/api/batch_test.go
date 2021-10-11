package vespa

import (
	"context"
	"errors"
	"io"
	"io/ioutil"
	"net/http"
	"reflect"
	"sort"
	"strings"
	"testing"
)

func TestNewBatchService(t *testing.T) {
	client := &Client{}

	want := &BatchService{
		client: client,
	}
	got := NewBatchService(client)

	if !reflect.DeepEqual(want, got) {
		t.Errorf(" expected NewCreateService() to return %+v, got %+v", want, got)
	}
}

func TestBatchService_Namespace(t *testing.T) {
	type args struct {
		name string
	}
	tests := []struct {
		name string
		args args
		want *BatchService
	}{
		{
			name: " with default value",
			args: args{
				name: "default",
			},
			want: &BatchService{
				namespace: "default",
			},
		},
		{
			name: " with specific value",
			args: args{
				name: "specific",
			},
			want: &BatchService{
				namespace: "specific",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &BatchService{}
			if got := s.Namespace(tt.args.name); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.Namespace() = %v, want %v", got, tt.want)
			}
		})
	}
}
func TestBatchService_Scheme(t *testing.T) {
	type args struct {
		scheme string
	}
	tests := []struct {
		name string
		args args
		want *BatchService
	}{
		{
			name: " with cars value",
			args: args{
				scheme: "cars",
			},
			want: &BatchService{
				scheme: "cars",
			},
		},
		{
			name: " with places value",
			args: args{
				scheme: "places",
			},
			want: &BatchService{
				scheme: "places",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &BatchService{}
			if got := s.Scheme(tt.args.scheme); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("BatchService.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestBatchService_Timeout(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		timeout   string
		headers   http.Header
	}
	type args struct {
		timeout string
	}
	tests := []struct {
		name   string
		fields fields
		args   args
		want   *BatchService
	}{
		{
			name: "with a second value",
			args: args{
				timeout: "12s",
			},
			want: &BatchService{
				timeout: "12s",
			},
		},
		{
			name: "with a minute value",
			args: args{
				timeout: "1m",
			},
			want: &BatchService{
				timeout: "1m",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &BatchService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}

			if got := s.Timeout(tt.args.timeout); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestBatchService_Add(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		timeout   string
		headers   http.Header
		requests  []BatchableRequest
	}
	type args struct {
		requests []BatchableRequest
	}
	tests := []struct {
		name   string
		fields fields
		args   args
		want   *BatchService
	}{
		{
			name: "with valid data it should be set on requests field",
			args: args{
				requests: []BatchableRequest{
					&CreateBatchRequest{
						namespace: "default",
						scheme:    "job",
						id:        "21",
						data:      "data",
					},
					&CreateBatchRequest{
						namespace: "default",
						scheme:    "job",
						id:        "22",
						data:      "data2",
					},
				},
			},
			want: &BatchService{
				requests: []BatchableRequest{
					&CreateBatchRequest{
						namespace: "default",
						scheme:    "job",
						id:        "21",
						data:      "data",
					},
					&CreateBatchRequest{
						namespace: "default",
						scheme:    "job",
						id:        "22",
						data:      "data2",
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &BatchService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
				requests:  tt.fields.requests,
			}
			if got := s.Add(tt.args.requests...); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("BatchService.Add() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestBatchService_Len(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		timeout   string
		headers   http.Header
		requests  []BatchableRequest
	}
	tests := []struct {
		name   string
		fields fields
		want   int
	}{
		{
			name: "with 2 requests expect len of 2",
			fields: fields{
				requests: []BatchableRequest{
					&CreateBatchRequest{
						id:   "21",
						data: "data",
					},
					&CreateBatchRequest{
						id:   "22",
						data: "data2",
					},
				},
			},
			want: 2,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &BatchService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
				requests:  tt.fields.requests,
			}
			if got := s.Len(); got != tt.want {
				t.Errorf("BatchService.Len() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestBatchService_Do(t *testing.T) {
	ctx := context.Background()

	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		timeout   string
		headers   http.Header
		requests  []BatchableRequest
	}
	type args struct {
		ctx context.Context
	}
	tests := []struct {
		name   string
		fields fields
		args   args
		want   *BatchResponse
	}{
		{
			name: "when the process completes the operation, we should get the expected response",
			args: args{
				ctx: ctx,
			},
			fields: fields{
				requests: []BatchableRequest{
					&CreateBatchRequest{
						namespace: "default",
						scheme:    "post",
						id:        "21",
						data:      "data",
					},
					&CreateBatchRequest{
						namespace: "default",
						scheme:    "post",
						id:        "22",
						data:      "data2",
					},
				},
				client: &httpClientBatchMock{
					responses: map[string]BatchMockResponse{
						"/document/v1/default/post/docid/21": {
							code: 200,
							data: ioutil.NopCloser(strings.NewReader(`
								{
									"id":      "21",
									"pathId":  "/document/v1/default/post/docid/21"
								}
							`)),
							err: nil,
						},
						"/document/v1/default/post/docid/22": {
							code: 200,
							data: ioutil.NopCloser(strings.NewReader(`
								{
									"id":      "22",
									"pathId":  "/document/v1/default/post/docid/22"						
								}
							`)),
							err: nil,
						},
					},
				},
			},
			want: &BatchResponse{
				Errors: false,
				Items: []BatchResponseItem{
					{
						Status:  200,
						ID:      "21",
						PathID:  "/document/v1/default/post/docid/21",
						Message: "",
					},
					{
						Status:  200,
						ID:      "22",
						PathID:  "/document/v1/default/post/docid/22",
						Message: "",
					},
				},
			},
		},
		{
			name: "when the request is invalid we should get an error",
			args: args{
				ctx: ctx,
			},
			fields: fields{
				requests: []BatchableRequest{
					&CreateBatchRequest{
						namespace: "default",
						scheme:    "post",
						data:      "data",
					},
				},
				client: &httpClientBatchMock{
					responses: map[string]BatchMockResponse{},
				},
			},
			want: &BatchResponse{
				Errors: true,
				Items: []BatchResponseItem{
					{
						Status:  0,
						Message: "missing required fields for operation: [id]",
					},
				},
			},
		},
		{
			name: "when performing a requests returns an error we should get an error",
			args: args{
				ctx: ctx,
			},
			fields: fields{
				requests: []BatchableRequest{
					&CreateBatchRequest{
						namespace: "default",
						scheme:    "post",
						id:        "21",
						data:      "data",
					},
				},
				client: &httpClientBatchMock{
					err:       errors.New("http client error"),
					responses: map[string]BatchMockResponse{},
				},
			},
			want: &BatchResponse{
				Errors: true,
				Items: []BatchResponseItem{
					{
						Status:  0,
						Message: "http client error",
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &BatchService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
				requests:  tt.fields.requests,
			}
			got := s.Do(tt.args.ctx)
			// We sort the output by id so we don't care about the order of
			// the routines
			sort.Slice(got.Items, func(i, j int) bool {
				return got.Items[i].ID < got.Items[j].ID
			})

			if !reflect.DeepEqual(got.Items, tt.want.Items) {
				t.Errorf("BatchService.Do() = %v, want %v", got.Items, tt.want.Items)
			}

		})
	}
}

type httpClientBatchMock struct {
	err error

	responses map[string]BatchMockResponse
}

type BatchMockResponse struct {
	code int
	err  error
	data io.ReadCloser
}

func (m *httpClientBatchMock) PerformRequest(ctx context.Context, opt PerformRequest) (*http.Response, error) {
	res := &http.Response{}

	if m.err != nil {
		return nil, m.err
	}

	item, ok := m.responses[opt.Path]
	if !ok {
		return nil, errors.New("no response set on test")
	}

	if item.code == 0 {
		return nil, item.err
	}

	res.StatusCode = item.code

	res.Body = item.data

	return res, nil
}
