package it.unitn.disi.smatch.oracles.wordnet;

import it.unitn.disi.common.DISIException;
import it.unitn.disi.common.utils.MiscUtils;
import it.unitn.disi.smatch.SMatchException;
import it.unitn.disi.smatch.data.ling.ISense;
import it.unitn.disi.smatch.data.mappings.IMappingElement;
import it.unitn.disi.smatch.oracles.ISenseMatcher;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.*;
import net.sf.extjwnl.data.list.PointerTargetNode;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import net.sf.extjwnl.data.list.PointerTargetTree;
import net.sf.extjwnl.dictionary.Dictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implements version of WN matcher which use a fast internal data structure.
 * Contains routines for generating such structures.
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class InMemoryWordNetBinaryArray implements ISenseMatcher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryWordNetBinaryArray.class);

    // arrays with WordNet keys
    private final long[] adj_syn;
    private final long[] adj_opp;
    private final long[] noun_mg;
    private final long[] noun_opp;
    private final long[] adv_opp;
    private final long[] verb_mg;
    private final long[] nominalizations;

    public InMemoryWordNetBinaryArray(
            String adjectiveSynonyms,
            String adjectiveAntonyms,
            String nounHypernyms,
            String nounAntonyms,
            String adverbAntonyms,
            String verbHypernyms,
            String nominalizations
    ) throws SMatchException {
        log.info("Loading WordNet cache to memory...");
        this.adj_syn = readArray(adjectiveSynonyms, "adjective synonyms");
        this.adj_opp = readArray(adjectiveAntonyms, "adjective antonyms");
        this.noun_mg = readArray(nounHypernyms, "noun hypernyms");
        this.noun_opp = readArray(nounAntonyms, "noun antonyms");
        this.verb_mg = readArray(verbHypernyms, "verb hypernyms");
        this.adv_opp = readArray(adverbAntonyms, "adverb antonyms");
        this.nominalizations = readArray(nominalizations, "nominalizations");
        log.info("Loaded WordNet cache to memory");
    }

    public char getRelation(List<ISense> sourceSenses, List<ISense> targetSenses) {
        // Check for synonymy
        for (ISense sourceSense : sourceSenses) {
            for (ISense targetSense : targetSenses) {
                if (isSourceSynonymTarget(sourceSense, targetSense)) {
                    return IMappingElement.EQUIVALENCE;
                }
            }
        }
        // Check for less general than
        for (ISense sourceSense : sourceSenses) {
            for (ISense targetSense : targetSenses) {
                if (isSourceLessGeneralThanTarget(sourceSense, targetSense)) {
                    return IMappingElement.LESS_GENERAL;
                }
            }
        }
        // Check for more general than
        for (ISense sourceSense : sourceSenses) {
            for (ISense targetSense : targetSenses) {
                if (isSourceMoreGeneralThanTarget(sourceSense, targetSense)) {
                    return IMappingElement.MORE_GENERAL;
                }
            }
        }
        // Check for opposite meaning
        for (ISense sourceSense : sourceSenses) {
            for (ISense targetSense : targetSenses) {
                if (isSourceOppositeToTarget(sourceSense, targetSense)) {
                    return IMappingElement.DISJOINT;
                }
            }
        }
        return IMappingElement.IDK;
    }

    private boolean isSourceOppositeToTargetInt(long sourceSense, long targetSense, POS sourcePOS, POS targetPOS) {
        long key;
        if (targetSense > sourceSense) {
            key = (targetSense << 32) + sourceSense;
        } else {
            key = (sourceSense << 32) + targetSense;
        }

        if ((POS.NOUN == sourcePOS) && (POS.NOUN == targetPOS)) {
            if (Arrays.binarySearch(noun_opp, key) >= 0) {
                log.trace("Found ! using ! (ANTONYM) between nouns");
                return true;
            }
        } else {
            if ((POS.ADJECTIVE == sourcePOS) && (POS.ADJECTIVE == targetPOS)) {
                if (Arrays.binarySearch(adj_opp, key) >= 0) {
                    log.trace("Found ! using ! (ANTONYM) between adjectives");
                    return true;
                }
            } else {
                if ((POS.ADVERB == sourcePOS) && (POS.ADVERB == targetPOS)) {
                    if (Arrays.binarySearch(adv_opp, key) >= 0) {
                        log.trace("Found ! using ! (ANTONYM) between adverbs");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isSourceLessGeneralThanTargetInt(long sourceSense, long targetSense, POS sourcePOS, POS targetPOS) {
        long key = (sourceSense << 32) + targetSense;
        if ((POS.NOUN == sourcePOS) && (POS.NOUN == targetPOS)) {
            if (Arrays.binarySearch(noun_mg, key) >= 0) {
                log.trace("Found < using @,#m,#s,#p (HYPERNYM, MEMBER_, SUBSTANCE_, PART_HOLONYM) between nouns");
                return true;
            }
        } else {
            if ((POS.VERB == sourcePOS) && (POS.VERB == targetPOS)) {
                if (Arrays.binarySearch(verb_mg, key) >= 0) {
                    log.trace("Found < using @ (HYPERNYM) between verbs");
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isSourceSynonymTargetInt(long sourceSense, long targetSense, POS sourcePOS, POS targetPOS) {
        if (sourceSense == targetSense) {
            return true;
        }

        long key;
        if (targetSense > sourceSense) {
            key = (targetSense << 32) + sourceSense;
        } else {
            key = (sourceSense << 32) + targetSense;
        }

        if ((POS.ADJECTIVE == sourcePOS) && (POS.ADJECTIVE == targetPOS)) {
            if (Arrays.binarySearch(adj_syn, key) >= 0) {
                log.trace("Found = using & (SIMILAR_TO) between adjectives");
                return true;
            }
        }
        if ((POS.NOUN == sourcePOS) && (POS.VERB == targetPOS)) {
            key = (targetSense << 32) + sourceSense;
            if (Arrays.binarySearch(nominalizations, key) >= 0) {
                log.trace("Found = using + (DERIVATION) between a noun and a verb");
                return true;
            }
        }
        if ((POS.VERB == sourcePOS) && (POS.NOUN == targetPOS)) {
            key = (sourceSense << 32) + targetSense;
            if (Arrays.binarySearch(nominalizations, key) >= 0) {
                log.trace("Found = using + (DERIVATION) between a verb and a noun");
                return true;
            }
        }
        return false;
    }

    private static long[] readArray(String fileName, String name) throws SMatchException {
        long[] result = readHash(fileName);
        log.debug("Read " + name + ": " + result.length);
        return result;
    }

    private static long[] readHash(String fileName) throws SMatchException {
        try {
            return (long[]) MiscUtils.readObject(fileName);
        } catch (DISIException e) {
            throw new SMatchException(e.getMessage(), e);
        }
    }

    public boolean isSourceMoreGeneralThanTarget(ISense source, ISense target) {
        return isSourceLessGeneralThanTarget(target, source);
    }

    public boolean isSourceLessGeneralThanTarget(ISense source, ISense target) {
        return (source instanceof WordNetSense) && (target instanceof WordNetSense)
                && isSourceLessGeneralThanTargetInt(
                ((WordNetSense) source).getOffset(),
                ((WordNetSense) target).getOffset(),
                ((WordNetSense) source).getPOS(),
                ((WordNetSense) target).getPOS());
    }

    public boolean isSourceSynonymTarget(ISense source, ISense target) {
        return (source instanceof WordNetSense) && (target instanceof WordNetSense)
                && isSourceSynonymTargetInt(
                ((WordNetSense) source).getOffset(),
                ((WordNetSense) target).getOffset(),
                ((WordNetSense) source).getPOS(),
                ((WordNetSense) target).getPOS());
    }

    public boolean isSourceOppositeToTarget(ISense source, ISense target) {
        return (source instanceof WordNetSense) && (target instanceof WordNetSense)
                && isSourceOppositeToTargetInt(
                ((WordNetSense) source).getOffset(),
                ((WordNetSense) target).getOffset(),
                ((WordNetSense) source).getPOS(),
                ((WordNetSense) target).getPOS());
    }

    /**
     * Create caches of WordNet to speed up matching.
     *
     * @param jwnlPropertiesPath extJWNL properties file path
     * @param adjectiveSynonyms  adjective synonyms file path
     * @param adjectiveAntonyms  adjective antonyms file path
     * @param nounHypernyms      noun hypernyms file path
     * @param nounAntonyms       noun antonyms file path
     * @param adverbAntonyms     adverb antonyms file path
     * @param verbHypernyms      verb hypernyms file path
     * @param nominalizations    nominalizations file path
     * @throws SMatchException SMatchException
     */
    public static void createWordNetCaches(String jwnlPropertiesPath,
                                           String adjectiveSynonyms,
                                           String adjectiveAntonyms,
                                           String nounHypernyms,
                                           String nounAntonyms,
                                           String adverbAntonyms,
                                           String verbHypernyms,
                                           String nominalizations
    ) throws SMatchException {
        Dictionary dic = WordNet.getDictionary(jwnlPropertiesPath);

        log.info("Creating WordNet caches...");
        convertAndWrite(findNominalizations(dic), nominalizations);
        convertAndWrite(findAdjectiveSynonyms(dic), adjectiveSynonyms);
        convertAndWrite(findAdverbAntonyms(dic), adverbAntonyms);
        convertAndWrite(findAdjectiveAntonyms(dic), adjectiveAntonyms);
        convertAndWrite(findNounAntonyms(dic), nounAntonyms);
        convertAndWrite(findNounHypernyms(dic), nounHypernyms);
        convertAndWrite(findVerbHypernyms(dic), verbHypernyms);
        log.info("Created WordNet caches");
    }

    private static void convertAndWrite(Set<Long> keys, String fileName) throws SMatchException {
        try {
            long[] keysArr = new long[keys.size()];
            int i = 0;
            for (Long key : keys) {
                keysArr[i] = key;
                i++;
            }
            Arrays.sort(keysArr);
            MiscUtils.writeObject(keysArr, fileName);
        } catch (DISIException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static Set<Long> findNominalizations(Dictionary dic) throws SMatchException {
        log.info("Creating nominalizations array...");
        try {
            Set<Long> keys = new HashSet<>();
            int count = 0;
            Iterator<Synset> it = dic.getSynsetIterator(POS.VERB);
            while (it.hasNext()) {
                count++;
                if (0 == count % 1000) {
                    log.debug("nominalizations: " + count);
                }
                Synset source = it.next();
                List<Pointer> pointers = source.getPointers(PointerType.DERIVATION);
                for (Pointer pointer : pointers) {
                    if (POS.NOUN.equals(pointer.getTargetPOS())) {
                        long targetOffset = pointer.getTargetOffset();
                        long key = (source.getOffset() << 32) + targetOffset;
                        keys.add(key);
                    }
                }
            }
            log.info("Nominalizations: " + keys.size());
            return keys;
        } catch (JWNLException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static Set<Long> findAdjectiveSynonyms(Dictionary dic) throws SMatchException {
        log.info("Creating adjective synonyms array...");
        try {
            Set<Long> keys = new HashSet<>();
            int count = 0;
            Iterator<Synset> it = dic.getSynsetIterator(POS.ADJECTIVE);
            while (it.hasNext()) {
                count++;
                if (0 == count % 1000) {
                    log.debug("adjective synonyms: " + count);
                }
                Synset source = it.next();
                long sourceOffset = source.getOffset();
                List<Pointer> pointers = source.getPointers(PointerType.SIMILAR_TO);
                for (Pointer ptr : pointers) {
                    long targetOffset = ptr.getTargetOffset();
                    long key;
                    if (targetOffset > sourceOffset) {
                        key = (targetOffset << 32) + sourceOffset;
                    } else {
                        key = (sourceOffset << 32) + targetOffset;
                    }
                    keys.add(key);
                }
            }
            log.info("Adjective synonyms: " + keys.size());
            return keys;
        } catch (JWNLException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static Set<Long> findAdverbAntonyms(Dictionary dic) throws SMatchException {
        log.info("Creating adverb antonyms array...");
        try {
            Set<Long> keys = new HashSet<>();
            int count = 0;
            Iterator<Synset> it = dic.getSynsetIterator(POS.ADVERB);
            while (it.hasNext()) {
                count++;
                if (0 == count % 1000) {
                    log.debug("adverb antonyms: " + count);
                }
                Synset source = it.next();
                long sourceOffset = source.getOffset();
                List<Pointer> pointers = source.getPointers(PointerType.ANTONYM);
                for (Pointer ptr : pointers) {
                    long targetOffset = ptr.getTargetOffset();
                    long key;
                    if (targetOffset > sourceOffset) {
                        key = (targetOffset << 32) + sourceOffset;
                    } else {
                        key = (sourceOffset << 32) + targetOffset;
                    }
                    keys.add(key);
                }
            }
            log.info("Adverbs antonyms: " + keys.size());
            return keys;
        } catch (JWNLException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static Set<Long> findAdjectiveAntonyms(Dictionary dic) throws SMatchException {
        log.info("Creating adjective antonyms array...");
        try {
            Set<Long> keys = new HashSet<>();
            int count = 0;
            Iterator<Synset> it = dic.getSynsetIterator(POS.ADJECTIVE);
            while (it.hasNext()) {
                count++;
                if (0 == count % 1000) {
                    log.debug("adjective antonyms: " + count);
                }
                Synset current = it.next();
                traverseTree(keys, PointerUtils.getExtendedAntonyms(current), current.getOffset());
                traverseListSym(keys, PointerUtils.getAntonyms(current), current.getOffset());
            }
            log.info("Adjective antonyms: " + keys.size());
            return keys;
        } catch (JWNLException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static Set<Long> findNounAntonyms(Dictionary dic) throws SMatchException {
        log.info("Creating noun antonyms array...");
        try {
            Set<Long> keys = new HashSet<>();
            int count = 0;
            Iterator<Synset> it = dic.getSynsetIterator(POS.NOUN);
            while (it.hasNext()) {
                count++;
                if (0 == count % 10000) {
                    log.debug("noun antonyms: " + count);
                }
                Synset source = it.next();

                cartPr(keys, source.getPointers(PointerType.PART_MERONYM));
                cartPr(keys, source.getPointers(PointerType.SUBSTANCE_MERONYM));
                cartPr(keys, source.getPointers(PointerType.MEMBER_MERONYM));
            }

            log.info("Noun antonyms: " + keys.size());
            return keys;
        } catch (JWNLException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static Set<Long> findNounHypernyms(Dictionary dic) throws SMatchException {
        log.info("Creating noun hypernyms array...");
        try {
            Set<Long> keys = new HashSet<>();
            int count = 0;
            Iterator<Synset> it = dic.getSynsetIterator(POS.NOUN);
            while (it.hasNext()) {
                count++;
                if (0 == count % 10000) {
                    log.debug("noun hypernyms: " + count);
                }
                Synset source = it.next();
                long sourceOffset = source.getOffset();
                traverseTreeMG(keys, PointerUtils.getHypernymTree(source), sourceOffset);
                traverseTreeMG(keys, PointerUtils.getInheritedHolonyms(source), sourceOffset);
                traverseTreeMG(keys, PointerUtils.getInheritedMemberHolonyms(source), sourceOffset);
                traverseTreeMG(keys, PointerUtils.getInheritedPartHolonyms(source), sourceOffset);
                traverseTreeMG(keys, PointerUtils.getInheritedSubstanceHolonyms(source), sourceOffset);
                traverseListMG(keys, PointerUtils.getHolonyms(source), sourceOffset);
                traverseListMG(keys, PointerUtils.getMemberHolonyms(source), sourceOffset);
                traverseListMG(keys, PointerUtils.getPartHolonyms(source), sourceOffset);
                traverseListMG(keys, PointerUtils.getSubstanceHolonyms(source), sourceOffset);
            }
            log.info("Noun hypernyms: " + keys.size());
            return keys;
        } catch (JWNLException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static Set<Long> findVerbHypernyms(Dictionary dic) throws SMatchException {
        log.info("Creating verb hypernyms array...");
        try {
            Set<Long> keys = new HashSet<>();
            int count = 0;
            Iterator<Synset> it = dic.getSynsetIterator(POS.VERB);
            while (it.hasNext()) {
                count++;
                if (0 == count % 1000) {
                    log.debug("verb hypernyms: " + count);
                }
                Synset source = it.next();
                long sourceOffset = source.getOffset();
                traverseTreeMG(keys, PointerUtils.getHypernymTree(source), sourceOffset);
            }
            log.info("Verb hypernyms: " + keys.size());
            return keys;
        } catch (JWNLException e) {
            throw new SMatchException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static void cartPr(Set<Long> keys, List<Pointer> t) throws JWNLException {
        for (int i = 0; i < t.size(); i++) {
            Pointer ps = t.get(i);
            long sourceOffset = ps.getTargetSynset().getOffset();
            for (int j = i + 1; j < t.size(); j++) {
                Pointer pt = t.get(j);
                long targetOffset = pt.getTargetSynset().getOffset();
                if (sourceOffset != targetOffset) {
                    long key;
                    if (targetOffset > sourceOffset) {
                        key = (targetOffset << 32) + sourceOffset;
                    } else {
                        key = (sourceOffset << 32) + targetOffset;
                    }
                    keys.add(key);
                }
            }
        }
    }

    private static void traverseListMG(Set<Long> keys, PointerTargetNodeList pointers, long sourceOffset) {
        for (Object pointer : pointers) {
            long targetOffset = ((PointerTargetNode) pointer).getSynset().getOffset();
            if (sourceOffset != targetOffset) {
                long key = (sourceOffset << 32) + targetOffset;
                keys.add(key);
            }
        }
    }

    private static void traverseListSym(Set<Long> keys, PointerTargetNodeList pointers, long sourceOffset) {
        for (Object ptn : pointers) {
            long targetOffset = ((PointerTargetNode) ptn).getSynset().getOffset();
            if (sourceOffset != targetOffset) {
                long key;//null;
                if (targetOffset > sourceOffset) {
                    key = (targetOffset << 32) + sourceOffset;
                } else {
                    key = (sourceOffset << 32) + targetOffset;
                }
                keys.add(key);
            }
        }
    }

    private static void traverseTreeMG(Set<Long> keys, PointerTargetTree syn, long sourceOffset) {
        for (Object aMGListsList : syn.toList()) {
            for (Object ptn : (PointerTargetNodeList) aMGListsList) {
                long targetOffset = ((PointerTargetNode) ptn).getSynset().getOffset();
                if (sourceOffset != targetOffset) {
                    long key = (sourceOffset << 32) + targetOffset;
                    keys.add(key);
                }
            }
        }
    }

    private static void traverseTree(Set<Long> keys, PointerTargetTree syn, long sourceOffset) {
        for (Object aMGListsList : syn.toList()) {
            for (Object ptn : (PointerTargetNodeList) aMGListsList) {
                long targetOffset = ((PointerTargetNode) ptn).getSynset().getOffset();
                if (sourceOffset != targetOffset) {
                    long key;
                    if (targetOffset > sourceOffset) {
                        key = (targetOffset << 32) + sourceOffset;
                    } else {
                        key = (sourceOffset << 32) + targetOffset;
                    }
                    keys.add(key);
                }
            }
        }
    }
}
