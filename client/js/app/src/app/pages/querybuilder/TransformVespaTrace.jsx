let traceID = '';
let processes = {};
let output = {};
let traceStartTimestamp = 0;
let topSpanId = '';
let parentID = '';

// Generates a random hex string of size "size"
const genRanHex = (size) =>
  [...Array(size)]
    .map(() => Math.floor(Math.random() * 16).toString(16))
    .join('');

export default function transform(trace) {
  traceID = genRanHex(32);
  output = { data: [{ traceID: traceID, spans: [], processes: {} }] };
  let data = output['data'][0]['spans'];
  processes = output['data'][0]['processes'];
  processes.p0 = { serviceName: 'Query', tags: [] };
  let temp = trace['trace']['children'];
  let spans = findChildren(temp);
  let query = findChildren(spans); // only used for getting the start time
  traceStartTimestamp = getTraceStartTime(query);
  let totalTraceDuration = spans[spans.length - 1]['timestamp'] * 1000;
  topSpanId = genRanHex(16);
  let topSpan = {
    traceID: traceID,
    spanID: topSpanId,
    operationName: 'Complete',
    startTime: traceStartTimestamp,
    duration: totalTraceDuration,
    references: [],
    tags: [],
    logs: [],
    processID: 'p0',
  };
  data.push(topSpan);

  const retrieved = findLogsAndChildren(spans, topSpan);
  const logs = retrieved['logs'];
  const children = retrieved['children'];
  traverseLogs(logs);
  createChildren(children);

  return output;
}

function findLogsAndChildren(spans, topSpan) {
  let logs = [];
  let children = [];
  for (let span of spans) {
    if (span.hasOwnProperty('children')) {
      let log = [];
      for (let x of span['children']) {
        if (Array.isArray(x['message'])) {
          children.push(x['message']);
        } else {
          log.push(x);
        }
      }
      logs.push(log);
    } else if (span.hasOwnProperty('message')) {
      topSpan['logs'].push({
        timestamp: traceStartTimestamp + span['timestamp'] * 1000,
        fields: [{ key: 'message', type: 'string', value: span['message'] }],
      });
    }
  }
  return { logs: logs, children: children };
}

function traverseLogs(logs) {
  let first = true;
  let data = output['data'][0]['spans'];
  for (let log of logs) {
    let logStartTimestamp = traceStartTimestamp + log[0]['timestamp'] * 1000;
    let logDuration =
      (log[log.length - 1]['timestamp'] - log[0]['timestamp']) * 1000;
    let spanID = genRanHex(16);
    if (first) {
      parentID = spanID;
      first = false;
    }
    let childSpan = {
      traceID: traceID,
      spanID: spanID,
      operationName: 'test',
      startTime: logStartTimestamp,
      duration: logDuration,
      references: [
        { refType: 'CHILD_OF', traceID: traceID, spanID: topSpanId },
      ],
      tags: [],
      logs: [],
      processID: 'p0',
    };
    data.push(childSpan);
    for (let logPoint of log) {
      if (logPoint.hasOwnProperty('message')) {
        childSpan['logs'].push({
          timestamp: traceStartTimestamp + logPoint['timestamp'] * 1000,
          fields: [
            { key: 'message', type: 'string', value: logPoint['message'] },
          ],
        });
      }
    }
  }
}

function createChildren(children) {
  for (let i = 0; i < children.length; i++) {
    let child = children[i][0];
    let processKey = `p${i + 1}`;
    processes[processKey] = { serviceName: `Span${i}`, tags: [] };
    let spanID = genRanHex(16);
    let data = output['data'][0]['spans'];
    let startTimestamp = Date.parse(child['start_time']) * 1000;
    let newSpan = {
      traceID: traceID,
      spanID: spanID,
      operationName: `query${i}`,
      startTime: startTimestamp,
      duration: child['duration_ms'] * 1000,
      references: [{ refType: 'CHILD_OF', traceID: traceID, spanID: parentID }],
      tags: [],
      logs: [],
      processID: processKey,
    };
    data.push(newSpan);
    let traces = child['traces'];
    for (let k = 0; k < traces.length; k++) {
      let trace = traces[k];
      let traceTimestamp = trace['timestamp_ms'];
      let events;
      let firstEvent;
      let duration;
      if (trace['tag'] === 'query_execution') {
        events = trace['threads'][0]['traces'];
        firstEvent = events[0];
        duration = (traceTimestamp - firstEvent['timestamp_ms']) * 1000;
      } else if (trace['tag'] === 'query_execution_plan') {
        events = [];
        let nextTrace = traces[k + 1];
        firstEvent = trace;
        // query execution plan has no events, duration must therefore be found using the next trace
        if (nextTrace['tag'] === 'query_execution') {
          duration =
            (nextTrace['threads'][0]['traces'][0]['timestamp_ms'] -
              traceTimestamp) *
            1000;
        } else {
          duration = (nextTrace['timestamp_ms'] - traceTimestamp) * 1000;
        }
      } else {
        events = trace['traces'];
        firstEvent = events[0];
        duration = (traceTimestamp - firstEvent['timestamp_ms']) * 1000;
      }
      let childSpanID = genRanHex(16);
      let childSpan = {
        traceID: traceID,
        spanID: childSpanID,
        operationName: trace['tag'],
        startTime: startTimestamp + firstEvent['timestamp_ms'] * 1000,
        duration: duration,
        references: [{ refType: 'CHILD_OF', traceID: traceID, spanID: spanID }],
        tags: [],
        logs: [],
        processID: processKey,
      };
      data.push(childSpan);
      if (events.length > 0) {
        for (let j = 0; j < events.length; j++) {
          let event = events[j];
          let eventID = genRanHex(16);
          let eventStart = event['timestamp_ms'];
          let operationName;
          if (event.hasOwnProperty('event')) {
            operationName = event['event'];
            if (
              operationName === 'Complete query setup' ||
              operationName === 'MatchThread::run Done'
            ) {
              duration = (traceTimestamp - eventStart) * 1000;
            } else {
              duration = (events[j + 1]['timestamp_ms'] - eventStart) * 1000;
            }
          } else {
            operationName = event['tag'];
            duration = (events[j + 1]['timestamp_ms'] - eventStart) * 1000;
          }
          let eventSpan = {
            traceID: traceID,
            spanID: eventID,
            operationName: operationName,
            startTime: startTimestamp + eventStart * 1000,
            duration: duration,
            references: [
              { refType: 'CHILD_OF', traceID: traceID, spanID: childSpanID },
            ],
            tags: [],
            logs: [],
            processID: processKey,
          };
          data.push(eventSpan);
        }
      }
    }
  }
}

function findChildren(traces) {
  for (let trace of traces) {
    if (trace.hasOwnProperty('children')) {
      return trace['children'];
    }
  }
}

// Get an estimated start time by using the start time of the query and subtracting the current run time
function getTraceStartTime(trace) {
  for (let x of trace) {
    if (Array.isArray(x['message'])) {
      let timestamp = Date.parse(x['message'][0]['start_time']) * 1000;
      let currentTimestamp = x['timestamp'] * 1000;
      return timestamp - currentTimestamp;
    }
  }
}
