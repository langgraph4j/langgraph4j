//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.9-SNAPSHOT

import io.javelit.core.Jt;
import org.bsc.javelit.JtIFrame;

public class JtIFrameApp {


    public static void main(String[] args) {

        var app = new JtIFrameApp();

        app.view();
    }

    public void view() {
        Jt.title("JtIFrame test App").use();

        JtIFrame.builder()
                .height("100vh")
                .uri("https://langgraph4j.github.io/langgraph4j/")
                .use();

    }
}
