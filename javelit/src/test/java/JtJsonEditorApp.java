//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.9-SNAPSHOT

import io.javelit.core.Jt;
import org.bsc.javelit.JtJsonEditor;

public class JtJsonEditorApp {

    public static void main(String[] args) {
        Jt.title("JtJsonEditor test App").use();

        String value = """
                {"name":"Ada Lovelace","age":36,"newsletter":true}
                """;
        String schema = """
                {
                  "title": "Profile",
                  "type": "object",
                  "properties": {
                    "name": {"type": "string", "minLength": 1},
                    "age": {"type": "integer", "minimum": 0},
                    "newsletter": {"type": "boolean"}
                  },
                  "required": ["name"]
                }
                """;

        String editedJson = JtJsonEditor.builder(value, "").use();
        Jt.markdown("""
        ```
        %s
        ```
        """.formatted(editedJson)).use();

        JtJsonEditor.builder(value, schema)
                .disabled(true)
                .use();
    }
}
