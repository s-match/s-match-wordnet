package it.unitn.disi.smatch.oracles.uby;

import it.unitn.disi.smatch.data.ling.ISense;
import it.unitn.disi.smatch.data.ling.Sense;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.lmf.api.Uby;
import de.tudarmstadt.ukp.lmf.model.core.LexicalEntry;
import de.tudarmstadt.ukp.lmf.model.enums.ERelNameSemantics;
import de.tudarmstadt.ukp.lmf.model.morphology.Lemma;
import de.tudarmstadt.ukp.lmf.model.semantics.Synset;
import de.tudarmstadt.ukp.lmf.model.semantics.SynsetRelation;

/**
 *   
 * WordNet-based sense implementation.
 *
 */
public class UbySense implements ISense , Serializable {

	private static final Logger log = LoggerFactory.getLogger(UbySense.class);
	
	private Synset synset;
	private Uby uby;

	private UbyLinguisticOracle oracle;
	
	public UbySense(Synset synset, UbyLinguisticOracle oracle) {		
		Objects.requireNonNull(synset);
		Objects.requireNonNull(oracle);
		this.synset = synset;		
		this.oracle = oracle;
	}

	@Override
	public String getId() {
		return synset.getId();		
	}

	@Override
	public String getGloss() {
		// todo not using getGloss because it's a mashup of sense glosses. Definition seems more
		// convenient, but maybe a fallback to glosses would be useful
		String ret = synset.getDefinitionText();
		if (ret == null){
			log.debug("Found null in synset " + synset.getId() + ", returning empty string as gloss" );
			return "";
		}
		return ret;
	}

	@Override
	public List<String> getLemmas() {
		ArrayList<String> ret = new ArrayList();
		//uby.
		try {
			for (de.tudarmstadt.ukp.lmf.model.core.Sense ubySense : synset.getSenses()){
				LexicalEntry ubyLexicalEntry = null;
				try {
					ubyLexicalEntry = ubySense.getLexicalEntry();
					Objects.requireNonNull(ubyLexicalEntry);
				} catch (Exception ex){
					log.error("Error while retrieving lexical entry for sense " + ubySense.getId(), ex);
				}				
				try {
					Lemma lemma = ubyLexicalEntry.getLemma();
					Objects.requireNonNull(lemma);
				} catch (Exception ex){
					log.error("Error while retrieving lemma for lexicalEntry " + ubyLexicalEntry.getId(), ex);
				}
				
			}
		} catch (Exception ex){
			log.error("Error while retrieving lemmas for synset " + synset.getId(), ", returning an empty list", ex);			
		}
		return Collections.unmodifiableList(ret);
	}

	
	
	// todo g SenseAxis, SynsetRelations or both?
	@Override
	public List<ISense> getParents() throws LinguisticOracleException {
		return getRelationTargets(
				-1, 
				ERelNameSemantics.HYPERNYM, 
				ERelNameSemantics.HYPERNYMINSTANCE);
	}

	@Override
	public List<ISense> getParents(int depth) throws LinguisticOracleException {
		return  getRelationTargets(
				depth, 
				ERelNameSemantics.HYPERNYM, 
				ERelNameSemantics.HYPERNYMINSTANCE);
	}

	@Override
	public List<ISense> getChildren() throws LinguisticOracleException {
		return  getRelationTargets(
				-1, 
				ERelNameSemantics.HYPONYM, 
				ERelNameSemantics.HYPONYMINSTANCE);
	}

	@Override
	public List<ISense> getChildren(int depth) throws LinguisticOracleException {
		return getRelationTargets(
				depth, 
				ERelNameSemantics.HYPONYM, 
				ERelNameSemantics.HYPONYMINSTANCE);
	}
	
	
	/**
	 * On error from uby should just log an error and return an empty list.
	 * @param depth if -1 the transitive closure is returned
	 */
	// todo specify relation direction!
	public List<ISense> getRelationTargets(int depth, String... relNamesArr){
		
		if (depth < -1){
			throw new IllegalArgumentException("depth must be greater or equal to -1, found instead " + depth);
		}
		
		if (depth != 1){
			throw new UnsupportedOperationException("TODO - developer forgot to implement depth != -1 case!");
		}
		
		ArrayList<ISense> ret = new ArrayList();
		List<SynsetRelation> rels = synset.getSynsetRelations();		
		
		ArrayList<String> relNames = new ArrayList<String>(Arrays.asList(relNamesArr));
		
		try {
			for (SynsetRelation synsetRelation : synset.getSynsetRelations()){
				try {					
					Objects.requireNonNull(synsetRelation);
					
					if (relNames.contains(synsetRelation.getRelName())){
						UbySense senseToAdd = new UbySense(synsetRelation.getTarget(), oracle);
						ret.add(senseToAdd);
					}
					
				} catch (Exception ex){
					log.error("Error while processing synset relation for synset " + synset.getId(), ex);
				}				
			}
		} catch (Exception ex){
			log.error("Error while retrieving " + relNames + " for synset " + synset.getId(), ", returning an empty list", ex);			
		}
		return Collections.unmodifiableList(ret);
		
	}

}