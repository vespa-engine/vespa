import React, { useState } from 'react';
import SimpleDropDownForm from 'app/pages/querybuilder/query-filters/SimpleDropDownForm';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';

function send(method, url, query) {
  dispatch(ACTION.SET_HTTP, { loading: true });
  fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json;charset=utf-8' },
    body: query,
  })
    .then((response) => response.json())
    .then((result) =>
      dispatch(ACTION.SET_HTTP, {
        response: JSON.stringify(result, null, 4),
      })
    )
    .catch((error) => dispatch(ACTION.SET_HTTP, { error }));
}

export default function QueryEndpoint() {
  const messageMethods = { post: { name: 'POST' }, get: { name: 'GET' } };
  const [method, setMethod] = useState(messageMethods.post.name);
  const [url, setUrl] = useState('http://localhost:8080/search/');
  const query = useQueryBuilderContext((ctx) => ctx.query.input);

  const updateMethod = (e) => {
    e.preventDefault();
    const newMethod = e.target.value;
    setMethod(newMethod);
  };

  return (
    <>
      <SimpleDropDownForm
        options={messageMethods}
        value={method}
        className="methodselector"
        onChange={updateMethod}
      />
      <input
        size="30"
        className="textbox"
        value={url}
        onChange={({ target }) => setUrl(target.value)}
      />
      <button className="button" onClick={() => send(method, url, query)}>
        Send
      </button>
    </>
  );
}
