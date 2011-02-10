package synoptic.tests.units;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

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
import synoptic.model.PartitionGraph;
import synoptic.tests.SynopticTest;
import synoptic.util.InternalSynopticException;

/**
 * Tests for synoptic.invariants.TemporalInvariantSet class.
 * 
 * @author ivan
 */
public class TemporalInvariantSetTests extends SynopticTest {
    /**
     * We test invariants by parsing a string representing a log, and mining
     * invariants from the resulting graph.
     */
    static TraceParser parser;

    // Set up the parser state.
    static {
        parser = new TraceParser();
        try {
            parser.addRegex("^(?<TYPE>)$");
        } catch (ParseException e) {
            throw new InternalSynopticException(e);
        }
        parser.addSeparator("^--$");
    }

    /**
     * Generates a random log composed of three types of events ("a", "b", "c"),
     * and includes random partitions of the form "---".
     * 
     * @return The randomly generated log.
     */
    public static String[] genRandomLog() {
        ArrayList<String> log = new ArrayList<String>();
        // Arbitrary partition limit.
        int numPartitions = 5;

        // Event types allowed in the log, with partition string at index 0.
        String[] eventTypes = new String[] { "--", "a", "b", "c" };

        // Generate a random log.
        while (numPartitions != 0) {
            int rndIndex = Main.random.nextInt(eventTypes.length);
            log.add(eventTypes[rndIndex]);
            if (rndIndex == 0) {
                numPartitions -= 1;
            }
        }
        return log.toArray(new String[log.size()]);
    }

    /**
     * Creates a single string out of an array of strings, joined together and
     * delimited using a newline
     * 
     * @param strAr
     *            array of strings to join
     * @return the joined string
     */
    private static String joinString(String[] strAr) {
        StringBuilder sb = new StringBuilder();
        for (String s : strAr) {
            sb.append(s);
            sb.append('\n');
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    /**
     * Generates an initial graph based on a sequence of log events.
     * 
     * @param a
     *            log of events, each one in the format: (?<TYPE>)
     * @return an initial graph corresponding to the log of events
     * @throws ParseException
     * @throws InternalSynopticException
     */
    public static Graph<LogEvent> genInitialGraph(String[] events)
            throws ParseException, InternalSynopticException {
        String traceStr = joinString(events);
        List<LogEvent> parsedEvents = parser.parseTraceString(traceStr, "test",
                -1);
        return parser.generateDirectTemporalRelation(parsedEvents, true);
    }

    /**
     * Generates a TemporalInvariantSet based on a sequence of log events -- a
     * set of invariants that are mined from the log, and hold true for the
     * initial graph of the log.
     * 
     * @param a
     *            log of events, each one in the format: (?<TYPE>)
     * @return an invariant set for the input log
     * @throws ParseException
     * @throws InternalSynopticException
     */
    public static TemporalInvariantSet genInvariants(String[] events)
            throws ParseException, InternalSynopticException {
        Graph<LogEvent> inputGraph = genInitialGraph(events);
        PartitionGraph result = new PartitionGraph(inputGraph, true);
        return result.getInvariants();
    }

    /**
     * Generates a TemporalInvariantSet based on a sequence of log events -- a
     * set of invariants that are mined from the log, and hold true for the
     * initial graph of the log. This set includes all Tautological invariants
     * as well. So these are the "raw" invariants which are mined.
     * 
     * @param a
     *            log of events, each one in the format: (?<TYPE>)
     * @return an invariant set for the input log
     * @throws ParseException
     * @throws InternalSynopticException
     */
    public static TemporalInvariantSet genInvariantsIncludingTautological(
            String[] events) throws ParseException, InternalSynopticException {
        Graph<LogEvent> inputGraph = genInitialGraph(events);
        return TemporalInvariantSet.computeInvariants(inputGraph);
    }

    /**
     * Generates a list of tautological invariants for a trace. Currently these
     * only involve INITIAL and TERMINAL trace nodes.
     * 
     * @param allEvents
     *            The list of all events in the trace for which to generate the
     *            tautological invariants
     * @return A list of tautological temporal invariants for the input trace
     */
    public static List<ITemporalInvariant> genTautologicalInvariants(
            List<String> allEvents) {
        ArrayList<ITemporalInvariant> invs = new ArrayList<ITemporalInvariant>();
        for (String e : allEvents) {
            // x AFby TERMINAL
            invs.add(new AlwaysFollowedInvariant(e, Main.terminalNodeLabel,
                    SynopticTest.defRelation));
            // INITIAL AFby x
            invs.add(new AlwaysFollowedInvariant(Main.initialNodeLabel, e,
                    SynopticTest.defRelation));
            // x NFby INITIAL
            invs.add(new NeverFollowedInvariant(e, Main.initialNodeLabel,
                    SynopticTest.defRelation));
            // TERMINAL NFby x
            invs.add(new NeverFollowedInvariant(Main.terminalNodeLabel, e,
                    SynopticTest.defRelation));
            // INITIAL AP x
            invs.add(new AlwaysPrecedesInvariant(Main.initialNodeLabel, e,
                    SynopticTest.defRelation));
            // x AP TERMINAL
            invs.add(new AlwaysPrecedesInvariant(e, Main.terminalNodeLabel,
                    SynopticTest.defRelation));
        }
        // INITIAL AFby TERMINAL
        invs.add(new AlwaysFollowedInvariant(Main.initialNodeLabel,
                Main.terminalNodeLabel, SynopticTest.defRelation));
        // TERMINAL NFby INITIAL
        invs.add(new NeverFollowedInvariant(Main.terminalNodeLabel,
                Main.initialNodeLabel, SynopticTest.defRelation));
        // TERMINAL NFby TERMINAL
        invs.add(new NeverFollowedInvariant(Main.terminalNodeLabel,
                Main.terminalNodeLabel, SynopticTest.defRelation));
        // INITIAL NFby INITIAL
        invs.add(new NeverFollowedInvariant(Main.initialNodeLabel,
                Main.initialNodeLabel, SynopticTest.defRelation));
        // INITIAL AP TERMINAL
        invs.add(new AlwaysPrecedesInvariant(Main.initialNodeLabel,
                Main.terminalNodeLabel, SynopticTest.defRelation));
        logger.fine("Returning tautological invariants: " + invs.toString());
        return invs;
    }

    /**
     * Tests whether the invariants involving INITIAL\TERMINAL nodes are mined
     * correctly.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void testTautologicalInvariantMining()
            throws InternalSynopticException, ParseException {

        String[] log = genRandomLog();
        ArrayList<String> allEvents = new ArrayList<String>();
        for (String e : log) {
            if (!e.equals("--")) {
                allEvents.add(e);
            }
        }

        // Generate set including tautological invariants.
        TemporalInvariantSet s1 = genInvariantsIncludingTautological(log);

        // Generate set without tautological invariants.
        TemporalInvariantSet s2 = genInvariants(log);

        logger.fine("set including tautological: " + s1);
        logger.fine("set without tautological: " + s2);

        s2.addAll(genTautologicalInvariants(allEvents));
        logger.fine("set without tautological + true tautological: " + s2);

        assertTrue(s1.sameInvariants(s2));
    }

    /**
     * Tests the sameInvariants() method.
     */
    @Test
    public void testSameInvariants() {
        TemporalInvariantSet s1 = new TemporalInvariantSet();
        TemporalInvariantSet s2 = new TemporalInvariantSet();

        assertTrue(s1.sameInvariants(s2));
        assertTrue(s2.sameInvariants(s1));

        s1.add(new AlwaysFollowedInvariant("a", "b", SynopticTest.defRelation));
        assertFalse(s1.sameInvariants(s2));
        assertFalse(s2.sameInvariants(s1));

        s2.add(new AlwaysFollowedInvariant("a", "b", SynopticTest.defRelation));
        assertTrue(s1.sameInvariants(s2));
        assertTrue(s2.sameInvariants(s1));

        s1.add(new NeverFollowedInvariant("b", "a", SynopticTest.defRelation));
        assertFalse(s1.sameInvariants(s2));
        assertFalse(s2.sameInvariants(s1));

        // Add a similar looking invariant, but different in the B/b label.
        s2.add(new NeverFollowedInvariant("B", "a", SynopticTest.defRelation));
        assertFalse(s1.sameInvariants(s2));
        assertFalse(s2.sameInvariants(s1));
    }

    /**
     * Checks the mined invariants from a log with just two events types.
     * 
     * @throws ParseException
     * @throws InternalSynopticException
     */
    @Test
    public void mineBasicTest() throws ParseException,
            InternalSynopticException {
        String[] log = new String[] { "a", "b", "--" };
        TemporalInvariantSet minedInvs = genInvariants(log);
        TemporalInvariantSet trueInvs = new TemporalInvariantSet();

        trueInvs.add(new AlwaysFollowedInvariant("a", "b",
                SynopticTest.defRelation));
        trueInvs.add(new AlwaysPrecedesInvariant("a", "b",
                SynopticTest.defRelation));
        trueInvs.add(new NeverFollowedInvariant("b", "a",
                SynopticTest.defRelation));
        trueInvs.add(new NeverFollowedInvariant("b", "b",
                SynopticTest.defRelation));
        trueInvs.add(new NeverFollowedInvariant("a", "a",
                SynopticTest.defRelation));

        assertTrue(trueInvs.sameInvariants(minedInvs));
    }

    /**
     * Compose a log in which "a AFby b" is the only true invariant.
     * 
     * @throws ParseException
     * @throws InternalSynopticException
     */
    @Test
    public void mineAFbyTest() throws ParseException, InternalSynopticException {
        String[] log = new String[] { "a", "a", "b", "--", "b", "a", "b", "--" };
        TemporalInvariantSet minedInvs = genInvariants(log);
        TemporalInvariantSet trueInvs = new TemporalInvariantSet();

        trueInvs.add(new AlwaysFollowedInvariant("a", "b",
                SynopticTest.defRelation));
        assertTrue(trueInvs.sameInvariants(minedInvs));
    }

    /**
     * Compose a log in which "a NFby b" is the only true invariant.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void mineNFbyTest() throws ParseException, InternalSynopticException {
        String[] log = new String[] { "a", "a", "--", "a", "--", "b", "a",
                "--", "b", "b", "--", "b", "--" };
        TemporalInvariantSet minedInvs = genInvariants(log);
        TemporalInvariantSet trueInvs = new TemporalInvariantSet();

        trueInvs.add(new NeverFollowedInvariant("a", "b",
                SynopticTest.defRelation));
        assertTrue(trueInvs.sameInvariants(minedInvs));
    }

    /**
     * Compose a log in which "a AP b" is the only true invariant.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void mineAPbyTest() throws ParseException, InternalSynopticException {
        String[] log = new String[] { "a", "a", "b", "--", "a", "--", "a", "b",
                "a", "b", "--" };
        TemporalInvariantSet minedInvs = genInvariants(log);
        TemporalInvariantSet trueInvs = new TemporalInvariantSet();

        trueInvs.add(new AlwaysPrecedesInvariant("a", "b",
                SynopticTest.defRelation));
        assertTrue(trueInvs.sameInvariants(minedInvs));
    }

    /**
     * Tests the correctness of the invariants mined between two partitions,
     * which have no event types in common.
     * 
     * @throws ParseException
     * @throws InternalSynopticException
     */
    @Test
    public void mineAcrossMultiplePartitionsTest() throws ParseException,
            InternalSynopticException {
        String[] log1 = new String[] { "a", "b", "--" };
        String[] log2 = new String[] { "x", "y", "--" };
        String[] log3 = new String[] { "a", "b", "--", "x", "y", "--" };

        TemporalInvariantSet minedInvs1 = genInvariants(log1);
        TemporalInvariantSet minedInvs2 = genInvariants(log2);
        TemporalInvariantSet minedInvs3 = genInvariants(log3);

        // Mined log3 invariants should be a union of invariants mined form log1
        // and log2, as well as a few invariants that relate events between the
        // two partitions.
        TemporalInvariantSet trueInvs3 = new TemporalInvariantSet();
        for (ITemporalInvariant inv : minedInvs1) {
            trueInvs3.add(inv);
        }
        for (ITemporalInvariant inv : minedInvs2) {
            trueInvs3.add(inv);
        }

        trueInvs3.add(new NeverFollowedInvariant("a", "x",
                SynopticTest.defRelation));
        trueInvs3.add(new NeverFollowedInvariant("a", "y",
                SynopticTest.defRelation));
        trueInvs3.add(new NeverFollowedInvariant("b", "x",
                SynopticTest.defRelation));
        trueInvs3.add(new NeverFollowedInvariant("b", "y",
                SynopticTest.defRelation));

        trueInvs3.add(new NeverFollowedInvariant("x", "a",
                SynopticTest.defRelation));
        trueInvs3.add(new NeverFollowedInvariant("x", "b",
                SynopticTest.defRelation));
        trueInvs3.add(new NeverFollowedInvariant("y", "a",
                SynopticTest.defRelation));
        trueInvs3.add(new NeverFollowedInvariant("y", "b",
                SynopticTest.defRelation));

        logger.fine("mined: " + minedInvs3);
        assertTrue(trueInvs3.sameInvariants(minedInvs3));
    }

    /**
     * Mines invariants from a randomly generated log and then uses the model
     * checker to check that every mined invariant actually holds.
     * 
     * <pre>
     * TODO: this checks only one side of the approximation. We need a test to
     * check the other side.
     * </pre>
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void testApproximationExactnessTest() throws ParseException,
            InternalSynopticException {

        String[] log = genRandomLog();

        Graph<LogEvent> inputGraph = genInitialGraph(log);
        PartitionGraph graph = new PartitionGraph(inputGraph, true);
        TemporalInvariantSet minedInvs = graph.getInvariants();

        // Test with FSM checker.
        Main.useFSMChecker = true;
        List<RelationPath<LogEvent>> cExamples = minedInvs
                .getAllCounterExamples(inputGraph);
        if (cExamples != null) {
            logger.fine("log: " + log);
            logger.fine("minedInvs: " + minedInvs);
            logger.fine("[FSM] cExamples: " + cExamples);
        }
        assertTrue(cExamples == null);

        // Test with LTL checker.
        Main.useFSMChecker = false;
        cExamples = minedInvs.getAllCounterExamples(inputGraph);
        if (cExamples != null) {
            logger.fine("log: " + log);
            logger.fine("minedInvs: " + minedInvs);
            logger.fine("[LTL] cExamples: " + cExamples);
        }
        assertTrue(cExamples == null);
    }
}
