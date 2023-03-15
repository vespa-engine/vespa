package feed

import (
	"fmt"
	"strconv"
	"strings"
)

// A Vespa document ID.
type DocumentId struct {
	Type         string
	Namespace    string
	Number       *int64
	Group        string
	UserSpecific string
}

func (d DocumentId) Equal(o DocumentId) bool {
	return d.Type == o.Type &&
		d.Namespace == o.Namespace &&
		((d.Number == nil && o.Number == nil) || *d.Number == *o.Number) &&
		d.Group == o.Group &&
		d.UserSpecific == o.UserSpecific
}

func (d DocumentId) String() string {
	var sb strings.Builder
	sb.WriteString("id:")
	sb.WriteString(d.Namespace)
	sb.WriteString(":")
	sb.WriteString(d.Type)
	sb.WriteString(":")
	if d.Number != nil {
		sb.WriteString("n=")
		sb.WriteString(strconv.FormatInt(*d.Number, 10))
	} else if d.Group != "" {
		sb.WriteString("g=")
		sb.WriteString(d.Group)
	}
	sb.WriteString(":")
	sb.WriteString(d.UserSpecific)
	return sb.String()
}

func parseError(value string) error {
	return fmt.Errorf("invalid document: expected id:<namespace>:<document-type>:[n=<number>|g=<group>]:<user-specific>, got %q", value)
}

// ParseDocumentId parses a serialized document ID string.
func ParseDocumentId(serialized string) (DocumentId, error) {
	parts := strings.SplitN(serialized, ":", 4)
	if len(parts) < 4 || parts[0] != "id" {
		return DocumentId{}, parseError(serialized)
	}
	namespace := parts[1]
	if namespace == "" {
		return DocumentId{}, parseError(serialized)
	}
	docType := parts[2]
	if docType == "" {
		return DocumentId{}, parseError(serialized)
	}
	rest := strings.SplitN(parts[3], ":", 2)
	if len(rest) < 2 {
		return DocumentId{}, parseError(serialized)
	}
	var number *int64
	group := ""
	userSpecific := ""
	for _, part := range rest {
		if number == nil && strings.HasPrefix(part, "n=") {
			n, err := strconv.ParseInt(part[2:], 10, 64)
			if err != nil {
				return DocumentId{}, parseError(serialized)
			}
			number = &n
		} else if group == "" && strings.HasPrefix(part, "g=") {
			group = part[2:]
			if len(group) == 0 {
				return DocumentId{}, parseError(serialized)
			}
		} else {
			userSpecific = part
		}
	}
	if userSpecific == "" {
		return DocumentId{}, parseError(serialized)
	}
	return DocumentId{
		Namespace:    namespace,
		Type:         docType,
		Number:       number,
		Group:        group,
		UserSpecific: userSpecific,
	}, nil
}
