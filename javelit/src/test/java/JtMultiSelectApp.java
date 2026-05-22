//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.8.17

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
                .disabled( disabled )
                .items( Set.of( "one", "two", "three", "four", "five") )
                .use();

        Jt.markdown( Objects.toString(result) ).use();

    }
}
