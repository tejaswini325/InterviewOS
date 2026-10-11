package com.interviewos.config;

import com.interviewos.adaptive.AdaptiveEngine;
import com.interviewos.evaluation.McqScorer;
import com.interviewos.evaluation.RubricScorer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean AdaptiveEngine adaptiveEngine() { return new AdaptiveEngine(); }
    @Bean RubricScorer rubricScorer(AppProperties p) {
        var c = p.scoring();
        return new RubricScorer(new RubricScorer.Rules(c.correctThreshold(), c.partialThreshold(), c.stuffingCap(), c.minWordsPerConcept()));
    }
    @Bean McqScorer mcqScorer() { return new McqScorer(); }

    @Bean OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("InterviewOS API").version("1.0"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
