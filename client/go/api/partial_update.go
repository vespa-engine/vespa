package vespa

// PartialUpdateField allow us to use the function Fields() with multiple
// parameters
type PartialUpdateField struct {
	Key   string
	Value interface{}
}

// PartialUpdateItem is the representation Vespa uses to declare a field update
type PartialUpdateItem struct {
	Assign interface{} `json:"assign"`
}

// NewPartialUpdateField returns a new *NewPartialUpdateField
func NewPartialUpdateField(key string, value interface{}) *PartialUpdateField {
	return &PartialUpdateField{Key: key, Value: value}
}
