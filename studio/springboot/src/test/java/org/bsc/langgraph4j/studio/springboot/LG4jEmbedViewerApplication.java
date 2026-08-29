package org.bsc.langgraph4j.studio.springboot;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.studio.LG4JEmbedViewerService;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@SpringBootApplication
public class LG4JEmbedViewerApplication {

    public static void main(String[] args) {

        SpringApplication.run(LG4JEmbedViewerApplication.class, args);
    }

    @Controller
    public static class ConsoleController implements CommandLineRunner {
        final LangGraphStudioServer.Instance instance;
        final LG4JEmbedViewerService viewerService;


        public ConsoleController(LangGraphStudioServer.Instance instance, LG4JEmbedViewerService viewerService) {
            this.instance = instance;
            this.viewerService = viewerService;
        }

        /**
         * Executes the command-line interface to demonstrate a Spring Boot application.
         * This method logs a welcome message, constructs a graph using an agent executor,
         * compiles it into a workflow, invokes the workflow with a specific input,
         * and then logs the final result.
         *
         * @param args Command line arguments (Unused in this context)
         * @throws Exception If any error occurs during execution
         */
        @Override
        public void run(String... args) throws Exception {
            final var agent = instance.graph().compile(instance.compileConfig());

            final var reader = new BufferedReader(new InputStreamReader(System.in));

            while (askToContinue(reader)) {
                agent.streamSnapshots(GraphInput.noArgs(), RunnableConfig.empty())
                        .forEachAsync(viewerService::dispatchAsync)
                        .thenAccept(snapshots -> {


                        })
                        .join();

            }

        }

        /**
         * Prompts the user on the console asking whether to run the graph again
         * or exit the application.
         *
         * @param reader the reader used to read the user's answer from standard input
         * @return {@code true} if the user wants to run again, {@code false} to exit
         * @throws java.io.IOException if an I/O error occurs while reading the input
         */
        private boolean askToContinue(BufferedReader reader) throws java.io.IOException {
            while( true ) {
                System.out.println("\nRun? (y/n): ");

                final var line = reader.readLine();
                if( line == null ) {
                    return false;
                }

                final var answer = line.trim().toLowerCase();
                if( answer.equals("y") || answer.equals("yes") ) {
                    return true;
                }
                if( answer.equals("n") || answer.equals("no") ) {
                    return false;
                }

                System.out.println("Please answer 'y' or 'n'.");
            }
        }
    }
}
