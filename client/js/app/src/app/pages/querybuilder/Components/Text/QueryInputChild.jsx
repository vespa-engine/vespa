import React, { useContext, useEffect } from 'react';
import AddPropertyButton from '../Buttons/AddPropertyButton';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import QueryDropdownForm from './QueryDropDownForm';
import SimpleForm from './SimpleForm';

export default function QueryInputChild({ id }) {
  const { inputs, setInputs, childMap } = useContext(QueryInputContext);

  let childArray;
  let parentType;
  let index;
  index = inputs.findIndex((element) => element.id === id);
  parentType = inputs[index].type;
  childArray = inputs[index].children;

  // const updateType = (e) => {
  //     e.preventDefault();
  //     const newType = e.target.value;
  //     const newInputs =inputs.slice();
  //     const children = newInputs[parentType].children;
  // }

  // const updateInput = (e) => {
  //     e.preventDefault();
  //     const fid = parseInt(e.target.id.replace("v", ""));
  //     const index = childArray.findIndex((element) => element.id === fid)
  //     childArray[index].input = e.target.value;
  //     const newInputs = inputs.slice();
  //     setInputs(newInputs);
  // }

  const inputList = childArray.map((child) => {
    return (
      <div key={child.id} id={child.id}>
        <QueryDropdownForm
          choices={childMap[parentType]}
          id={child.id}
          child={true}
        />
        {child.hasChildren ? (
          <>
            <AddPropertyButton id={child.id} />
          </>
        ) : (
          <SimpleForm id={`v${child.id}`} size="30" />
        )}
        <Child id={child.id} type={parentType} child={child} />
      </div>
    );
  });

  return <>{inputList}</>;
}

function Child({ child, id, type }) {
  const { inputs, setInputs, childMap } = useContext(QueryInputContext);
  console.log(child);

  const nestedChildren = (child.children || []).map((child) => {
    return (
      <>
        <QueryDropdownForm
          choices={childMap[type]}
          id={child.id}
          child={true}
        />
        {child.hasChildren ? (
          <>
            <AddPropertyButton id={child.id} />
          </>
        ) : (
          <SimpleForm id={`v${child.id}`} size="30" />
        )}
        <Child key={child.id} child={child} id={child.id} type={child.type} />
      </>
    );
  });

  return <div>{nestedChildren}</div>;
}
