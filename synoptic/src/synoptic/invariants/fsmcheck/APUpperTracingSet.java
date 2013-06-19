package synoptic.invariants.fsmcheck;

import synoptic.invariants.BinaryInvariant;
import synoptic.model.event.EventType;
import synoptic.model.interfaces.INode;
import synoptic.util.time.ITime;

public class APUpperTracingSet<T extends INode<T>> extends
        ConstrainedTracingSet<T> {

    /**
     * State0: Neither A nor B seen
     */
    HistoryNode s0;

    /**
     * State1: A seen
     */
    HistoryNode s1;

    /**
     * State2: A seen, then B seen within time bound
     */
    HistoryNode s2;

    /**
     * State3: B seen first or after A but out of time bound
     */
    HistoryNode s3;

    public APUpperTracingSet(EventType a, EventType b, ITime tBound) {
        super(a, b, tBound);
    }

    public APUpperTracingSet(BinaryInvariant inv) {
        super(inv);
    }

    @Override
    public void setInitial(T input) {

        EventType name = input.getEType();

        // Get max time delta of all transitions
        ITime tNew = getMaxTimeDelta(input.getAllTransitions());
        
        HistoryNode newHistory = new HistoryNode(input, null, 1);
        s0 = s1 = s2 = s3 = null;

        // A  =>  s0 -> s1
        if (a.equals(name)) {
            s1 = newHistory;
            t = tNew;
            
        // B  =>  s0 -> s3
        } else if (b.equals(name)) {
            s3 = newHistory;
            
        // ![A,B]  =>  s0 -> s0
        } else {
            s0 = newHistory;
        }
    }

    @Override
    public void transition(T input) {

        EventType name = input.getEType();

        // Get max time delta of all transitions
        ITime tNew = getMaxTimeDelta(input.getAllTransitions());

        // Increment running time delta by incoming one (tNew)
        t = t.incrBy(tNew);

        // Check if the new time delta is larger than the upper-bound time
        // constraint (tBound)
        boolean overTime;
        if (t.compareTo(tBound) <= 0) {
            overTime = false;
        } else {
            overTime = true;
        }

        // Precompute whether this event is the A or B of this invariant
        boolean isA = false;
        boolean isB = false;
        if (a.equals(name)) {
            isA = true;
        } else if (b.equals(name)) {
            isB = true;
        }

        // Store old state nodes
        HistoryNode s0Old = s0;
        HistoryNode s1Old = s1;
        HistoryNode s2Old = s2;
        HistoryNode s3Old = s3;

        // Final state nodes after this transition will be stored in these
        s0 = s1 = s2 = s3 = null;

        // s0 -> s0
        if (s0Old != null && !isA && !isB) {
            s0 = s0Old;
        }

        // s0 -> s1
        if (s0Old != null && isA) {
            s1 = s0Old;
            t = tNew;
        }

        // s1 -> s2
        if (s1Old != null && (isB && !overTime || !isB && !isA)) {
            s2 = s1Old;
            t.incrBy(tNew);
        }

        // s2 -> s2
        if (s2Old != null && (isB && !overTime || !isB && !isA)) {
            s2 = preferShorter(s2Old, s2);
            t.incrBy(tNew);
        }

        // s0 -> s3
        if (s0Old != null && isB) {
            s3 = s0Old;
        }

        // s1 -> s3
        if (s1Old != null && isB && overTime) {
            s3 = preferShorter(s1Old, s3);
        }

        // s2 -> s3
        if (s2Old != null && isB && overTime) {
            s3 = preferShorter(s2Old, s3);
        }

        // s3 -> s3
        if (s3Old != null) {
            s3 = preferShorter(s3Old, s3);
        }

        s0 = extend(input, s0);
        s1 = extend(input, s1);
        s2 = extend(input, s2);
        s3 = extend(input, s3);
    }

    @Override
    public HistoryNode failpath() {
        return s3;
    }

    @Override
    public APUpperTracingSet<T> copy() {
        APUpperTracingSet<T> result = new APUpperTracingSet<T>(a, b, tBound);
        result.s0 = s0;
        result.s1 = s1;
        result.s2 = s2;
        result.s3 = s3;
        result.t = t;
        return result;
    }

    @Override
    public void mergeWith(TracingStateSet<T> other) {
        APUpperTracingSet<T> casted = (APUpperTracingSet<T>) other;
        s0 = preferShorter(s0, casted.s0);
        s1 = preferShorter(s1, casted.s1);
        s2 = preferShorter(s2, casted.s2);
        s3 = preferShorter(s3, casted.s3);
        if (t.lessThan(casted.t)) {
            t = casted.t;
        }
    }

    @Override
    public boolean isSubset(TracingStateSet<T> other) {
        APUpperTracingSet<T> casted = (APUpperTracingSet<T>) other;
        if (casted.s0 == null && s0 != null) {
            return false;
        } else if (casted.s1 == null && s1 != null) {
            return false;
        } else if (casted.s2 == null && s2 != null) {
            return false;
        } else if (casted.s3 == null && s3 != null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("APUpper: ");
        appendWNull(result, s3); // Failure case first.
        result.append(" | ");
        appendWNull(result, s2);
        result.append(" | ");
        appendWNull(result, s1);
        result.append(" | ");
        appendWNull(result, s0);
        return result.toString();
    }
}
