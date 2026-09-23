package com.agui.json.mixins;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Jackson mixin interface for configuring JSON serialization of {@code Resume} objects.
 * <p>
 * Resume is a simple record ({@code interruptId}, {@code status}, {@code payload}), so no
 * polymorphic type discrimination is required. This mixin only pins the property order so
 * the emitted JSON consistently mirrors the AG-UI wire format:
 * <pre>{@code
 * {
 *   "interruptId": "int-1",
 *   "status": "resolved",
 *   "payload": { "confirmed": true }
 * }
 * }</pre>
 * <p>
 * The {@code status} field ({@code ResumeStatus}) relies on the shared
 * {@link EnumWithValueMixin} (registered separately) to serialize as its lowercase wire
 * value (e.g. {@code "resolved"}, {@code "cancelled"}) rather than its Java enum name.
 *
 * @see <a href="https://docs.ag-ui.com/concepts/interrupts">AG-UI Interrupts</a>
 */
@JsonPropertyOrder({"interruptId", "status", "payload"})
public interface ResumeMixin {
}
