//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.8.12

import io.javelit.core.Jt;
import org.bsc.javelit.JtSpinner;

import java.time.Duration;
import java.time.Instant;

public class JtSpinnerTestApp {


    public static void main(String[] args) {

        var app = new JtSpinnerTestApp();

        app.view();
    }

    public void view() {
        Jt.title("JtSpinner test App").use();

        var overlay = Jt.toggle("overlay").value(false).use();

        Jt.divider().use();

        if( Jt.button("show spinner (use case 1)").use() ) {

            JtSpinner.builder()
                    .message("**this is the spinner test**")
                    .onStart(() -> {
                        try {
                            Thread.sleep(1000 * 5);
                        } catch (InterruptedException e) {
                            Jt.error("interrupted exception");
                        }
                        return "my result";
                    })
                    .onComplete((result, elapsed) ->
                            Jt.info("**Completed in** %ds".formatted(elapsed.toSeconds())))
                    .overlay()
                    .use();
        }

        if( Jt.button("show spinner (use case 2)").use() ) {

            var spinner = JtSpinner.builder()
                    .message("**this is the spinner test**")
                    .overlay()
                    .use();

                    final var start = Instant.now();
                    try {
                        Thread.sleep(1000 * 5);
                        final var elapsed = Duration.between(start, Instant.now());

                        Jt.info("**Completed in** %ds".formatted(elapsed.toSeconds())).use(spinner);
                    } catch (InterruptedException e) {
                        Jt.error("interrupted exception").use(spinner);
                    }


        }
    }
}
