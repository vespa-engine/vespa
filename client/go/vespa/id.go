// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document ids
// Author: bratseth

package vespa

import (
	"errors"
	"strings"
)

func IdToURLPath(documentId string) (string, error) {
	parts := strings.Split(documentId, ":")
	formatAdvice := "Id should be on the form id:<namespace>:<document-type>:<attribute>?:<local-id-string>"
	if len(parts) < 5 {
		return "", errors.New(formatAdvice)
	}

	scheme := parts[0]
	namespace := parts[1]
	documentType := parts[2]
	attribute := parts[3]
	localId := strings.Join(parts[4:], "")

	var group string
	var number string
	if strings.HasPrefix(attribute, "g=") {
		group = attribute[2:]
	} else if strings.HasPrefix(attribute, "n=") {
		number = attribute[2:]
	} else if attribute != "" {
		return "", errors.New(formatAdvice + ": Attribute must be g=<string> or n=<integer>")
	}

	if scheme != "id" {
		return "", errors.New(formatAdvice)
	}

	var attributeAsPath string
	if group != "" {
		attributeAsPath = "group/" + group + "/"
	} else if number != "" {
		attributeAsPath = "number/" + number + "/"
	}
	return namespace + "/" + documentType + "/" + attributeAsPath + "docid/" + localId, nil
}
