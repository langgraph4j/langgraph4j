package org.bsc.langgraph4j.agentexecutor;

import java.util.*;
import java.util.function.Predicate;

/**
 * Declarative target for {@link SkillInjector}'s unload operation.
 * Sealed over exactly four variants: {@link None}, {@link All},
 * {@link Ids}, {@link Matching}. Construct via the static factories
 * {@link #none()}, {@link #all()}, {@link #ids(String...)},
 * {@link #matching(Predicate)}.
 *
 * <p>Project targets Java 17, so dispatch inside {@link SkillInjector} uses
 * an {@code if-else instanceof} chain — the lazy permits self-check in
 * {@link PermitsCheck} keeps every dispatch site honest.
 *
 * <p>Semantics: {@code None} keeps everything (no-op), {@code All} clears
 * every activated id (ephemeral), {@code Ids} drops a specific set,
 * {@code Matching} drops ids accepted by its predicate.
 */
public sealed interface UnloadTarget
        permits UnloadTarget.None, UnloadTarget.All, UnloadTarget.Ids, UnloadTarget.Matching {

    /** Keep every currently-activated skill. */
    record None() implements UnloadTarget {}

    /** Clear every currently-activated skill (ephemeral semantics). */
    record All()  implements UnloadTarget {}

    /** Drop a concrete set of skill ids. */
    record Ids(List<String> ids) implements UnloadTarget {}

    /** Drop every skill id accepted by {@link Matching#test()}. */
    record Matching(Predicate<String> test) implements UnloadTarget {}

    // factories

    /** @return a "no-op" target. */
    static None none() { return new None(); }

    /** @return a "clear all" target. */
    static All all()   { return new All();  }

    /** @return a "drop these ids" target (nulls / duplicates removed). */
    static Ids ids(String... ids) {
        Objects.requireNonNull(ids, "ids");
        var unique = new ArrayList<String>(ids.length);
        for (String id : ids) {
            if (id != null && !unique.contains(id)) unique.add(id);
        }
        return new Ids(Collections.unmodifiableList(unique));
    }

    /** @return a "drop ids matching predicate" target. */
    static Matching matching(Predicate<String> test) {
        return new Matching(Objects.requireNonNull(test, "test"));
    }

    // core operation

    /**
     * Apply this target to an {@code activeSkills} list and return the
     * remaining list. The input is never mutated.
     *
     * @param activeSkills never {@code null}, may be empty
     * @return filtered list (same ref if nothing changed, mutable copy if it did)
     */
    default List<String> apply(List<String> activeSkills) {
        Objects.requireNonNull(activeSkills, "activeSkills");
        assertPermitsCoverExpected();
        if (activeSkills.isEmpty()) return activeSkills;

        if (this instanceof None) {
            return activeSkills;
        }
        if (this instanceof All) {
            return List.of();
        }
        if (this instanceof Ids ids) {
            if (ids.ids().isEmpty()) return activeSkills;
            var drop = new HashSet<>(ids.ids());
            boolean anyHit = false;
            for (var id : activeSkills) if (drop.contains(id)) { anyHit = true; break; }
            if (!anyHit) return activeSkills;
            var out = new ArrayList<String>(activeSkills.size());
            for (var id : activeSkills) if (!drop.contains(id)) out.add(id);
            return out;
        }
        if (this instanceof Matching p) {
            var pred = p.test();
            boolean anyHit = false;
            for (var id : activeSkills) if (pred.test(id)) { anyHit = true; break; }
            if (!anyHit) return activeSkills;
            var out = new ArrayList<String>(activeSkills.size());
            for (var id : activeSkills) if (!pred.test(id)) out.add(id);
            return out;
        }
        throw unexpectedSubtype(this);
    }

    // helpers

    /**
     * Verifies (via reflection) that the sealed permits clause covers exactly
     * the four subtypes this module expects. Runs once, lazily, via the
     * {@link PermitsCheck} holder.
     *
     * <p>Why reflection? Because the project targets Java 17, dispatch sites
     * use manual {@code if-else instanceof} chains — the compiler does NOT
     * warn when a fifth permitted subtype is added and a chain is not
     * extended. This one-time volatile-read guard costs effectively nothing.
     */
    static void assertPermitsCoverExpected() {
        if (!PermitsCheck.RAN) {
            throw new AssertionError("PermitsCheck did not run");
        }
    }

    /** Lazy holder — validates permits once on first dispatch-through. */
    final class PermitsCheck {
        static final boolean RAN = runOnce();
        private static boolean runOnce() {
            var expected = Arrays.asList("None","All","Ids","Matching");
            var actual = Arrays.stream(UnloadTarget.class.getPermittedSubclasses())
                    .map(Class::getSimpleName)
                    .sorted()
                    .toList();
            var expectedSorted = expected.stream().sorted().toList();
            if (!expectedSorted.equals(actual)) {
                throw new ExceptionInInitializerError(
                        "UnloadTarget sealed permits mismatch. expected=" + expectedSorted
                                + " actual=" + actual);
            }
            return true;
        }
    }

    private static IllegalStateException unexpectedSubtype(UnloadTarget t) {
        assertPermitsCoverExpected();
        return new IllegalStateException(
                "Unexpected sealed subtype of UnloadTarget: " + t.getClass());
    }
}
