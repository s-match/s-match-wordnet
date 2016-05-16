package it.unitn.disi.smatch.oracles.wordnet;

import it.unitn.disi.common.DISIException;
import it.unitn.disi.common.utils.MiscUtils;
import it.unitn.disi.smatch.SMatchException;
import it.unitn.disi.smatch.data.ling.ISense;
import it.unitn.disi.smatch.data.mappings.IMappingElement;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import it.unitn.disi.smatch.oracles.ISenseMatcher;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;
import it.unitn.disi.smatch.oracles.SenseMatcherException;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.*;
import net.sf.extjwnl.data.list.PointerTargetNode;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import net.sf.extjwnl.data.list.PointerTargetTree;
import net.sf.extjwnl.data.relationship.AsymmetricRelationship;
import net.sf.extjwnl.data.relationship.RelationshipFinder;
import net.sf.extjwnl.data.relationship.RelationshipList;
import net.sf.extjwnl.dictionary.Dictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Implements a Linguistic Oracle and Sense Matcher using WordNet.
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class WordNet implements ILinguisticOracle, ISenseMatcher {

    private static final Logger log = LoggerFactory.getLogger(WordNet.class);

    private static final Pattern offset = Pattern.compile("\\d+");

    private final Dictionary dic;

    // contains all the multiwords in WordNet
    private final Map<String, List<List<String>>> multiwords;

    private final Map<String, Character> sensesCache = new ConcurrentHashMap<>();

    public WordNet() throws SMatchException {
        this(null, null, true);
    }

    public WordNet(String jwnlPropertiesPath) throws SMatchException {
        this(jwnlPropertiesPath, null, true);
    }

    public WordNet(String jwnlPropertiesPath, String multiwordsFileName) throws SMatchException {
        this(jwnlPropertiesPath, multiwordsFileName, true);
    }

    public WordNet(String jwnlPropertiesPath, String multiwordsFileName, boolean loadArrays) throws SMatchException {
        dic = getDictionary(jwnlPropertiesPath);

        if (null != multiwordsFileName) {
            if (loadArrays) {
                log.info("Loading multiwords: " + multiwordsFileName);
                multiwords = readHash(multiwordsFileName);
                log.info("loaded multiwords: " + multiwords.size());
            } else {
                multiwords = new HashMap<>();
            }
        } else {
            // create it
            multiwords = createMultiwordHash(dic);
        }
    }

    public static Dictionary getDictionary(String jwnlPropertiesPath) throws SMatchException {
        try {
            if (null != jwnlPropertiesPath) {
                log.info("Initializing extJWNL (" + jwnlPropertiesPath + ")");

                InputStream is = MiscUtils.getInputStream(jwnlPropertiesPath);
                try {
                    return Dictionary.getInstance(is);
                } finally {
                    if (null != is) {
                        is.close();
                    }
                }
            } else {
                log.info("Initializing extJWNL (default resource instance)");
                return Dictionary.getDefaultResourceInstance();
            }
        } catch (JWNLException | DISIException | IOException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public List<ISense> getSenses(String label) throws LinguisticOracleException {
        List<ISense> result = Collections.emptyList();
        try {
            IndexWordSet lemmas = dic.lookupAllIndexWords(label);
            if (null != lemmas && 0 < lemmas.size()) {
                result = new ArrayList<>(lemmas.size());
                for (POS pos : POS.values()) {
                    IndexWord indexWord = lemmas.getIndexWord(pos);
                    if (null != indexWord) {
                        for (Synset synset : indexWord.getSenses()) {
                            result.add(new WordNetSense(synset));
                        }
                    }
                }
            }
        } catch (JWNLException e) {
            throw new LinguisticOracleException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        return result;
    }

    public List<String> getBaseForms(String derivation) throws LinguisticOracleException {
        try {
            List<String> result = new ArrayList<>();
            IndexWordSet tmp = dic.lookupAllIndexWords(derivation);
            if (null != tmp) {
                IndexWord[] indexWordArray = tmp.getIndexWordArray();
                for (IndexWord indexWord : indexWordArray) {
                    String lemma = indexWord.getLemma();
                    if (null != lemma && !result.contains(lemma)) {
                        result.add(lemma);
                    }
                }
            } else {
                if (null != dic.getMorphologicalProcessor()) {
                    for (POS pos : POS.values()) {
                        List<String> posLemmas = dic.getMorphologicalProcessor().lookupAllBaseForms(pos, derivation);
                        for (String lemma : posLemmas) {
                            if (!result.contains(lemma)) {
                                result.add(lemma);
                            }
                        }
                    }
                }
            }
            if (0 == result.size()) {
                result.add(derivation);
            }
            return result;
        } catch (JWNLException e) {
            throw new LinguisticOracleException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public boolean isEqual(String str1, String str2) throws LinguisticOracleException {
        try {
            IndexWordSet lemmas1 = dic.lookupAllIndexWords(str1);
            IndexWordSet lemmas2 = dic.lookupAllIndexWords(str2);
            if ((lemmas1 == null) || (lemmas2 == null) || (lemmas1.size() < 1) || (lemmas2.size() < 1)) {
                return false;
            } else {
                IndexWord[] v1 = lemmas1.getIndexWordArray();
                IndexWord[] v2 = lemmas2.getIndexWordArray();
                for (IndexWord aV1 : v1) {
                    for (IndexWord aV2 : v2) {
                        if (aV1.equals(aV2)) {
                            return true;
                        }
                    }
                }
            }
        } catch (JWNLException e) {
            throw new LinguisticOracleException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        return false;
    }

    public char getRelation(List<ISense> sourceSenses, List<ISense> targetSenses) throws SenseMatcherException {
        for (ISense sourceSense : sourceSenses) {
            for (ISense targetSense : targetSenses) {
                if (getRelationFromOracle(sourceSense, targetSense, IMappingElement.EQUIVALENCE)) {
                    if (log.isTraceEnabled()) {
                        log.trace("Found = using & (SIMILAR_TO) between " +
                                        sourceSense.getId() + Arrays.toString(sourceSense.getLemmas().toArray()) + " and " +
                                        targetSense.getId() + Arrays.toString(targetSense.getLemmas().toArray())
                        );
                    }
                    return IMappingElement.EQUIVALENCE;
                }
            }
        }
        //  Check for less general than
        for (ISense sourceSense : sourceSenses) {
            for (ISense targetSense : targetSenses) {
                if (getRelationFromOracle(sourceSense, targetSense, IMappingElement.LESS_GENERAL)) {
                    if (log.isTraceEnabled()) {
                        log.trace("Found < using @,#m,#s,#p (HYPERNYM, MEMBER_, SUBSTANCE_, PART_HOLONYM) between " +
                                        sourceSense.getId() + Arrays.toString(sourceSense.getLemmas().toArray()) + " and " +
                                        targetSense.getId() + Arrays.toString(targetSense.getLemmas().toArray())
                        );
                    }
                    return IMappingElement.LESS_GENERAL;
                }
            }
        }
        //  Check for more general than
        for (ISense sourceSense : sourceSenses) {
            for (ISense targetSense : targetSenses) {
                if (getRelationFromOracle(sourceSense, targetSense, IMappingElement.MORE_GENERAL)) {
                    if (log.isTraceEnabled()) {
                        log.trace("Found > using @,#m,#s,#p (HYPERNYM, MEMBER_, SUBSTANCE_, PART_HOLONYM) between " +
                                        sourceSense.getId() + Arrays.toString(sourceSense.getLemmas().toArray()) + " and " +
                                        targetSense.getId() + Arrays.toString(targetSense.getLemmas().toArray())
                        );
                    }
                    return IMappingElement.MORE_GENERAL;
                }
            }
        }
        //  Check for opposite meaning
        for (ISense sourceSense : sourceSenses) {
            for (ISense targetSense : targetSenses) {
                if (getRelationFromOracle(sourceSense, targetSense, IMappingElement.DISJOINT)) {
                    if (log.isTraceEnabled()) {
                        log.trace("Found ! using ! (ANTONYM) between " +
                                        sourceSense.getId() + Arrays.toString(sourceSense.getLemmas().toArray()) + " and " +
                                        targetSense.getId() + Arrays.toString(targetSense.getLemmas().toArray())
                        );
                    }
                    return IMappingElement.DISJOINT;
                }
            }
        }
        return IMappingElement.IDK;
    }

    /**
     * Method which returns whether particular type of relation between
     * two senses holds(according to oracle).
     * It uses cache to store already obtained relations in order to improve performance.
     *
     * @param source the string of source
     * @param target the string of target
     * @param rel    the relation between source and target
     * @return whether particular type of relation holds between two senses according to oracle
     * @throws it.unitn.disi.smatch.oracles.SenseMatcherException SenseMatcherException
     */
    private boolean getRelationFromOracle(ISense source, ISense target, char rel) throws SenseMatcherException {
        final String sensePairKey = source.toString() + "\t" + target.toString();
        Character cachedRelation = sensesCache.get(sensePairKey);
        // if we don't have cached relation check which one exist and put it to cash
        if (null == cachedRelation) {
            // check for synonymy
            if (isSourceSynonymTarget(source, target)) {
                sensesCache.put(sensePairKey, IMappingElement.EQUIVALENCE);
                return rel == IMappingElement.EQUIVALENCE;
            } else {
                // check for opposite meaning
                if (isSourceOppositeToTarget(source, target)) {
                    sensesCache.put(sensePairKey, IMappingElement.DISJOINT);
                    return rel == IMappingElement.DISJOINT;
                } else {
                    // check for less general than
                    if (isSourceLessGeneralThanTarget(source, target)) {
                        sensesCache.put(sensePairKey, IMappingElement.LESS_GENERAL);
                        return rel == IMappingElement.LESS_GENERAL;
                    } else {
                        // check for more general than
                        if (isSourceMoreGeneralThanTarget(source, target)) {
                            sensesCache.put(sensePairKey, IMappingElement.MORE_GENERAL);
                            return rel == IMappingElement.MORE_GENERAL;
                        } else {
                            sensesCache.put(sensePairKey, IMappingElement.IDK);
                            return IMappingElement.IDK == rel;
                        }
                    }
                }
            }
        } else {
            return rel == cachedRelation;
        }
    }

    public boolean isSourceSynonymTarget(ISense source, ISense target) throws SenseMatcherException {
        if (source.equals(target)) {
            return true;
        }
        if ((source instanceof WordNetSense) && (target instanceof WordNetSense)) {
            try {
                WordNetSense sourceSyn = (WordNetSense) source;
                WordNetSense targetSyn = (WordNetSense) target;
                //is synonym
                RelationshipList list = RelationshipFinder.findRelationships(sourceSyn.getSynset(), targetSyn.getSynset(), PointerType.SIMILAR_TO);
                if (list.size() > 0) {
                    return !((POS.ADJECTIVE == sourceSyn.getPOS()) || (POS.ADJECTIVE == targetSyn.getPOS())) || (list.get(0).getDepth() == 0);
                }
            } catch (CloneNotSupportedException | JWNLException e) {
                throw new SenseMatcherException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            }
        }
        return false;
    }

    public boolean isSourceOppositeToTarget(ISense source, ISense target) throws SenseMatcherException {
        if (source.equals(target)) {
            return false;
        }
        if ((source instanceof WordNetSense) && (target instanceof WordNetSense)) {
            try {
                WordNetSense sourceSyn = (WordNetSense) source;
                WordNetSense targetSyn = (WordNetSense) target;
                //  Checks whether senses are siblings (thus they are opposite)
                if (POS.NOUN != sourceSyn.getPOS() || POS.NOUN != targetSyn.getPOS()) {
                    RelationshipList list = RelationshipFinder.findRelationships(sourceSyn.getSynset(), targetSyn.getSynset(), PointerType.ANTONYM);
                    if (list.size() > 0) {
                        return true;
                    }
                }
            } catch (CloneNotSupportedException | JWNLException e) {
                throw new SenseMatcherException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            }
        }
        return false;
    }

    /**
     * Checks whether source sense less general than target.
     * Currently used version of Java WordNet Interface Library finds more general relationships
     * (hypernymy and holonymy) faster than less general so this method
     * just flip the parameters and call isSourceMoreGeneralThanTarget method.
     *
     * @param source the string of source
     * @param target the string of target
     * @return true if source is less general than target
     */
    public boolean isSourceLessGeneralThanTarget(ISense source, ISense target) throws SenseMatcherException {
        return isSourceMoreGeneralThanTarget(target, source);
    }

    public boolean isSourceMoreGeneralThanTarget(ISense source, ISense target) throws SenseMatcherException {
        if ((source instanceof WordNetSense) && (target instanceof WordNetSense)) {
            WordNetSense sourceSyn = (WordNetSense) source;
            WordNetSense targetSyn = (WordNetSense) target;

            if ((POS.NOUN == sourceSyn.getPOS() && POS.NOUN == targetSyn.getPOS()) || (POS.VERB == sourceSyn.getPOS() && POS.VERB == targetSyn.getPOS())) {
                if (source.equals(target)) {
                    return false;
                }
                try {
                    // find all more general relationships from WordNet
                    RelationshipList list = RelationshipFinder.findRelationships(sourceSyn.getSynset(), targetSyn.getSynset(), PointerType.HYPERNYM);
                    if (!isUnidirectional(list)) {
                        PointerTargetTree ptt = PointerUtils.getInheritedMemberHolonyms(targetSyn.getSynset());
                        PointerTargetNodeList ptnl = PointerUtils.getMemberHolonyms(targetSyn.getSynset());
                        if (!traverseTree(ptt, ptnl, sourceSyn.getSynset())) {
                            ptt = PointerUtils.getInheritedPartHolonyms(targetSyn.getSynset());
                            ptnl = PointerUtils.getPartHolonyms(targetSyn.getSynset());
                            if (!traverseTree(ptt, ptnl, sourceSyn.getSynset())) {
                                ptt = PointerUtils.getInheritedSubstanceHolonyms(targetSyn.getSynset());
                                ptnl = PointerUtils.getSubstanceHolonyms(targetSyn.getSynset());
                                if (traverseTree(ptt, ptnl, sourceSyn.getSynset())) {
                                    return true;
                                }
                            } else {
                                return true;
                            }
                        } else {
                            return true;
                        }
                    } else {
                        return true;
                    }
                } catch (CloneNotSupportedException | JWNLException e) {
                    throw new SenseMatcherException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                }
            }
        }
        return false;
    }

    public ISense createSense(String id) throws LinguisticOracleException {
        if (id.length() < 3 || 1 != id.indexOf('#')) {
            throw new LinguisticOracleException("Malformed sense id: " + id);
        }
        final String pos = id.substring(0, 1);
        final String off = id.substring(2);
        if (!"navr".contains(pos) || !offset.matcher(off).matches()) {
            throw new LinguisticOracleException("Malformed sense id: " + id);
        }
        try {
            Synset synset = dic.getSynsetAt(POS.getPOSForKey(pos), Long.parseLong(off));
            if (null == synset) {
                throw new LinguisticOracleException("Synset not found: " + id);
            }
            return new WordNetSense(synset);
        } catch (JWNLException e) {
            throw new LinguisticOracleException(e.getMessage(), e);
        }
    }

    public List<List<String>> getMultiwords(String beginning) throws LinguisticOracleException {
        return multiwords.get(beginning);
    }

    /**
     * traverses PointerTargetTree.
     *
     * @param syn    synonyms
     * @param ptnl   target node list
     * @param source source synset
     * @return if source was found
     */
    private static boolean traverseTree(PointerTargetTree syn, PointerTargetNodeList ptnl, Synset source) {
        List MGListsList = syn.toList();
        for (Object aMGListsList : MGListsList) {
            PointerTargetNodeList MGList = (PointerTargetNodeList) aMGListsList;
            for (Object aMGList : MGList) {
                Synset toAdd = ((PointerTargetNode) aMGList).getSynset();
                if (toAdd.equals(source)) {
                    return true;
                }
            }
        }
        for (Object aPtnl : ptnl) {
            Synset toAdd = ((PointerTargetNode) aPtnl).getSynset();
            if (toAdd.equals(source)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks the directionality of semantic relations in the list.
     *
     * @param list a list with relations
     * @return true if relations in the list are unidirectional
     */
    private boolean isUnidirectional(RelationshipList list) {
        if (list.size() > 0) {
            try {
                if (((AsymmetricRelationship) list.get(0)).getCommonParentIndex() == 0) {
                    return true;
                }
            } catch (IndexOutOfBoundsException ex) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads the hashmap with multiwords. The multiwords are stored in the following format:
     * Key - the first word in the multiwords
     * Value - List of Lists, which contain the other words in the all the multiwords starting with key.
     *
     * @param url hashmap location
     * @return multiwords hashmap
     * @throws SMatchException SMatchException
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<List<String>>> readHash(String url) throws SMatchException {
        try {
            return (Map<String, List<List<String>>>) MiscUtils.readObject(url);
        } catch (DISIException e) {
            throw new SMatchException(e.getMessage(), e);
        }
    }

    /**
     * Create caches of WordNet to speed up matching.
     *
     * @param jwnlPropertiesPath path to extJWNL config file
     * @param multiwordsFileName path to file with multiwords
     * @throws SMatchException SMatchException
     */
    public static void createWordNetCaches(String jwnlPropertiesPath, String multiwordsFileName) throws SMatchException {
        Dictionary dic = getDictionary(jwnlPropertiesPath);

        log.info("Creating WordNet caches...");
        writeMultiwords(dic, multiwordsFileName);
        log.info("Done");
    }

    private static Map<String, List<List<String>>> createMultiwordHash(Dictionary dic) throws SMatchException {
        log.info("Creating multiword hash...");
        Map<String, List<List<String>>> result = new HashMap<>();
        POS[] parts = new POS[]{POS.NOUN, POS.ADJECTIVE, POS.VERB, POS.ADVERB};
        for (POS pos : parts) {
            collectMultiwords(dic, result, pos);
        }
        log.info("Multiwords: " + result.size());
        return result;
    }

    private static void writeMultiwords(Dictionary dic, String multiwordsFileName) throws SMatchException {
        try {
            MiscUtils.writeObject(createMultiwordHash(dic), multiwordsFileName);
        } catch (DISIException e) {
            throw new SMatchException(e.getMessage(), e);
        }
    }

    private static void collectMultiwords(Dictionary dic, Map<String, List<List<String>>> multiwords, POS pos) throws SMatchException {
        try {
            int count = 0;
            Iterator i = dic.getIndexWordIterator(pos);
            while (i.hasNext()) {
                IndexWord iw = (IndexWord) i.next();
                String lemma = iw.getLemma();
                if (-1 < lemma.indexOf(' ')) {
                    count++;
                    if (0 == count % 10000) {
                        log.debug("multiwords: " + count);
                    }
                    String[] tokens = lemma.split(" ");
                    List<List<String>> mwEnds = multiwords.get(tokens[0]);
                    if (null == mwEnds) {
                        mwEnds = new ArrayList<>();
                    }
                    List<String> currentMWEnd = new ArrayList<>(Arrays.asList(tokens));
                    currentMWEnd.remove(0);
                    mwEnds.add(currentMWEnd);
                    multiwords.put(tokens[0], mwEnds);
                }
            }
            log.info(pos.getKey() + " multiwords: " + count);
        } catch (JWNLException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}