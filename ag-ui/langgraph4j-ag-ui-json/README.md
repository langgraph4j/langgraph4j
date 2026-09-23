# LangGraph4j AG-UI JSON support

This module provides a Jackson-based implementation of the AG-UI serialization protocol, used to
serialize/deserialize AG-UI core model classes (events, messages, interrupt/resume outcomes, ...)
to/from JSON.

## Classes overview

### AGUIJacksonSerializer
Implementation of the AG-UI `Serializer` interface, backed by a Jackson `ObjectMapper` configured to ignore unknown properties on read and to omit null
properties on write. It registers the mixins listed below (only if not already present) so that
AG-UI core classes, which are not directly Jackson-annotated, can be correctly (de)serialized.

### Jackson Mixins

- `EventMixin`
- `MessageMixin`
- `OutcomeMixin`
- `ResumeMixin`
- `StateMixin`
- `EnumWithValueMixin`
