package org.bsc.langgraph4j.agui.sdk;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;


import static java.lang.String.format;

public class Tools {

    @Tool( description = "Send an email to someone")
    public String sendEmail(
            @ToolParam( description = "destination address") String to,
            @ToolParam( description = "subject of the email") String subject,
            @ToolParam( description = "body of the email") String body
    ) {
        // This is a placeholder for the actual implementation
        return format("mail sent to %s with subject %s", to, subject);
    }

    @Tool( description = "Get the weather in location")
    public String queryWeather(@ToolParam( description = "The query to use in your search.") String query) {
        // This is a placeholder for the actual implementation
        return "Cold, with a low of 13 degrees";
    }
}
