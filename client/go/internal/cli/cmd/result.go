package cmd

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
