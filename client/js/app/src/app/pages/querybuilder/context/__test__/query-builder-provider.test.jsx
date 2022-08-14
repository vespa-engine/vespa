import {
  ACTION,
  reducer,
} from 'app/pages/querybuilder/context/query-builder-provider';
import parameters from 'app/pages/querybuilder/context/parameters';
import { cloneDeep, omitBy } from 'lodash';

const state = reducer();

test('default state', () => {
  const fixed = { ...state, params: hideTypes(state.params) };
  expect(fixed).toEqual({
    http: {},
    params: {
      children: [{ id: '0', input: '', type: 'yql' }],
    },
    query: {
      input: '{\n    "yql": ""\n}',
    },
    request: {
      body: '{\n    "yql": ""\n}',
      fullUrl: 'http://localhost:8080/search/',
      method: 'POST',
      url: 'http://localhost:8080/search/',
    },
  });
});

test('manipulates inputs', () => {
  function assert(state, queryJson, querySearchParams, params) {
    expect(hideTypes(state.params).children).toEqual(params);
    expect({ ...state.query, input: JSON.parse(state.query.input) }).toEqual(
      queryJson
    );

    const spState = reducer(state, { action: ACTION.SET_METHOD, data: 'GET' });
    expect(hideTypes(spState.params).children).toEqual(params);
    expect(spState.query).toEqual(querySearchParams);
  }

  const s1 = reduce(state, [[ACTION.INPUT_ADD, { type: 'hits' }]]);
  assert(s1, { input: { yql: '', hits: null } }, { input: 'yql=&hits=' }, [
    { id: '0', input: '', type: 'yql' },
    { id: '1', input: '', type: 'hits' },
  ]);

  const s2 = reduce(s1, [
    [ACTION.INPUT_UPDATE, { id: '1', input: '12' }],
    [ACTION.INPUT_ADD, { type: 'ranking' }],
    [ACTION.INPUT_UPDATE, { id: '1', type: 'offset' }],
    [ACTION.INPUT_REMOVE, '0'],
    [ACTION.INPUT_ADD, { id: '2', type: 'location' }],
    [ACTION.INPUT_ADD, { id: '2', type: 'matchPhase' }],
    [ACTION.INPUT_UPDATE, { id: '2.0', input: 'us' }],
  ]);
  assert(
    s2,
    { input: { offset: 12, ranking: { location: 'us', matchPhase: {} } } },
    { input: 'offset=12&ranking.location=us' },
    [
      { id: '1', input: '12', type: 'offset' },
      {
        id: '2',
        input: '',
        type: 'ranking',
        children: [
          { id: '2.0', input: 'us', type: 'location' },
          { id: '2.1', input: '', type: 'matchPhase', children: [] },
        ],
      },
    ]
  );

  assert(
    reduce(s2, [[ACTION.INPUT_UPDATE, { id: '2', type: 'noCache' }]]),
    { input: { offset: 12, noCache: false } },
    { input: 'offset=12&noCache=' },
    [
      { id: '1', input: '12', type: 'offset' },
      { id: '2', input: '', type: 'noCache' },
    ]
  );

  assert(
    reduce(s2, [[ACTION.INPUT_REMOVE, '2']]),
    { input: { offset: 12 } },
    { input: 'offset=12' },
    [{ id: '1', input: '12', type: 'offset' }]
  );
});

test('set query', () => {
  const query = (method, input) =>
    reduce(state, [
      [ACTION.INPUT_REMOVE, '0'],
      [ACTION.SET_METHOD, method],
      [ACTION.SET_QUERY, input],
    ]);
  function assert(inputJson, inputSearchParams, params) {
    const s2 = query('POST', inputJson);
    expect(hideTypes(s2.params).children).toEqual(params);
    expect(s2.query.input).toEqual(inputJson);
    expect(s2.query.error).toBeUndefined();

    if (inputSearchParams == null) return;
    const s3 = query('GET', inputSearchParams);
    expect(hideTypes(s3.params).children).toEqual(params);
    expect(s3.query.input).toEqual(inputSearchParams);
    expect(s3.query.error).toBeUndefined();
  }

  function error(method, input, error) {
    const s = query(method, input);
    expect(s.params.children).toEqual([]);
    expect(s.query.input).toEqual(input);
    expect(s.query.error).toEqual(error);
  }

  assert('{"yql":"abc"}', '?yql=abc', [{ id: '0', input: 'abc', type: 'yql' }]);

  assert(
    '{"hits":12,"ranking":{"location":"us","matchPhase":{"attribute":"[\\"a b\\"]"}},"noCache":true,"offset":""}',
    'hits=12&ranking.location=us&noCache=true&ranking.matchPhase.attribute=%5B%22a+b%22%5D&offset',
    [
      { id: '0', input: '12', type: 'hits' },
      {
        id: '1',
        input: '',
        type: 'ranking',
        children: [
          { id: '1.0', input: 'us', type: 'location' },
          {
            id: '1.1',
            input: '',
            type: 'matchPhase',
            children: [{ id: '1.1.0', input: '["a b"]', type: 'attribute' }],
          },
        ],
      },
      { id: '2', input: 'true', type: 'noCache' },
      { id: '3', input: '', type: 'offset' },
    ]
  );

  assert('{"ranking":{"matchPhase":{}}}', null, [
    {
      id: '0',
      input: '',
      type: 'ranking',
      children: [
        {
          id: '0.0',
          input: '',
          type: 'matchPhase',
          children: [],
        },
      ],
    },
  ]);

  let msg = "Unknown property 'asd' on root level";
  error('POST', '{"asd":123}', msg);
  error('GET', 'asd=123', msg);

  msg = "Unknown property 'asd' under 'matchPhase'";
  error('POST', '{"ranking":{"matchPhase":{"asd":123}}}', msg);
  error('GET', 'ranking.matchPhase.asd=123', msg);

  error('POST', '{"yql":"test}', 'Unexpected end of JSON input');

  msg =
    "Property 'ranking' cannot have value, supported children: features,freshness,listFeatures,location,matchPhase,profile,properties,queryCache,sorting";
  error('POST', '{"ranking":123}', msg);
  error('GET', 'ranking=123', msg);

  error('POST', '{"yql":{}}', "Expected property 'yql' to be String");
});

function hideTypes({ type, children, ...copy }) {
  if (type.name) copy.type = type.name;
  if (children) copy.children = children.map(hideTypes);
  return copy;
}

function reduce(state, operations) {
  return operations.reduce(
    (acc, [action, data]) => reducer(acc, { action, data }),
    state
  );
}
