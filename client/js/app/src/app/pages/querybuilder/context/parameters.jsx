function param(name, type, props = {}) {
  let children;
  if (Array.isArray(type)) {
    children = Object.fromEntries(type.map((child) => [child.name, child]));
    type = 'Parent';
  }
  return Object.assign({ name, type }, children && { children }, props);
}

// https://docs.vespa.ai/en/reference/query-api-reference.html
export default param('root', [
  param('yql', 'String'),

  // Native Execution Parameters
  param('hits', 'Integer', { min: 0, default: 10 }),
  param('offset', 'Integer', { min: 0, default: 0 }),
  param('queryProfile', 'String', { default: 'default' }),
  param('groupingSessionCache', 'Boolean', { default: true }),
  param('searchChain', 'String', { default: 'default' }),
  param('timeout', 'Float', { min: 0, default: 0.5 }),
  param('noCache', 'Boolean', { default: false }),

  // Query Model
  param('model', [
    param('defaultIndex', 'String', { default: 'default' }),
    param('encoding', 'String', { default: 'utf-8' }),
    param('filter', 'String'),
    param('locale', 'String'),
    param('language', 'String'),
    param('queryString', 'String'),
    param('restrict', 'String'),
    param('searchPath', 'String'),
    param('sources', 'String'),
    param('type', 'String'),
  ]),

  // Ranking
  param('ranking', [
    param('location', 'String'),
    param('features', 'Parent', { children: 'String' }),
    param('listFeatures', 'Boolean', { default: false }),
    param('profile', 'String', { default: 'default' }),
    param('properties', 'String', { children: 'String' }),
    param('softtimeout', [
      param('enable', 'Boolean', { default: true }),
      param('factor', 'Float', { min: 0, max: 1, default: 0.7 }),
    ]),
    param('sorting', 'String'),
    param('freshness', 'String'),
    param('queryCache', 'Boolean', { default: false }),
    param('rerankCount', 'Integer', { min: 0 }),
    param('matching', [
      param('numThreadsPerSearch', 'Integer', { min: 0 }),
      param('minHitsPerThread', 'Integer', { min: 0 }),
      param('numSearchPartitions', 'Integer', { min: 0 }),
      param('termwiseLimit', 'Float', { min: 0, max: 1 }),
      param('postFilterThreshold', 'Float', { min: 0, max: 1 }),
      param('approximateThreshold', 'Float', { min: 0, max: 1 }),
    ]),
    param('matchPhase', [
      param('attribute', 'String'),
      param('maxHits', 'Integer', { min: 0 }),
      param('ascending', 'Boolean'),
      param('diversity', [
        param('attribute', 'String'),
        param('minGroups', 'Integer', { min: 0 }),
      ]),
    ]),
  ]),

  // Grouping
  param('collapsesize', 'Integer', { min: 1, default: 1 }),
  param('collapsefield', 'String'),
  param('collapse', [param('summary', 'String')]),
  param('grouping', [
    param('defaultMaxGroups', 'Integer', { min: -1, default: 10 }),
    param('defaultMaxHits', 'Integer', { min: -1, default: 10 }),
    param('globalMaxGroups', 'Integer', { min: -1, default: 10000 }),
    param('defaultPrecisionFactor', 'Float', { min: 0, default: 2.0 }),
  ]),

  // Presentation
  param('presentation', [
    param('bolding', 'Boolean', { default: true }),
    param('format', 'String', { default: 'default' }),
    param('template', 'String'),
    param('summary', 'String'),
    param('timing', 'Boolean', { default: false }),
  ]),

  // Tracing
  param('trace', [
    param('level', 'Integer', { min: 1 }),
    param('explainLevel', 'Integer', { min: 1 }),
    param('profileDepth', 'Integer', { min: 1 }),
    param('timestamps', 'Boolean', { default: false }),
    param('query', 'Boolean', { default: true }),
  ]),

  // Semantic Rules
  param('rules', [
    param('off', 'Boolean', { default: true }),
    param('rulebase', 'String'),
  ]),
  param('tracelevel', [param('rules', 'Integer', { min: 0 })]),

  // Dispatch
  param('dispatch', [param('topKProbability', 'Float', { min: 0, max: 1 })]),

  // Other
  param('recall', 'String'),
  param('user', 'String'),
  param('hitcountestimate', 'Boolean', { default: false }),
  param('metrics', [param('ignore', 'Boolean', { default: false })]),
  param('weakAnd', [param('replace', 'Boolean', { default: false })]),
  param('wand', [param('hits', 'Integer', { default: 100 })]),
  param('sorting', [param('degrading', 'Boolean', { default: true })]),

  param('streaming', [
    param('userid', 'Integer'),
    param('groupname', 'String'),
    param('selection', 'String'),
    param('priority', 'String'),
    param('maxbucketspervisitor', 'Integer'),
  ]),
]).children;
