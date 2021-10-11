package vespa

import (
	"context"
	"encoding/json"
	"errors"
	"io/ioutil"
	"net/http"
	"net/url"
	"reflect"
	"strings"
	"testing"
)

func TestNewGetService(t *testing.T) {
	client := &Client{}

	want := &GetService{
		client:    client,
		namespace: DefaultNamespace,
	}

	got := NewGetService(client)

	if !reflect.DeepEqual(want, got) {
		t.Errorf(" expected NewGetService() to return %+v, got %+v", want, got)
	}
}

func TestGetService_Namespace(t *testing.T) {
	type args struct {
		name string
	}
	tests := []struct {
		name string
		args args
		want *GetService
	}{
		{
			name: "Set the default value to namespace",
			args: args{
				name: "default",
			},
			want: &GetService{
				namespace: "default",
			},
		},
		{
			name: "Set a specific value to namespace",
			args: args{
				name: "specific",
			},
			want: &GetService{
				namespace: "specific",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &GetService{}

			if got := s.Namespace(tt.args.name); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("GetService.Namespace() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestGetService_Scheme(t *testing.T) {
	type args struct {
		scheme string
	}
	tests := []struct {
		name string
		args args
		want *GetService
	}{
		{
			name: "Set the job to scheme",
			args: args{
				scheme: "job",
			},
			want: &GetService{
				scheme: "job",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &GetService{}

			if got := s.Scheme(tt.args.scheme); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("GetService.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestGetService_ID(t *testing.T) {
	type args struct {
		id string
	}
	tests := []struct {
		name string
		args args
		want *GetService
	}{
		{
			name: "Set the job to scheme",
			args: args{
				id: "54af81d8-6b35-11eb-9439-0242ac130002",
			},
			want: &GetService{
				id: "54af81d8-6b35-11eb-9439-0242ac130002",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &GetService{}

			if got := s.ID(tt.args.id); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("GetService.ID() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestGetService_Timeout(t *testing.T) {
	type args struct {
		timeout string
	}
	tests := []struct {
		name string
		args args
		want *GetService
	}{
		{
			name: "Set a second value to timeout",
			args: args{
				timeout: "12s",
			},
			want: &GetService{
				timeout: "12s",
			},
		},
		{
			name: "Set a minute value to timeout",
			args: args{
				timeout: "1m",
			},
			want: &GetService{
				timeout: "1m",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &GetService{}

			if got := s.Timeout(tt.args.timeout); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("GetService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestGetService_Validate(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
		timeout   string
		headers   http.Header
	}
	tests := []struct {
		name    string
		fields  fields
		wantErr bool
	}{
		{
			name:    "Missing required fields should return error",
			fields:  fields{},
			wantErr: true,
		},
		{
			name: "Missing one required field should return an error",
			fields: fields{
				namespace: "default",
				id:        "54af81d8-6b35-11eb-9439-0242ac130002",
			},
			wantErr: true,
		},
		{
			name: "All fields set should return error nil",
			fields: fields{
				namespace: "default",
				scheme:    "job",
				id:        "54af81d8-6b35-11eb-9439-0242ac130002",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &GetService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}
			if err := s.Validate(); (err != nil) != tt.wantErr {
				t.Errorf("GetService.Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestGetService_buildURL(t *testing.T) {
	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
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
			name: "When called, it should return the expected values",
			fields: fields{
				namespace: "default",
				scheme:    "job",
				id:        "54af81d8-6b35-11eb-9439-0242ac130002",
			},
			want:  "GET",
			want1: "/document/v1/default/job/docid/54af81d8-6b35-11eb-9439-0242ac130002",
			want2: url.Values{},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &GetService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}
			got, got1, got2 := s.buildURL()
			if got != tt.want {
				t.Errorf("GetService.buildURL() got = %v, want %v", got, tt.want)
			}
			if got1 != tt.want1 {
				t.Errorf("GetService.buildURL() got1 = %v, want %v", got1, tt.want1)
			}
			if !reflect.DeepEqual(got2, tt.want2) {
				t.Errorf("GetService.buildURL() got2 = %v, want %v", got2, tt.want2)
			}
		})
	}
}

func TestGetService_Do(t *testing.T) {
	ctx := context.Background()

	type fields struct {
		client    HTTPClient
		namespace string
		scheme    string
		id        string
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
		want    *GetResponse
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
			name: "When perform request return error, it should return error",
			fields: fields{
				client: &httpClientMock{
					err: errors.New("errors"),
				},
				scheme:    "job",
				namespace: "default",
				id:        "54af81d8-6b35-11eb-9439-0242ac130002",
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
						"id":      "id:default:job:aaa",
						"pathId":  "/document/v1/default/job/docid/aaa",
						"fields": {
							"sddocname": "job",
							"documentid": "id:default:job::abc1234",
							"id": "abc1234",
							"external_id": "zyx2345",
							"title": "Sr Software Engineer",
							"description": "Senior <hi>Software</hi> Engineer provides innovative, simple solutions that<sep/>",
							"hash": [
								104,
								97,
								115,
								104,
								50,
								53,
								54

							],
							"feed_id": 46007,
							"hiring_organization": "Guegue Comunicaciones",
							"url": "https://jobs.foo.bar/job/Sr-Software-Engineer-MA-01752/707809600/?feedId=289100&utm_source=Indeed&utm_campaign=TJX_Indeed&utm_source=Indeed&mediaGuid=2f0f3f22-5295-44fb-a9f0-4ce776277ecc&bidCode=6dc807ce-a039-400c-b6d5-dd7ceea39c85&sponsored=ppc",
							"url_mobile": "https://jobs.mobile.foo.bar/job/Sr-Software-Engineer-MA-01752/707809600/?feedId=289100&utm_source=Indeed&utm_campaign=TJX_Indeed&utm_source=Indeed&mediaGuid=2f0f3f22-5295-44fb-a9f0-4ce776277ecc&bidCode=6dc807ce-a039-400c-b6d5-dd7ceea39c85&sponsored=ppc",
							"street_address1": "4846 University Street",
							"street_address2": "",
							"city": "Seattle",
							"state": "WA",
							"country": "",
							"country_name": "",
							"region": "",
							"postal_code": 98106,
							"location": {
								"y": 3.7401e+07,
								"x": -1.21996e+08
							},
							"bid_cpc": 0.5,
							"creation_date": 1612310992,
							"posting_creation_date": 1612310992,
							"posting_update_date": 1612310992,
							"posting_last_seen": 1612310992,
							"hidden": false,
							"sequence": 1
						}					
					}
					`)),
				},
				id:        "aaa",
				scheme:    "job",
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want: &GetResponse{
				Status:  200,
				ID:      "id:default:job:aaa",
				PathID:  "/document/v1/default/job/docid/aaa",
				Message: "",
				Fields: json.RawMessage{
					123, 10, 9, 9, 9, 9, 9, 9, 9, 34, 115, 100, 100, 111, 99, 110, 97, 109, 101, 34, 58, 32, 34, 106, 111, 98, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 100, 111, 99, 117, 109, 101, 110, 116, 105, 100, 34, 58, 32, 34, 105, 100, 58, 100, 101, 102, 97, 117, 108, 116, 58, 106, 111, 98, 58, 58, 97, 98, 99, 49, 50, 51, 52, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 105, 100, 34, 58, 32, 34, 97, 98, 99, 49, 50, 51, 52, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 101, 120, 116, 101, 114, 110, 97, 108, 95, 105, 100, 34, 58, 32, 34, 122, 121, 120, 50, 51, 52, 53, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 116, 105, 116, 108, 101, 34, 58, 32, 34, 83, 114, 32, 83, 111, 102, 116, 119, 97, 114, 101, 32, 69, 110, 103, 105, 110, 101, 101, 114, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 100, 101, 115, 99, 114, 105, 112, 116, 105, 111, 110, 34, 58, 32, 34, 83, 101, 110, 105, 111, 114, 32, 60, 104, 105, 62, 83, 111, 102, 116, 119, 97, 114, 101, 60, 47, 104, 105, 62, 32, 69, 110, 103, 105, 110, 101, 101, 114, 32, 112, 114, 111, 118, 105, 100, 101, 115, 32, 105, 110, 110, 111, 118, 97, 116, 105, 118, 101, 44, 32, 115, 105, 109, 112, 108, 101, 32, 115, 111, 108, 117, 116, 105, 111, 110, 115, 32, 116, 104, 97, 116, 60, 115, 101, 112, 47, 62, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 104, 97, 115, 104, 34, 58, 32, 91, 10, 9, 9, 9, 9, 9, 9, 9, 9, 49, 48, 52, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 57, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 49, 49, 53, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 49, 48, 52, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 53, 48, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 53, 51, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 53, 52, 10, 10, 9, 9, 9, 9, 9, 9, 9, 93, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 102, 101, 101, 100, 95, 105, 100, 34, 58, 32, 52, 54, 48, 48, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 104, 105, 114, 105, 110, 103, 95, 111, 114, 103, 97, 110, 105, 122, 97, 116, 105, 111, 110, 34, 58, 32, 34, 71, 117, 101, 103, 117, 101, 32, 67, 111, 109, 117, 110, 105, 99, 97, 99, 105, 111, 110, 101, 115, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 117, 114, 108, 34, 58, 32, 34, 104, 116, 116, 112, 115, 58, 47, 47, 106, 111, 98, 115, 46, 102, 111, 111, 46, 98, 97, 114, 47, 106, 111, 98, 47, 83, 114, 45, 83, 111, 102, 116, 119, 97, 114, 101, 45, 69, 110, 103, 105, 110, 101, 101, 114, 45, 77, 65, 45, 48, 49, 55, 53, 50, 47, 55, 48, 55, 56, 48, 57, 54, 48, 48, 47, 63, 102, 101, 101, 100, 73, 100, 61, 50, 56, 57, 49, 48, 48, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 99, 97, 109, 112, 97, 105, 103, 110, 61, 84, 74, 88, 95, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 109, 101, 100, 105, 97, 71, 117, 105, 100, 61, 50, 102, 48, 102, 51, 102, 50, 50, 45, 53, 50, 57, 53, 45, 52, 52, 102, 98, 45, 97, 57, 102, 48, 45, 52, 99, 101, 55, 55, 54, 50, 55, 55, 101, 99, 99, 38, 98, 105, 100, 67, 111, 100, 101, 61, 54, 100, 99, 56, 48, 55, 99, 101, 45, 97, 48, 51, 57, 45, 52, 48, 48, 99, 45, 98, 54, 100, 53, 45, 100, 100, 55, 99, 101, 101, 97, 51, 57, 99, 56, 53, 38, 115, 112, 111, 110, 115, 111, 114, 101, 100, 61, 112, 112, 99, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 117, 114, 108, 95, 109, 111, 98, 105, 108, 101, 34, 58, 32, 34, 104, 116, 116, 112, 115, 58, 47, 47, 106, 111, 98, 115, 46, 109, 111, 98, 105, 108, 101, 46, 102, 111, 111, 46, 98, 97, 114, 47, 106, 111, 98, 47, 83, 114, 45, 83, 111, 102, 116, 119, 97, 114, 101, 45, 69, 110, 103, 105, 110, 101, 101, 114, 45, 77, 65, 45, 48, 49, 55, 53, 50, 47, 55, 48, 55, 56, 48, 57, 54, 48, 48, 47, 63, 102, 101, 101, 100, 73, 100, 61, 50, 56, 57, 49, 48, 48, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 99, 97, 109, 112, 97, 105, 103, 110, 61, 84, 74, 88, 95, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 109, 101, 100, 105, 97, 71, 117, 105, 100, 61, 50, 102, 48, 102, 51, 102, 50, 50, 45, 53, 50, 57, 53, 45, 52, 52, 102, 98, 45, 97, 57, 102, 48, 45, 52, 99, 101, 55, 55, 54, 50, 55, 55, 101, 99, 99, 38, 98, 105, 100, 67, 111, 100, 101, 61, 54, 100, 99, 56, 48, 55, 99, 101, 45, 97, 48, 51, 57, 45, 52, 48, 48, 99, 45, 98, 54, 100, 53, 45, 100, 100, 55, 99, 101, 101, 97, 51, 57, 99, 56, 53, 38, 115, 112, 111, 110, 115, 111, 114, 101, 100, 61, 112, 112, 99, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 114, 101, 101, 116, 95, 97, 100, 100, 114, 101, 115, 115, 49, 34, 58, 32, 34, 52, 56, 52, 54, 32, 85, 110, 105, 118, 101, 114, 115, 105, 116, 121, 32, 83, 116, 114, 101, 101, 116, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 114, 101, 101, 116, 95, 97, 100, 100, 114, 101, 115, 115, 50, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 99, 105, 116, 121, 34, 58, 32, 34, 83, 101, 97, 116, 116, 108, 101, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 97, 116, 101, 34, 58, 32, 34, 87, 65, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 99, 111, 117, 110, 116, 114, 121, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 99, 111, 117, 110, 116, 114, 121, 95, 110, 97, 109, 101, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 114, 101, 103, 105, 111, 110, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 97, 108, 95, 99, 111, 100, 101, 34, 58, 32, 57, 56, 49, 48, 54, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 108, 111, 99, 97, 116, 105, 111, 110, 34, 58, 32, 123, 10, 9, 9, 9, 9, 9, 9, 9, 9, 34, 121, 34, 58, 32, 51, 46, 55, 52, 48, 49, 101, 43, 48, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 34, 120, 34, 58, 32, 45, 49, 46, 50, 49, 57, 57, 54, 101, 43, 48, 56, 10, 9, 9, 9, 9, 9, 9, 9, 125, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 98, 105, 100, 95, 99, 112, 99, 34, 58, 32, 48, 46, 53, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 99, 114, 101, 97, 116, 105, 111, 110, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 99, 114, 101, 97, 116, 105, 111, 110, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 117, 112, 100, 97, 116, 101, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 108, 97, 115, 116, 95, 115, 101, 101, 110, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 104, 105, 100, 100, 101, 110, 34, 58, 32, 102, 97, 108, 115, 101, 44, 10, 9, 9, 9, 9, 9, 9, 9, 34, 115, 101, 113, 117, 101, 110, 99, 101, 34, 58, 32, 49, 10, 9, 9, 9, 9, 9, 9, 125,
				},
			},
			wantErr: false,
		},
		{
			name: "when perform request returns with an http error, we should return a message",
			fields: fields{
				client: &httpClientMock{
					code: 500,
					err:  nil,
					body: ioutil.NopCloser(strings.NewReader(`
					{
						"pathId":  "/document/v1/default/jobs/docid/001",
						"message":	"Unknown bucket space mapping for document type 'jobs' in id: 'id:default:jobs::001"
					}
					`)),
				},
				id:        "001",
				scheme:    "jobs",
				namespace: "default",
			},
			args: args{
				ctx: ctx,
			},
			want: &GetResponse{
				Status:  500,
				PathID:  "/document/v1/default/jobs/docid/001",
				Message: "Unknown bucket space mapping for document type 'jobs' in id: 'id:default:jobs::001",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &GetService{
				client:    tt.fields.client,
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				timeout:   tt.fields.timeout,
				headers:   tt.fields.headers,
			}
			got, err := s.Do(tt.args.ctx)
			if (err != nil) != tt.wantErr {
				t.Errorf("GetService.Do() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("GetService.Do() = %v, want %v", got, tt.want)
			}
		})
	}
}
