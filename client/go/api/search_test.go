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

func TestNewSearchService(t *testing.T) {
	client := &Client{}

	want := &SearchService{
		client:    client,
		namespace: DefaultNamespace,
	}

	got := NewSearchService(client)

	if !reflect.DeepEqual(want, got) {
		t.Errorf(" expected NewSearchService() to return %+v, got %+v", want, got)
	}
}

func TestSearchService_Namespace(t *testing.T) {
	type args struct {
		name string
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set the default value to namespace",
			args: args{
				name: "default",
			},
			want: &SearchService{
				namespace: "default",
			},
		},
		{
			name: "Set a specific value to namespace",
			args: args{
				name: "specific",
			},
			want: &SearchService{
				namespace: "specific",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Namespace(tt.args.name); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Namespace() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Scheme(t *testing.T) {
	type args struct {
		scheme string
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set the job to scheme",
			args: args{
				scheme: "job",
			},
			want: &SearchService{
				scheme: "job",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Scheme(tt.args.scheme); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Ranking(t *testing.T) {
	type args struct {
		ranking Ranking
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set ranking",
			args: args{
				ranking: Ranking{
					Profile: "custom",
				},
			},
			want: &SearchService{
				ranking: Ranking{
					Profile: "custom",
				},
			},
		},
		{
			name: "Set ranking with softtimeout",
			args: args{
				ranking: Ranking{
					Profile: "custom",
					SoftTimeout: &SoftTimeout{
						Enabled: true,
					},
				},
			},
			want: &SearchService{
				ranking: Ranking{
					Profile: "custom",
					SoftTimeout: &SoftTimeout{
						Enabled: true,
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Ranking(tt.args.ranking); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.ranking() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Timeout(t *testing.T) {
	type args struct {
		timeout string
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set a second value to timeout",
			args: args{
				timeout: "12s",
			},
			want: &SearchService{
				timeout: "12s",
			},
		},
		{
			name: "Set a minute value to timeout",
			args: args{
				timeout: "1m",
			},
			want: &SearchService{
				timeout: "1m",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Timeout(tt.args.timeout); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Query(t *testing.T) {
	type args struct {
		query string
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set a second value to timeout",
			args: args{
				query: "nurse",
			},
			want: &SearchService{
				query: "nurse",
			},
		},
		{
			name: "Set a minute value to timeout",
			args: args{
				query: "engineer",
			},
			want: &SearchService{
				query: "engineer",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Query(tt.args.query); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Type(t *testing.T) {
	type args struct {
		searchType string
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set a second value to timeout",
			args: args{
				searchType: "all",
			},
			want: &SearchService{
				searchType: "all",
			},
		},
		{
			name: "Set a minute value to timeout",
			args: args{
				searchType: "any",
			},
			want: &SearchService{
				searchType: "any",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Type(tt.args.searchType); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Hits(t *testing.T) {
	type args struct {
		hits int
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set a second value to timeout",
			args: args{
				hits: 10,
			},
			want: &SearchService{
				hits: 10,
			},
		},
		{
			name: "Set a minute value to timeout",
			args: args{
				hits: 25,
			},
			want: &SearchService{
				hits: 25,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Hits(tt.args.hits); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Timeout() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Fields(t *testing.T) {
	type args struct {
		fields []string
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set the required fields to display in the final result",
			args: args{
				fields: []string{"title", "description"},
			},
			want: &SearchService{
				selectedFields: "title, description",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}
			if got := s.Fields(tt.args.fields...); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Fields() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Where(t *testing.T) {
	type args struct {
		query  string
		params []interface{}
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set a where clause with a contain condition",
			args: args{
				query: `title contains ?`,
				params: []interface{}{
					"software",
				},
			},
			want: &SearchService{
				whereClause: whereParams{
					where: `title contains ?`,
					params: []interface{}{
						"software",
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Where(tt.args.query, tt.args.params...); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Where() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Limit(t *testing.T) {
	type args struct {
		limit int
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set limit in the query",
			args: args{
				limit: 25,
			},
			want: &SearchService{
				limit: 25,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Limit(tt.args.limit); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Limit() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Offset(t *testing.T) {

	type args struct {
		offset int
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set offset in the query",
			args: args{
				offset: 10,
			},
			want: &SearchService{
				offset: 10,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.Offset(tt.args.offset); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Offset() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_OrderBy(t *testing.T) {

	type args struct {
		field  string
		isDesc bool
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "Set order by title ASC",
			args: args{
				field: "title",
			},
			want: &SearchService{
				orderBy: "title",
			},
		},
		{
			name: "Set order by title DESC",
			args: args{
				field:  "title",
				isDesc: true,
			},
			want: &SearchService{
				orderBy: "title",
				isDesc:  true,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}

			if got := s.OrderBy(tt.args.field, tt.args.isDesc); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.OrderBy() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_buildQuery(t *testing.T) {
	type fields struct {
		scheme         string
		selectedFields string
		whereClause    whereParams
		limit          int
		offset         int
		orderBy        string
		isDesc         bool
		query          string
		searchType     string
		hits           int
	}
	tests := []struct {
		name   string
		fields fields
		want   SearchRequest
	}{
		{
			name: "Set a contain condition to the final YQL query",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
			},
			want: SearchRequest{
				YQL:     `select * from job where title contains "developer";`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "When using a query we should get the desired Search Request",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "status contains ?",
					params: []interface{}{
						"active",
					},
				},
				query: "amazing job",
			},
			want: SearchRequest{
				YQL:     `select * from job where userQuery() AND status contains "active";`,
				Type:    "all",
				Query:   "amazing job",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "When using query but not where, we should get the desired Search Request",
			fields: fields{
				scheme: "job",
				query:  "amazing job",
			},
			want: SearchRequest{
				YQL:     `select * from job where userQuery();`,
				Type:    "all",
				Query:   "amazing job",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "Set fields and a contain condition to the final YQL query",
			fields: fields{
				scheme:         "job",
				selectedFields: "title, description",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
			},
			want: SearchRequest{
				YQL:     `select title, description from job where title contains "developer";`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "Set fields and all inactive jobs condition to the final YQL query",
			fields: fields{
				scheme:         "job",
				selectedFields: "title, description",
				whereClause: whereParams{
					where: "hidden = ?",
					params: []interface{}{
						true,
					},
				},
			},
			want: SearchRequest{
				YQL:     `select title, description from job where hidden = true;`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "Set fields and get the jobs with CPC greater than 0.20 to the final YQL query",
			fields: fields{
				scheme:         "job",
				selectedFields: "title, description, bid_cpc",
				whereClause: whereParams{
					where: "bid_cpc > ?",
					params: []interface{}{
						0.20,
					},
				},
			},
			want: SearchRequest{
				YQL:     `select title, description, bid_cpc from job where bid_cpc > 0.20;`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "Set a phrase condition to the final YQL query",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "title contains phrase(?)",
					params: []interface{}{
						"software developer",
					},
				},
			},
			want: SearchRequest{
				YQL:     `select * from job where title contains phrase("software developer");`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "Set a match condition with a limit and offset",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "title matches ?",
					params: []interface{}{
						"developer",
					},
				},
				limit:  25,
				offset: 50,
			},
			want: SearchRequest{
				YQL:     `select * from job where title matches "developer" limit 75 offset 50;`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "Set an order by title ASC",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "range(sequence, ?, ?)",
					params: []interface{}{
						0, 5,
					},
				},
				orderBy: "sequence",
			},
			want: SearchRequest{
				YQL:     `select * from job where range(sequence, 0, 5) order by sequence;`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "Set an order by title DESC",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "range(sequence, ?, ?)",
					params: []interface{}{
						0, 5,
					},
				},
				orderBy: "sequence",
				isDesc:  true,
			},
			want: SearchRequest{
				YQL:     `select * from job where range(sequence, 0, 5) order by sequence desc;`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},

		{
			name: "Set pagination without condition",
			fields: fields{
				scheme:  "job",
				limit:   10,
				offset:  50,
				orderBy: "title",
			},
			want: SearchRequest{
				YQL:     `select * from job order by title limit 60 offset 50;`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "When using IN with numbers, we should get the expected result",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "id IN(?)",
					params: []interface{}{
						[]int{1, 2, 3},
					},
				},
			},
			want: SearchRequest{
				YQL:     `select * from job where id IN(1,2,3);`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "When using IN with string, we should get the expected result",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "name IN(?)",
					params: []interface{}{
						[]string{"Tom", "Joe", "Aaron"},
					},
				},
			},
			want: SearchRequest{
				YQL:     `select * from job where name IN("Tom","Joe","Aaron");`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "When using IN with int64, we should get the value between single quotes",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "value > ?",
					params: []interface{}{
						int64(355425505594573937),
					},
				},
			},
			want: SearchRequest{
				YQL:     `select * from job where value > '355425505594573937';`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "when type is set, we should see it on Search Request",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				searchType: "any",
			},
			want: SearchRequest{
				YQL:     `select * from job where title contains "developer";`,
				Type:    "any",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "when hitss is set, we should see it on Search Request",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				hits: 25,
			},
			want: SearchRequest{
				YQL:     `select * from job where title contains "developer";`,
				Type:    "all",
				Query:   "",
				Hits:    25,
				Ranking: "",
			},
		},
		{
			name: "When using []WeightedElement, we should get the desired output",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "weightedSet(?, ?)",
					params: []interface{}{
						"feed_id",
						[]WeightedElement{
							{
								Index: "32234",
								Value: 1,
							},
							{
								Index: "50123",
								Value: 2,
							},
						},
					},
				},
			},
			want: SearchRequest{
				YQL:     `select * from job where weightedSet("feed_id", {"32234":1,"50123":2});`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
		{
			name: "When using WeakAnd, we should get the desired output",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "?",
					params: []interface{}{
						WeakAnd{
							Hits: 10,
							Entries: []WeakAndEntry{
								{Field: "default", Value: "software"},
								{Field: "default", Value: "engineer"},
							},
						},
					},
				},
			},
			want: SearchRequest{
				YQL:     `select * from job where ([{"targetHits":10}] weakAnd(default contains "software", default contains "engineer"));`,
				Type:    "all",
				Query:   "",
				Hits:    0,
				Ranking: "",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{
				scheme:         tt.fields.scheme,
				selectedFields: tt.fields.selectedFields,
				whereClause:    tt.fields.whereClause,
				limit:          tt.fields.limit,
				offset:         tt.fields.offset,
				orderBy:        tt.fields.orderBy,
				isDesc:         tt.fields.isDesc,
				query:          tt.fields.query,
				searchType:     tt.fields.searchType,
				hits:           tt.fields.hits,
			}
			s.buildQuery()

			if !reflect.DeepEqual(tt.want, s.searchRequest) {
				t.Errorf(" expected buildQuery() to return %+v, got %+v", tt.want, s.searchRequest)
			}
		})
	}
}

func TestSearchService_Validate(t *testing.T) {
	type fields struct {
		client      HTTPClient
		namespace   string
		scheme      string
		whereClause whereParams
		query       string
		timeout     string
		headers     http.Header
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
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				namespace: "default",
			},
			wantErr: true,
		},
		{
			name: "All fields set should return error nil",
			fields: fields{
				scheme: "job",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				namespace: "default",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{
				client:      tt.fields.client,
				namespace:   tt.fields.namespace,
				scheme:      tt.fields.scheme,
				whereClause: tt.fields.whereClause,
				query:       tt.fields.query,
				timeout:     tt.fields.timeout,
				headers:     tt.fields.headers,
			}

			if err := s.Validate(); (err != nil) != tt.wantErr {
				t.Errorf("SearchService.Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestSearchService_buildURL(t *testing.T) {
	type fields struct {
		client               HTTPClient
		namespace            string
		scheme               string
		whereClause          whereParams
		query                string
		timeout              string
		headers              http.Header
		presentation         []Presentation
		ranking              Ranking
		rankingFeaturesQuery RankingFeaturesQuery
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
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				scheme: "job",
			},
			want:  "POST",
			want1: "/search/",
			want2: url.Values{},
		},
		{
			name: "When called with presentation, it should return the expected values",
			fields: fields{
				namespace: "default",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				scheme: "job",
				presentation: []Presentation{
					{
						key:   "presentation.bolding",
						value: true,
					},
				},
			},
			want:  "POST",
			want1: "/search/",
			want2: url.Values{
				"presentation.bolding": []string{"true"},
			},
		},
		{
			name: "When called with ranking, it should return the expected values",
			fields: fields{
				namespace: "default",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				scheme: "job",
				ranking: Ranking{
					Profile: "custom_rank",
				},
			},
			want:  "POST",
			want1: "/search/",
			want2: url.Values{
				"ranking": []string{"custom_rank"},
			},
		},
		{
			name: "When called with ranking softimeout, it should return the expected values",
			fields: fields{
				namespace: "default",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				scheme: "job",
				ranking: Ranking{
					Profile: "custom_rank",
					SoftTimeout: &SoftTimeout{
						Enabled: false,
					},
				},
			},
			want:  "POST",
			want1: "/search/",
			want2: url.Values{
				"ranking":                    []string{"custom_rank"},
				"ranking.softtimeout.enable": []string{"false"},
			},
		},
		{
			name: "When called with ranking features query, it should return the expected values",
			fields: fields{
				namespace: "default",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				scheme: "job",
				rankingFeaturesQuery: RankingFeaturesQuery{
					Values: map[string]interface{}{
						"param1": 1,
						"param2": 1.2,
					},
				},
			},
			want:  "POST",
			want1: "/search/",
			want2: url.Values{
				"ranking.features.query(param1)": []string{"1"},
				"ranking.features.query(param2)": []string{"1.2"},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{
				client:               tt.fields.client,
				namespace:            tt.fields.namespace,
				scheme:               tt.fields.scheme,
				whereClause:          tt.fields.whereClause,
				query:                tt.fields.query,
				timeout:              tt.fields.timeout,
				headers:              tt.fields.headers,
				presentation:         tt.fields.presentation,
				ranking:              tt.fields.ranking,
				rankingFeaturesQuery: tt.fields.rankingFeaturesQuery,
			}
			got, got1, got2 := s.buildURL()
			if got != tt.want {
				t.Errorf("SearchService.buildURL() got = %v, want %v", got, tt.want)
			}
			if got1 != tt.want1 {
				t.Errorf("SearchService.buildURL() got1 = %v, want %v", got1, tt.want1)
			}
			if !reflect.DeepEqual(got2, tt.want2) {
				t.Errorf("SearchService.buildURL() got2 = %v, want %v", got2, tt.want2)
			}
		})
	}
}

func TestSearchService_Source(t *testing.T) {
	type fields struct {
		scheme string
		where  string
		offset int
		limit  int
		params []interface{}
	}
	tests := []struct {
		name   string
		fields fields
		want   SearchRequest
	}{
		{
			name: "",
			fields: fields{
				scheme: "job",
				where:  `title contains ?`,
				params: []interface{}{
					"developer",
				},
			},
			want: SearchRequest{
				YQL:  `select * from job where title contains "developer";`,
				Type: "all",
			},
		},
		{
			name: "with pagination",
			fields: fields{
				scheme: "job",
				where:  `title contains ?`,
				params: []interface{}{
					"developer",
				},
				offset: 0,
				limit:  10,
			},
			want: SearchRequest{
				YQL:  `select * from job where title contains "developer" limit 10;`,
				Type: "all",
			},
		},
		{
			name: "with offset 10",
			fields: fields{
				scheme: "job",
				where:  `title contains ?`,
				params: []interface{}{
					"developer",
				},
				offset: 10,
				limit:  10,
			},
			want: SearchRequest{
				YQL:  `select * from job where title contains "developer" limit 20 offset 10;`,
				Type: "all",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{
				client:         nil,
				namespace:      "",
				scheme:         tt.fields.scheme,
				selectedFields: "",
				whereClause:    whereParams{where: tt.fields.where, params: tt.fields.params},
				limit:          tt.fields.limit,
				offset:         tt.fields.offset,
				orderBy:        "",
				isDesc:         false,
				timeout:        "",
				presentation:   []Presentation{},
				headers:        map[string][]string{},
			}

			got := s.Source()

			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Source() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_Do(t *testing.T) {
	ctx := context.Background()

	type fields struct {
		client      HTTPClient
		namespace   string
		scheme      string
		whereClause whereParams
		query       string
		timeout     string
		headers     http.Header
	}
	type args struct {
		ctx context.Context
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		want    *SearchResponse
		wantErr bool
	}{
		{
			name:   "When validate return error, it should return error",
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
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				query: `select * from job where title contains "developer";`,
			},
			args: args{
				ctx: ctx,
			},
			want:    nil,
			wantErr: true,
		},
		{
			name: "When perform request succeed, it should return a SearchResponse",
			fields: fields{
				client: &httpClientMock{
					code: 200,
					err:  nil,
					body: ioutil.NopCloser(strings.NewReader(`
					{
						"root": {
							"id": "toplevel",
							"relevance": 1,
							"fields": {
								"totalCount": 1
							},
							"coverage": {
								"coverage": 100,
								"documents": 1,
								"full": true,
								"nodes": 1,
								"results": 1,
								"resultsFull": 1
							},
							"children": [{
								"id": "id:default:job::abc1234",
								"relevance": 1,
								"source": "job",
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
							}]
						}
					}
					`)),
				},
				scheme:    "job",
				namespace: "default",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				query: `select * from job where title contains "developer";`,
			},
			args: args{
				ctx: ctx,
			},
			want: &SearchResponse{
				Status: 200,
				Root: Root{
					ID:        "toplevel",
					Relevance: 1,
					Fields: Fields{
						TotalCount: 1,
					},
					Coverage: Coverage{
						Coverage:    100,
						Documents:   1,
						Full:        true,
						Nodes:       1,
						Results:     1,
						ResultsFull: 1,
					},
					Children: []Child{
						{
							ID:        "id:default:job::abc1234",
							Relevance: 1,
							Source:    "job",
							Fields: json.RawMessage{
								123, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 100, 100, 111, 99, 110, 97, 109, 101, 34, 58, 32, 34, 106, 111, 98, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 100, 111, 99, 117, 109, 101, 110, 116, 105, 100, 34, 58, 32, 34, 105, 100, 58, 100, 101, 102, 97, 117, 108, 116, 58, 106, 111, 98, 58, 58, 97, 98, 99, 49, 50, 51, 52, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 105, 100, 34, 58, 32, 34, 97, 98, 99, 49, 50, 51, 52, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 101, 120, 116, 101, 114, 110, 97, 108, 95, 105, 100, 34, 58, 32, 34, 122, 121, 120, 50, 51, 52, 53, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 116, 105, 116, 108, 101, 34, 58, 32, 34, 83, 114, 32, 83, 111, 102, 116, 119, 97, 114, 101, 32, 69, 110, 103, 105, 110, 101, 101, 114, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 100, 101, 115, 99, 114, 105, 112, 116, 105, 111, 110, 34, 58, 32, 34, 83, 101, 110, 105, 111, 114, 32, 60, 104, 105, 62, 83, 111, 102, 116, 119, 97, 114, 101, 60, 47, 104, 105, 62, 32, 69, 110, 103, 105, 110, 101, 101, 114, 32, 112, 114, 111, 118, 105, 100, 101, 115, 32, 105, 110, 110, 111, 118, 97, 116, 105, 118, 101, 44, 32, 115, 105, 109, 112, 108, 101, 32, 115, 111, 108, 117, 116, 105, 111, 110, 115, 32, 116, 104, 97, 116, 60, 115, 101, 112, 47, 62, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 104, 97, 115, 104, 34, 58, 32, 91, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 49, 48, 52, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 57, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 49, 49, 53, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 49, 48, 52, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 53, 48, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 53, 51, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 53, 52, 10, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 93, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 102, 101, 101, 100, 95, 105, 100, 34, 58, 32, 52, 54, 48, 48, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 104, 105, 114, 105, 110, 103, 95, 111, 114, 103, 97, 110, 105, 122, 97, 116, 105, 111, 110, 34, 58, 32, 34, 71, 117, 101, 103, 117, 101, 32, 67, 111, 109, 117, 110, 105, 99, 97, 99, 105, 111, 110, 101, 115, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 117, 114, 108, 34, 58, 32, 34, 104, 116, 116, 112, 115, 58, 47, 47, 106, 111, 98, 115, 46, 102, 111, 111, 46, 98, 97, 114, 47, 106, 111, 98, 47, 83, 114, 45, 83, 111, 102, 116, 119, 97, 114, 101, 45, 69, 110, 103, 105, 110, 101, 101, 114, 45, 77, 65, 45, 48, 49, 55, 53, 50, 47, 55, 48, 55, 56, 48, 57, 54, 48, 48, 47, 63, 102, 101, 101, 100, 73, 100, 61, 50, 56, 57, 49, 48, 48, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 99, 97, 109, 112, 97, 105, 103, 110, 61, 84, 74, 88, 95, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 109, 101, 100, 105, 97, 71, 117, 105, 100, 61, 50, 102, 48, 102, 51, 102, 50, 50, 45, 53, 50, 57, 53, 45, 52, 52, 102, 98, 45, 97, 57, 102, 48, 45, 52, 99, 101, 55, 55, 54, 50, 55, 55, 101, 99, 99, 38, 98, 105, 100, 67, 111, 100, 101, 61, 54, 100, 99, 56, 48, 55, 99, 101, 45, 97, 48, 51, 57, 45, 52, 48, 48, 99, 45, 98, 54, 100, 53, 45, 100, 100, 55, 99, 101, 101, 97, 51, 57, 99, 56, 53, 38, 115, 112, 111, 110, 115, 111, 114, 101, 100, 61, 112, 112, 99, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 117, 114, 108, 95, 109, 111, 98, 105, 108, 101, 34, 58, 32, 34, 104, 116, 116, 112, 115, 58, 47, 47, 106, 111, 98, 115, 46, 109, 111, 98, 105, 108, 101, 46, 102, 111, 111, 46, 98, 97, 114, 47, 106, 111, 98, 47, 83, 114, 45, 83, 111, 102, 116, 119, 97, 114, 101, 45, 69, 110, 103, 105, 110, 101, 101, 114, 45, 77, 65, 45, 48, 49, 55, 53, 50, 47, 55, 48, 55, 56, 48, 57, 54, 48, 48, 47, 63, 102, 101, 101, 100, 73, 100, 61, 50, 56, 57, 49, 48, 48, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 99, 97, 109, 112, 97, 105, 103, 110, 61, 84, 74, 88, 95, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 109, 101, 100, 105, 97, 71, 117, 105, 100, 61, 50, 102, 48, 102, 51, 102, 50, 50, 45, 53, 50, 57, 53, 45, 52, 52, 102, 98, 45, 97, 57, 102, 48, 45, 52, 99, 101, 55, 55, 54, 50, 55, 55, 101, 99, 99, 38, 98, 105, 100, 67, 111, 100, 101, 61, 54, 100, 99, 56, 48, 55, 99, 101, 45, 97, 48, 51, 57, 45, 52, 48, 48, 99, 45, 98, 54, 100, 53, 45, 100, 100, 55, 99, 101, 101, 97, 51, 57, 99, 56, 53, 38, 115, 112, 111, 110, 115, 111, 114, 101, 100, 61, 112, 112, 99, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 114, 101, 101, 116, 95, 97, 100, 100, 114, 101, 115, 115, 49, 34, 58, 32, 34, 52, 56, 52, 54, 32, 85, 110, 105, 118, 101, 114, 115, 105, 116, 121, 32, 83, 116, 114, 101, 101, 116, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 114, 101, 101, 116, 95, 97, 100, 100, 114, 101, 115, 115, 50, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 99, 105, 116, 121, 34, 58, 32, 34, 83, 101, 97, 116, 116, 108, 101, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 97, 116, 101, 34, 58, 32, 34, 87, 65, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 99, 111, 117, 110, 116, 114, 121, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 99, 111, 117, 110, 116, 114, 121, 95, 110, 97, 109, 101, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 114, 101, 103, 105, 111, 110, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 97, 108, 95, 99, 111, 100, 101, 34, 58, 32, 57, 56, 49, 48, 54, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 108, 111, 99, 97, 116, 105, 111, 110, 34, 58, 32, 123, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 121, 34, 58, 32, 51, 46, 55, 52, 48, 49, 101, 43, 48, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 120, 34, 58, 32, 45, 49, 46, 50, 49, 57, 57, 54, 101, 43, 48, 56, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 125, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 98, 105, 100, 95, 99, 112, 99, 34, 58, 32, 48, 46, 53, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 99, 114, 101, 97, 116, 105, 111, 110, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 99, 114, 101, 97, 116, 105, 111, 110, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 117, 112, 100, 97, 116, 101, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 108, 97, 115, 116, 95, 115, 101, 101, 110, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 104, 105, 100, 100, 101, 110, 34, 58, 32, 102, 97, 108, 115, 101, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 101, 113, 117, 101, 110, 99, 101, 34, 58, 32, 49, 10, 9, 9, 9, 9, 9, 9, 9, 9, 125,
							},
						},
					},
				},
			},
			wantErr: false,
		},
		{
			name: "When there's not any result",
			fields: fields{
				client: &httpClientMock{
					code: 200,
					err:  nil,
					body: ioutil.NopCloser(strings.NewReader(`
					{
						"root": {
							"id": "toplevel",
							"relevance": 1,
							"fields": {
								"totalCount": 0
							},
							"coverage": {
								"coverage": 100,
								"documents": 1,
								"full": true,
								"nodes": 1,
								"results": 1,
								"resultsFull": 1
							},
							"children": []
						}
					}
					`)),
				},
				scheme:    "job",
				namespace: "default",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"pilot",
					},
				},
				query: `select * from job where title contains "pilot";`,
			},
			args: args{
				ctx: ctx,
			},
			want: &SearchResponse{
				Status: 200,
				Root: Root{
					ID:        "toplevel",
					Relevance: 1,
					Fields: Fields{
						TotalCount: 0,
					},
					Coverage: Coverage{
						Coverage:    100,
						Documents:   1,
						Full:        true,
						Nodes:       1,
						Results:     1,
						ResultsFull: 1,
					},
					Children: []Child{},
				},
			},
			wantErr: false,
		},
		{
			name: "When the relevance is a string(NAN) we should treat it as 0",
			fields: fields{
				client: &httpClientMock{
					code: 200,
					err:  nil,
					body: ioutil.NopCloser(strings.NewReader(`
					{
						"root": {
							"id": "toplevel",
							"relevance": 1,
							"fields": {
								"totalCount": 1
							},
							"coverage": {
								"coverage": 100,
								"documents": 1,
								"full": true,
								"nodes": 1,
								"results": 1,
								"resultsFull": 1
							},
							"children": [{
								"id": "id:default:job::abc1234",
								"relevance": "NaN",
								"source": "job",
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
							}]
						}
					}
					`)),
				},
				scheme:    "job",
				namespace: "default",
				whereClause: whereParams{
					where: "title contains ?",
					params: []interface{}{
						"developer",
					},
				},
				query: `select * from job where title contains "developer";`,
			},
			args: args{
				ctx: ctx,
			},
			want: &SearchResponse{
				Status: 200,
				Root: Root{
					ID:        "toplevel",
					Relevance: 1,
					Fields: Fields{
						TotalCount: 1,
					},
					Coverage: Coverage{
						Coverage:    100,
						Documents:   1,
						Full:        true,
						Nodes:       1,
						Results:     1,
						ResultsFull: 1,
					},
					Children: []Child{
						{
							ID:        "id:default:job::abc1234",
							Relevance: 0,
							Source:    "job",
							Fields: json.RawMessage{
								123, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 100, 100, 111, 99, 110, 97, 109, 101, 34, 58, 32, 34, 106, 111, 98, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 100, 111, 99, 117, 109, 101, 110, 116, 105, 100, 34, 58, 32, 34, 105, 100, 58, 100, 101, 102, 97, 117, 108, 116, 58, 106, 111, 98, 58, 58, 97, 98, 99, 49, 50, 51, 52, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 105, 100, 34, 58, 32, 34, 97, 98, 99, 49, 50, 51, 52, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 101, 120, 116, 101, 114, 110, 97, 108, 95, 105, 100, 34, 58, 32, 34, 122, 121, 120, 50, 51, 52, 53, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 116, 105, 116, 108, 101, 34, 58, 32, 34, 83, 114, 32, 83, 111, 102, 116, 119, 97, 114, 101, 32, 69, 110, 103, 105, 110, 101, 101, 114, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 100, 101, 115, 99, 114, 105, 112, 116, 105, 111, 110, 34, 58, 32, 34, 83, 101, 110, 105, 111, 114, 32, 60, 104, 105, 62, 83, 111, 102, 116, 119, 97, 114, 101, 60, 47, 104, 105, 62, 32, 69, 110, 103, 105, 110, 101, 101, 114, 32, 112, 114, 111, 118, 105, 100, 101, 115, 32, 105, 110, 110, 111, 118, 97, 116, 105, 118, 101, 44, 32, 115, 105, 109, 112, 108, 101, 32, 115, 111, 108, 117, 116, 105, 111, 110, 115, 32, 116, 104, 97, 116, 60, 115, 101, 112, 47, 62, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 104, 97, 115, 104, 34, 58, 32, 91, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 49, 48, 52, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 57, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 49, 49, 53, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 49, 48, 52, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 53, 48, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 53, 51, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 53, 52, 10, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 93, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 102, 101, 101, 100, 95, 105, 100, 34, 58, 32, 52, 54, 48, 48, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 104, 105, 114, 105, 110, 103, 95, 111, 114, 103, 97, 110, 105, 122, 97, 116, 105, 111, 110, 34, 58, 32, 34, 71, 117, 101, 103, 117, 101, 32, 67, 111, 109, 117, 110, 105, 99, 97, 99, 105, 111, 110, 101, 115, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 117, 114, 108, 34, 58, 32, 34, 104, 116, 116, 112, 115, 58, 47, 47, 106, 111, 98, 115, 46, 102, 111, 111, 46, 98, 97, 114, 47, 106, 111, 98, 47, 83, 114, 45, 83, 111, 102, 116, 119, 97, 114, 101, 45, 69, 110, 103, 105, 110, 101, 101, 114, 45, 77, 65, 45, 48, 49, 55, 53, 50, 47, 55, 48, 55, 56, 48, 57, 54, 48, 48, 47, 63, 102, 101, 101, 100, 73, 100, 61, 50, 56, 57, 49, 48, 48, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 99, 97, 109, 112, 97, 105, 103, 110, 61, 84, 74, 88, 95, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 109, 101, 100, 105, 97, 71, 117, 105, 100, 61, 50, 102, 48, 102, 51, 102, 50, 50, 45, 53, 50, 57, 53, 45, 52, 52, 102, 98, 45, 97, 57, 102, 48, 45, 52, 99, 101, 55, 55, 54, 50, 55, 55, 101, 99, 99, 38, 98, 105, 100, 67, 111, 100, 101, 61, 54, 100, 99, 56, 48, 55, 99, 101, 45, 97, 48, 51, 57, 45, 52, 48, 48, 99, 45, 98, 54, 100, 53, 45, 100, 100, 55, 99, 101, 101, 97, 51, 57, 99, 56, 53, 38, 115, 112, 111, 110, 115, 111, 114, 101, 100, 61, 112, 112, 99, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 117, 114, 108, 95, 109, 111, 98, 105, 108, 101, 34, 58, 32, 34, 104, 116, 116, 112, 115, 58, 47, 47, 106, 111, 98, 115, 46, 109, 111, 98, 105, 108, 101, 46, 102, 111, 111, 46, 98, 97, 114, 47, 106, 111, 98, 47, 83, 114, 45, 83, 111, 102, 116, 119, 97, 114, 101, 45, 69, 110, 103, 105, 110, 101, 101, 114, 45, 77, 65, 45, 48, 49, 55, 53, 50, 47, 55, 48, 55, 56, 48, 57, 54, 48, 48, 47, 63, 102, 101, 101, 100, 73, 100, 61, 50, 56, 57, 49, 48, 48, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 99, 97, 109, 112, 97, 105, 103, 110, 61, 84, 74, 88, 95, 73, 110, 100, 101, 101, 100, 38, 117, 116, 109, 95, 115, 111, 117, 114, 99, 101, 61, 73, 110, 100, 101, 101, 100, 38, 109, 101, 100, 105, 97, 71, 117, 105, 100, 61, 50, 102, 48, 102, 51, 102, 50, 50, 45, 53, 50, 57, 53, 45, 52, 52, 102, 98, 45, 97, 57, 102, 48, 45, 52, 99, 101, 55, 55, 54, 50, 55, 55, 101, 99, 99, 38, 98, 105, 100, 67, 111, 100, 101, 61, 54, 100, 99, 56, 48, 55, 99, 101, 45, 97, 48, 51, 57, 45, 52, 48, 48, 99, 45, 98, 54, 100, 53, 45, 100, 100, 55, 99, 101, 101, 97, 51, 57, 99, 56, 53, 38, 115, 112, 111, 110, 115, 111, 114, 101, 100, 61, 112, 112, 99, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 114, 101, 101, 116, 95, 97, 100, 100, 114, 101, 115, 115, 49, 34, 58, 32, 34, 52, 56, 52, 54, 32, 85, 110, 105, 118, 101, 114, 115, 105, 116, 121, 32, 83, 116, 114, 101, 101, 116, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 114, 101, 101, 116, 95, 97, 100, 100, 114, 101, 115, 115, 50, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 99, 105, 116, 121, 34, 58, 32, 34, 83, 101, 97, 116, 116, 108, 101, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 116, 97, 116, 101, 34, 58, 32, 34, 87, 65, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 99, 111, 117, 110, 116, 114, 121, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 99, 111, 117, 110, 116, 114, 121, 95, 110, 97, 109, 101, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 114, 101, 103, 105, 111, 110, 34, 58, 32, 34, 34, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 97, 108, 95, 99, 111, 100, 101, 34, 58, 32, 57, 56, 49, 48, 54, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 108, 111, 99, 97, 116, 105, 111, 110, 34, 58, 32, 123, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 121, 34, 58, 32, 51, 46, 55, 52, 48, 49, 101, 43, 48, 55, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 120, 34, 58, 32, 45, 49, 46, 50, 49, 57, 57, 54, 101, 43, 48, 56, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 125, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 98, 105, 100, 95, 99, 112, 99, 34, 58, 32, 48, 46, 53, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 99, 114, 101, 97, 116, 105, 111, 110, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 99, 114, 101, 97, 116, 105, 111, 110, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 117, 112, 100, 97, 116, 101, 95, 100, 97, 116, 101, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 112, 111, 115, 116, 105, 110, 103, 95, 108, 97, 115, 116, 95, 115, 101, 101, 110, 34, 58, 32, 49, 54, 49, 50, 51, 49, 48, 57, 57, 50, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 104, 105, 100, 100, 101, 110, 34, 58, 32, 102, 97, 108, 115, 101, 44, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9, 34, 115, 101, 113, 117, 101, 110, 99, 101, 34, 58, 32, 49, 10, 9, 9, 9, 9, 9, 9, 9, 9, 125,
							},
						},
					},
				},
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{
				client:      tt.fields.client,
				namespace:   tt.fields.namespace,
				scheme:      tt.fields.scheme,
				whereClause: tt.fields.whereClause,
				query:       tt.fields.query,
				timeout:     tt.fields.timeout,
				headers:     tt.fields.headers,
			}
			got, err := s.Do(tt.args.ctx)
			if (err != nil) != tt.wantErr {
				t.Errorf("SearchService.Do() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Do() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestWhereWithSliceParam(t *testing.T) {

	svc := SearchService{}
	param := []int{1, 2, 3}

	want := SearchService{
		whereClause: whereParams{
			params: []interface{}{
				[]int{1, 2, 3},
				"01",
			},
		},
	}

	svc.Where("", param, "01")

	if !reflect.DeepEqual(svc, want) {
		t.Errorf("SearchService.Where() = %v, want %v", svc, want)
	}

}

func TestSearchService_Presentation(t *testing.T) {
	type fields struct {
		client         HTTPClient
		namespace      string
		scheme         string
		selectedFields string
		whereClause    whereParams
		limit          int
		offset         int
		orderBy        string
		isDesc         bool
		query          string
		timeout        string
		presentation   []Presentation
		headers        http.Header
	}
	type args struct {
		key   string
		value interface{}
	}
	tests := []struct {
		name   string
		fields fields
		args   args
		want   *SearchService
	}{
		{
			name: "when used we should expect the value in the presentation array",
			args: args{
				key:   "presentation.summary",
				value: "record",
			},
			want: &SearchService{
				presentation: []Presentation{
					{
						key:   "presentation.summary",
						value: "record",
					},
				},
			},
		},
		{
			name: "when used with a bool value should expect the value in the presentation array",
			args: args{
				key:   "presentation.summary",
				value: true,
			},
			want: &SearchService{
				presentation: []Presentation{
					{
						key:   "presentation.summary",
						value: true,
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{
				client:         tt.fields.client,
				namespace:      tt.fields.namespace,
				scheme:         tt.fields.scheme,
				selectedFields: tt.fields.selectedFields,
				whereClause:    tt.fields.whereClause,
				limit:          tt.fields.limit,
				offset:         tt.fields.offset,
				orderBy:        tt.fields.orderBy,
				isDesc:         tt.fields.isDesc,
				query:          tt.fields.query,
				timeout:        tt.fields.timeout,
				presentation:   tt.fields.presentation,
				headers:        tt.fields.headers,
			}
			if got := s.Presentation(tt.args.key, tt.args.value); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.Presentation() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSearchService_RankingFeatureQuery(t *testing.T) {
	type args struct {
		features RankingFeaturesQuery
	}
	tests := []struct {
		name string
		args args
		want *SearchService
	}{
		{
			name: "when calling rankingfeaturequery we should have the value set",
			args: args{
				features: RankingFeaturesQuery{
					Values: map[string]interface{}{
						"key": "value",
					},
				},
			},
			want: &SearchService{
				rankingFeaturesQuery: RankingFeaturesQuery{
					Values: map[string]interface{}{
						"key": "value",
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &SearchService{}
			if got := s.RankingFeatureQuery(tt.args.features); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("SearchService.RankingFeatureQuery() = %v, want %v", got, tt.want)
			}
		})
	}
}
