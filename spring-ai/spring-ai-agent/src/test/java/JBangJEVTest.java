// DEPS org.springaicommunity:typesafe-bom:0.3.0
// DEPS org.springaicommunity:typesafe-spring-ai


import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.io.IOException;
import java.time.Duration;

public class JBangJEVTest {


    public static void main(String[] args) throws IOException {


        final var typeSafeClient = TypeSafeClient.builder()
                //.apiKey(System.getenv(TypeSafeConstants.API_KEY_ENV))
                //.baseUrl(TypeSafeConstants.DEFAULT_BASE_URL)
                //.defaultModel(TypeSafeModels.JEV_LATEST)
                .apiKey("NIMBUS_FAKE_KEY")
                .baseUrl("http://localhost:11434")
                .defaultModel("nimble")
                .timeout(Duration.ofSeconds(10))
                .retryPolicy(RetryPolicy.defaults())
                .build();

        SystemOneResponse response = typeSafeClient.systemOne(SystemOneRequest.builder()
                .state("Help! My payouts have been failing for 3 days.")
                .question("is_urgent",   Noul.of("Does this convey urgency?"))
                .question("department",  Choice.builder()
                        .instructions("Which team should handle this?")
                        .option("billing",   "Payments, invoicing, refunds")
                        .option("technical", "Bugs, outages, integrations")
                        .option("sales",     "Pricing, upgrades, new accounts")
                        .build())
                .question("frustration", Score.of("How frustrated is the customer?",
                        "Calm", "Frustrated", "Very angry"))
                .build());

        System.out.printf("urgency: %f\n", response.noulValue("is_urgent"));
        System.out.printf("department: %s\n", response.choiceValue("department"));
        System.out.printf("confidence: %f\n", response.choice("department").confidence());
        System.out.printf("frustration: %f\n", response.scoreValue("frustration"));
    }

}