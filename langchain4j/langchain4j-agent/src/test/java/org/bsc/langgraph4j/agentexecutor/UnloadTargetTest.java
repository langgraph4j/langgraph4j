package org.bsc.langgraph4j.agentexecutor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the real {@link UnloadTarget} sealed hierarchy.
 *
 * <p>Covers:
 * <ol>
 *   <li>factory methods return the expected sealed variants</li>
 *   <li>{@code apply(List)} behaviour for all four variants</li>
 *   <li>NPE guards on predicate / ids-varargs inputs</li>
 *   <li>ids() factory deduplicates + drops nulls</li>
 *   <li>reflection check: sealed permits still cover exactly the 4 subtypes</li>
 *   <li>the class-load self-check fires correctly ({@code assertPermitsCoverExpected}
 *       would already have thrown on class-init if permits were wrong).</li>
 * </ol>
 */
class UnloadTargetTest {

    private static final List<String> FIXTURE =
            List.of("order-reply", "user-pref", "tool-guidance:foo", "debug");
    @Test
    void factoriesCreateExpectedVariants() {
        assertTrue(UnloadTarget.none()                           instanceof UnloadTarget.None);
        assertTrue(UnloadTarget.all()                            instanceof UnloadTarget.All);
        assertTrue(UnloadTarget.ids("a")                         instanceof UnloadTarget.Ids);
        assertTrue(UnloadTarget.ids("a","b")                     instanceof UnloadTarget.Ids);
        assertTrue(UnloadTarget.matching(s -> s.startsWith("o")) instanceof UnloadTarget.Matching);
    }

    @Test
    void idsVariantsCaptureValues() {
        assertEquals(List.of("a"),   ((UnloadTarget.Ids) UnloadTarget.ids("a")).ids());
        assertEquals(List.of("x","y"), ((UnloadTarget.Ids) UnloadTarget.ids("x","y")).ids());
    }

    @Test
    void idsFactoryDeduplicatesAndDropsNulls() {
        var ids = (UnloadTarget.Ids) UnloadTarget.ids("a", null, "a", "b", null);
        assertEquals(List.of("a","b"), ids.ids());
    }
    @Test
    void noneKeepsEverything() {
        var out = UnloadTarget.none().apply(FIXTURE);
        assertSame(FIXTURE, out, "unchanged → same reference returned (short-circuit)");
    }

    @Test
    void allClearsEverything() {
        var out = UnloadTarget.all().apply(FIXTURE);
        assertEquals(List.of(), out);
        // fixture non-empty, so must not share identity
        assertNotSame(FIXTURE, out);
    }

    @Test
    void idsDropsExactMatches() {
        var out = UnloadTarget.ids("order-reply", "debug", "NOT-THERE").apply(FIXTURE);
        assertEquals(List.of("user-pref", "tool-guidance:foo"), out);
    }

    @Test
    void idsDropsNonexistentWithoutAllocating() {
        var out = UnloadTarget.ids("NOT-THERE", "ALSO-NOT-THERE").apply(FIXTURE);
        assertSame(FIXTURE, out, "no real removal → same reference");
    }

    @Test
    void predicateDropsAcceptedValues() {
        var out = UnloadTarget.matching(s -> s.startsWith("tool-")).apply(FIXTURE);
        assertEquals(List.of("order-reply", "user-pref", "debug"), out);
    }

    @Test
    void emptyInputIsShortCircuited() {
        var in = List.<String>of();
        assertSame(in, UnloadTarget.none().apply(in));
        assertSame(in, UnloadTarget.all().apply(in));
        assertSame(in, UnloadTarget.ids("a").apply(in));
        assertSame(in, UnloadTarget.matching(s -> true).apply(in));
    }
    @Test
    void predicateFactoryRejectsNull() {
        assertThrows(NullPointerException.class, () -> UnloadTarget.matching(null));
    }

    @Test
    void idsFactoryVarargsRejectsNullArray() {
        assertThrows(NullPointerException.class, () -> UnloadTarget.ids((String[]) null));
    }

    @Test
    void applyRejectsNullList() {
        assertThrows(NullPointerException.class, () -> UnloadTarget.none().apply(null));
        assertThrows(NullPointerException.class, () -> UnloadTarget.all().apply(null));
        assertThrows(NullPointerException.class, () -> UnloadTarget.ids("x").apply(null));
        assertThrows(NullPointerException.class, () -> UnloadTarget.matching(s -> true).apply(null));
    }

    static final List<String> IMMUTABLE_FIXTURE =
            List.of("order-reply", "user-pref", "tool-guidance:foo", "debug");

    @Test
    void noneApply_InputListContentUnchanged() {
        var in = new ArrayList<>(IMMUTABLE_FIXTURE);
        var snap = List.copyOf(in);
        UnloadTarget.none().apply(in);
        assertEquals(snap, in, "None.apply must NOT mutate the input list");
    }

    @Test
    void allApply_InputListContentUnchanged() {
        var in = new ArrayList<>(IMMUTABLE_FIXTURE);
        var snap = List.copyOf(in);
        var out = UnloadTarget.all().apply(in);
        assertEquals(snap, in,  "All.apply must NOT mutate the input list");
        assertEquals(List.of(), out, "All.apply must return a new empty list");
        assertNotSame(in, out, "All.apply must return a NEW list, never the in ref");
    }

    @Test
    void idsApply_InputListContentUnchanged() {
        var in = new ArrayList<>(IMMUTABLE_FIXTURE);
        var snap = List.copyOf(in);
        var out = UnloadTarget.ids("order-reply", "debug").apply(in);
        assertEquals(snap, in,  "Ids.apply must NOT mutate the input list");
        assertEquals(List.of("user-pref", "tool-guidance:foo"), out);
    }

    @Test
    void predicateApply_InputListContentUnchanged() {
        var in = new ArrayList<>(IMMUTABLE_FIXTURE);
        var snap = List.copyOf(in);
        var out = UnloadTarget.matching(s -> s.startsWith("tool-")).apply(in);
        assertEquals(snap, in,  "Predicate.apply must NOT mutate the input list");
        assertEquals(List.of("order-reply","user-pref","debug"), out);
    }
    @Test
    void sealedPermitsCoversAllFourSubtypes() {
        var subtypes = Arrays.stream(UnloadTarget.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .sorted()
                .toList();
        assertEquals(List.of("All","Ids","Matching","None"), subtypes,
                "UnloadTarget sealed permits MUST match exactly the 4 record types. subtypes=" + subtypes);
    }
}
