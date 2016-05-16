package it.unitn.disi.smatch.oracles.uby.test;

import org.junit.Test;

import it.unitn.disi.smatch.IMatchManager;
import it.unitn.disi.smatch.MatchManager;
import it.unitn.disi.smatch.SMatchException;
import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.mappings.IMappingElement;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;

public class TestUbyLinguisticOracle {

		
    /**
     * Creates the classifications, matches them and processes the results.
     *
     * @throws SMatchException SMatchException
     */
	@Test
    public void example() throws SMatchException {
		
		/*
        System.out.println("Starting example...");
        System.out.println("Creating MatchManager...");
        IMatchManager mm = MatchManager.getInstanceFromResource("/it/unitn/disi/smatch/oracles/uby/test/conf/s-match.xml");

        String example = "Courses";
        System.out.println("Creating source context...");
        IContext s = mm.createContext();
        s.createRoot(example);

        System.out.println("Creating target context...");
        IContext t = mm.createContext();
        INode root = t.createRoot("Course");
        INode node = root.createChild("College of Arts and Sciences");
        node.createChild("English");

        node = root.createChild("College Engineering");
        node.createChild("Civil and Environmental Engineering");

        System.out.println("Preprocessing source context...");
        mm.offline(s);

        System.out.println("Preprocessing target context...");
        mm.offline(t);

        System.out.println("Matching...");
        IContextMapping<INode> result = mm.online(s, t);

        System.out.println("Processing results...");
        System.out.println("Printing matches:");
        for (IMappingElement<INode> e : result) {
            System.out.println(e.getSource().nodeData().getName() + "\t" + e.getRelation() + "\t" + e.getTarget().nodeData().getName());
        }

        System.out.println("Done");
        */
    }

}
