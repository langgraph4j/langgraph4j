//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.9-SNAPSHOT

import io.javelit.core.Jt;
import org.bsc.javelit.JtJsonEditor;

public class JtJsonEditorApp {

    public static void main(String[] args) {
        Jt.title("JtJsonEditor test App").use();

        String value = """
                {"prop1":"profile","prop2":"Ada Lovelace","prop3":36,"messages":[]}
                """;
        String schema = """
                {
                  "title": "State",
                  "type": "object",
                  "properties": {
                    "messages": {"type": "array", "minimum": 0}
                  },
                  "required": ["messages"]
                }
                """;

        String editedJson = JtJsonEditor.builder()
                .json(value)
                .schema(schema)
                .use();
        Jt.markdown("""
        ```
        %s
        ```
        """.formatted(editedJson)).use();

        JtJsonEditor.builder()
                .json(value)
                .schema(schema)
                .disabled(true)
                .use();
    }

    public static void main2(String[] args) {
        Jt.title("JtJsonEditor test App").use();

        String value = """
                {"prop1":"profile","prop2":"Ada Lovelace","prop3":36,"prop4":true}
                """;
        String schema = """
                {
                  "title": "Profile",
                  "type": "object",
                  "properties": {
                    "prop1": {"type": "string", "enum": ["profile", "contact"]},
                    "prop2": {"type": "string", "minLength": 1},
                    "prop3": {"type": "integer", "minimum": 0},
                    "prop4": {"type": "boolean"}
                  },
                  "required": ["prop1", "prop2"]
                }
                """;

        String editedJson = JtJsonEditor.builder()
                .json(value)
                .schema(schema)
                .use();
        Jt.markdown("""
        ```
        %s
        ```
        """.formatted(editedJson)).use();

        JtJsonEditor.builder()
                .json(value)
                .schema(schema)
                .disabled(true)
                .use();
    }
}
