package it.unitn.disi.smatch.oracles.wordnet;

import it.unitn.disi.smatch.data.ling.ISense;
import it.unitn.disi.smatch.data.ling.Sense;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.list.PointerTargetNode;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import net.sf.extjwnl.data.list.PointerTargetTree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * WordNet-based sense implementation.
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class WordNetSense extends Sense implements Serializable {

    private final Synset synset;

    /**
     * Constructs an instance linked to a synset.
     *
     * @param synset synset
     */
    public WordNetSense(Synset synset) {
        super(synset.getPOS().getKey() + "#" + synset.getOffset());
        this.synset = synset;
    }

    public String getGloss() {
        return synset.getGloss();
    }

    public List<String> getLemmas() {
        List<String> out = new ArrayList<>();

        for (int i = 0; i < synset.getWords().size(); i++) {
            out.add(synset.getWords().get(i).getLemma());
        }

        return out;
    }

    public List<ISense> getParents() throws LinguisticOracleException {
        return getParents(1);
    }

    public List<ISense> getParents(int depth) throws LinguisticOracleException {
        List<ISense> out = new ArrayList<>();
        try {
            PointerTargetTree hypernyms = PointerUtils.getHypernymTree(synset, depth);
            for (Iterator itr = hypernyms.toList().iterator(); itr.hasNext(); ) {
                if (itr.hasNext()) {
                    for (Object o : ((PointerTargetNodeList) itr.next())) {
                        Synset t = ((PointerTargetNode) o).getSynset();
                        if (!synset.equals(t)) {
                            out.add(new WordNetSense(t));
                        }
                    }
                }
            }
        } catch (JWNLException e) {
            throw new LinguisticOracleException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        return out;
    }

    public List<ISense> getChildren() throws LinguisticOracleException {
        return getChildren(1);
    }

    public List<ISense> getChildren(int depth) throws LinguisticOracleException {
        List<ISense> out = new ArrayList<>();
        try {
            PointerTargetTree hypernyms = PointerUtils.getHyponymTree(synset, depth);
            for (Iterator itr = hypernyms.toList().iterator(); itr.hasNext(); ) {
                if (itr.hasNext()) {
                    for (Object o : ((PointerTargetNodeList) itr.next())) {
                        Synset t = ((PointerTargetNode) o).getSynset();
                        if (!synset.equals(t)) {
                            out.add(new WordNetSense(t));
                        }
                    }
                }
            }
        } catch (JWNLException e) {
            throw new LinguisticOracleException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        return out;
    }

    public POS getPOS() {
        return synset.getPOS();
    }

    public long getOffset() {
        return synset.getOffset();
    }

    public Synset getSynset() {
        return synset;
    }
}