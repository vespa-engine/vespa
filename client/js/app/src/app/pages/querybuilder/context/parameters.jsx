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
  param('hits', 'Integer'),
  param('offset', 'Integer'),
  param('queryProfile', 'String'),
  param('groupingSessionCache', 'Boolean'),
  param('searchChain', 'String'),
  param('timeout', 'Double'),
  param('noCache', 'Boolean'),

  // Query Model
  param('model', [
    param('defaultIndex', 'String'),
    param('encoding', 'String'),
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
    param('listFeatures', 'Boolean'),
    param('profile', 'String'),
    param('properties', 'String'),
    param('softtimeout', [
      param('enable', 'Boolean'),
      param('factor', 'Float'),
    ]),
    param('sorting', 'String'),
    param('freshness', 'String'),
    param('queryCache', 'Boolean'),
    param('rerankCount', 'Integer'),
    param('matching', [
      param('numThreadsPerSearch', 'Integer'),
      param('minHitsPerThread', 'Integer'),
      param('numSearchPartitions', 'Integer'),
      param('termwiseLimit', 'Float'),
      param('postFilterThreshold', 'Float'),
      param('approximateThreshold', 'Float'),
    ]),
    param('matchPhase', [
      param('attribute', 'String'),
      param('maxHits', 'Integer'),
      param('ascending', 'Boolean'),
      param('diversity', [
        param('attribute', 'String'),
        param('minGroups', 'Integer'),
      ]),
    ]),
  ]),

  // Grouping
  param('collapsesize', 'Integer'),
  param('collapsefield', 'String'),
  param('collapse', [param('summary', 'String')]),
  param('grouping', [
    param('defaultMaxGroups', 'Integer'),
    param('defaultMaxHits', 'Integer'),
    param('globalMaxGroups', 'Integer'),
    param('defaultPrecisionFactor', 'Float'),
  ]),

  // Presentation
  param('presentation', [
    param('bolding', 'Boolean'),
    param('format', [param('tensors', 'String')]),
    param('template', 'String'),
    param('summary', 'String'),
    param('timing', 'Boolean'),
  ]),

  // Tracing
  param('trace', [
    param('level', 'Integer'),
    param('explainLevel', 'Integer'),
    param('profileDepth', 'Integer'),
    param('timestamps', 'Boolean'),
    param('query', 'Boolean'),
  ]),

  // Semantic Rules
  param('rules', [param('off', 'Boolean'), param('rulebase', 'String')]),
  param('tracelevel', [param('rules', 'Integer')]),

  // Dispatch
  param('dispatch', [param('topKProbability', 'Float')]),

  // Other
  param('recall', 'String'),
  param('user', 'String'),
  param('hitcountestimate', 'Boolean'),
  param('metrics', [param('ignore', 'Boolean')]),
  param('weakAnd', [param('replace', 'Boolean')]),
  param('wand', [param('hits', 'Integer')]),

  param('streaming', [
    param('userid', 'Integer'),
    param('groupname', 'String'),
    param('selection', 'String'),
    param('priority', 'String'),
    param('maxbucketspervisitor', 'Integer'),
  ]),
]).children;
