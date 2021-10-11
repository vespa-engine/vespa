package vespa

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"reflect"
	"strings"
)

// SearchService retrieves the documents depending on a query that is
// generated according to the parameters that the user provides
type SearchService struct {
	client HTTPClient

	namespace      string
	scheme         string
	selectedFields string
	whereClause    whereParams
	ranking        Ranking
	sorting        Sorting
	limit          int
	offset         int
	orderBy        string
	isDesc         bool
	timeout        string
	presentation   []Presentation
	headers        http.Header

	searchRequest        SearchRequest
	query                string
	searchType           string
	hits                 int
	rankingFeaturesQuery RankingFeaturesQuery
}

// SearchRequest is the data we send inside a search request body.
type SearchRequest struct {
	YQL     string `json:"yql,omitempty"`
	Type    string `json:"type,omitempty"`
	Query   string `json:"query,omitempty"`
	Hits    int    `json:"hits,omitempty"`
	Ranking string `json:"ranking,omitempty"`
}

// WeightedElement represents a single item inside a Vespa's WeightedSet
type WeightedElement struct {
	Index string
	Value int
}

// Ranking reflects the ranking configuration for a search request
type Ranking struct {
	Profile     string
	SoftTimeout *SoftTimeout
}

// SoftTimeout reflects the ranking.Softimeout request configuration
type SoftTimeout struct {
	Enabled bool
	Factor  float32
}

// Sorting enables certain sorting options
type Sorting struct {
	Degrading *bool
}

// Presentation holds presentation parameters that can be used on queries
type Presentation struct {
	key   string
	value interface{}
}

type whereParams struct {
	where  string
	params []interface{}
}

// NewSearchService will create an instance of SearchService.
func NewSearchService(client HTTPClient) *SearchService {
	return &SearchService{
		client:    client,
		namespace: DefaultNamespace,
	}
}

// Namespace sets the namespace of the document. If function is not called, it
// will use the default namespace.
func (s *SearchService) Namespace(name string) *SearchService {
	s.namespace = name
	return s
}

// Scheme sets the name of the scheme.
func (s *SearchService) Scheme(scheme string) *SearchService {
	s.scheme = scheme
	return s
}

// Timeout sets the Request timeout expresed in in seconds, or with optional ks, s, ms or µs unit.
func (s *SearchService) Timeout(timeout string) *SearchService {
	s.timeout = timeout
	return s
}

// Type sets the type of search we want to run. Options are: all(default), where, any, phrase
func (s *SearchService) Type(t string) *SearchService {
	s.searchType = t
	return s
}

// Hits sets the numer of records we want to return in a search
func (s *SearchService) Hits(n int) *SearchService {
	s.hits = n
	return s
}

// Query sets the main query input for a search
func (s *SearchService) Query(query string) *SearchService {
	s.query = query
	return s
}

// Fields selects the required fields to display in the final result
func (s *SearchService) Fields(fields ...string) *SearchService {
	s.selectedFields = strings.Join(fields, ", ")
	return s
}

// Where sets the condition part and the parameters inside the YQL query
func (s *SearchService) Where(where string, params ...interface{}) *SearchService {
	s.whereClause = whereParams{
		where:  where,
		params: params,
	}
	return s
}

// Ranking sets the name of the ranking to use.
func (s *SearchService) Ranking(ranking Ranking) *SearchService {
	s.ranking = ranking
	return s
}

// Sorting sets sorting options
func (s *SearchService) Sorting(sorting Sorting) *SearchService {
	s.sorting = sorting
	return s
}

// Limit sets the number of the hits to return from the result set
func (s *SearchService) Limit(limit int) *SearchService {
	s.limit = limit
	return s
}

// Offset sets the index of the first hit to return from the result set
func (s *SearchService) Offset(offset int) *SearchService {
	s.offset = offset
	return s
}

// OrderBy sets the field to order by. isDesc is a flag to determine the
// orientation of the order. ASC is the default value
func (s *SearchService) OrderBy(field string, isDesc bool) *SearchService {
	s.orderBy = field
	s.isDesc = isDesc
	return s
}

// RankingFeatureQuery adds features to the search query.
func (s *SearchService) RankingFeatureQuery(features RankingFeaturesQuery) *SearchService {
	s.rankingFeaturesQuery = features
	return s
}

// Presentation sets the presentation which can be any of the following keys:
// presentation.bolding. presentation.format, presentation.summary, presentation.template,
// presentation.timing. See https://docs.vespa.ai/en/reference/query-api-reference.html#presentation
// for options
func (s *SearchService) Presentation(key string, value interface{}) *SearchService {
	item := Presentation{
		key:   key,
		value: value,
	}
	s.presentation = append(s.presentation, item)
	return s
}

// Generate the YQL query
func (s *SearchService) buildQuery() {
	var setFields, setWhere, setLimit, setOffset, setOrderBy string
	// var idx int

	if s.selectedFields != "" {
		setFields = s.selectedFields
	} else {
		setFields = "*"
	}

	setWhere = s.prepareWhere()

	if s.offset > 0 {
		setOffset = fmt.Sprintf(" offset %d", s.offset)
	}

	if s.limit > 0 {
		setLimit = fmt.Sprintf(" limit %d", s.offset+s.limit)
	}

	if s.orderBy != "" {
		setOrderBy = fmt.Sprintf(" order by %s", s.orderBy)

		if s.isDesc {
			setOrderBy = fmt.Sprintf(" order by %s desc", s.orderBy)
		}
	}

	if s.searchType == "" { // use default when empty.
		s.searchType = "all"
	}

	// Concatenate all the components to create the YQL query
	yql := fmt.Sprintf("select %s from %s%s%s%s%s;", setFields, s.scheme, setWhere, setOrderBy, setLimit, setOffset)

	s.searchRequest = SearchRequest{
		Query:   s.query,
		YQL:     yql,
		Type:    s.searchType,
		Hits:    s.hits,
		Ranking: s.ranking.Profile,
	}
}

func (s *SearchService) prepareWhere() string {

	var setWhere string
	var idx int
	if s.whereClause.where != "" || s.query != "" {
		setWhere = s.whereClause.where

		for _, v := range []byte(setWhere) {
			if v == '?' && len(s.whereClause.params) > idx {
				setWhere = SearchReplace(setWhere, s.whereClause.params[idx])
				idx++
			}
		}

		switch {
		case s.query != "" && s.whereClause.where != "":
			setWhere = fmt.Sprintf(" where %s AND %s", "userQuery()", setWhere)
		case s.query != "":
			setWhere = fmt.Sprintf(" where %s", "userQuery()")
		default:
			setWhere = fmt.Sprintf(" where %s", setWhere)
		}

	}

	return setWhere
}

// SearchReplace replaces ? sign with the correct format depending on the input type
func SearchReplace(where string, in interface{}) string {
	switch t := in.(type) {
	case int, int32:
		where = strings.Replace(where, "?", fmt.Sprintf("%d", in), 1)
	case int64:
		where = strings.Replace(where, "?", fmt.Sprintf("'%d'", in), 1)
	case float32, float64:
		where = strings.Replace(where, "?", fmt.Sprintf("%.2f", in), 1)
	case string:
		where = strings.Replace(where, "?", fmt.Sprintf("%q", in), 1)
	case bool:
		where = strings.Replace(where, "?", fmt.Sprintf("%t", in), 1)
	case []int, []int32, []int64, []float32, []float64, []string, []bool:
		params := anythingToSlice(in)
		values := SearchReplaceArray(params)
		where = strings.Replace(where, "?", values, 1)
	case []WeightedElement:
		var values []string
		for _, item := range t {
			values = append(values, fmt.Sprintf(`"%s":%d`, item.Index, item.Value))
		}
		strValues := strings.Join(values, ",")

		where = strings.Replace(where, "?", fmt.Sprintf(`{%s}`, strValues), 1)
	case WeakAnd:
		where = strings.Replace(where, "?", t.Source(), 1)
	default:
		fmt.Print("Different type", t)
		where = strings.Replace(where, "?", fmt.Sprintf("%v", in), 1)
	}

	return where
}

// SearchReplaceArray handles cases where the params is an array object.
func SearchReplaceArray(params []interface{}) string {
	arr := make([]string, 0, len(params))
	for _, param := range params {
		arr = append(arr, SearchReplace("?", param))
	}

	return strings.Join(arr, ",")
}

func anythingToSlice(a interface{}) []interface{} {
	v := reflect.ValueOf(a)
	switch v.Kind() {
	case reflect.Slice, reflect.Array:
		result := make([]interface{}, v.Len())
		for i := 0; i < v.Len(); i++ {
			result[i] = v.Index(i).Interface()
		}
		return result
	default:
		panic("not supported")
	}
}

// Validate checks if the operation is valid.
func (s *SearchService) Validate() error {
	var invalid []string

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

func (s *SearchService) buildURL() (method, path string, parameters url.Values) {

	method = http.MethodPost
	path = "/search/"

	params := url.Values{}
	if s.timeout != "" {
		params.Set("timeout", s.timeout)
	}

	if s.ranking.Profile != "" {
		params.Set("ranking", s.ranking.Profile)
	}

	if s.ranking.SoftTimeout != nil {

		if !s.ranking.SoftTimeout.Enabled {
			params.Set("ranking.softtimeout.enable", "false")
		}
	}

	if s.sorting.Degrading != nil {
		if !*s.sorting.Degrading {
			params.Set("sorting.degrading", "false")
		}
	}

	for _, p := range s.presentation {
		params.Set(p.key, fmt.Sprintf("%v", p.value))
	}

	if len(s.rankingFeaturesQuery.Values) > 0 {
		for index, value := range s.rankingFeaturesQuery.Values {
			params.Set(fmt.Sprintf("ranking.features.query(%s)", index), fmt.Sprintf("%v", value))
		}
	}

	return method, path, params

}

// Source returns the body of the request we will make to create job
// endpoint
func (s *SearchService) Source() interface{} {
	s.buildQuery()

	return s.searchRequest
}

// Do executes the search operation. Returns a SearchResponse or an error.
func (s *SearchService) Do(ctx context.Context) (*SearchResponse, error) {

	if err := s.Validate(); err != nil {
		return nil, err
	}

	// Get URL for request
	method, path, params := s.buildURL()

	data := s.Source()

	pr := PerformRequest{
		Method:  method,
		Path:    path,
		Params:  params,
		Body:    data,
		Headers: s.headers,
	}

	// Get HTTP response
	res, err := s.client.PerformRequest(ctx, pr)

	if err != nil {
		return nil, err
	}
	defer func() {
		err := res.Body.Close()
		if err != nil {
			log.Print(err)
		}
	}()

	var sr SearchResponse
	sr.Status = res.StatusCode

	err = json.NewDecoder(res.Body).Decode(&sr)

	return &sr, err

}

// SearchResponse is the result of executing a YQL query in Vespa.
type SearchResponse struct {
	Status int  `json:"status"`
	Root   Root `json:"root,omitempty"`
}

// Root is the tree of returned data
type Root struct {
	ID        string    `json:"id"`
	Relevance Relevance `json:"relevance,omitempty"`
	Fields    Fields    `json:"fields,omitempty"`
	Coverage  Coverage  `json:"coverage,omitempty"`
	Errors    []Error   `json:"errors,omitempty"`
	Children  []Child   `json:"children,omitempty"`
}

// Fields attribute contains the number of hits
type Fields struct {
	TotalCount int `json:"totalCount,omitempty"`
}

// Coverage attribute is the metadata about how much of the total corpus has been scanned to return the given documents.
type Coverage struct {
	Coverage    int  `json:"coverage,omitempty"`
	Documents   int  `json:"documents,omitempty"`
	Full        bool `json:"full,omitempty"`
	Nodes       int  `json:"nodes,omitempty"`
	Results     int  `json:"results,omitempty"`
	ResultsFull int  `json:"resultsFull,omitempty"`
}

// Child attribute is the array of JSON objects with exactly the same structure as root.
type Child struct {
	ID        string          `json:"id,omitempty"`
	Relevance Relevance       `json:"relevance,omitempty"`
	Source    string          `json:"source,omitempty"`
	Fields    json.RawMessage `json:"fields,omitempty"`
}

// Error represents an error returned by Vespa.
type Error struct {
	Code       int    `json:"code,omitempty"`
	Message    string `json:"message,omitempty"`
	Source     string `json:"source,omitempty"`
	StackTrace string `json:"stackTrace,omitempty"`
	Summary    string `json:"summary,omitempty"`
	Transient  string `json:"transient,omitempty"`
}
