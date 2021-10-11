package vespa

import (
	"context"
	"errors"
	"io"
	"io/ioutil"
	"net/http"
	"net/url"
	"reflect"
	"strings"
	"testing"
)

func TestNewCreateService(t *testing.T) {
	client := &Client{}

	want := &CreateService{
		client:    client,
		namespace: DefaultNamespace,
	}
	got := NewCreateService(client)

	if !reflect.DeepEqual(want, got) {
		t.Errorf(" expected NewCreateService() to return %+v, got %+v", want, got)
	}
}

func TestCreateService_Namespace(t *testing.T) {
	type args struct {
		name string
	}
	tests := []struct {
		name string
		args args
		want *CreateService
	}{
		{
			name: " with default value",
			args: args{
				name: "default",
			},
			want: &CreateService{
				namespace: "default",
			},
		},
		{
			name: " with specific value",
			args: args{
				name: "specific",
			},
			want: &CreateService{
				namespace: "specific",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{}
			if got := s.Namespace(tt.args.name); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.Namespace() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateService_Scheme(t *testing.T) {
	type args struct {
		scheme string
	}
	tests := []struct {
		name string
		args args
		want *CreateService
	}{
		{
			name: " with cars value",
			args: args{
				scheme: "cars",
			},
			want: &CreateService{
				scheme: "cars",
			},
		},
		{
			name: " with places value",
			args: args{
				scheme: "places",
			},
			want: &CreateService{
				scheme: "places",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{}
			if got := s.Scheme(tt.args.scheme); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateService_ID(t *testing.T) {

	type args struct {
		id string
	}
	tests := []struct {
		name string
		args args
		want *CreateService
	}{
		{
			name: " with an id value value",
			args: args{
				id: "aaaa",
			},
			want: &CreateService{
				id: "aaaa",
			},
		},
		{
			name: " with another id value",
			args: args{
				id: "bbbb",
			},
			want: &CreateService{
				id: "bbbb",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{}
			if got := s.ID(tt.args.id); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.ID() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateService_Body(t *testing.T) {

	type args struct {
		body interface{}
	}

	object := struct {
		field string
	}{
		field: "value",
	}

	tests := []struct {
		name string
		args args
		want *CreateService
	}{
		{
			name: " with an string value",
			args: args{
				body: "string",
			},
			want: &CreateService{
				data: "string",
			},
		},
		{
			name: " with int value",
			args: args{
				body: 23,
			},
			want: &CreateService{
				data: 23,
			},
		},
		{
			name: " with struct value",
			args: args{
				body: object,
			},
			want: &CreateService{
				data: object,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{}
			if got := s.Body(tt.args.body); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.Body() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateService_Timeout(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
		data      interface{}
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
		want   *CreateService
	}{
		{
			name: "with a second value",
			args: args{
				timeout: "12s",
			},
			want: &CreateService{
				timeout: "12s",
			},
		},
		{
			name: "with a minute value",
			args: args{
				timeout: "1m",
			},
			want: &CreateService{
				timeout: "1m",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				data:      tt.fields.data,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}

			if got := s.Timeout(tt.args.timeout); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateService_Validate(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
		data      interface{}
		timeout   string
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
				data:      "data",
				namespace: "default",
			},
			wantErr: true,
		},
		{
			name: "all fields set should return error nil",
			fields: fields{
				id:        "1",
				scheme:    "posts",
				data:      "data",
				namespace: "default",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				data:      tt.fields.data,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}
			if err := s.Validate(); (err != nil) != tt.wantErr {
				t.Errorf("CreateService.Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestCreateService_buildURL(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
		data      interface{}
		timeout   string
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
			want:  "POST",
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
			want:  "POST",
			want1: "/document/v1/default/posts/docid/aaa",
			want2: url.Values{
				"timeout": []string{"12s"},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				data:      tt.fields.data,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}
			got, got1, got2 := s.buildURL()

			if got != tt.want {
				t.Errorf("CreateService.buildURL() got = %v, want %v", got, tt.want)
			}
			if got1 != tt.want1 {
				t.Errorf("CreateService.buildURL() got1 = %v, want %v", got1, tt.want1)
			}
			if !reflect.DeepEqual(got2, tt.want2) {
				t.Errorf("CreateService.buildURL() got2 = %v, want %v", got2, tt.want2)
			}
		})
	}
}

func TestCreateService_Source(t *testing.T) {
	data := struct {
		ID string `json:"id,omitempty"`
	}{
		ID: "aaa",
	}

	data2 := map[string]string{
		"key": "value",
	}

	type fields struct {
		data interface{}
	}
	tests := []struct {
		name   string
		fields fields
		want   interface{}
	}{
		{
			name: "when called, the fields should be under a fields key",
			fields: fields{
				data: data,
			},
			want: map[string]interface{}{
				"fields": data,
			},
		},
		{
			name: "when called and data is a map, map should be under a fields key",
			fields: fields{
				data: data2,
			},
			want: map[string]interface{}{
				"fields": data2,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{
				data: tt.fields.data,
			}
			got := s.Source()

			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.Source() = %v, want %v", got, tt.want)
			}
		})
	}
}

type httpClientMock struct {
	code      int
	err       error
	body      io.ReadCloser
	responses []mockResponse // used to simulate multiple requests
	current   int
}

type mockResponse struct {
	status int
	body   io.ReadCloser
}

func (m *httpClientMock) PerformRequest(ctx context.Context, opt PerformRequest) (*http.Response, error) {
	res := &http.Response{}

	if m.code == 0 {
		return nil, m.err
	}

	res.StatusCode = m.code
	res.Body = m.body

	if len(m.responses) > 0 {
		res.Body = m.responses[m.current].body
		res.StatusCode = m.responses[m.current].status

		m.current++
	}

	return res, nil
}

func TestCreateService_Do(t *testing.T) {

	ctx := context.Background()

	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
		data      interface{}
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
		want    *CreateResponse
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
				id:        "1",
				scheme:    "posts",
				data:      "data",
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want:    nil,
			wantErr: true,
		},
		{
			name: "when perform request succeeds, it should return a CreateResponse",
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
				id:        "1",
				scheme:    "posts",
				data:      "data",
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want: &CreateResponse{
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
				id:        "1",
				scheme:    "posts",
				data:      "data",
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want: &CreateResponse{
				Status:  400,
				PathID:  "/document/v1/default/job/docid/001",
				Message: "No field 'beat_that' in the structure of type 'work'",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				data:      tt.fields.data,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}
			got, err := s.Do(tt.args.ctx)
			if (err != nil) != tt.wantErr {
				t.Errorf("CreateService.Do() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateService.Do() = %v, want %v", got, tt.want)
			}
		})
	}
}
