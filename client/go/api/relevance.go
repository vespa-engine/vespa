package vespa

import (
	"encoding/json"
)

// Relevance is the type to hold relevance data, incluiding handling NaN cases.
type Relevance float64

// UnmarshalJSON decodes a json entry into thje right Relevance Implementation
func (r *Relevance) UnmarshalJSON(b []byte) error {
	var data interface{}

	err := json.Unmarshal(b, &data)
	if err != nil {
		return err
	}

	switch v := data.(type) {
	case float64:
		*r = Relevance(v)
	case string:
		*r = 0
	}

	return nil
}
