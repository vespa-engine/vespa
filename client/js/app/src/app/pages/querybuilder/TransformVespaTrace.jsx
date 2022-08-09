let traceID = '';
let processes = {};
let output = {};
let traceStartTimestamp = 0;

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
  let spans = digDownInTrace(temp);
  traceStartTimestamp = findTraceStartTime(spans);
  let firstSpan = createNewSpan(traceStartTimestamp);
  data.push(firstSpan);

  traverseSpans(spans, firstSpan);
  return output;
}

function traverseChildren(span, parent) {
  let data = output['data'][0]['spans'];
  let logSpan;
  if (span.hasOwnProperty('children')) {
    let duration =
      (span['children'][span['children'].length - 1]['timestamp'] -
        span['children'][0]['timestamp']) *
      1000;
    if (isNaN(duration) || duration === 0) {
      duration = 1;
    }
    parent['duration'] = duration;
    for (let i = 0; i < span['children'].length; i++) {
      let x = span['children'][i];
      if (x.hasOwnProperty('children')) {
        // Create a new parent span so that the timeline for the spans are correct
        logSpan = createNewSpan(
          traceStartTimestamp + x['timestamp'] * 1000,
          duration,
          'p0',
          parent['operationName'],
          [{ refType: 'CHILD_OF', traceID: traceID, spanID: parent['spanID'] }]
        );
        data.push(logSpan);
        traverseChildren(x, logSpan);
      } else if (Array.isArray(x['message'])) {
        createChildren(x['message'], parent['spanID']);
      } else {
        // only add logs with a timestamp
        if (x.hasOwnProperty('timestamp')) {
          let logPointStart = traceStartTimestamp + x['timestamp'] * 1000;
          let logPointDuration;
          if (i >= span['children'].length - 1) {
            logPointDuration = 1;
          } else {
            logPointDuration =
              findDuration(span['children'], i) - x['timestamp'] * 1000;
          }
          if (isNaN(logPointDuration) || logPointDuration === 0) {
            logPointDuration = 1;
          }
          let logSpan = createNewSpan(
            logPointStart,
            logPointDuration,
            'p0',
            x['message'],
            [
              {
                refType: 'CHILD_OF',
                traceID: traceID,
                spanID: parent['spanID'],
              },
            ]
          );
          data.push(logSpan);
        }
      }
    }
  }
}

function traverseSpans(spans, firstSpan) {
  let data = output['data'][0]['spans'];
  let totalDuration = findTotalDuration(spans);
  firstSpan['duration'] = totalDuration;
  for (let i = 0; i < spans.length; i++) {
    if (spans[i].hasOwnProperty('children')) {
      traverseChildren(spans[i], data[data.length - 1]);
    } else if (
      spans[i].hasOwnProperty('message') &&
      spans[i].hasOwnProperty('timestamp')
    ) {
      let span = createNewSpan(0, 0, 'p0', spans[i]['message'], [
        {
          refType: 'CHILD_OF',
          traceID: traceID,
          spanID: firstSpan['spanID'],
        },
      ]);
      data.push(span);
      span['startTime'] = traceStartTimestamp + spans[i]['timestamp'] * 1000;
      let duration;
      if (i >= spans.length - 1) {
        duration = 1;
      } else {
        duration = findDuration(spans, i) - spans[i]['timestamp'] * 1000;
      }
      if (isNaN(duration) || duration === 0) {
        duration = 1;
      }
      span['duration'] = duration;
    }
  }
}

function createChildren(children, parentID) {
  let child = children[0];
  let processKey = genRanHex(5);
  processes[processKey] = { serviceName: genRanHex(3), tags: [] };
  let spanID = genRanHex(16);
  let data = output['data'][0]['spans'];
  let startTimestamp = Date.parse(child['start_time']) * 1000;
  let newSpan = {
    traceID: traceID,
    spanID: spanID,
    operationName: 'something',
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

function digDownInTrace(traces) {
  for (let trace of traces) {
    if (trace.hasOwnProperty('children')) {
      return trace['children'];
    }
  }
}

// Get an estimated start time by using the start time of the query and subtracting the current run time
function getTraceStartTime(trace) {
  if (Array.isArray(trace['message'])) {
    let timestamp = Date.parse(trace['message'][0]['start_time']) * 1000;
    let currentTimestamp = trace['timestamp'] * 1000;
    return timestamp - currentTimestamp;
  }
}

function findTraceStartTime(spans) {
  let startTime = 0;
  for (let span of spans) {
    if (span.hasOwnProperty('children')) {
      startTime = findTraceStartTime(span['children']);
    } else if (span.hasOwnProperty('message')) {
      if (Array.isArray(span['message'])) {
        return getTraceStartTime(span);
      }
    }
    if (startTime !== 0) {
      return startTime;
    }
  }
  return startTime;
}

// Finds the total duration of the entire trace
function findTotalDuration(spans) {
  let i = spans.length - 1;
  while (i >= 0) {
    if (spans[i].hasOwnProperty('timestamp')) {
      return spans[i]['timestamp'] * 1000;
    }
    i--;
  }
  return 0;
}

// Finds the duration of a single span
function findDuration(span, i) {
  i = i + 1;
  while (i < span.length) {
    if (span[i].hasOwnProperty('timestamp')) {
      return span[i]['timestamp'] * 1000;
    }
    i++;
  }
  return 0;
}

function createNewSpan(
  startTime = 0,
  duration = 1,
  processID = 'p0',
  operationName = 'Complete',
  references = []
) {
  let spanID = genRanHex(16);
  let newSpan = {
    traceID: traceID,
    spanID: spanID,
    operationName: operationName,
    startTime: startTime,
    duration: duration,
    references: references,
    tags: [],
    logs: [],
    processID: processID,
  };
  return newSpan;
}
