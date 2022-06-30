import React, { useContext, useState } from 'react';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import SimpleButton from './SimpleButton';

export default function AddPropertyButton({ id }) {
  const { inputs, setInputs, childMap } = useContext(QueryInputContext);
  const [childId, setChildId] = useState(1);

  /**
   * Add a child to the input that has the provided id
   */
  const addChildProperty = () => {
    const newInputs = inputs.slice();
    let currentId = id.substring(0, 1);
    let index = newInputs.findIndex((element) => element.id === currentId); //get the index of the root parent
    let children = newInputs[index].children;
    let parentType = newInputs[index].type;
    for (let i = 3; i < id.length + 1; i += 2) {
      currentId = id.substring(0, i);
      index = children.findIndex((element) => element.id === currentId);
      parentType = parentType + '_' + children[index].type;
      children = children[index].children;
    }
    let type = childMap[parentType];
    children.push({
      id: id + '.' + childId,
      type: type[Object.keys(type)[0]].name,
      typeof: type[Object.keys(type)[0]].type,
      input: '',
      hasChildren: false,
      children: [],
    });
    setInputs(newInputs);
    setChildId((childId) => childId + 1);
  };

  return (
    <SimpleButton
      id={`propb${id}`}
      className={'addpropsbutton'}
      onClick={addChildProperty}
    >
      + Add property
    </SimpleButton>
  );
}
