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

func TestNewVisitService(t *testing.T) {
	client := &Client{}

	want := &VisitService{
		client:    client,
		namespace: DefaultNamespace,
	}

	got := NewVisitService(client)

	if !reflect.DeepEqual(want, got) {
		t.Errorf(" expected NewVisitService() to return %+v, got %+v", want, got)
	}
}

func TestVisitService_Namespace(t *testing.T) {
	type args struct {
		name string
	}
	tests := []struct {
		name string
		args args
		want *VisitService
	}{
		{
			name: "Set the default value to namespace",
			args: args{
				name: "default",
			},
			want: &VisitService{
				namespace: "default",
			},
		},
		{
			name: "Set a specific value to namespace",
			args: args{
				name: "specific",
			},
			want: &VisitService{
				namespace: "specific",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{}

			if got := v.Namespace(tt.args.name); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("VisitService.Namespace() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestVisitService_Scheme(t *testing.T) {
	type args struct {
		scheme string
	}
	tests := []struct {
		name string
		args args
		want *VisitService
	}{
		{
			name: "Set the job to scheme",
			args: args{
				scheme: "job",
			},
			want: &VisitService{
				scheme: "job",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{}

			if got := v.Scheme(tt.args.scheme); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("VisitService.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestVisitService_Continuation(t *testing.T) {
	type args struct {
		continuation string
	}
	tests := []struct {
		name string
		args args
		want *VisitService
	}{
		{
			name: "Set a continuation value",
			args: args{
				continuation: "BGAAABEBEBC",
			},
			want: &VisitService{
				continuation: "BGAAABEBEBC",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{}

			if got := v.Continuation(tt.args.continuation); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("VisitService.Continuation() = %v, want %v", got, tt.want)
			}
		})
	}
}

type customType int

func TestVisitService_Selection(t *testing.T) {
	type args struct {
		query  string
		params []interface{}
	}
	tests := []struct {
		name string
		args args
		want *VisitService
	}{
		{
			name: "Set the selection values",
			args: args{
				query: "music.author = ? and music.length <= ?",
				params: []interface{}{
					"Bon Jovi", 1000,
				},
			},
			want: &VisitService{
				selection: `music.author = "Bon Jovi" and music.length <= 1000`,
			},
		},
		{
			name: "With int value",
			args: args{
				query: "music.count = ?",
				params: []interface{}{
					23,
				},
			},
			want: &VisitService{
				selection: `music.count = 23`,
			},
		},
		{
			name: "With int64 value",
			args: args{
				query: "music.count = ?",
				params: []interface{}{
					int64(23),
				},
			},
			want: &VisitService{
				selection: `music.count = 23`,
			},
		},
		{
			name: "With bool",
			args: args{
				query: "music.active = ?",
				params: []interface{}{
					true,
				},
			},
			want: &VisitService{
				selection: `music.active = true`,
			},
		},
		{
			name: "With slice",
			args: args{
				query: "music.id IN(?)",
				params: []interface{}{
					[]int{1, 2, 3},
				},
			},
			want: &VisitService{
				selection: `music.id IN(1,2,3)`,
			},
		},
		{
			name: "With float",
			args: args{
				query: "music.rating = ?",
				params: []interface{}{
					0.51,
				},
			},
			want: &VisitService{
				selection: `music.rating = 0.51`,
			},
		},
		{
			name: "With different type",
			args: args{
				query: "music.rating = ?",
				params: []interface{}{
					0.51,
				},
			},
			want: &VisitService{
				selection: `music.rating = 0.51`,
			},
		},
		{
			name: "With custom type",
			args: args{
				query: "music.rating = ?",
				params: []interface{}{
					customType(1),
				},
			},
			want: &VisitService{
				selection: `music.rating = 1`,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{}

			got := v.Selection(tt.args.query, tt.args.params...)
			if got.selection != tt.want.selection {
				t.Errorf("VisitService.Selection() = %v, want %v", got.selection, tt.want.selection)
			}
		})
	}
}

func TestVisitService_WantedDocumentCount(t *testing.T) {
	type args struct {
		number int
	}
	tests := []struct {
		name string
		args args
		want *VisitService
	}{
		{
			name: "Set a wantedDocumentCount value",
			args: args{
				number: 1024,
			},
			want: &VisitService{
				wantedDocumentCount: 1024,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{}

			if got := v.WantedDocumentCount(tt.args.number); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("VisitService.WantedDocumentCount() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestVisitService_Concurrency(t *testing.T) {
	type args struct {
		number int
	}
	tests := []struct {
		name string
		args args
		want *VisitService
	}{
		{
			name: "Set a concurrency value",
			args: args{
				number: 100,
			},
			want: &VisitService{
				concurrency: 100,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{}

			if got := v.Concurrency(tt.args.number); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("VisitService.Concurrency() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestVisitService_Cluster(t *testing.T) {
	type args struct {
		cluster string
	}
	tests := []struct {
		name string
		args args
		want *VisitService
	}{
		{
			name: "Set a cluster value",
			args: args{
				cluster: "cluster-name",
			},
			want: &VisitService{
				cluster: "cluster-name",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{}

			if got := v.Cluster(tt.args.cluster); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("VisitService.Cluster() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestVisitService_Timeout(t *testing.T) {
	type args struct {
		timeout string
	}
	tests := []struct {
		name string
		args args
		want *VisitService
	}{
		{
			name: "Set a second value",
			args: args{
				timeout: "12s",
			},
			want: &VisitService{
				timeout: "12s",
			},
		},
		{
			name: "Set a minute value",
			args: args{
				timeout: "1m",
			},
			want: &VisitService{
				timeout: "1m",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{}

			if got := v.Timeout(tt.args.timeout); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("VisitService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestVisitService_Validate(t *testing.T) {
	type fields struct {
		client              HTTPClient
		namespace           string
		scheme              string
		timeout             string
		continuation        string
		selection           string
		selectionClause     whereParams
		wantedDocumentCount int
		cluster             string
		headers             http.Header
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
			name: "Missing one required field should return an error",
			fields: fields{
				namespace: "default",
			},
			wantErr: true,
		},
		{
			name: "All fields set should return error nil",
			fields: fields{
				namespace: "default",
				scheme:    "job",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{
				client:              tt.fields.client,
				namespace:           tt.fields.namespace,
				scheme:              tt.fields.scheme,
				timeout:             tt.fields.timeout,
				continuation:        tt.fields.continuation,
				selection:           tt.fields.selection,
				selectionClause:     tt.fields.selectionClause,
				wantedDocumentCount: tt.fields.wantedDocumentCount,
				cluster:             tt.fields.cluster,
				headers:             tt.fields.headers,
			}
			if err := v.Validate(); (err != nil) != tt.wantErr {
				t.Errorf("VisitService.Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestVisitService_buildURL(t *testing.T) {
	type fields struct {
		client              HTTPClient
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
	tests := []struct {
		name           string
		fields         fields
		wantMethod     string
		wantPath       string
		wantParameters url.Values
	}{
		{
			name: "when called, it should return the expected values",
			fields: fields{
				namespace: "default",
				scheme:    "posts",
			},
			wantMethod:     "GET",
			wantPath:       "/document/v1/default/posts/docid",
			wantParameters: url.Values{},
		},
		{
			name: "when called with timeout, it should return the expected values",
			fields: fields{
				namespace:    "default",
				scheme:       "posts",
				continuation: "BGAAABEBEBC",
				timeout:      "12s",
			},
			wantMethod: "GET",
			wantPath:   "/document/v1/default/posts/docid",
			wantParameters: url.Values{
				"continuation": []string{"BGAAABEBEBC"},
				"timeout":      []string{"12s"},
			},
		},
		{
			name: "when called with selection, it should return the expected values",
			fields: fields{
				namespace: "default",
				scheme:    "posts",
				selection: "music.author and music.length <= 1000",
			},
			wantMethod: "GET",
			wantPath:   "/document/v1/default/posts/docid",
			wantParameters: url.Values{
				"selection": []string{"music.author and music.length <= 1000"},
			},
		},
		{
			name: "when called with selection and include a wantedDocumentCount value, it should return the expected values",
			fields: fields{
				namespace:           "default",
				scheme:              "posts",
				selection:           "music.author and music.length <= 1000",
				wantedDocumentCount: 1024,
			},
			wantMethod: "GET",
			wantPath:   "/document/v1/default/posts/docid",
			wantParameters: url.Values{
				"selection":           []string{"music.author and music.length <= 1000"},
				"wantedDocumentCount": []string{"1024"},
			},
		},
		{
			name: "when called with selection and include a concurrency value, it should return the expected values",
			fields: fields{
				namespace:   "default",
				scheme:      "posts",
				selection:   "music.author and music.length <= 1000",
				concurrency: 100,
			},
			wantMethod: "GET",
			wantPath:   "/document/v1/default/posts/docid",
			wantParameters: url.Values{
				"selection":   []string{"music.author and music.length <= 1000"},
				"concurrency": []string{"100"},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{
				client:              tt.fields.client,
				namespace:           tt.fields.namespace,
				scheme:              tt.fields.scheme,
				timeout:             tt.fields.timeout,
				continuation:        tt.fields.continuation,
				selection:           tt.fields.selection,
				selectionClause:     tt.fields.selectionClause,
				wantedDocumentCount: tt.fields.wantedDocumentCount,
				cluster:             tt.fields.cluster,
				headers:             tt.fields.headers,
				concurrency:         tt.fields.concurrency,
			}
			gotMethod, gotPath, gotParameters := v.buildURL()
			if gotMethod != tt.wantMethod {
				t.Errorf("VisitService.buildURL() gotMethod = %v, want %v", gotMethod, tt.wantMethod)
			}
			if gotPath != tt.wantPath {
				t.Errorf("VisitService.buildURL() gotPath = %v, want %v", gotPath, tt.wantPath)
			}
			if !reflect.DeepEqual(gotParameters, tt.wantParameters) {
				t.Errorf("VisitService.buildURL() gotParameters = %v, want %v", gotParameters, tt.wantParameters)
			}
		})
	}
}

func TestVisitService_Do(t *testing.T) {
	ctx := context.Background()

	type fields struct {
		client              HTTPClient
		namespace           string
		scheme              string
		timeout             string
		continuation        string
		selection           string
		selectionClause     whereParams
		wantedDocumentCount int
		cluster             string
		headers             http.Header
	}
	type args struct {
		ctx context.Context
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		want    *VisitResponse
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
				continuation: "BGAAABEBEBC",
				scheme:       "posts",
				namespace:    "default",
			},
			args: args{
				ctx: ctx,
			},
			want:    nil,
			wantErr: true,
		},
		{
			name: "when perform request succeeds, it should return a VisitResponse",
			fields: fields{
				client: &httpClientMock{
					code: 200,
					err:  nil,
					body: ioutil.NopCloser(strings.NewReader(`
					{
						"id":      "id:default:post:001",
						"pathId":  "/document/v1/default/jobs/docid"						
					}
					`)),
				},
				scheme:    "jobs",
				namespace: "default",
				cluster:   "cluster",
			},
			args: args{
				ctx: ctx,
			},
			want: &VisitResponse{
				Status:  200,
				ID:      "id:default:post:001",
				PathID:  "/document/v1/default/jobs/docid",
				Message: "",
			},
			wantErr: false,
		},
		{
			name: "when perform request succeeds and include a wantedDocumentCount, it should return a VisitResponse",
			fields: fields{
				client: &httpClientMock{
					code: 200,
					err:  nil,
					body: ioutil.NopCloser(strings.NewReader(`
					{
						"id":      "id:default:post:001",
						"pathId":  "/document/v1/default/job/docid"						
					}
					`)),
				},
				scheme:              "job",
				namespace:           "default",
				cluster:             "cluster",
				wantedDocumentCount: 1024,
			},
			args: args{
				ctx: ctx,
			},
			want: &VisitResponse{
				Status:  200,
				ID:      "id:default:post:001",
				PathID:  "/document/v1/default/job/docid",
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
						"pathId":  "/document/v1/default/jobs/docid/001",
						"message":	"No field 'beat_that' in the structure of type 'work'"
					}
					`)),
				},
				scheme:    "jobs",
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want: &VisitResponse{
				Status:  400,
				PathID:  "/document/v1/default/jobs/docid/001",
				Message: "No field 'beat_that' in the structure of type 'work'",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			v := &VisitService{
				client:              tt.fields.client,
				namespace:           tt.fields.namespace,
				scheme:              tt.fields.scheme,
				timeout:             tt.fields.timeout,
				continuation:        tt.fields.continuation,
				selection:           tt.fields.selection,
				selectionClause:     tt.fields.selectionClause,
				wantedDocumentCount: tt.fields.wantedDocumentCount,
				cluster:             tt.fields.cluster,
				headers:             tt.fields.headers,
			}
			got, err := v.Do(tt.args.ctx)
			if (err != nil) != tt.wantErr {
				t.Errorf("VisitService.Do() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("VisitService.Do() = %v, want %v", got, tt.want)
			}
		})
	}
}
