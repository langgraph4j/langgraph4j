## Langgraph4j Studio - Spring Boot reference implementation

### Add dependency 

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-studio-springboot</artifactId>
    <version>1.6.0-beta4</version>
</dependency>
```

### Create a Custom Configuration Bean

```java
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;

import javax.management.InvalidApplicationException;

@Configuration
public class LangGraphStudioSampleConfig extends AbstractLangGraphStudioConfig {

    final LangGraphFlow flow;

    public LangGraphStudioSampleConfig() throws GraphStateException {

        var workflow = new StateGraph<>(AgentState::new);

        // define your workflow   
        this.flow = LangGraphFlow.builder()
                .title("LangGraph Studio")
                .stateGraph(workflow)
                .build();

    }

    @Override
    public LangGraphFlow getFlow() {
        return this.flow;
    }
}

```

### Create and Run a Standard Spring Boot Application

```java
@SpringBootApplication
public class LangGraphStudioApplication {

	public static void main(String[] args) {

		SpringApplication.run(LangGraphStudioApplication.class, args);
	}

}
```