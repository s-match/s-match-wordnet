/*******************************************************************************
 * Copyright 2015
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package it.unitn.disi.smatch.oracles.uby.test.experimental;

import de.tudarmstadt.ukp.lmf.model.miscellaneous.EVarType;
import de.tudarmstadt.ukp.lmf.model.miscellaneous.VarType;
import de.tudarmstadt.ukp.lmf.model.semantics.SynsetRelation;

/**
 * Experiment to augment default uby classes  
 * 
 * @since 0.1
 */
public class MySynsetRelation extends SynsetRelation {
	
	@VarType(type = EVarType.ATTRIBUTE)
	protected String myField;
	
	public MySynsetRelation() {
		super();
		//this.depth = 0;
		this.myField = "";
	}

	public String getMyField() {
		return myField;
	}

	public void setMyField(String myField) {
		this.myField = myField;
	}

	/*
	public int getDepth() {
		return depth;
	}*/

	/*
	public void setDepth(int depth) {
		this.depth = depth;
	}
	*/
	
	
}
