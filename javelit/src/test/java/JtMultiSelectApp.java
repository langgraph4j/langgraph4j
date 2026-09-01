//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.9.0-beta4

import io.javelit.core.Jt;
import org.bsc.javelit.JtMultiSelect;

import java.util.Objects;
import java.util.Set;

public class JtMultiSelectApp {


    public static void main(String[] args) {

        var app = new JtMultiSelectApp();

        app.view();
    }

    public void view() {
        Jt.title("JtMultiSelectApp test App").use();

        final var disabled = Jt.toggle("disable").value(false).use();


        var result = JtMultiSelect.builder()
                .key("multiSelect1")
                .disabled( disabled )
                .items( Set.of( "one", "two", "three", "four", "five") )
                .use();

        Jt.markdown( Objects.toString(result) ).use();

        var result2 = JtMultiSelect.builder()
                .key("multiSelect2")
                .disabled( disabled )
                .items( Set.of( "one", "two", "three", "four", "five") )
                .use();

        Jt.markdown( Objects.toString(result2) ).use();

    }
}
