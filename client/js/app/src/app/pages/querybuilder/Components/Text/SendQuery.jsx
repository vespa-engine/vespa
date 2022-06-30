import React, { useContext, useState } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';
import SimpleButton from '../Buttons/SimpleButton';
import SimpleForm from './SimpleForm';
import { QueryInputContext } from '../Contexts/QueryInputContext';

export default function SendQuery() {
  const { inputs } = useContext(QueryInputContext);
  const messageMethods = { post: { name: 'POST' }, get: { name: 'GET' } };
  const [method, setMethod] = useState(messageMethods.post.name);

  const updateMethod = (e) => {
    e.preventDefault();
    const newMethod = e.target.value;
    setMethod(newMethod);
  };

  //TODO: Handle sending the query
  function handleClick() {
    const json = buildJSON(inputs, {});
    send(json);
    console.log(json);
  }

  function send(json) {
    console.log('Sending JSON');
  }

  function buildJSON(inputs, json) {
    let queryJson = json;
    for (let i = 0; i < inputs.length; i++) {
      let current = inputs[i];
      let key = current.type;
      if (current.hasChildren) {
        let child = {};
        child = buildJSON(current.children, child);
        queryJson[key] = child;
      } else {
        queryJson[key] = parseInput(current.input, current.typeof);
      }
    }
    return queryJson;
  }

  function parseInput(input, type) {
    switch (type) {
      case 'Integer':
      case 'Long':
        return parseInt(input);
        break;

      case 'Float':
        return parseFloat(input);
        break;

      case 'Boolean':
        return input.toLowerCase() === 'true' ? true : false;
        break;

      default:
        return input;
    }
  }

  return (
    <>
      <SimpleDropDownForm
        choices={messageMethods}
        id="method"
        className="methodselector"
        onChange={updateMethod}
      />
      <SimpleForm
        id="url"
        className="textbox"
        initial="http://localhost:8080/search/"
        size="30"
      />
      <SimpleButton id="send" className="button" onClick={handleClick}>
        Send
      </SimpleButton>
    </>
  );
}
