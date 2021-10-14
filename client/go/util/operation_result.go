// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A struct containing the result of an operation
// Author: bratseth

package util

type OperationResult struct {
	Success bool
	Message string // Mandatory message
	Detail  string // Optional detail message
	Payload string // Optional payload - may be present whether or not the operation was success
}

func Success(message string) OperationResult {
	return OperationResult{Success: true, Message: message}
}

func SuccessWithPayload(message string, payload string) OperationResult {
	return OperationResult{Success: true, Message: message, Payload: payload}
}

func Failure(message string) OperationResult {
	return OperationResult{Success: false, Message: message}
}

func FailureWithPayload(message string, payload string) OperationResult {
	return OperationResult{Success: false, Message: message, Payload: payload}
}

func FailureWithDetail(message string, detail string) OperationResult {
	return OperationResult{Success: false, Message: message, Detail: detail}
}
