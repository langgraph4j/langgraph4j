package org.bsc.langgraph4j.spring.ai.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public interface AgentPayment  {

    class Tools {

        record Transaction(
                @JsonPropertyDescription("the product name bought") String product,
                @JsonPropertyDescription("code operation") String code
        ) {}

        @Tool(name="submit-payment", description="submit a payment for purchasing a specific product")
        Transaction submitPayment(
                @ToolParam( description="the product name") String product,
                @ToolParam( description="the product price") double price,
                @ToolParam( description="the product currency") String currency,
                @ToolParam( description="International Bank Account Number (IBAN)") String iban,
                ToolContext toolContext ) {
            return new Transaction( product,"123456789B" );

        }

        @Tool(name="retrieve-iban", description="retrieve IBAN information")
        String retrieveIBAN()  {
            return """
                    GB82WEST12345698765432
                    """;
        }

    }

    static Tools tools() {
        return new Tools();
    }
}
