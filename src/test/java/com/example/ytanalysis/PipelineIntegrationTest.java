package com.example.ytanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ytanalysis.client.OpenRouterClient;
import com.example.ytanalysis.service.PipelineOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * A full-context integration test: {@code @SpringBootTest} boots the entire Spring
 * application (every bean, no web server by default), so this proves the whole object graph
 * <em>composes</em> — the same class of failure that took a few rounds to fix during the
 * port (e.g. a collaborator that needed to be a {@code @Component}).
 *
 * <p>It performs no network and no subprocess work — it only inspects the container, so it is
 * fast and safe to run in CI.
 */
@SpringBootTest
class PipelineIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theWholeBeanGraphWiresUp() {
        // Every @Service/@Component/@RestController must be instantiable. Any look-up here
        // throws if a constructor dependency is missing (like the CliArgumentParser bean
        // needed by CliRunner).
        assertThat(context.getBean(PipelineOrchestrator.class)).isNotNull();
        assertThat(context.getBean(OpenRouterClient.class)).isNotNull();
    }

    @Test
    void theRetryableClientIsWrappedInAnAopProxy() {
        // @Retryable relies on AOP: Spring must have replaced the plain OpenRouterClient
        // bean with a proxy that intercepts calls to the annotated leaf methods. This test
        // is the canary that @EnableRetry + @Retryable actually took effect — without it,
        // calls would silently call the raw bean and never retry.
        OpenRouterClient client = context.getBean(OpenRouterClient.class);
        assertThat(AopUtils.isAopProxy(client)).isTrue();
    }
}