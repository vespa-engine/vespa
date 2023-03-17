package feed

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"net/url"
	"strconv"
	"strings"
)

var asciiSpace = [256]uint8{'\t': 1, '\n': 1, '\v': 1, '\f': 1, '\r': 1, ' ': 1}

type Operation int

const (
	OperationPut = iota
	OperationUpdate
	OperationRemove
)

type Document struct {
	Id        DocumentId
	Operation Operation

	IdString  string          `json:"id"`
	PutId     string          `json:"put"`
	UpdateId  string          `json:"update"`
	RemoveId  string          `json:"remove"`
	Condition string          `json:"condition"`
	Create    bool            `json:"create"`
	Fields    json.RawMessage `json:"fields"`
}

// FeedURL returns the HTTP method and URL to use for feeding this document.
func (d Document) FeedURL(baseurl string, queryParams url.Values) (string, *url.URL, error) {
	u, err := url.Parse(baseurl)
	if err != nil {
		return "", nil, fmt.Errorf("invalid base url: %w", err)
	}
	httpMethod := ""
	switch d.Operation {
	case OperationPut:
		httpMethod = "POST"
	case OperationUpdate:
		httpMethod = "PUT"
	case OperationRemove:
		httpMethod = "DELETE"
	}
	if d.Condition != "" {
		queryParams.Set("condition", d.Condition)
	}
	if d.Create {
		queryParams.Set("create", "true")
	}
	u.Path = d.Id.URLPath()
	u.RawQuery = queryParams.Encode()
	return httpMethod, u, nil
}

// Body returns the body part of this document, suitable for sending to the /document/v1 API.
func (d Document) Body() []byte {
	jsonObject := `{"fields":`
	body := make([]byte, 0, len(jsonObject)+len(d.Fields)+1)
	body = append(body, []byte(jsonObject)...)
	body = append(body, d.Fields...)
	body = append(body, byte('}'))
	return body
}

// Decoder decodes documents from a JSON structure which is either an array of objects, or objects separated by newline.
type Decoder struct {
	buf   *bufio.Reader
	dec   *json.Decoder
	array bool
	jsonl bool
}

func (d *Decoder) guessMode() error {
	for !d.array && !d.jsonl {
		b, err := d.buf.ReadByte()
		if err != nil {
			return err
		}
		// Skip leading whitespace
		if b < 0x80 && asciiSpace[b] != 0 {
			continue
		}
		switch rune(b) {
		case '{':
			d.jsonl = true
		case '[':
			d.array = true
		default:
			return fmt.Errorf("unexpected token: %q", string(b))
		}
		if err := d.buf.UnreadByte(); err != nil {
			return err
		}
		if d.array {
			// prepare for decoding objects inside array
			if _, err := d.dec.Token(); err != nil {
				return err
			}
		}
	}
	return nil
}

func (d *Decoder) readCloseToken() error {
	if !d.array {
		return nil
	}
	_, err := d.dec.Token()
	return err
}

func (d *Decoder) Decode() (Document, error) {
	doc, err := d.decode()
	if err != nil && err != io.EOF {
		return Document{}, fmt.Errorf("invalid json at byte offset %d: %w", d.dec.InputOffset(), err)
	}
	return doc, err
}

func (d *Decoder) decode() (Document, error) {
	if err := d.guessMode(); err != nil {
		return Document{}, err
	}
	if !d.dec.More() {
		err := io.EOF
		if tokenErr := d.readCloseToken(); tokenErr != nil {
			err = tokenErr
		}
		return Document{}, err
	}
	doc := Document{}
	if err := d.dec.Decode(&doc); err != nil {
		return Document{}, err
	}
	if err := parseDocument(&doc); err != nil {
		return Document{}, err
	}
	return doc, nil
}

func parseDocument(d *Document) error {
	id := ""
	if d.IdString != "" {
		d.Operation = OperationPut
		id = d.IdString
	} else if d.PutId != "" {
		d.Operation = OperationPut
		id = d.PutId
	} else if d.UpdateId != "" {
		d.Operation = OperationUpdate
		id = d.UpdateId
	} else if d.RemoveId != "" {
		d.Operation = OperationRemove
		id = d.RemoveId
	} else {
		return fmt.Errorf("invalid document: missing operation: %v", d)
	}
	docId, err := ParseDocumentId(id)
	if err != nil {
		return err
	}
	d.Id = docId
	return nil
}

func NewDecoder(r io.Reader) *Decoder {
	buf := bufio.NewReader(r)
	return &Decoder{
		buf: buf,
		dec: json.NewDecoder(buf),
	}
}

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

// URLPaths returns the feeding path for this document.
func (d DocumentId) URLPath() string {
	var sb strings.Builder
	sb.WriteString("/document/v1/")
	sb.WriteString(url.PathEscape(d.Namespace))
	sb.WriteString("/")
	sb.WriteString(url.PathEscape(d.Type))
	if d.Number != nil {
		sb.WriteString("/number/")
		n := uint64(*d.Number)
		sb.WriteString(strconv.FormatUint(n, 10))
	} else if d.Group != "" {
		sb.WriteString("/group/")
		sb.WriteString(url.PathEscape(d.Group))
	} else {
		sb.WriteString("/docid")
	}
	sb.WriteString("/")
	sb.WriteString(url.PathEscape(d.UserSpecific))
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
