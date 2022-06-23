import React, { useState } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';
import SimpleButton from '../Buttons/SimpleButton';
import SimpleForm from './SimpleForm';

export default function SendQuery() {
  const messageMethods = { post: { name: 'POST' }, get: { name: 'GET' } };
  const [method, setMethod] = useState(messageMethods.post.name);

  const updateMethod = (e) => {
    e.preventDefault();
    const newMethod = e.target.value;
    setMethod(newMethod);
  };

  //TODO: Handle sending the query
  function handleClick() {
    console.log('Click happened');
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
