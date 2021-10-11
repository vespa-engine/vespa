package vespa

import (
	"context"
	"errors"
	"io/ioutil"
	"net/http"
	"net/url"
	"reflect"
	"strings"
	"testing"
)

func TestNewDeleteService(t *testing.T) {
	client := &Client{}

	want := &DeleteService{
		client:    client,
		namespace: DefaultNamespace,
	}
	got := NewDeleteService(client)

	if !reflect.DeepEqual(want, got) {
		t.Errorf(" expected NewDeleteService() to return %+v, got %+v", want, got)
	}
}

func TestDeleteService_Namespace(t *testing.T) {
	type args struct {
		name string
	}
	tests := []struct {
		name string
		args args
		want *DeleteService
	}{
		{
			name: " with default value",
			args: args{
				name: "default",
			},
			want: &DeleteService{
				namespace: "default",
			},
		},
		{
			name: " with specific value",
			args: args{
				name: "specific",
			},
			want: &DeleteService{
				namespace: "specific",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{}
			if got := s.Namespace(tt.args.name); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DeleteService.Namespace() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestDeleteService_Scheme(t *testing.T) {
	type args struct {
		scheme string
	}
	tests := []struct {
		name string
		args args
		want *DeleteService
	}{
		{
			name: " with cars value",
			args: args{
				scheme: "cars",
			},
			want: &DeleteService{
				scheme: "cars",
			},
		},
		{
			name: " with places value",
			args: args{
				scheme: "places",
			},
			want: &DeleteService{
				scheme: "places",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{}
			if got := s.Scheme(tt.args.scheme); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DeleteService.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestDeleteService_Cluster(t *testing.T) {
	type args struct {
		cluster string
	}
	tests := []struct {
		name string
		args args
		want *DeleteService
	}{
		{
			name: " with value",
			args: args{
				cluster: "cluster-name",
			},
			want: &DeleteService{
				cluster: "cluster-name",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{}
			if got := s.Cluster(tt.args.cluster); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DeleteService.Cluster() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestDeleteService_ID(t *testing.T) {

	type args struct {
		id string
	}
	tests := []struct {
		name string
		args args
		want *DeleteService
	}{
		{
			name: " with an id value value",
			args: args{
				id: "aaaa",
			},
			want: &DeleteService{
				id: "aaaa",
			},
		},
		{
			name: " with another id value",
			args: args{
				id: "bbbb",
			},
			want: &DeleteService{
				id: "bbbb",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{}
			if got := s.ID(tt.args.id); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DeleteService.ID() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestDeleteService_Selection(t *testing.T) {
	type args struct {
		query  string
		params []interface{}
	}
	tests := []struct {
		selection string
		args      args
		want      *DeleteService
	}{
		{
			selection: "with value",
			args: args{
				query: "music.author = ? and music.length <= ?",
				params: []interface{}{
					"Bon Jovi", 1000,
				},
			},
			want: &DeleteService{
				selection: `music.author = "Bon Jovi" and music.length <= 1000`,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.selection, func(t *testing.T) {
			s := &DeleteService{}
			got := s.Selection(tt.args.query, tt.args.params...)
			if got.selection != tt.want.selection {
				t.Errorf("DeleteService.Selection() = %v, want %v", got.selection, tt.want.selection)
			}
		})
	}
}

func TestDeleteService_Timeout(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
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
		want   *DeleteService
	}{
		{
			name: "with a second value",
			args: args{
				timeout: "12s",
			},
			want: &DeleteService{
				timeout: "12s",
			},
		},
		{
			name: "with a minute value",
			args: args{
				timeout: "1m",
			},
			want: &DeleteService{
				timeout: "1m",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}

			if got := s.Timeout(tt.args.timeout); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DeleteService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestDeleteService_Validate(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
		timeout   string
		selection string
		headers   http.Header
	}
	tests := []struct {
		name    string
		fields  fields
		wantErr bool
	}{
		{
			name:    "missing required fields should return error",
			fields:  fields{},
			wantErr: true,
		},
		{
			name: "missing one required field should return error",
			fields: fields{
				scheme:    "posts",
				namespace: "default",
			},
			wantErr: true,
		},
		{
			name: "all fields set should return error nil",
			fields: fields{
				id:        "1",
				scheme:    "posts",
				namespace: "default",
			},
			wantErr: false,
		},
		{
			name: "sequence but no id should be valid",
			fields: fields{
				selection: "count >= 10",
				scheme:    "posts",
				namespace: "default",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				timeout:   tt.fields.timeout,
				selection: tt.fields.selection,
				headers:   tt.fields.headers,
			}
			if err := s.Validate(); (err != nil) != tt.wantErr {
				t.Errorf("DeleteService.Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestDeleteService_buildURL(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
		timeout   string
		selection string
		headers   http.Header
	}
	tests := []struct {
		name   string
		fields fields
		want   string
		want1  string
		want2  url.Values
	}{
		{
			name: "when called, it should return the expected values",
			fields: fields{
				namespace: "default",
				scheme:    "posts",
				id:        "aaa",
			},
			want:  "DELETE",
			want1: "/document/v1/default/posts/docid/aaa",
			want2: url.Values{},
		},
		{
			name: "when called with timeout, it should return the expected values",
			fields: fields{
				namespace: "default",
				scheme:    "posts",
				id:        "aaa",
				timeout:   "12s",
			},
			want:  "DELETE",
			want1: "/document/v1/default/posts/docid/aaa",
			want2: url.Values{
				"timeout": []string{"12s"},
			},
		},
		{
			name: "when called with selection, it should return the expected values",
			fields: fields{
				namespace: "default",
				scheme:    "posts",
				id:        "aaa",
				selection: "music.author and music.length <= 1000",
			},
			want:  "DELETE",
			want1: "/document/v1/default/posts/docid/aaa",
			want2: url.Values{
				"selection": []string{"music.author and music.length <= 1000"},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				timeout:   tt.fields.timeout,
				selection: tt.fields.selection,
				headers:   tt.fields.headers,
			}
			got, got1, got2 := s.buildURL()

			if got != tt.want {
				t.Errorf("DeleteService.buildURL() got = %v, want %v", got, tt.want)
			}
			if got1 != tt.want1 {
				t.Errorf("DeleteService.buildURL() got1 = %v, want %v", got1, tt.want1)
			}
			if !reflect.DeepEqual(got2, tt.want2) {
				t.Errorf("DeleteService.buildURL() got2 = %v, want %v", got2, tt.want2)
			}
		})
	}
}

func TestDeleteService_Do(t *testing.T) {

	ctx := context.Background()

	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
		data      map[string]PartialUpdateItem
		timeout   string
		headers   http.Header
	}
	type args struct {
		ctx context.Context
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		want    *DeleteResponse
		wantErr bool
	}{
		{
			name:   "when validate return error, it should return error",
			fields: fields{},
			args: args{
				ctx: ctx,
			},
			want:    nil,
			wantErr: true,
		},
		{
			name: "when perform request return error, it should return error",
			fields: fields{
				client: &httpClientMock{
					err: errors.New("errors"),
				},
				id:     "1",
				scheme: "posts",
				data: map[string]PartialUpdateItem{
					"data": {Assign: "data"},
				},
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want:    nil,
			wantErr: true,
		},
		{
			name: "when perform request succeeds, it should return a UpdateResponse",
			fields: fields{
				client: &httpClientMock{
					code: 200,
					err:  nil,
					body: ioutil.NopCloser(strings.NewReader(`
					{
						"id":      "id:default:post:001",
						"pathId":  "/document/v1/default/job/docid/001"						
					}
					`)),
				},
				id:     "1",
				scheme: "posts",
				data: map[string]PartialUpdateItem{
					"data": {Assign: "data"},
				},
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want: &DeleteResponse{
				Status:  200,
				ID:      "id:default:post:001",
				PathID:  "/document/v1/default/job/docid/001",
				Message: "",
			},
			wantErr: false,
		},
		{
			name: "when perform request returns with an http error, we should return a message",
			fields: fields{
				client: &httpClientMock{
					code: 400,
					err:  nil,
					body: ioutil.NopCloser(strings.NewReader(`
					{
						"pathId":  "/document/v1/default/job/docid/001",
						"message":	"No field 'beat_that' in the structure of type 'work'"
					}
					`)),
				},
				id:     "1",
				scheme: "posts",
				data: map[string]PartialUpdateItem{
					"data": {Assign: "data"},
				},
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want: &DeleteResponse{
				Status:  400,
				PathID:  "/document/v1/default/job/docid/001",
				Message: "No field 'beat_that' in the structure of type 'work'",
			},
			wantErr: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}
			got, err := s.Do(tt.args.ctx)
			if (err != nil) != tt.wantErr {
				t.Errorf("DeleteService.Do() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DeleteService.Do() = %+v, want %+v", got, tt.want)
			}
		})
	}
}

func TestDeleteService_DoWithContinuation(t *testing.T) {

	client := &httpClientMock{
		code: 200,
		err:  nil,
		responses: []mockResponse{
			{
				status: 200,
				body: ioutil.NopCloser(strings.NewReader(`
				{
					"pathId":  "/document/v1/default/job/docid/",
					"continuation": "AaaaahhZh",
					"documentCount": 10
				}
				`)),
			},
			{
				status: 200,
				body: ioutil.NopCloser(strings.NewReader(`
				{
					"pathId":  "/document/v1/default/job/docid/",
					"continuation": "AaaaahhZh",
					"documentCount": 10
				}
				`)),
			},
			{
				status: 200,
				body: ioutil.NopCloser(strings.NewReader(`
				{
					"pathId":  "/document/v1/default/job/docid/",
					"documentCount": 2
				}
				`)),
			},
		},
	}

	s := &DeleteService{
		client:    client,
		namespace: "default",
		scheme:    "job",

		selection: "feed_id = 22",
		cluster:   "cluster-name",
	}

	ctx := context.Background()

	wantErr := false
	want := &DeleteResponse{
		Status:        200,
		PathID:        "/document/v1/default/job/docid/",
		DocumentCount: 22,
	}

	got, err := s.Do(ctx)
	if (err != nil) != wantErr {
		t.Errorf("DeleteService.Do() error = %v, wantErr %v", err, wantErr)
		return
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("DeleteService.Do() = %+v, want %+v", got, want)
	}
}

func TestDeleteService_Continuation(t *testing.T) {
	type args struct {
		continuation string
	}
	tests := []struct {
		name string
		args args
		want *DeleteService
	}{
		{
			name: "with value",
			args: args{
				continuation: "aaahhhzzzAa",
			},
			want: &DeleteService{
				continuation: "aaahhhzzzAa",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &DeleteService{}
			if got := s.Continuation(tt.args.continuation); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DeleteService.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}
