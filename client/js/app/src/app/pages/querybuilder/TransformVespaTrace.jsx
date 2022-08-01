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
  //let data = output['data'][0]['spans'];
  processes = output['data'][0]['processes'];
  processes.p0 = { serviceName: 'Query', tags: [] };
  let temp = trace['trace']['children'];
  let spans = findChildren(temp);
  traceStartTimestamp = findTraceStartTime(spans);
  topSpanId = genRanHex(16);
  //let topSpanFirstHalf = createNewSpan(traceStartTimestamp);
  //data.push(topSpanFirstHalf);

  const retrieved = findLogsAndChildren(spans);
  const logs = retrieved['logs'];
  const children = retrieved['children'];
  traverseLogs(logs);
  createChildren(children);

  return output;
}

function findLogsAndChildren(spans) {
  let logs = [];
  let children = [];
  let data = output['data'][0]['spans'];
  //let hitQuery = false;
  //let topSpanSecondHalf = createNewSpan();
  //let secondHalfDuration = 0;
  //output['data'][0]['spans'].push(topSpanSecondHalf);
  //let firstHitSecondHalf = true;
  for (let i = 0; i < spans.length - 1; i++) {
    if (spans[i].hasOwnProperty('children')) {
      //firstHitSecondHalf = true;
      //topSpanSecondHalf = createNewSpan();
      //output['data'][0]['spans'].push(topSpanSecondHalf);
      let log = [];
      for (let x of spans[i]['children']) {
        if (Array.isArray(x['message'])) {
          if (log.length > 0) {
            // finished moving down the search chain
            // create a new array for holding the logs that represent moving up the search chain
            logs.push(log);
            log = [];
          }
          //hitQuery = true;
          children.push(x['message']);
        } else {
          // only add logs with a timestamp
          if (x.hasOwnProperty('timestamp')) {
            log.push(x);
          }
        }
      }
      logs.push(log);
    } else if (
      spans[i].hasOwnProperty('message') &&
      spans[i].hasOwnProperty('timestamp')
    ) {
      let span = createNewSpan(0, 0, 'p0', spans[i]['message'])[0];
      data.push(span);
      span['startTime'] = traceStartTimestamp + spans[i]['timestamp'];
      let duration;
      if (i === spans.length - 1) {
        duration = 1;
      } else {
        duration = spans[i + 1]['timestamp'] - spans[i]['timestamp'];
        duration = duration === 0 ? 1 : duration;
      }
      span['duration'] = duration;
      // if (hitQuery) {
      //   if (firstHitSecondHalf) {
      //     secondHalfDuration = span['timestamp'] * 1000;
      //     topSpanSecondHalf['startTime'] =
      //       traceStartTimestamp + secondHalfDuration;
      //     firstHitSecondHalf = false;
      //   }
      //   topSpanSecondHalf['duration'] =
      //     span['timestamp'] * 1000 - secondHalfDuration;
      //   topSpanSecondHalf['logs'].push({
      //     timestamp: traceStartTimestamp + span['timestamp'] * 1000,
      //     fields: [{ key: 'message', type: 'string', value: span['message'] }],
      //   });
      // } else {
      //   topSpanFirstHalf['duration'] = span['timestamp'] * 1000;
      //   topSpanFirstHalf['logs'].push({
      //     timestamp: traceStartTimestamp + span['timestamp'] * 1000,
      //     fields: [{ key: 'message', type: 'string', value: span['message'] }],
      //   });
      // }
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
    if (logDuration === 0) {
      logDuration = 1;
    }
    let spanID = genRanHex(16);
    if (first) {
      parentID = spanID;
      first = false;
    }
    let temp = createNewSpan(
      logStartTimestamp,
      logDuration,
      'p0',
      'test'
      //[{ refType: 'CHILD_OF', traceID: traceID, spanID: topSpanId }]
    );
    let childSpan = temp[0];
    let childSpanID = temp[1];
    data.push(childSpan);
    for (let i = 0; i < log.length - 1; i++) {
      if (log[i].hasOwnProperty('message')) {
        let logPointStart = traceStartTimestamp + log[i]['timestamp'] * 1000;
        let logPointDuration;
        if (i > log.length - 1) {
          logPointDuration = 1;
        } else {
          logPointDuration =
            (log[i + 1]['timestamp'] - log[i]['timestamp']) * 1000;
          logPointDuration = logPointDuration === 0 ? 1 : logPointDuration;
        }
        let logSpan = createNewSpan(
          logPointStart,
          logPointDuration,
          'p0',
          log[i]['message'],
          [{ refType: 'CHILD_OF', traceID: traceID, spanID: childSpanID }]
        )[0];
        data.push(logSpan);
        // childSpan['logs'].push({
        //   timestamp: traceStartTimestamp + log[i]['timestamp'] * 1000,
        //   fields: [
        //     { key: 'message', type: 'string', value: log[i]['message'] },
        //   ],
        // });
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

//TODO: remove if not needed later
function findDuration(spans) {
  let notFound = true;
  let duration = 0;
  let i = spans.length - 1;
  while (notFound && i >= 0) {
    if (spans[i].hasOwnProperty('timestamp')) {
      duration = spans[i]['timestamp'];
      notFound = false;
    } else {
      i--;
    }
  }
  return duration;
}

function createNewSpan(
  startTime = 0,
  duration = 0,
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
  return [newSpan, spanID];
}
