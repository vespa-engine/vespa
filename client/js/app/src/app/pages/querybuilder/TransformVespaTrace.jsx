// Generates a random hex string of size "size"
const genRanHex = (size) =>
  [...Array(size)]
    .map(() => Math.floor(Math.random() * 16).toString(16))
    .join('');

const traceID = genRanHex(32);
const output = { data: [{ traceID: traceID, spans: [], processes: {} }] };
const processes = output['data'][0]['processes'];
const processMap = new Map();
const data = output['data'][0]['spans'];
let traceStartTimestamp = 0;
let processID = 1;

export default function transform(trace) {
  let temp = trace['trace']['children'];
  let message = temp[0]['message'].split(' ');
  processes.p0 = { serviceName: message[3], tags: [] };
  let spans = digDownInTrace(temp);
  traceStartTimestamp = findTraceStartTime(spans);
  let firstSpan = createNewSpan(traceStartTimestamp, 0, 'p0', message[6]);
  const totalDuration = findTotalDuration(spans);
  firstSpan['duration'] = totalDuration;
  data.push(firstSpan);

  traverseSpans(spans, firstSpan);
  return output;
}

function traverseChildren(span, parent) {
  let logSpan;
  if (span.hasOwnProperty('children')) {
    let duration =
      (span['children'][span['children'].length - 1]['timestamp'] -
        span['children'][0]['timestamp']) *
      1000;
    if (isNaN(duration) || duration <= 0) {
      duration = 1;
    }
    parent['duration'] = duration;
    for (let i = 0; i < span['children'].length; i++) {
      let x = span['children'][i];
      if (x.hasOwnProperty('children')) {
        // Create a new parent span so that the timeline for the spans are correct
        let message = findProcessName(parent['operationName']);
        let processKey = message === '' ? 'p0' : getProcess(message);
        logSpan = createNewSpan(
          traceStartTimestamp + x['timestamp'] * 1000,
          duration,
          processKey,
          parent['operationName'],
          [{ refType: 'CHILD_OF', traceID: traceID, spanID: parent['spanID'] }]
        );
        data.push(logSpan);
        traverseChildren(x, logSpan);
      } else if (Array.isArray(x['message'])) {
        createProtonSpans(x['message'], parent['spanID']);
      } else if (x.hasOwnProperty('message') && x.hasOwnProperty('timestamp')) {
        // only add logs with a timestamp
        let logPointDuration;
        if (i >= span['children'].length - 1) {
          logPointDuration = 1;
        } else {
          logPointDuration =
            findDuration(span['children'], i) - x['timestamp'] * 1000;
        }
        if (isNaN(logPointDuration) || logPointDuration <= 0) {
          logPointDuration = 1;
        }
        addLogSpan(data, x, logPointDuration, parent);
      }
    }
  }
}

function traverseSpans(spans, firstSpan) {
  for (let i = 0; i < spans.length; i++) {
    if (spans[i].hasOwnProperty('children')) {
      traverseChildren(spans[i], data[data.length - 1]);
    } else if (
      spans[i].hasOwnProperty('message') &&
      spans[i].hasOwnProperty('timestamp')
    ) {
      let duration;
      if (i >= spans.length - 1) {
        duration = 1;
      } else {
        duration = findDuration(spans, i) - spans[i]['timestamp'] * 1000;
      }
      if (isNaN(duration) || duration <= 0) {
        duration = 1;
      }
      addLogSpan(data, spans[i], duration, firstSpan);
    }
  }
}

function addLogSpan(data, span, duration, parent) {
  let logPointStart = traceStartTimestamp + span['timestamp'] * 1000;
  let message = findProcessName(span['message']);
  let processKey = message === '' ? 'p0' : getProcess(message);
  let logSpan = createNewSpan(
    logPointStart,
    duration,
    processKey,
    span['message'],
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

function createProtonSpans(children, parentID) {
  let child = children[0];
  let processKey = genRanHex(5);
  processes[processKey] = { serviceName: 'Proton:' + genRanHex(3), tags: [] };
  let startTimestamp = Date.parse(child['start_time']) * 1000;
  let newSpan = createNewSpan(
    startTimestamp,
    child['duration_ms'] * 1000,
    processKey,
    'Search Dispatch',
    [{ refType: 'CHILD_OF', traceID: traceID, spanID: parentID }]
  );
  data.push(newSpan);
  // eslint-disable-next-line no-prototype-builtins
  if (!child.hasOwnProperty('traces')) {
    return;
  }
  let traces = child['traces'];
  for (let k = 0; k < traces.length; k++) {
    let trace = traces[k];
    let traceTimestamp = trace['timestamp_ms'];
    let events;
    let firstEvent;
    let duration;
    processKey = getProcessID();
    processes[processKey] = { serviceName: trace['tag'], tags: [] };
    if (trace['tag'] === 'query_execution') {
      traverseQueryExecution(
        trace,
        traceTimestamp,
        startTimestamp,
        processKey,
        newSpan['spanID']
      );
    } else {
      if (trace['tag'] === 'query_execution_plan') {
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
      let childSpan = createNewSpan(
        startTimestamp + firstEvent['timestamp_ms'] * 1000,
        duration,
        processKey,
        trace['tag'],
        [{ refType: 'CHILD_OF', traceID: traceID, spanID: newSpan['spanID'] }]
      );
      data.push(childSpan);
      traverseEvents(events, childSpan, startTimestamp, traceTimestamp);
    }
  }
}

// the query execution tag might have several threads
function traverseQueryExecution(
  trace,
  traceTimestamp,
  startTimestamp,
  processKey,
  spanID
) {
  let threads = trace['threads'];
  for (let i = 0; i < threads.length; i++) {
    let events = threads[i]['traces'];
    let firstEvent = events[0];
    let duration = (traceTimestamp - firstEvent['timestamp_ms']) * 1000;
    let span = createNewSpan(
      startTimestamp + firstEvent['timestamp_ms'] * 1000,
      duration,
      processKey,
      trace['tag'],
      [{ refType: 'CHILD_OF', traceID: traceID, spanID: spanID }]
    );
    data.push(span);
    traverseEvents(events, span, startTimestamp, traceTimestamp);
  }
}

function traverseEvents(events, parent, startTimestamp, traceTimestamp) {
  for (let i = 0; i < events.length; i++) {
    let event = events[i];
    let eventStart = event['timestamp_ms'];
    let operationName;
    let duration;
    if (event.hasOwnProperty('event')) {
      operationName = event['event'];
      if (
        operationName === 'Complete query setup' ||
        operationName === 'MatchThread::run Done'
      ) {
        duration = (traceTimestamp - eventStart) * 1000;
      } else {
        duration = (events[i + 1]['timestamp_ms'] - eventStart) * 1000;
      }
    } else {
      operationName = event['tag'];
      duration = (events[i + 1]['timestamp_ms'] - eventStart) * 1000;
    }
    let eventSpan = createNewSpan(
      startTimestamp + eventStart * 1000,
      duration,
      parent['processID'],
      operationName,
      [
        {
          refType: 'CHILD_OF',
          traceID: traceID,
          spanID: parent['spanID'],
        },
      ]
    );
    data.push(eventSpan);
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

function getProcess(key) {
  if (processMap.has(key)) {
    return processMap.get(key);
  } else {
    let id = 'p' + getProcessID();
    processes[id] = { serviceName: key, tags: [] };
    processMap.set(key, id);
    return id;
  }
}

function getProcessID() {
  processID += 1;
  return processID;
}

// find a name to use for a process using the operationName from a span
function findProcessName(string) {
  let regex = /(?:[a-z]+\.)+[a-zA-Z]+/gm;
  let match = string.match(regex);
  if (match != null && match.length > 0) {
    let temp = match[0];
    temp = temp.split('.');
    return temp[temp.length - 1];
  } else {
    return '';
  }
}
