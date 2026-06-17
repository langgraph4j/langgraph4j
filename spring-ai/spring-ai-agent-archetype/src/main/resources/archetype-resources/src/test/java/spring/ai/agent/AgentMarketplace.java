#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.spring.ai.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public interface AgentMarketplace {

    class Tools {
        record Product(
                @JsonPropertyDescription("the product name") String name,
                @JsonPropertyDescription("the product price") double price,
                @JsonPropertyDescription("the product price currency") String currency) {}

        @Tool( description="search for a specific product in the marketplace")
        Product searchByProduct(@ToolParam( description="the product name to search") String product, ToolContext toolContext) {
            return new Product( "X", 1000, "EUR" );
        }

    }

    static Tools tools() {
        return new Tools();
    }
}
