package synoptic.tests.units;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import synoptic.invariants.AlwaysFollowedInvariant;
import synoptic.invariants.AlwaysPrecedesInvariant;
import synoptic.invariants.ITemporalInvariant;
import synoptic.invariants.NeverFollowedInvariant;
import synoptic.invariants.RelationPath;
import synoptic.invariants.TemporalInvariantSet;
import synoptic.main.Main;
import synoptic.main.ParseException;
import synoptic.main.TraceParser;
import synoptic.model.Graph;
import synoptic.model.LogEvent;
import synoptic.model.Partition;
import synoptic.model.PartitionGraph;
import synoptic.model.Relation;
import synoptic.model.interfaces.IGraph;
import synoptic.model.interfaces.INode;
import synoptic.tests.SynopticTest;
import synoptic.util.InternalSynopticException;

/**
 * Checks the FSM model checker against the NASA model checker to compare their
 * results for generating counter examples of temporal invariants on graphs.
 * This is a parameterized JUnit test -- tests in this class are run with
 * parameters generated by method annotated with @Parameters.
 * 
 * @author ivan
 */
@RunWith(value = Parameterized.class)
public class ModelCheckersTests extends SynopticTest {

    /**
     * Generates parameters for this unit test. The first instance of this test
     * (using first set of parameters) will run using the FSM checker, while the
     * second instance (using the second set of parameters) will run using the
     * NASA model checker.
     * 
     * @return The set of parameters to pass to the constructor the unit test.
     */
    @Parameters
    public static Collection<Object[]> data() {
        Object[][] data = new Object[][] { { true }, { false } };
        return Arrays.asList(data);
    }

    public ModelCheckersTests(boolean useFSMChecker) {
        Main.useFSMChecker = useFSMChecker;
    }

    /**
     * Test that the graph g generates or not (depending on the value of
     * cExampleExists) a counter-example for invariant inv, which is exactly the
     * expectedPath through the graph g.
     */
    private static <T extends INode<T>> void testCExamplePath(IGraph<T> g,
            ITemporalInvariant inv, boolean cExampleExists, List<T> expectedPath)
            throws InternalSynopticException, ParseException {

        TemporalInvariantSet invs = new TemporalInvariantSet();
        invs.add(inv);
        List<RelationPath<T>> cexamples = invs.getAllCounterExamples(g);

        if (cexamples != null) {
            logger.info("model-checker counter-example:"
                    + cexamples.get(0).path);
        }

        if (!cExampleExists) {
            assertTrue(cexamples == null);
            return;
        }

        // Else, there should be just one counter-example
        assertTrue(cexamples.size() == 1);
        List<T> cexamplePath = cexamples.get(0).path;

        // logger.info("model-checker counter-example:" + cexamplePath);
        logger.info("correct counter-example:" + expectedPath);

        // Check that the counter-example is of the right length.
        assertTrue(cexamplePath.size() == expectedPath.size());

        // Check that cexamplePath is exactly the expectedPath
        for (int i = 0; i < cexamplePath.size(); i++) {
            assertTrue(cexamplePath.get(i) == expectedPath.get(i));
        }
        return;
    }

    /**
     * Test that the list of events representing a linear graph generates or not
     * (depending on value of cExampleExists) a single counter-example for
     * invariant inv that includes the prefix of linear graph of length up to
     * cExampleIndex (which starts counting at 0 = INITIAL, and may index
     * TERMINAL).
     */
    private static void testLinearGraphCExample(String[] events,
            ITemporalInvariant inv, boolean cExampleExists,
            int lastCExampleIndex) throws InternalSynopticException,
            ParseException {
        // Create the graph.
        Graph<LogEvent> g = SynopticTest.genInitialLinearGraph(events);
        Set<LogEvent> initNodes = g.getInitialNodes();

        if (!cExampleExists) {
            // Don't bother constructing the counter-example path.
            testCExamplePath(g, inv, cExampleExists, null);
            return;
        }

        // There should be just one initial node.
        assertTrue(initNodes.size() == 1);

        // Build the expectedPath by traversing the entire graph.
        LinkedList<LogEvent> expectedPath = new LinkedList<LogEvent>();
        LogEvent nextNode = initNodes.iterator().next();
        expectedPath.add(nextNode);
        for (int i = 1; i <= lastCExampleIndex; i++) {
            nextNode = nextNode.getTransitions().get(0).getTarget();
            expectedPath.add(nextNode);
        }
        testCExamplePath(g, inv, cExampleExists, expectedPath);
    }

    /**
     * The list of partially ordered events is condensed into a partition graph
     * (the most compressed model). This graph is then checked for existence or
     * not (depending on value of cExampleExists) of a counter-example for
     * invariant inv specified by cExampleLabels. The format for each event
     * string in the events array (?<TIME>) (?<TYPE>); the format for each
     * element in the counter-example path is (?<TYPE>). We get away with just
     * TYPE for specifying the counter-example because we will deal with the
     * initial partition graph -- where there is exactly one node for each event
     * type. <br />
     * <br />
     * NOTE: INITIAL is always included, therefore cExampleLabels should not
     * include it. However, if TERMINAL is to be included, it should be
     * specified in cExampleLabels.
     * 
     * @throws Exception
     */
    private static void testPartitionGraphCExample(String[] events,
            ITemporalInvariant inv, boolean cExampleExists,
            String[] cExampleLabels) throws Exception {

        TraceParser parser = new TraceParser();
        parser.addRegex("^(?<TIME>)(?<TYPE>)$");
        parser.addSeparator("^--$");
        PartitionGraph pGraph = genInitialPartitionGraph(events, parser);

        exportTestGraph(pGraph, 1);

        if (!cExampleExists) {
            // If there no cExample is expected then there's no reason to build
            // a path.
            testCExamplePath(pGraph, inv, cExampleExists, null);
            return;
        }

        // There should be just one initial node.
        Set<Partition> initNodes = pGraph.getInitialNodes();
        assertTrue(initNodes.size() == 1);

        LinkedList<Partition> expectedPath = new LinkedList<Partition>();
        Partition nextNode = initNodes.iterator().next();

        // Build the expectedPath by traversing the graph, starting from the
        // initial node by finding the appropriate partition at each hop by
        // matching on TIME of each cExampleEvents elements.
        expectedPath.add(nextNode);
        nextCExampleHop:
        for (int i = 0; i < cExampleLabels.length; i++) {
            // VectorTime nextTime = new VectorTime(cExampleLabels[i]);
            String nextLabel = cExampleLabels[i];
            for (Relation<Partition> transition : nextNode.getTransitions()) {
                for (LogEvent event : transition.getTarget().getMessages()) {
                    if (event.getLabel().equals(nextLabel)) {
                        nextNode = transition.getTarget();
                        expectedPath.add(nextNode);
                        continue nextCExampleHop;
                    }
                }
            }
            org.junit.Assert.fail("Unable to locate transition from "
                    + nextNode.toString() + " to a partition with label"
                    + nextLabel);
        }
        testCExamplePath(pGraph, inv, cExampleExists, expectedPath);
    }

    // //////////////////////////// AFby:

    /**
     * Tests that a linear graph with a cycle does not generate an AFby
     * c-example.
     * 
     * @throws Exception
     */
    @Test
    public void NoAFbyLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "1,1,0 a", "1,2,0 x", "1,3,0 y",
                "1,4,0 z", "1,5,0 a", "1,3,1 b", "1,6,0 x", "1,7,0 y",
                "1,7,1 b" };

        ITemporalInvariant inv = new AlwaysFollowedInvariant("a", "b",
                SynopticTest.defRelation);

        testPartitionGraphCExample(events, inv, false, null);
    }

    /**
     * Tests that a linear graph with a cycle does generate an AFby c-example.
     * 
     * @throws Exception
     */
    @Test
    public void AFbyLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "1,1,0 a", "1,2,0 x", "1,3,0 y",
                "1,4,0 z", "1,5,0 a", "1,3,1 w", "1,6,0 x", "1,7,0 y",
                "1,7,1 w" };

        ITemporalInvariant inv = new AlwaysFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        String[] cExampleLabels = new String[] { "a", "x", "y", "w",
                Main.terminalNodeLabel };
        testPartitionGraphCExample(events, inv, true, cExampleLabels);
    }

    /**
     * Tests that a linear graph does not generate an AFby c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void NoAFbyLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "a", "x", "y", "b" };
        ITemporalInvariant inv = new AlwaysFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, false, 0);
    }

    /**
     * Tests that a linear graph does generate an AFby c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void AFbyLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "a", "x", "y", "z" };
        ITemporalInvariant inv = new AlwaysFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, true, 5);
    }

    // //////////////////////////// NFby:

    /**
     * Tests that a linear graph with a cycle does not generate an NFby
     * c-example.
     * 
     * @throws Exception
     */
    @Test
    public void NoNFbyLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "1,1,0 a", "1,2,0 x", "1,3,0 y",
                "1,4,0 z", "1,5,0 a", "1,3,1 w", "1,6,0 x", "1,7,0 y",
                "1,7,1 w" };

        ITemporalInvariant inv = new NeverFollowedInvariant("a", "b",
                SynopticTest.defRelation);

        testPartitionGraphCExample(events, inv, false, null);
    }

    /**
     * Tests that a linear graph with a cycle does generate an NFby c-example.
     * 
     * @throws Exception
     */
    @Test
    public void NFbyLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "1,1,0 a", "1,2,0 x", "1,3,0 y",
                "1,4,0 z", "1,5,0 a", "1,3,1 b", "1,6,0 x", "1,7,0 y",
                "1,7,1 b" };

        ITemporalInvariant inv = new NeverFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        String[] cExampleLabels = new String[] { "a", "x", "y", "b" };
        testPartitionGraphCExample(events, inv, true, cExampleLabels);
    }

    /**
     * Tests that a linear graph does not generate an NFby c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void NoNFbyLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "a", "x", "y", "z" };
        ITemporalInvariant inv = new NeverFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, false, 0);
    }

    /**
     * Tests that a linear graph does generate an NFby c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void NFbyLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "a", "x", "y", "z", "b" };
        ITemporalInvariant inv = new NeverFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, true, 5);
    }

    // //////////////////////////// AP:

    /**
     * Tests that a linear graph with a cycle does not generate an AP c-example.
     * 
     * @throws Exception
     */
    @Test
    public void NoAPLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "1,1,0 a", "1,2,0 x", "1,3,0 y",
                "1,4,0 b", "1,5,0 a", "1,3,1 z", "1,6,0 x", "1,7,0 y",
                "1,7,1 z" };

        ITemporalInvariant inv = new AlwaysPrecedesInvariant("a", "b",
                SynopticTest.defRelation);

        testPartitionGraphCExample(events, inv, false, null);
    }

    /**
     * Tests that a linear graph with a cycle does generate an AP c-example.
     * 
     * @throws Exception
     */
    @Test
    public void APLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "1,1,0 z", "1,2,0 x", "1,3,0 y",
                "1,4,0 b", "1,5,0 z", "1,3,1 w", "1,6,0 x", "1,7,0 y",
                "1,7,1 w" };

        ITemporalInvariant inv = new AlwaysPrecedesInvariant("a", "b",
                SynopticTest.defRelation);
        String[] cExampleLabels = new String[] { "z", "x", "y", "b" };
        testPartitionGraphCExample(events, inv, true, cExampleLabels);
    }

    /**
     * Tests that a linear graph does not generate an AP c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void NoAPLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "x", "a", "x", "y", "b" };
        ITemporalInvariant inv = new AlwaysPrecedesInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, false, 0);
    }

    /**
     * Tests that a linear graph does generate an AP c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void APLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "x", "y", "z", "b", "a" };
        ITemporalInvariant inv = new AlwaysPrecedesInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, true, 4);
    }
}
