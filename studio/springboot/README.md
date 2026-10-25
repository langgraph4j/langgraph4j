## Langgraph4j Studio - Spring Boot reference implementation

This module provide the reference implementation of LangGraph4j Studio using Spring Boot framework.

### Add dependency

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-studio-springboot</artifactId>
    <version>1.9.7</version>
</dependency>
```

From version `1.9.8` a new Studio implementation based on AG-UI protocol has been introduced.
This new implementation will be the only supported and previous is going to be deprecate.

## New Implementation (from `1.9.8`)

_<TO DO >_

## Legacy Implementation (before `1.9.8`)

This module provides the class `LangGraphStudioConfig` that registers LangGraph4j Studio Servlets in a [Spring Boot](https://spring.io/projects/spring-boot) server.  
For more details, see the [LangGraph4j Studio documentation]().


### Create a Custom Configuration Bean

```java
@Configuration
public class LangGraphStudioSampleConfig extends LangGraphStudioConfig {

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        StateGraph<AgentState> workflow = createWorkflow(); 

        // define your workflow

        var instance = LangGraphStudioServer.Instance.builder()
                .title("LangGraph Studio")
                .graph(workflow)
                .build();

        return Map.of("default", instance);
    }
}
```

### Create and Run a Standard Spring Boot Application

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LangGraphStudioApplication {

	public static void main(String[] args) {
		SpringApplication.run(LangGraphStudioApplication.class, args);
	}

}
```

### Open Studio

To run LangGraph4j Studio application, open browser and navigate to:

```
http://localhost:<port>/?instance=<instance_id>
```

where `<port>` is the port you specified in the configuration and `<instance_id>` is the instance id you specified as key in the `instanceMap` ("default" in the example above).
