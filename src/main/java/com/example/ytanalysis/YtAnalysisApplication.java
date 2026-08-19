package com.example.ytanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The Spring Boot entry point — the Java equivalent of {@code python -m src.cli}.
 *
 * <p>In Python the whole program lives in {@code cli.py::main()}. Here we only have a
 * thin {@code main()} that hands control to Spring, and everything else is a bean that
 * Spring wires together. Two things are worth understanding before reading on:
 *
 * <ol>
 *   <li><b>{@code @SpringBootApplication}</b> is one annotation standing for three:
 *       {@code @EnableAutoConfiguration} (Spring guesses good defaults from the jars on
 *       the classpath), {@code @ComponentScan} (finds every {@code @Component}/@Service/
 *       @RestController} in this package and below), and {@code @Configuration} (allow
 *       {@code @Bean} methods here). In short: it bootstraps the IoC container.</li>
 *   <li><b>Dependency injection</b> — the core of Spring. Instead of each class
 *       constructing its collaborators with {@code new}, Spring "wires" them from the
 *       container (annotated fields/methods/constructors). When {@code main} runs, Spring
 *       creates every bean, resolving constructor arguments — so you never write
 *       {@code new OpenRouterClient(...)} yourself.</li>
 * </ol>
 */
@SpringBootApplication
public class YtAnalysisApplication {

    /**
     * Program entry point.
     *
     * <p>This is intentionally different from a textbook "web-only" Spring Boot app:
     * the same jar must work both as a {@link WebApplicationType#SERVLET web} server
     * (the REST API) and as a plain CLI. We look at the raw command-line args ourselves:
     *
     * <ul>
     *   <li>No args → we run as a REST server (Tomcat starts on :8080).</li>
     *   <li>Any arg (e.g. {@code --url ...}) → we switch the application type to
     *       {@link WebApplicationType#NONE}, so <b>no Tomcat is started</b> — there is
     *       nothing for it to stay alive for, and it would block the CLI from exiting.</li>
     * </ul>
     */
    public static void main(String[] args) {
        // Build the SpringApplication ourselves so we can tweak it before run().
        SpringApplication app = new SpringApplication(YtAnalysisApplication.class);

        boolean cliMode = args.length > 0;

        // CLI mode: disable the embedded web server when the user passed any argument.
        // There is nothing for Tomcat to stay alive for, and it would block the CLI from
        // exiting. In REST (no-args) mode we leave the SERVLET type so Tomcat starts.
        if (cliMode) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }

        // Launch the IoC container, create every bean, then run CommandLineRunners.
        ConfigurableApplicationContext context = app.run(args);

        // Only the CLI must terminate the JVM. (REST mode must return here and let Tomcat's
        // non-daemon threads keep the process alive to serve requests.)
        if (cliMode) {
            // SpringApplication.run() alone NEVER calls System.exit(). That matters for the
            // CLI: without this the JVM would not stop, and the exit code would be lost.
            // SpringApplication.exit() looks up any ExitCodeGenerator beans (we have one:
            // CliRunner) and derives the code, so `echo $LASTEXITCODE` is correct after a
            // `java -jar`.
            int exitCode = SpringApplication.exit(context);
            System.exit(exitCode);
        }
    }
}