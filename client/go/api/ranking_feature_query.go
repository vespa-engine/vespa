package vespa

// RankingFeaturesQuery represents a Vespa's Ranking Feature Query
type RankingFeaturesQuery struct {
	Values map[string]interface{}
}

// NewRankingFeaturesQuery creates a new *RankingFeaturesQuery
func NewRankingFeaturesQuery() *RankingFeaturesQuery {
	values := make(map[string]interface{})
	return &RankingFeaturesQuery{Values: values}
}

// Set adds a new RankingFeatures entry
func (rfq *RankingFeaturesQuery) Set(name string, value interface{}) {
	rfq.Values[name] = value
}
