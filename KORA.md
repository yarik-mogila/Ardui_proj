# Kora Framework Guide for Building Microservices

## Audience and How to Use This Guide
This guide is for:
- Backend engineers building Kora services in Java or Kotlin.
- Coding agents that need deterministic, implementation-ready instructions.

This document is written as an operational playbook, not as a high-level overview.
Use it in this order:
1. Set up baseline project structure and dependencies.
2. Pick required modules.
3. Follow each module section strictly in sequence: `Do this first` -> `Then this` -> `Verify this`.
4. Use the final checklist before marking work done.

Version target in all dependency snippets: `ru.tinkoff.kora:kora-parent:1.2.9`.

Scope covered in depth:
- Components (DI)
- Tests
- DB (JDBC, R2DBC, Vertx, Cassandra)
- HTTP Server
- HTTP Client
- gRPC Server
- gRPC Client
- OpenTelemetry Integration
- Kafka
- Configuration
- Caching (Caffeine, Redis)
- OpenAPI Generator (HTTP client + server codegen)

## Prerequisites and Baseline Project Setup
### Environment requirements
- JDK 17+ (JDK 21 is also used in template projects).
- Gradle 7+ (8+ recommended).
- Docker or compatible container runtime for integration tests (Testcontainers).
- For gRPC: Protobuf compiler via Gradle plugin.

### Baseline repository layout
Recommended service layout:
```text
src/main/java|kotlin/...       # app code
src/main/resources/application.conf or application.yaml
src/main/resources/db/migration # db migrations if needed
src/test/...                   # unit/integration tests
build.gradle or build.gradle.kts
```

### Baseline Java build file setup
Use this baseline in `build.gradle` and add module dependencies later.

```groovy
plugins {
    id "java"
    id "application"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations {
    koraBom
    annotationProcessor.extendsFrom(koraBom)
    compileOnly.extendsFrom(koraBom)
    implementation.extendsFrom(koraBom)
    api.extendsFrom(koraBom)
    testImplementation.extendsFrom(koraBom)
    testAnnotationProcessor.extendsFrom(koraBom)
}

dependencies {
    koraBom platform("ru.tinkoff.kora:kora-parent:1.2.9")
    annotationProcessor "ru.tinkoff.kora:annotation-processors"
    testAnnotationProcessor "ru.tinkoff.kora:annotation-processors"
}

application {
    applicationName = "application"
    mainClass = "com.example.Application"
    applicationDefaultJvmArgs = ["-Dfile.encoding=UTF-8"]
}
```

### Baseline Kotlin build file setup
Use this baseline in `build.gradle.kts` and add module dependencies later.

```kotlin
plugins {
    id("application")
    kotlin("jvm") version "1.9.25"
    id("com.google.devtools.ksp") version "1.9.25-1.0.20"
}

val koraBom: Configuration by configurations.creating
configurations {
    ksp.get().extendsFrom(koraBom)
    compileOnly.get().extendsFrom(koraBom)
    api.get().extendsFrom(koraBom)
    implementation.get().extendsFrom(koraBom)
    testImplementation.get().extendsFrom(koraBom)
}

dependencies {
    koraBom(platform("ru.tinkoff.kora:kora-parent:1.2.9"))
    ksp("ru.tinkoff.kora:symbol-processors")
    kspTest("ru.tinkoff.kora:symbol-processors")
}

kotlin {
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    sourceSets.main { kotlin.srcDir("build/generated/ksp/main/kotlin") }
    sourceSets.test { kotlin.srcDir("build/generated/ksp/test/kotlin") }
}

application {
    applicationName = "application"
    mainClass.set("com.example.ApplicationKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}
```

### Standalone note
Examples below are fully embedded in this document.

## Minimal Application Bootstrap (Java and Kotlin)
Use this minimal bootstrap before adding module-specific logic.

### Java
```java
package com.example;

import ru.tinkoff.kora.application.graph.KoraApplication;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.hocon.HoconConfigModule;
import ru.tinkoff.kora.logging.logback.LogbackModule;

@KoraApp
public interface Application extends HoconConfigModule, LogbackModule {
    static void main(String[] args) {
        KoraApplication.run(ApplicationGraph::graph);
    }
}
```

### Kotlin
```kotlin
package com.example

import ru.tinkoff.kora.application.graph.KoraApplication
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.config.hocon.HoconConfigModule
import ru.tinkoff.kora.logging.logback.LogbackModule

@KoraApp
interface Application : HoconConfigModule, LogbackModule

fun main() {
    KoraApplication.run { ApplicationGraph.graph() }
}
```

### Minimum runnable path
```bash
./gradlew classes
./gradlew run
./gradlew test
```

## Configuration
### What this module solves
Provides structured, typed, environment-aware runtime configuration with HOCON or YAML and compile-time extractor generation.

### Do this first
1. Choose HOCON (`config-hocon`) or YAML (`config-yaml`) as primary source.
2. Add module dependency and include corresponding config module in `@KoraApp`.
3. Define typed configuration contracts with `@ConfigSource` and `@ConfigValueExtractor`.

### Then this
1. Configure required and optional values with explicit env substitution.
2. Bind configuration into components by constructor injection.
3. Add startup-safe defaults for non-critical values.

### Verify this
1. Service starts with all required env vars present.
2. Missing required value fails early at startup.
3. Typed config values are parsed to expected runtime types.

### Required dependencies
Java (`build.gradle`):
```groovy
dependencies {
    implementation "ru.tinkoff.kora:config-hocon" // or config-yaml
    implementation "ru.tinkoff.kora:logging-logback"
}
```

Kotlin (`build.gradle.kts`):
```kotlin
dependencies {
    implementation("ru.tinkoff.kora:config-hocon") // or config-yaml
    implementation("ru.tinkoff.kora:logging-logback")
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends HoconConfigModule, LogbackModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application : HoconConfigModule, LogbackModule
```

### Configuration keys
HOCON example:
```hocon
foo {
  valueEnvRequired = ${ENV_VALUE_REQUIRED}
  valueEnvOptional = ${?ENV_VALUE_OPTIONAL}
  valueEnvDefault = ${?ENV_VALUE_DEFAULT}
  valueEnvDefault = "someDefaultValue"
  valueDuration = "250s"
  valueListAsArray = ["v1", "v2"]
  bar {
    someBarString = "someString"
    baz.someBazString = "someString"
  }
}
```

YAML example:
```yaml
foo:
  valueEnvRequired: ${ENV_VALUE_REQUIRED}
  valueEnvOptional: ${?ENV_VALUE_OPTIONAL}
  valueEnvDefault: ${ENV_VALUE_DEFAULT:someDefaultValue}
  valueDuration: "250s"
  valueListAsArray: ["v1", "v2"]
  bar:
    someBarString: "someString"
    baz:
      someBazString: "someString"
```

### Minimal Java example
```java
package com.example.config;

import jakarta.annotation.Nullable;
import java.time.Duration;
import ru.tinkoff.kora.config.common.annotation.ConfigSource;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigSource("foo")
public interface FooConfig {
    String valueEnvRequired();
    @Nullable String valueEnvOptional();
    String valueEnvDefault();
    Duration valueDuration();

    @ConfigValueExtractor
    interface BarConfig {
        String someBarString();
    }

    BarConfig bar();
}
```

### Minimal Kotlin example
```kotlin
package com.example.config

import ru.tinkoff.kora.config.common.annotation.ConfigSource
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
import java.time.Duration

@ConfigSource("foo")
interface FooConfig {
    fun valueEnvRequired(): String
    fun valueEnvOptional(): String?
    fun valueEnvDefault(): String
    fun valueDuration(): Duration

    @ConfigValueExtractor
    interface BarConfig {
        fun someBarString(): String
    }

    fun bar(): BarConfig
}
```

### Production-style example
#### Embedded source: FooConfig.java
```java
package ru.tinkoff.kora.example.config.hocon;

import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import ru.tinkoff.kora.config.common.annotation.ConfigSource;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigSource("foo")
public interface FooConfig {

    enum EnumValue {
        ANY,
        SOME
    }

    String valueEnvRequired();

    @Nullable
    String valueEnvOptional();

    String valueEnvDefault();

    String valueRef();

    String valueString();

    UUID valueUuid();

    Pattern valuePattern();

    EnumValue valueEnum();

    LocalDate valueLocalDate();

    LocalTime valueLocalTime();

    LocalDateTime valueLocalDateTime();

    OffsetTime valueOffsetTime();

    OffsetDateTime valueOffsetDateTime();

    Period valuePeriodAsInt();

    Period valuePeriodAsString();

    Duration valueDuration();

    int valueInt();

    long valueLong();

    BigInteger valueBigInt();

    double valueDouble();

    BigDecimal valueBigDecimal();

    boolean valueBoolean();

    List<String> valueListAsString();

    List<String> valueListAsArray();

    Set<String> valueSetAsString();

    Set<String> valueSetAsArray();

    Map<String, String> valueMap();

    Properties valueProperties();

    @ConfigValueExtractor
    interface BarConfig {

        String someBarString();

        BazConfig baz();

        @ConfigValueExtractor
        interface BazConfig {

            String someBazString();
        }
    }

    BarConfig bar();

    List<BarConfig> bars();
}
```

#### Embedded source: application.conf (HOCON config)
```hocon
foo {
  valueEnvRequired = ${ENV_VALUE_REQUIRED}
  valueEnvOptional = ${?ENV_VALUE_OPTIONAL}
  valueEnvDefault = ${?ENV_VALUE_DEFAULT}
  valueEnvDefault = "someDefaultValue"
  valueString = "SomeString"
  valueRef = ${foo.valueString}"Other"${foo.valueString}
  valueUuid = "20684ccb-81f8-4fac-8ec0-297b08ff993d"
  valuePattern = ".*somePattern.*"
  valueEnum = "ANY"
  valueLocalDate = "2020-10-10"
  valueLocalTime = "12:10:10"
  valueLocalDateTime = "2020-10-10T12:10:10"
  valueOffsetTime = "12:10:10+03:00"
  valueOffsetDateTime = "2020-10-10T12:10:10+03:00"
  valuePeriodAsInt = 1
  valuePeriodAsString = "1d"
  valueDuration = "250s"
  valueInt = 1
  valueLong = 2
  valueBigInt = 3
  valueDouble = 4.1
  valueBigDecimal = 5.1
  valueBoolean = true
  valueListAsString = "v1,v2"
  valueListAsArray = ["v1", "v2"]
  valueSetAsString = "v1,v2"
  valueSetAsArray = ["v1", "v2"]
  valueMap = {
    "k1" = "v1"
    "k2" = "v2"
  }
  valueProperties = {
    "k1" = "v1"
    "k2" = "v2"
  }
  bar = {
    someBarString = "someString"
    baz.someBazString = "someString"
  }
  bars = [
    {
      someBarString = "someString1"
      baz.someBazString = "someString1"
    },
    {
      someBarString = "someString2"
      baz.someBazString = "someString2"
    }
  ]
}
```

#### Embedded source: application.yaml (YAML config)
```yaml
foo:
  valueEnvRequired: ${ENV_VALUE_REQUIRED}
  valueEnvOptional: ${?ENV_VALUE_OPTIONAL}
  valueEnvDefault: ${ENV_VALUE_DEFAULT:someDefaultValue}
#  valueEnvDefault: "someDefaultValue"
  valueString: "SomeString"
  valueRef: ${foo.valueString}Other${foo.valueString}
  valueUuid: "20684ccb-81f8-4fac-8ec0-297b08ff993d"
  valuePattern: ".*somePattern.*"
  valueEnum: "ANY"
  valueLocalDate: "2020-10-10"
  valueLocalTime: "12:10:10"
  valueLocalDateTime: "2020-10-10T12:10:10"
  valueOffsetTime: "12:10:10+03:00"
  valueOffsetDateTime: "2020-10-10T12:10:10+03:00"
  valuePeriodAsInt: 1
  valuePeriodAsString: "1d"
  valueDuration: "250s"
  valueInt: 1
  valueLong: 2
  valueBigInt: 3
  valueDouble: 4.1
  valueBigDecimal: 5.1
  valueBoolean: true
  valueListAsString: "v1,v2"
  valueListAsArray: ["v1", "v2"]
  valueSetAsString: "v1,v2"
  valueSetAsArray: ["v1", "v2"]
  valueMap:
    k1: "v1"
    k2: "v2"
  valueProperties:
    k1: "v1"
    k2: "v2"
  bar:
    someBarString: "someString"
    baz:
      someBazString: "someString"
  bars:
    - someBarString: "someString1"
      baz:
        someBazString: "someString1"
    - someBarString: "someString2"
      baz:
        someBazString: "someString2"
```
### Testing approach
- Use `KoraConfigModification.ofString(...)` in tests to inject temporary config.
- For env-sensitive config, set system properties through `KoraConfigModification.withSystemProperty(...)`.

### Common errors and exact fixes
- Failure signature: startup fails with missing required key under `foo.valueEnvRequired`.
  - Fix: set `ENV_VALUE_REQUIRED` or provide explicit value in config file.
- Failure signature: parsing error for duration/period/UUID values.
  - Fix: use valid formats from examples (`"250s"`, `"2020-10-10"`, UUID string).

### Minimum runnable path
```bash
ENV_VALUE_REQUIRED=valueRequired ./gradlew run
./gradlew test
```

### Agent rules
1. Always create typed config interfaces before implementing business logic.
2. Never read raw config strings in service code when extractor can model it.
3. Treat required values as hard startup contracts.
4. Keep env var names stable and documented.

## Components (DI Container)
### What this module solves
Compile-time dependency graph generation, deterministic wiring, and runtime lifecycle management without reflection-based runtime DI.

### Do this first
1. Mark entrypoint interface with `@KoraApp`.
2. Add components via `@Component` classes or `@Module` factory methods.
3. Mark at least one meaningful root when required using `@Root`.

### Then this
1. Use constructor injection for all dependencies.
2. Resolve ambiguity with `@Tag` when multiple implementations exist.
3. Use `@KoraSubmodule` for multi-module architecture when needed.

### Verify this
1. Graph generation succeeds at compile time.
2. No unresolved component dependencies.
3. Tagged dependencies resolve exactly to intended implementation.

### Required dependencies
Java:
```groovy
dependencies {
    implementation "ru.tinkoff.kora:config-hocon"
    implementation "ru.tinkoff.kora:logging-logback"
}
```

Kotlin:
```kotlin
dependencies {
    implementation("ru.tinkoff.kora:config-hocon")
    implementation("ru.tinkoff.kora:logging-logback")
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends HoconConfigModule, LogbackModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application : HoconConfigModule, LogbackModule
```

### Configuration keys
No container-specific mandatory keys. Container behavior is compile-time and graph-driven.

### Minimal Java example
```java
package com.example.components;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;

@Component
final class UserRepository {
    String findName(long id) { return "user-" + id; }
}

@Root
@Component
final class UserService {
    private final UserRepository repository;

    UserService(UserRepository repository) {
        this.repository = repository;
    }

    String getName(long id) { return repository.findName(id); }
}
```

### Minimal Kotlin example
```kotlin
package com.example.components

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root

@Component
class UserRepository {
    fun findName(id: Long): String = "user-$id"
}

@Root
@Component
class UserService(private val repository: UserRepository) {
    fun getName(id: Long): String = repository.findName(id)
}
```

### Production-style example
#### Embedded source: Application.java (CRUD template)
```java
package ru.tinkoff.kora.example;

import ru.tinkoff.kora.application.graph.KoraApplication;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.hocon.HoconConfigModule;
import ru.tinkoff.kora.database.jdbc.JdbcDatabaseModule;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServerModule;
import ru.tinkoff.kora.json.module.JsonModule;
import ru.tinkoff.kora.logging.logback.LogbackModule;
import ru.tinkoff.kora.micrometer.module.MetricsModule;
import ru.tinkoff.kora.openapi.management.OpenApiManagementModule;
import ru.tinkoff.kora.resilient.ResilientModule;
import ru.tinkoff.kora.validation.module.ValidationModule;

@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        JdbcDatabaseModule,
        ValidationModule,
        JsonModule,
        CaffeineCacheModule,
        ResilientModule,
        MetricsModule,
        OpenApiManagementModule,
        UndertowHttpServerModule {

    static void main(String[] args) {
        KoraApplication.run(ApplicationGraph::graph);
    }
}
```

#### Embedded source: Application.kt (Kotlin CRUD)
```kotlin
package ru.tinkoff.kora.kotlin.example.crud

import ru.tinkoff.kora.application.graph.KoraApplication
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.config.hocon.HoconConfigModule
import ru.tinkoff.kora.database.jdbc.JdbcDatabaseModule
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServerModule
import ru.tinkoff.kora.json.module.JsonModule
import ru.tinkoff.kora.logging.logback.LogbackModule
import ru.tinkoff.kora.micrometer.module.MetricsModule
import ru.tinkoff.kora.openapi.management.OpenApiManagementModule
import ru.tinkoff.kora.resilient.ResilientModule
import ru.tinkoff.kora.validation.module.ValidationModule


@KoraApp
interface Application : HoconConfigModule, LogbackModule, JdbcDatabaseModule, ValidationModule, JsonModule,
    CaffeineCacheModule, ResilientModule, MetricsModule, OpenApiManagementModule, UndertowHttpServerModule

fun main() {
    KoraApplication.run { ApplicationGraph.graph() }
}
```

#### Embedded source: InterceptedController.java
```java
package ru.tinkoff.kora.example.http.server;

import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.InterceptWith;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerInterceptor;
import ru.tinkoff.kora.http.server.common.HttpServerModule;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

/**
 * @see ServerInterceptor - Intercepts all controllers on HttpServer
 * @see ControllerInterceptor - Intercepts all controler methods
 * @see MethodInterceptor - Intercepts particular method
 */
@InterceptWith(InterceptedController.ControllerInterceptor.class)
@Component
@HttpController
public final class InterceptedController {

    public static final class ControllerInterceptor implements HttpServerInterceptor {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Override
        public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain)
                throws Exception {
            logger.info("Controller Level Interceptor");
            return chain.process(context, request);
        }
    }

    public static final class MethodInterceptor implements HttpServerInterceptor {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Override
        public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain)
                throws Exception {
            logger.info("Method Level Interceptor");
            return chain.process(context, request);
        }
    }

    @Tag(HttpServerModule.class)
    @Component
    public static final class ServerInterceptor implements HttpServerInterceptor {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Override
        public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain)
                throws Exception {
            logger.info("Server Level Interceptor");
            return chain.process(context, request);
        }
    }

    @InterceptWith(MethodInterceptor.class)
    @HttpRoute(method = HttpMethod.GET, path = "/intercepted")
    public HttpServerResponse get() {
        return HttpServerResponse.of(200, HttpBody.plaintext("Hello world"));
    }
}
```
### Testing approach
- Validate container wiring using `@KoraAppTest` and `@TestComponent` injection.
- Prefer component-level tests for graph correctness and constructor contracts.

### Common errors and exact fixes
- Failure signature: compile-time error "component not found" for constructor dependency.
  - Fix: annotate dependency with `@Component` or provide factory in `@Module`.
- Failure signature: ambiguous dependency resolution.
  - Fix: apply `@Tag` to providers and consumers consistently.

### Minimum runnable path
```bash
./gradlew classes
./gradlew run
```

### Agent rules
1. Use constructor injection only.
2. Do not instantiate components manually in production code.
3. Prefer explicit tags over implicit assumptions when multiple candidates exist.
4. Keep `@KoraApp` interface slim and module-oriented.

## Testing
### What this module solves
Provides a Kora-native JUnit5 extension for graph-aware tests, component injection, graph replacement, and config overrides.

### Do this first
1. Add `test-junit5` dependency.
2. Annotate test class with `@KoraAppTest(...)`.
3. Inject test targets using `@TestComponent`.

### Then this
1. Replace dependencies using mocks (`@Mock`/`@MockK`) together with `@TestComponent`.
2. Add temporary test config using `KoraAppTestConfigModifier`.
3. Use Testcontainers for database/Kafka integration tests.

### Verify this
1. Test graph starts successfully.
2. Replaced test components are actually used by root components.
3. Integration tests isolate environment per test run.

### Required dependencies
Java:
```groovy
dependencies {
    testImplementation "ru.tinkoff.kora:test-junit5"
    testImplementation "org.mockito:mockito-core:5.18.0"
    testImplementation "org.testcontainers:junit-jupiter:1.19.8"
}
```

Kotlin:
```kotlin
dependencies {
    testImplementation("ru.tinkoff.kora:test-junit5")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
}
```

### @KoraApp wiring
Test can reuse main app or custom test app:

Java:
```java
@KoraAppTest(Application.class)
class ComponentTests {}
```

Kotlin:
```kotlin
@KoraAppTest(Application::class)
class ComponentTests
```

### Configuration keys
Use inline HOCON for tests:

```hocon
db {
  jdbcUrl = ${POSTGRES_JDBC_URL}
  username = ${POSTGRES_USER}
  password = ${POSTGRES_PASS}
  poolName = "kora"
}
pet-cache.maximumSize = 0
```

YAML equivalent:
```yaml
db:
  jdbcUrl: ${POSTGRES_JDBC_URL}
  username: ${POSTGRES_USER}
  password: ${POSTGRES_PASS}
  poolName: "kora"
pet-cache:
  maximumSize: 0
```

### Minimal Java example
```java
@KoraAppTest(Application.class)
class MyServiceTests {
    @org.mockito.Mock
    @TestComponent
    private MyRepository repository;

    @TestComponent
    private MyService service;

    @org.junit.jupiter.api.Test
    void shouldWork() {
        org.mockito.Mockito.when(repository.findName(1L)).thenReturn("A");
        org.junit.jupiter.api.Assertions.assertEquals("A", service.get(1L));
    }
}
```

### Minimal Kotlin example
```kotlin
@KoraAppTest(Application::class)
class MyServiceTests {
    @field:io.mockk.MockK
    @TestComponent
    lateinit var repository: MyRepository

    @TestComponent
    lateinit var service: MyService

    @org.junit.jupiter.api.Test
    fun shouldWork() {
        io.mockk.every { repository.findName(1L) } returns "A"
        org.junit.jupiter.api.Assertions.assertEquals("A", service.get(1L))
    }
}
```

### Production-style example
#### Embedded source: ComponentTests.java
```java
package ru.tinkoff.kora.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.Collections;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import ru.tinkoff.kora.example.openapi.http.server.model.PetCreateTO;
import ru.tinkoff.kora.example.openapi.http.server.model.PetUpdateTO;
import ru.tinkoff.kora.example.repository.PetRepository;
import ru.tinkoff.kora.example.service.PetCache;
import ru.tinkoff.kora.example.service.PetService;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestConfigModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraConfigModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;

@KoraAppTest(Application.class)
class ComponentTests implements KoraAppTestConfigModifier {

    @Mock
    @TestComponent
    private PetCache petCache;
    @Mock
    @TestComponent
    private PetRepository petRepository;

    @TestComponent
    private PetService petService;

    @NotNull
    @Override
    public KoraConfigModification config() {
        return KoraConfigModification.ofString("""
                resilient {
                   circuitbreaker.pet {
                     slidingWindowSize = 2
                     minimumRequiredCalls = 2
                     failureRateThreshold = 100
                     permittedCallsInHalfOpenState = 1
                     waitDurationInOpenState = 15s
                   }
                   timeout.pet.duration = 5000ms
                   retry.pet {
                     delay = 100ms
                     attempts = 0
                   }
                 }""");
    }

    @Test
    void updatePetWithNewCategoryCreated() {
        // given
        mockCache();
        mockRepository();

        var added = petService.add(new PetCreateTO("dog"));
        assertEquals(1, added.id());

        // when
        Mockito.when(petRepository.findById(anyLong())).thenReturn(Optional.of(added));
        var updated = petService.update(added.id(),
                new PetUpdateTO(PetUpdateTO.StatusEnum.PENDING, "cat"));
        assertTrue(updated.isPresent());
        assertEquals(1, updated.get().id());

        // then
        Mockito.verify(petRepository).insert(any());
    }

    @Test
    void updatePetWithSameCategory() {
        // given
        mockCache();
        mockRepository();

        var added = petService.add(new PetCreateTO("dog"));
        assertEquals(1, added.id());

        // when
        Mockito.when(petRepository.findById(anyLong())).thenReturn(Optional.of(added));
        var updated = petService.update(added.id(),
                new PetUpdateTO(PetUpdateTO.StatusEnum.PENDING, "cat"));
        assertTrue(updated.isPresent());
        assertNotEquals(0, updated.get().id());

        // then
        Mockito.verify(petRepository).insert(any());
    }

    private void mockCache() {
        Mockito.when(petCache.get(anyLong())).thenReturn(null);
        Mockito.when(petCache.put(anyLong(), any())).then(invocation -> invocation.getArguments()[1]);
        Mockito.when(petCache.get(anyCollection())).thenReturn(Collections.emptyMap());
    }

    private void mockRepository() {
        Mockito.when(petRepository.insert(any())).thenReturn(1L);
        Mockito.when(petRepository.findById(anyLong())).thenReturn(Optional.empty());
    }
}
```

#### Embedded source: IntegrationTests.java
```java
package ru.tinkoff.kora.example;

import io.goodforgod.testcontainers.extensions.ContainerMode;
import io.goodforgod.testcontainers.extensions.Network;
import io.goodforgod.testcontainers.extensions.jdbc.ConnectionPostgreSQL;
import io.goodforgod.testcontainers.extensions.jdbc.JdbcConnection;
import io.goodforgod.testcontainers.extensions.jdbc.Migration;
import io.goodforgod.testcontainers.extensions.jdbc.TestcontainersPostgreSQL;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.example.TestApplication.TestPetRepository;
import ru.tinkoff.kora.example.openapi.http.server.model.PetCreateTO;
import ru.tinkoff.kora.example.openapi.http.server.model.PetUpdateTO;
import ru.tinkoff.kora.example.service.PetService;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestConfigModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraConfigModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test application container that extends the main application and may add
 * components from shared modules that are not used in this app, but can be
 * used in other similar applications.
 * For example, when you have separate READ API and WRITE API applications.
 * It can also expose save/delete/update methods only for testing as fast
 * utility methods.
 * <p>
 * We strongly recommend black-box testing as the primary testing strategy.
 * -------
 * Test Application than extends Real Application and may be adds some components
 * from common modules that are not used in Real App, but may be used in other similar apps.
 * Like when you have different READ API application and WRITE API application
 * or may be, you need some save/delete/update methods only for testing as fast test utils.
 * <p>
 * But we STRONGLY ENCOURAGE AND RECOMMEND TO USE black box testing as a primary source of truth for
 * tests.
 */
@TestcontainersPostgreSQL(
        network = @Network(shared = true),
        mode = ContainerMode.PER_RUN,
        migration = @Migration(
                engine = Migration.Engines.FLYWAY,
                apply = Migration.Mode.PER_METHOD,
                drop = Migration.Mode.PER_METHOD))
@KoraAppTest(TestApplication.class)
class IntegrationTests implements KoraAppTestConfigModifier {

    @ConnectionPostgreSQL
    private JdbcConnection connection;

    @TestComponent
    private PetService petService;

    @TestComponent
    private TestPetRepository testPetRepository;

    @NotNull
    @Override
    public KoraConfigModification config() {
        return KoraConfigModification.ofString("""
                        db {
                          jdbcUrl = ${POSTGRES_JDBC_URL}
                          username = ${POSTGRES_USER}
                          password = ${POSTGRES_PASS}
                          poolName = "kora"
                        }
                        pet-cache.maximumSize = 0
                        resilient {
                           circuitbreaker.pet {
                             slidingWindowSize = 2
                             minimumRequiredCalls = 2
                             failureRateThreshold = 100
                             permittedCallsInHalfOpenState = 1
                             waitDurationInOpenState = 15s
                           }
                           timeout.pet.duration = 5000ms
                           retry.pet {
                             delay = 100ms
                             attempts = 0
                           }
                         }""")
                .withSystemProperty("POSTGRES_JDBC_URL", connection.params().jdbcUrl())
                .withSystemProperty("POSTGRES_USER", connection.params().username())
                .withSystemProperty("POSTGRES_PASS", connection.params().password());
    }

    @BeforeEach
    void cleanup() {
        testPetRepository.deleteAll();
    }

    @Test
    void updatePetWithNewCategoryCreated() {
        // given
        var added = petService.add(new PetCreateTO("dog"));
        assertEquals(1, added.id());

        // when
        var updated = petService.update(added.id(),
                new PetUpdateTO(PetUpdateTO.StatusEnum.PENDING, "cat"));
        assertTrue(updated.isPresent());
        assertEquals(1, updated.get().id());

        // then
        assertEquals(1, testPetRepository.findAll().size());
    }

    @Test
    void updatePetWithSameCategory() {
        // given
        var added = petService.add(new PetCreateTO("dog"));
        assertEquals(1, added.id());

        // when
        var updated = petService.update(added.id(),
                new PetUpdateTO(PetUpdateTO.StatusEnum.PENDING, "cat"));
        assertTrue(updated.isPresent());
        assertNotEquals(0, updated.get().id());

        // then
        assertEquals(1, testPetRepository.findAll().size());
    }
}
```

#### Embedded source: BlackBoxTests.java
```java
package ru.tinkoff.kora.example;

import static org.junit.jupiter.api.Assertions.*;

import io.goodforgod.testcontainers.extensions.ContainerMode;
import io.goodforgod.testcontainers.extensions.Network;
import io.goodforgod.testcontainers.extensions.jdbc.ConnectionPostgreSQL;
import io.goodforgod.testcontainers.extensions.jdbc.JdbcConnection;
import io.goodforgod.testcontainers.extensions.jdbc.Migration;
import io.goodforgod.testcontainers.extensions.jdbc.TestcontainersPostgreSQL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

@TestcontainersPostgreSQL(
        network = @Network(shared = true),
        mode = ContainerMode.PER_RUN,
        migration = @Migration(
                engine = Migration.Engines.FLYWAY,
                apply = Migration.Mode.PER_METHOD,
                drop = Migration.Mode.PER_METHOD))
class BlackBoxTests {

    private static final AppContainer container = AppContainer.build()
            .withNetwork(org.testcontainers.containers.Network.SHARED);

    @ConnectionPostgreSQL
    private JdbcConnection connection;

    @BeforeAll
    public static void setup(@ConnectionPostgreSQL JdbcConnection connection) {
        var params = connection.paramsInNetwork().orElseThrow();
        container.withEnv(Map.of(
                "POSTGRES_JDBC_URL", params.jdbcUrl(),
                "POSTGRES_USER", params.username(),
                "POSTGRES_PASS", params.password(),
                "CACHE_MAX_SIZE", "0",
                "RETRY_ATTEMPTS", "0",
                "LOGGING_LEVEL_KORA", "DEBUG",
                "LOGGING_LEVEL_APP", "DEBUG"));

        container.start();
    }

    @AfterAll
    public static void cleanup() {
        container.stop();
    }

    @Test
    void addPet() throws Exception {
        // given
        var httpClient = HttpClient.newHttpClient();
        var requestBody = new JSONObject()
                .put("name", "doggie")
                .put("category", new JSONObject()
                        .put("name", "Dogs"));

        // when
        var request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .uri(container.getURI().resolve("/v3/pets"))
                .timeout(Duration.ofSeconds(5))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());

        // then
        connection.assertCountsEquals(1, "pets");
        var responseBody = new JSONObject(response.body());
        assertNotNull(responseBody.query("/id"));
        assertNotEquals(0L, responseBody.query("/id"));
        assertNotNull(responseBody.query("/status"));
        assertEquals(requestBody.query("/name"), responseBody.query("/name"));
    }

    @Test
    void getPet() throws Exception {
        // given
        var httpClient = HttpClient.newHttpClient();
        var createRequestBody = new JSONObject()
                .put("name", "doggie");

        // when
        var createRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(createRequestBody.toString()))
                .uri(container.getURI().resolve("/v3/pets"))
                .timeout(Duration.ofSeconds(5))
                .build();

        var createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode(), createResponse.body());
        connection.assertCountsEquals(1, "pets");
        var createResponseBody = new JSONObject(createResponse.body());

        // then
        var getRequest = HttpRequest.newBuilder()
                .GET()
                .uri(container.getURI().resolve("/v3/pets/" + createResponseBody.query("/id")))
                .timeout(Duration.ofSeconds(5))
                .build();

        var getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode(), getResponse.body());

        var getResponseBody = new JSONObject(getResponse.body());
        JSONAssert.assertEquals(createResponseBody.toString(), getResponseBody.toString(), JSONCompareMode.LENIENT);
    }

    @Test
    void getPetNotFound() throws Exception {
        // given
        var httpClient = HttpClient.newHttpClient();

        // when
        var getRequest = HttpRequest.newBuilder()
                .GET()
                .uri(container.getURI().resolve("/v3/pets/1"))
                .timeout(Duration.ofSeconds(5))
                .build();

        // then
        var getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, getResponse.statusCode(), getResponse.body());
    }

    @Test
    void updatePet() throws Exception {
        // given
        var httpClient = HttpClient.newHttpClient();
        var createRequestBody = new JSONObject()
                .put("name", "doggie");

        var createRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(createRequestBody.toString()))
                .uri(container.getURI().resolve("/v3/pets"))
                .timeout(Duration.ofSeconds(5))
                .build();

        var createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode(), createResponse.body());
        connection.assertCountsEquals(1, "pets");
        var createResponseBody = new JSONObject(createResponse.body());

        // when
        var updateRequestBody = new JSONObject()
                .put("name", "doggie2")
                .put("status", "pending");

        var updateRequest = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofString(updateRequestBody.toString()))
                .uri(container.getURI().resolve("/v3/pets/" + createResponseBody.query("/id")))
                .timeout(Duration.ofSeconds(5))
                .build();

        var updateResponse = httpClient.send(updateRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResponse.statusCode(), updateResponse.body());
        var updateResponseBody = new JSONObject(updateResponse.body());

        // then
        var getRequest = HttpRequest.newBuilder()
                .GET()
                .uri(container.getURI().resolve("/v3/pets/" + createResponseBody.query("/id")))
                .timeout(Duration.ofSeconds(5))
                .build();

        var getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode(), getResponse.body());

        var getResponseBody = new JSONObject(getResponse.body());
        JSONAssert.assertEquals(updateResponseBody.toString(), getResponseBody.toString(), JSONCompareMode.LENIENT);
    }

    @Test
    void deletePet() throws Exception {
        // given
        var httpClient = HttpClient.newHttpClient();
        var createRequestBody = new JSONObject()
                .put("name", "doggie");

        var createRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(createRequestBody.toString()))
                .uri(container.getURI().resolve("/v3/pets"))
                .timeout(Duration.ofSeconds(5))
                .build();

        var createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode(), createResponse.body());
        connection.assertCountsEquals(1, "pets");
        var createResponseBody = new JSONObject(createResponse.body());

        // when
        var deleteRequest = HttpRequest.newBuilder()
                .DELETE()
                .uri(container.getURI().resolve("/v3/pets/" + createResponseBody.query("/id")))
                .timeout(Duration.ofSeconds(5))
                .build();

        var deleteResponse = httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleteResponse.statusCode(), deleteResponse.body());

        // then
        connection.assertCountsEquals(0, "pets");
    }
}
```

#### Embedded source: ComponentTests.kt
```kotlin
package ru.tinkoff.kora.kotlin.example.crud

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.example.crud.openapi.http.server.model.CategoryCreateTO
import ru.tinkoff.kora.example.crud.openapi.http.server.model.PetCreateTO
import ru.tinkoff.kora.example.crud.openapi.http.server.model.PetUpdateTO
import ru.tinkoff.kora.kotlin.example.crud.model.PetWithCategory
import ru.tinkoff.kora.kotlin.example.crud.repository.CategoryRepository
import ru.tinkoff.kora.kotlin.example.crud.repository.PetRepository
import ru.tinkoff.kora.kotlin.example.crud.service.PetCache
import ru.tinkoff.kora.kotlin.example.crud.service.PetService
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestConfigModifier
import ru.tinkoff.kora.test.extension.junit5.KoraConfigModification
import ru.tinkoff.kora.test.extension.junit5.TestComponent

@KoraAppTest(Application::class)
class ComponentTests : KoraAppTestConfigModifier {

    @field:MockK
    @TestComponent
    lateinit var petCache: PetCache

    @field:MockK
    @TestComponent
    lateinit var petRepository: PetRepository

    @field:MockK
    @TestComponent
    lateinit var categoryRepository: CategoryRepository

    @TestComponent
    lateinit var petService: PetService

    override fun config(): KoraConfigModification = KoraConfigModification.ofString(
        """
           resilient {
              circuitbreaker.pet {
                slidingWindowSize = 2
                minimumRequiredCalls = 2
                failureRateThreshold = 100
                permittedCallsInHalfOpenState = 1
                waitDurationInOpenState = 15s
              }
              timeout.pet {
                duration = 5000ms
              }
              retry.pet {
                delay = 100ms
                attempts = 0
              }
            }
    """.trimIndent()
    )

    @Test
    fun updatePetWithNewCategoryCreated() {
        // given
        mockCache()
        mockRepository(mapOf("dog" to 1, "cat" to 2))

        val added = petService.add(PetCreateTO("dog", CategoryCreateTO("dog")))
        assertEquals(1, added.id)
        assertEquals(1, added.category.id)
        verify { petRepository.insert(any()) }
        verify { categoryRepository.insert(any()) }

        // when
        every { petRepository.findById(any()) } returns added
        val updated = petService.update(
            added.id,
            PetUpdateTO("cat", PetUpdateTO.StatusEnum.PENDING, CategoryCreateTO("cat"))
        )
        assertNotNull(updated)
        assertEquals(1, updated!!.id)
        assertEquals(2, updated.category.id)

        // then
        verify { petRepository.update(any()) }
        verify { categoryRepository.insert(any()) }
    }

    @Test
    fun updatePetWithSameCategory() {
        // given
        mockCache()
        mockRepository(mapOf("dog" to 1, "cat" to 2))

        val added = petService.add(PetCreateTO("dog", CategoryCreateTO("dog")))
        assertEquals(1, added.id)
        assertEquals(1, added.category.id)
        verify { petRepository.insert(any()) }
        verify { categoryRepository.insert(any()) }

        // when
        every { petRepository.findById(any()) } returns added
        every { categoryRepository.findByName(any()) } returns added.category
        val updated = petService.update(
            added.id,
            PetUpdateTO("cat", PetUpdateTO.StatusEnum.PENDING, CategoryCreateTO("dog"))
        )
        assertNotNull(updated)
        assertEquals(1, updated!!.id)
        assertEquals(1, updated.category.id)

        // then
        verify { petRepository.update(any()) }
        verify { categoryRepository.insert(any()) }
    }

    private fun mockCache() {
        every { petCache.get(any<Long>()) } returns null
        every { petCache.put(any<Long>(), any()) } returnsArgument (1)
        every { petCache.get(any<Collection<Long>>()) } returns emptyMap<Long, PetWithCategory>()
    }

    private fun mockRepository(categoryNameToId: Map<String, Long>) {
        categoryNameToId.forEach { (name, id) -> every { categoryRepository.insert(name) } returns id }
        every { categoryRepository.findByName(any()) } returns null
        every { petRepository.insert(any()) } returns 1
        every { petRepository.findById(any()) } returns null
        every { petRepository.update(any()) } returns Unit
    }
}
```

#### Embedded source: IntegrationTests.kt
```kotlin
package ru.tinkoff.kora.kotlin.example.crud

import io.goodforgod.testcontainers.extensions.ContainerMode
import io.goodforgod.testcontainers.extensions.Network
import io.goodforgod.testcontainers.extensions.jdbc.ConnectionPostgreSQL
import io.goodforgod.testcontainers.extensions.jdbc.JdbcConnection
import io.goodforgod.testcontainers.extensions.jdbc.Migration
import io.goodforgod.testcontainers.extensions.jdbc.TestcontainersPostgreSQL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.example.crud.openapi.http.server.model.CategoryCreateTO
import ru.tinkoff.kora.example.crud.openapi.http.server.model.PetCreateTO
import ru.tinkoff.kora.example.crud.openapi.http.server.model.PetUpdateTO
import ru.tinkoff.kora.kotlin.example.crud.service.PetService
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestConfigModifier
import ru.tinkoff.kora.test.extension.junit5.KoraConfigModification
import ru.tinkoff.kora.test.extension.junit5.TestComponent


/**
 * Test application container that extends the main application and may add some components
 * from shared modules that are not used in this app, but can be useful in similar apps.
 * For example, when you have separate READ API and WRITE API applications.
 * It can also expose save/delete/update methods only for testing as fast utility methods.
 * <p>
 * We strongly recommend using black-box testing as the primary testing approach.
 * -------
 * Test Application than extends Real Application and may be adds some components
 * from common modules that are not used in Real App, but may be used in other similar apps.
 * Like when you have different READ API application and WRITE API application
 * or may be, you need some save/delete/update methods only for testing as fast test utils.
 * <p>
 * But we STRONGLY ENCOURAGE AND RECOMMEND TO USE black box testing as a primary source of truth for tests.
 */
@TestcontainersPostgreSQL(
    network = Network(shared = true),
    mode = ContainerMode.PER_RUN,
    migration = Migration(
        engine = Migration.Engines.FLYWAY,
        apply = Migration.Mode.PER_METHOD,
        drop = Migration.Mode.PER_METHOD
    )
)
@KoraAppTest(TestApplication::class)
class IntegrationTests(@ConnectionPostgreSQL val connection: JdbcConnection) : KoraAppTestConfigModifier {

    @TestComponent
    lateinit var petService: PetService

    @TestComponent
    lateinit var testPetRepository: TestApplication.TestPetRepository

    @TestComponent
    lateinit var testCategoryRepository: TestApplication.TestCategoryRepository

    override fun config(): KoraConfigModification = KoraConfigModification.ofString(
        """
        db {
          jdbcUrl = "${connection.params().jdbcUrl()}"
          username = "${connection.params().username()}"
          password = "${connection.params().password()}"
          poolName = "kora"
        }
        pet-cache.maximumSize = 0
        resilient {
           circuitbreaker.pet {
             slidingWindowSize = 2
             minimumRequiredCalls = 2
             failureRateThreshold = 100
             permittedCallsInHalfOpenState = 1
             waitDurationInOpenState = 15s
           }
           timeout.pet.duration = 5000ms
           retry.pet {
             delay = 100ms
             attempts = 0
           }
         }
    """.trimIndent()
    )

    @Test
    fun updatePetWithNewCategoryCreated() {
        // given
        val added = petService.add(PetCreateTO("dog", CategoryCreateTO("dog")))
        assertEquals(1, added.id)
        assertEquals(1, added.category.id)

        // when
        val updated = petService.update(
            added.id,
            PetUpdateTO("cat", PetUpdateTO.StatusEnum.PENDING, CategoryCreateTO("cat"))
        )
        assertNotNull(updated)
        assertEquals(1, updated!!.id)
        assertEquals(2, updated.category.id)

        // then
        assertEquals(1, testPetRepository.findAll().size)
        assertEquals(2, testCategoryRepository.findAll().size)
    }

    @Test
    fun updatePetWithSameCategory() {
        // given
        val added = petService.add(PetCreateTO("dog", CategoryCreateTO("dog")))
        assertEquals(1, added.id)
        assertEquals(1, added.category.id)

        // when
        val updated = petService.update(
            added.id,
            PetUpdateTO("cat", PetUpdateTO.StatusEnum.PENDING, CategoryCreateTO("dog"))
        )
        assertNotNull(updated)
        assertEquals(1, updated!!.id)
        assertEquals(1, updated.category.id)

        // then
        assertEquals(1, testPetRepository.findAll().size)
        assertEquals(1, testCategoryRepository.findAll().size)
    }
}
```
### Testing approach
- Unit/component tests: replace external dependencies with mocks.
- Integration tests: use Testcontainers and real module wiring.
- Black-box tests: run built artifact/container and hit external endpoints.

### Common errors and exact fixes
- Failure signature: tests start but component is null/uninjected.
  - Fix: mark field/parameter with `@TestComponent` and ensure it is reachable from test roots.
- Failure signature: `MockitoExtension`/`MockKExtension` conflict.
  - Fix: do not combine extension with `@KoraAppTest`; use framework-native test component wiring.

### Minimum runnable path
```bash
./gradlew test
```

### Agent rules
1. Default to black-box tests for end-to-end behavior.
2. Use component tests for targeted behavior and wiring checks.
3. Keep test configuration local to test classes.
4. Never depend on external shared state.

## Database
### What this module solves
Compile-time repository generation over multiple backends with type-safe query binding, mapping, batching, and optional reactive styles.

### Do this first
1. Pick backend: JDBC, R2DBC, Vertx, or Cassandra.
2. Add backend dependency and driver.
3. Define repositories with `@Repository` and query methods.

### Then this
1. Model entities and mappings using `@Column`, `@Id`, and backend-specific annotations.
2. Add migration scripts under `db/migration`.
3. Add telemetry and pool settings in config.

### Verify this
1. Connection starts and pool initializes.
2. Repository methods execute with correct SQL/CQL binding.
3. Integration tests pass against containerized DB.

### Required dependencies
Java (`build.gradle`) examples:
```groovy
dependencies {
    implementation "org.postgresql:postgresql:42.7.7"
    implementation "ru.tinkoff.kora:database-jdbc"
    // OR
    implementation "org.postgresql:r2dbc-postgresql:1.0.4.RELEASE"
    implementation "ru.tinkoff.kora:database-r2dbc"
    // OR
    implementation "io.vertx:vertx-pg-client:4.3.8"
    implementation "ru.tinkoff.kora:database-vertx"
    // OR
    implementation "ru.tinkoff.kora:database-cassandra"
}
```

Kotlin (`build.gradle.kts`) examples:
```kotlin
dependencies {
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("ru.tinkoff.kora:database-jdbc")
    // select one backend per service unless you explicitly need several
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends HoconConfigModule, LogbackModule, JdbcDatabaseModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application : HoconConfigModule, LogbackModule, JdbcDatabaseModule
```

Alternative modules:
- `R2dbcDatabaseModule`
- `VertxDatabaseModule`
- `CassandraDatabaseModule`

### Configuration keys
HOCON (JDBC):
```hocon
db {
  jdbcUrl = ${POSTGRES_JDBC_URL}
  username = ${POSTGRES_USER}
  password = ${POSTGRES_PASS}
  maxPoolSize = 10
  poolName = "kora"
  telemetry.logging.enabled = true
}
```

YAML (JDBC equivalent):
```yaml
db:
  jdbcUrl: ${POSTGRES_JDBC_URL}
  username: ${POSTGRES_USER}
  password: ${POSTGRES_PASS}
  maxPoolSize: 10
  poolName: "kora"
  telemetry:
    logging:
      enabled: true
```

HOCON (R2DBC):
```hocon
db {
  r2dbcUrl = ${POSTGRES_R2DBC_URL}
  username = ${POSTGRES_USER}
  password = ${POSTGRES_PASS}
}
```

HOCON (Vertx):
```hocon
db {
  connectionUri = ${POSTGRES_VERTX_URI}
  username = ${POSTGRES_USER}
  password = ${POSTGRES_PASS}
}
```

HOCON (Cassandra):
```hocon
cassandra {
  auth.login = ${CASSANDRA_USER}
  auth.password = ${CASSANDRA_PASS}
  basic.contactPoints = ${CASSANDRA_CONTACT_POINTS}
  basic.dc = ${CASSANDRA_DC}
  basic.sessionKeyspace = ${CASSANDRA_KEYSPACE}
}
```

### Minimal Java example
```java
@Repository
public interface UserRepository extends JdbcRepository {
    record User(@Id Long id, String name) {}

    @Query("SELECT %{return#selects} FROM %{return#table} WHERE id = :id")
    java.util.Optional<User> findById(long id);

    @Id
    @Query("INSERT INTO %{entity#inserts -= id}")
    long insert(User user);
}
```

### Minimal Kotlin example
```kotlin
@Repository
interface UserRepository : JdbcRepository {
    data class User(@Id val id: Long?, val name: String)

    @Query("SELECT %{return#selects} FROM %{return#table} WHERE id = :id")
    fun findById(id: Long): User?

    @Id
    @Query("INSERT INTO %{entity#inserts -= id}")
    fun insert(user: User): Long
}
```

### Production-style example
#### Embedded source: JdbcCrudMacrosRepository.java
```java
package ru.tinkoff.kora.example.jdbc;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.common.annotation.*;
import ru.tinkoff.kora.database.jdbc.EntityJdbc;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

@Repository
public interface JdbcCrudMacrosRepository extends JdbcRepository {

    @EntityJdbc
    @Table("entities")
    record Entity(@Id String id,
                  @Column("value1") int field1,
                  String value2,
                  @Nullable String value3) {}

    @Query("SELECT %{return#selects} FROM %{return#table} WHERE id = :id")
    Optional<Entity> findById(String id);

    @Query("SELECT %{return#selects} FROM %{return#table}")
    List<Entity> findAll();

    @Query("INSERT INTO %{entity#inserts}")
    UpdateCount insert(Entity entity);

    @Query("INSERT INTO %{entity#inserts}")
    UpdateCount insertBatch(@Batch List<Entity> entity);

    @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
    UpdateCount update(Entity entity);

    @Query("UPDATE %{entity#table} SET %{entity#updates} WHERE %{entity#where = @id}")
    UpdateCount updateBatch(@Batch List<Entity> entity);

    @Query("INSERT INTO %{entity#inserts} ON CONFLICT (id) DO UPDATE SET %{entity#updates}")
    UpdateCount upsert(Entity entity);

    @Query("INSERT INTO %{entity#inserts} ON CONFLICT (id) DO UPDATE SET %{entity#updates}")
    UpdateCount upsertBatch(@Batch List<Entity> entity);

    @Query("DELETE FROM entities WHERE id = :id")
    UpdateCount deleteById(String id);

    @Query("DELETE FROM entities")
    UpdateCount deleteAll();
}
```

#### Embedded source: R2dbcCrudRepository.java
```java
package ru.tinkoff.kora.example.r2dbc;

import jakarta.annotation.Nullable;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.common.annotation.*;
import ru.tinkoff.kora.database.r2dbc.R2dbcRepository;

@Repository
public interface R2dbcCrudRepository extends R2dbcRepository {

    record Entity(String id,
                  @Column("value1") int field1,
                  String value2,
                  @Nullable String value3) {}

    @Query("SELECT * FROM entities WHERE id = :id")
    Mono<Entity> findById(String id);

    @Query("SELECT * FROM entities")
    Flux<Entity> findAll();

    @Query("SELECT * FROM entities")
    Mono<List<Entity>> findAllMono();

    @Query("""
            INSERT INTO entities(id, value1, value2, value3)
            VALUES (:entity.id, :entity.field1, :entity.value2, :entity.value3)
            """)
    Mono<Void> insert(Entity entity);

    @Query("""
            INSERT INTO entities(id, value1, value2, value3)
            VALUES (:entity.id, :entity.field1, :entity.value2, :entity.value3)
            """)
    Mono<UpdateCount> insertBatch(@Batch List<Entity> entity);

    @Query("""
            UPDATE entities
            SET value1 = :entity.field1, value2 = :entity.value2, value3 = :entity.value3
            WHERE id = :entity.id
            """)
    Mono<Void> update(Entity entity);

    @Query("""
            UPDATE entities
            SET value1 = :entity.field1, value2 = :entity.value2, value3 = :entity.value3
            WHERE id = :entity.id
            """)
    Mono<UpdateCount> updateBatch(@Batch List<Entity> entity);

    @Query("DELETE FROM entities WHERE id = :id")
    Mono<Void> deleteById(String id);

    @Query("DELETE FROM entities")
    Mono<UpdateCount> deleteAll();
}
```

#### Embedded source: VertxCrudSyncRepository.java
```java
package ru.tinkoff.kora.example.vertx;

import jakarta.annotation.Nullable;
import java.util.List;
import ru.tinkoff.kora.database.common.UpdateCount;
import ru.tinkoff.kora.database.common.annotation.*;
import ru.tinkoff.kora.database.vertx.VertxRepository;

@Repository
public interface VertxCrudSyncRepository extends VertxRepository {

    record Entity(String id,
                  @Column("value1") int field1,
                  String value2,
                  @Nullable String value3) {}

    @Query("SELECT * FROM entities WHERE id = :id")
    Entity findById(String id);

    @Query("SELECT * FROM entities")
    List<Entity> findAll();

    @Query("""
            INSERT INTO entities(id, value1, value2, value3)
            VALUES (:entity.id, :entity.field1, :entity.value2, :entity.value3)
            """)
    void insert(Entity entity);

    @Query("""
            INSERT INTO entities(id, value1, value2, value3)
            VALUES (:entity.id, :entity.field1, :entity.value2, :entity.value3)
            """)
    UpdateCount insertBatch(@Batch List<Entity> entity);

    @Query("""
            UPDATE entities
            SET value1 = :entity.field1, value2 = :entity.value2, value3 = :entity.value3
            WHERE id = :entity.id
            """)
    void update(Entity entity);

    @Query("""
            UPDATE entities
            SET value1 = :entity.field1, value2 = :entity.value2, value3 = :entity.value3
            WHERE id = :entity.id
            """)
    UpdateCount updateBatch(@Batch List<Entity> entity);

    @Query("DELETE FROM entities WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM entities")
    UpdateCount deleteAll();
}
```

#### Embedded source: CassandraCrudSyncRepository.java
```java
package ru.tinkoff.kora.example.cassandra;

import jakarta.annotation.Nullable;
import java.util.List;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;
import ru.tinkoff.kora.database.common.annotation.*;

@Repository
public interface CassandraCrudSyncRepository extends CassandraRepository {

    record Entity(String id,
                  @Column("value1") int field1,
                  String value2,
                  @Nullable String value3) {}

    @Query("SELECT * FROM entities WHERE id = :id")
    @Nullable
    Entity findById(String id);

    @Query("SELECT * FROM entities")
    List<Entity> findAll();

    @Query("""
            INSERT INTO entities(id, value1, value2, value3)
            VALUES (:entity.id, :entity.field1, :entity.value2, :entity.value3)
            """)
    void insert(Entity entity);

    @Query("""
            INSERT INTO entities(id, value1, value2, value3)
            VALUES (:entity.id, :entity.field1, :entity.value2, :entity.value3)
            """)
    void insertBatch(@Batch List<Entity> entity);

    @Query("""
            UPDATE entities
            SET value1 = :entity.field1, value2 = :entity.value2, value3 = :entity.value3
            WHERE id = :entity.id
            """)
    void update(Entity entity);

    @Query("""
            UPDATE entities
            SET value1 = :entity.field1, value2 = :entity.value2, value3 = :entity.value3
            WHERE id = :entity.id
            """)
    void updateBatch(@Batch List<Entity> entity);

    @Query("DELETE FROM entities WHERE id = :id")
    void deleteById(String id);

    @Query("TRUNCATE entities")
    void deleteAll();
}
```

#### Embedded source: CassandraUdtRepository.java
```java
package ru.tinkoff.kora.example.cassandra;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.cassandra.CassandraRepository;
import ru.tinkoff.kora.database.cassandra.annotation.UDT;
import ru.tinkoff.kora.database.common.annotation.*;

@Repository
public interface CassandraUdtRepository extends CassandraRepository {

    record Entity(String id, Name name) {

        @UDT
        public record Name(String first, String last) {}
    }

    @Query("SELECT * FROM entities_udt WHERE id = :id")
    @Nullable
    Entity findById(String id);

    @Query("""
            INSERT INTO entities_udt(id, name)
            VALUES (:entity.id, :entity.name)
            """)
    void insert(Entity entity);
}
```
### Testing approach
- Use Testcontainers for backend-specific integration tests.
- Run migrations per test method where isolation is required.
- Keep repository tests backend-focused and deterministic.

### Common errors and exact fixes
- Failure signature: JDBC URL set to R2DBC format (or opposite).
  - Fix: ensure `jdbcUrl` starts with `jdbc:` and `r2dbcUrl` starts with `r2dbc:`.
- Failure signature: `@Id` generation mismatch in insert methods.
  - Fix: align SQL statement with return strategy (`RETURNING id` or `@Id` usage pattern from examples).
- Failure signature: repository macro errors.
  - Fix: keep macro placeholders exactly in supported syntax (`%{entity#...}`, `%{return#...}`).

### Minimum runnable path
```bash
./gradlew flywayMigrate
./gradlew run
./gradlew test
```

### Agent rules
1. Choose one DB backend first; do not mix by default.
2. Start with one repository and one integration test before expanding.
3. Use macros for standard CRUD, custom SQL only where needed.
4. Ensure migration scripts are present before run/test.

## Caching
### What this module solves
Provides local (Caffeine) and distributed (Redis) caching with declarative annotations and imperative APIs.

### Do this first
1. Choose cache backend: Caffeine for in-memory, Redis for shared/distributed cache.
2. Add cache module dependency and include cache module in `@KoraApp`.
3. Define cache contracts with `@Cache` interfaces.

### Then this
1. Add declarative cache aspects (`@Cacheable`, `@CachePut`, `@CacheInvalidate`) on service methods.
2. Configure TTL/size/prefix settings for each cache path.
3. Add key mappers (`CacheKeyMapper`) for complex method arguments.

### Verify this
1. Cache hits and misses behave as expected.
2. Invalidate and invalidate-all semantics are correct.
3. Redis connection and key-prefix strategy are configured correctly in non-local environments.

### Required dependencies
Java:
```groovy
dependencies {
    implementation "ru.tinkoff.kora:cache-caffeine" // or cache-redis
    implementation "ru.tinkoff.kora:config-hocon"
    implementation "ru.tinkoff.kora:logging-logback"
}
```

Kotlin:
```kotlin
dependencies {
    implementation("ru.tinkoff.kora:cache-caffeine") // or cache-redis
    implementation("ru.tinkoff.kora:config-hocon")
    implementation("ru.tinkoff.kora:logging-logback")
}
```

### @KoraApp wiring
Caffeine-only (Java):
```java
@KoraApp
public interface Application extends HoconConfigModule, LogbackModule, CaffeineCacheModule {}
```

Redis-only (Java):
```java
@KoraApp
public interface Application extends HoconConfigModule, LogbackModule, RedisCacheModule {}
```

Composite (Kotlin):
```kotlin
@KoraApp
interface Application : HoconConfigModule, LogbackModule, CaffeineCacheModule, RedisCacheModule
```

### Configuration keys
HOCON (Caffeine):
```hocon
my-cache {
  maximumSize = 1000
}
```

HOCON (Redis + Lettuce):
```hocon
my-cache {
  keyPrefix = "my-"
  expireAfterWrite = 10s
  expireAfterAccess = 10s
}

lettuce {
  uri = ${REDIS_URL}
  user = ${REDIS_USER}
  password = ${REDIS_PASS}
  socketTimeout = 15s
  commandTimeout = 15s
}
```

YAML equivalents:
```yaml
my-cache:
  maximumSize: 1000
  keyPrefix: "my-"
  expireAfterWrite: 10s
  expireAfterAccess: 10s

lettuce:
  uri: ${REDIS_URL}
  user: ${REDIS_USER}
  password: ${REDIS_PASS}
  socketTimeout: 15s
  commandTimeout: 15s
```

### Minimal Java example
```java
@Cache("my-cache")
public interface UserCache extends CaffeineCache<String, Long> {}

@Component
public class UserService {

    @Cacheable(UserCache.class)
    public Long get(String id) {
        return java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }

    @CachePut(UserCache.class)
    public Long put(String id) {
        return java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }

    @CacheInvalidate(UserCache.class)
    public void invalidate(String id) {}
}
```

### Minimal Kotlin example
```kotlin
@Cache("my-cache")
interface UserCache : CaffeineCache<String, Long>

@Component
open class UserService {

    @Cacheable(UserCache::class)
    open fun get(id: String): Long = kotlin.random.Random.nextLong()

    @CachePut(UserCache::class)
    open fun put(id: String): Long = kotlin.random.Random.nextLong()

    @CacheInvalidate(UserCache::class)
    open fun invalidate(id: String) {}
}
```

### Production-style example
#### Embedded source: Application.java (Caffeine)
```java
package ru.tinkoff.kora.example.cache.caffeine;

import ru.tinkoff.kora.application.graph.KoraApplication;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.hocon.HoconConfigModule;
import ru.tinkoff.kora.logging.logback.LogbackModule;

@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        CaffeineCacheModule {

    static void main(String[] args) {
        KoraApplication.run(ApplicationGraph::graph);
    }
}
```
#### Embedded source: SimpleCache.java (Caffeine)
```java
package ru.tinkoff.kora.example.cache.caffeine;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCache;

@Cache("my-cache")
public interface SimpleCache extends CaffeineCache<String, Long> {

}
```
#### Embedded source: CompositeCache.java (Caffeine)
```java
package ru.tinkoff.kora.example.cache.caffeine;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCache;

@Cache("my-cache")
public interface CompositeCache extends CaffeineCache<CompositeCache.Key, Long> {

    record Key(String userId, String traceId) {}
}
```
#### Embedded source: SimpleService.java (Caffeine)
```java
package ru.tinkoff.kora.example.cache.caffeine;

import jakarta.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import ru.tinkoff.kora.cache.CacheKeyMapper;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.annotation.Root;

@Root
@Component
public class SimpleService {

    public record UserContext(String userId, String traceId) {}

    public static final class UserContextMapping implements CacheKeyMapper<String, UserContext> {

        @Nonnull
        @Override
        public String map(UserContext arg) {
            return arg.userId();
        }
    }

    @Mapping(UserContextMapping.class)
    @Cacheable(SimpleCache.class)
    public Long getMapping(UserContext context) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @Cacheable(SimpleCache.class)
    public Long get(String id) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @CachePut(value = SimpleCache.class, parameters = { "id" })
    public Long put(BigDecimal arg2, String arg3, String id) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @CacheInvalidate(SimpleCache.class)
    public void delete(String id) {}

    @CacheInvalidate(value = SimpleCache.class, invalidateAll = true)
    public void deleteAll() {}
}
```
#### Embedded source: CompositeService.java (Caffeine)
```java
package ru.tinkoff.kora.example.cache.caffeine;

import jakarta.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import ru.tinkoff.kora.cache.CacheKeyMapper;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.annotation.Root;

@Root
@Component
public class CompositeService {

    public record UserContext(String userId, String traceId) {}

    public static final class UserContextMapping implements CacheKeyMapper<CompositeCache.Key, UserContext> {

        @Nonnull
        @Override
        public CompositeCache.Key map(UserContext arg) {
            return new CompositeCache.Key(arg.userId(), arg.traceId());
        }
    }

    @Mapping(UserContextMapping.class)
    @Cacheable(CompositeCache.class)
    public Long getMapping(UserContext context) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @Cacheable(CompositeCache.class)
    public Long get(String id, String traceId) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @CachePut(value = CompositeCache.class, parameters = { "id", "traceId" })
    public Long put(BigDecimal arg2, String arg3, String id, String traceId) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @CacheInvalidate(CompositeCache.class)
    public void delete(String id, String traceId) {}

    @CacheInvalidate(value = CompositeCache.class, invalidateAll = true)
    public void deleteAll() {}
}
```
#### Embedded source: application.conf (Caffeine)
```hocon
my-cache {
  maximumSize = 1000
}


logging.level {
  "root": "WARN"
  "ru.tinkoff.kora": "INFO"
  "ru.tinkoff.kora.example": "INFO"
}
```
#### Embedded source: build.gradle (Caffeine)
```groovy
plugins {
    id "java"
    id "jacoco"
    id "application"
}

configurations {
    koraBom
    annotationProcessor.extendsFrom(koraBom); compileOnly.extendsFrom(koraBom); implementation.extendsFrom(koraBom)
    api.extendsFrom(koraBom); testImplementation.extendsFrom(koraBom); testAnnotationProcessor.extendsFrom(koraBom)
}

dependencies {
    koraBom platform("ru.tinkoff.kora:kora-parent:$koraVersion")
    annotationProcessor "ru.tinkoff.kora:annotation-processors"

    implementation "ru.tinkoff.kora:cache-caffeine"

    implementation "ru.tinkoff.kora:logging-logback"
    implementation "ru.tinkoff.kora:config-hocon"

    testImplementation "ru.tinkoff.kora:test-junit5"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "application"
    mainClass = "ru.tinkoff.kora.example.cache.caffeine.Application"
    applicationDefaultJvmArgs = ["-Dfile.encoding=UTF-8"]
}

//noinspection GroovyAssignabilityCheck
run {
    environment([
            "": ""
    ])
}

distTar {
    archiveFileName = "application.tar"
}

test {
    dependsOn tasks.distTar

    jvmArgs += [
            "-XX:+TieredCompilation",
            "-XX:TieredStopAtLevel=1",
    ]

    environment([
            "": ""
    ])

    useJUnitPlatform()
    testLogging {
        showStandardStreams(true)
        events("passed", "skipped", "failed")
        exceptionFormat("full")
    }

    exclude("**/\$*")

    jacoco {
        excludes += ["**/generated/**", "**/Application*", "**/\$*"]
    }

    reports {
        html.required = false
        junitXml.required = false
    }
}

compileJava {
    options.encoding("UTF-8")
    options.incremental(true)
    options.fork = false
}

check.dependsOn jacocoTestReport
jacocoTestReport {
    reports {
        xml.required = true
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
    classDirectories = files(classDirectories.files.collect { fileTree(dir: it, excludes: test.jacoco.excludes) })
}

javadoc {
    options.encoding = "UTF-8"
    if (JavaVersion.current().isJava9Compatible()) {
        options.addBooleanOption("html5", true)
    }
}
```
#### Embedded source: Application.java (Redis)
```java
package ru.tinkoff.kora.example.cache.redis;

import ru.tinkoff.kora.application.graph.KoraApplication;
import ru.tinkoff.kora.cache.redis.RedisCacheModule;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.hocon.HoconConfigModule;
import ru.tinkoff.kora.logging.logback.LogbackModule;

@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        RedisCacheModule {

    static void main(String[] args) {
        KoraApplication.run(ApplicationGraph::graph);
    }
}
```
#### Embedded source: SimpleCache.java (Redis)
```java
package ru.tinkoff.kora.example.cache.redis;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.redis.RedisCache;

@Cache("my-cache")
public interface SimpleCache extends RedisCache<String, Long> {

}
```
#### Embedded source: CompositeCache.java (Redis)
```java
package ru.tinkoff.kora.example.cache.redis;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.redis.RedisCache;

@Cache("my-cache")
public interface CompositeCache extends RedisCache<CompositeCache.Key, Long> {

    record Key(String userId, String traceId) {}
}
```
#### Embedded source: SimpleService.java (Redis)
```java
package ru.tinkoff.kora.example.cache.redis;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;

@Root
@Component
public class SimpleService {

    @Cacheable(SimpleCache.class)
    public Long get(String id) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @CachePut(value = SimpleCache.class, parameters = { "id" })
    public Long put(BigDecimal arg2, String arg3, String id) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @CacheInvalidate(SimpleCache.class)
    public void delete(String id) {}

    @CacheInvalidate(value = SimpleCache.class, invalidateAll = true)
    public void deleteAll() {}
}
```
#### Embedded source: CompositeService.java (Redis)
```java
package ru.tinkoff.kora.example.cache.redis;

import jakarta.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import ru.tinkoff.kora.cache.CacheKeyMapper;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.annotation.Root;

@Root
@Component
public class CompositeService {

    public record UserContext(String userId, String traceId) {}

    public static final class UserContextMapping implements CacheKeyMapper<CompositeCache.Key, UserContext> {

        @Nonnull
        @Override
        public CompositeCache.Key map(UserContext arg) {
            return new CompositeCache.Key(arg.userId(), arg.traceId());
        }
    }

    @Mapping(UserContextMapping.class)
    @Cacheable(CompositeCache.class)
    public Long getMapping(UserContext context) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @Cacheable(CompositeCache.class)
    public Long get(String id, String traceId) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @CachePut(value = CompositeCache.class, parameters = { "id", "traceId" })
    public Long put(BigDecimal arg2, String arg3, String id, String traceId) {
        return ThreadLocalRandom.current().nextLong(0, 100_000_000L);
    }

    @CacheInvalidate(CompositeCache.class)
    public void delete(String id, String traceId) {}

    @CacheInvalidate(value = CompositeCache.class, invalidateAll = true)
    public void deleteAll() {}
}
```
#### Embedded source: application.conf (Redis)
```hocon
my-cache {
  keyPrefix = "my-"
  expireAfterWrite = 10s
  expireAfterWrite = ${?CACHE_EXPIRE_WRITE}
  expireAfterAccess = 10s
  expireAfterAccess = ${?CACHE_EXPIRE_READ}
}


lettuce {
  uri = ${REDIS_URL}
  user = ${REDIS_USER}
  password = ${REDIS_PASS}
  socketTimeout = 15s
  commandTimeout = 15s
}


logging.level {
  "root": "WARN"
  "ru.tinkoff.kora": "INFO"
  "ru.tinkoff.kora.example": "INFO"
}
```
#### Embedded source: build.gradle (Redis)
```groovy
plugins {
    id "java"
    id "jacoco"
    id "application"
}

configurations {
    koraBom
    annotationProcessor.extendsFrom(koraBom); compileOnly.extendsFrom(koraBom); implementation.extendsFrom(koraBom)
    api.extendsFrom(koraBom); testImplementation.extendsFrom(koraBom); testAnnotationProcessor.extendsFrom(koraBom)
}

dependencies {
    koraBom platform("ru.tinkoff.kora:kora-parent:$koraVersion")
    annotationProcessor "ru.tinkoff.kora:annotation-processors"

    implementation "ru.tinkoff.kora:cache-redis"

    implementation "ru.tinkoff.kora:logging-logback"
    implementation "ru.tinkoff.kora:config-hocon"

    testImplementation "ru.tinkoff.kora:test-junit5"
    testImplementation "io.goodforgod:testcontainers-extensions-redis:0.12.2"
    testImplementation "redis.clients:jedis:4.4.3"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "application"
    mainClass = "ru.tinkoff.kora.example.cache.redis.Application"
    applicationDefaultJvmArgs = ["-Dfile.encoding=UTF-8"]
}

//noinspection GroovyAssignabilityCheck
run {
    environment([
            "": ""
    ])
}

distTar {
    archiveFileName = "application.tar"
}

test {
    dependsOn tasks.distTar

    jvmArgs += [
            "-XX:+TieredCompilation",
            "-XX:TieredStopAtLevel=1",
    ]

    environment([
            "": ""
    ])

    useJUnitPlatform()
    testLogging {
        showStandardStreams(true)
        events("passed", "skipped", "failed")
        exceptionFormat("full")
    }

    exclude("**/\$*")

    jacoco {
        excludes += ["**/generated/**", "**/Application*", "**/\$*"]
    }

    reports {
        html.required = false
        junitXml.required = false
    }
}

compileJava {
    options.encoding("UTF-8")
    options.incremental(true)
    options.fork = false
}

check.dependsOn jacocoTestReport
jacocoTestReport {
    reports {
        xml.required = true
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
    classDirectories = files(classDirectories.files.collect { fileTree(dir: it, excludes: test.jacoco.excludes) })
}

javadoc {
    options.encoding = "UTF-8"
    if (JavaVersion.current().isJava9Compatible()) {
        options.addBooleanOption("html5", true)
    }
}
```

### Testing approach
- Unit-test cache-key and annotation semantics at service level.
- Integration-test Redis-backed caches with Testcontainers.
- Explicitly test invalidate/invalidateAll behavior and TTL expectations.

### Common errors and exact fixes
- Failure signature: cache aspect does not apply to method.
  - Fix: in Java use non-final class/method patterns required by AOP; in Kotlin use `open` class/method for aspect weaving.
- Failure signature: wrong cache keys for complex parameters.
  - Fix: add `CacheKeyMapper` and/or explicit `parameters = {...}` ordering in cache annotations.
- Failure signature: Redis cache operations fail at startup/runtime.
  - Fix: validate `lettuce.uri`, credentials, TLS/timeouts, and keyPrefix configuration.

### Minimum runnable path
```bash
./gradlew run
./gradlew test
```

### Agent rules
1. Start with one cache contract and one annotated method before composing multiple caches.
2. Use Caffeine by default for local performance; introduce Redis when cross-instance coherence is required.
3. Always define deterministic cache key strategy for multi-argument methods.
4. Treat invalidation semantics as part of business contract and test them explicitly.


## HTTP Server
### What this module solves
Declarative and imperative HTTP handling, request/response mapping, validation, interceptors, and built-in telemetry.

### Do this first
1. Add HTTP server module dependency.
2. Wire server module into `@KoraApp`.
3. Configure public/private ports and telemetry.

### Then this
1. Create controllers with `@HttpController` + `@Component`.
2. Define routes with `@HttpRoute` and typed parameters.
3. Add interceptors and error handlers where required.

### Verify this
1. Endpoints respond on configured public port.
2. Health/readiness endpoints are reachable on private port (if configured).
3. Validation and error handling produce expected HTTP status codes.

### Required dependencies
Java:
```groovy
dependencies {
    implementation "ru.tinkoff.kora:http-server-undertow"
    implementation "ru.tinkoff.kora:json-module"
    implementation "ru.tinkoff.kora:validation-module"
}
```

Kotlin:
```kotlin
dependencies {
    implementation("ru.tinkoff.kora:http-server-undertow")
    implementation("ru.tinkoff.kora:json-module")
    implementation("ru.tinkoff.kora:validation-module")
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        JsonModule,
        ValidationModule,
        UndertowHttpServerModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application :
    HoconConfigModule,
    LogbackModule,
    JsonModule,
    ValidationModule,
    UndertowHttpServerModule
```

### Configuration keys
HOCON:
```hocon
httpServer {
  publicApiHttpPort = 8080
  privateApiHttpPort = 8085
  privateApiHttpLivenessPath = "/liveness"
  privateApiHttpReadinessPath = "/readiness"
  telemetry.logging.enabled = true
}
```

YAML equivalent:
```yaml
httpServer:
  publicApiHttpPort: 8080
  privateApiHttpPort: 8085
  privateApiHttpLivenessPath: "/liveness"
  privateApiHttpReadinessPath: "/readiness"
  telemetry:
    logging:
      enabled: true
```

### Minimal Java example
```java
@Component
@HttpController
public final class GreetingController {

    @ru.tinkoff.kora.http.common.annotation.HttpRoute(
            method = ru.tinkoff.kora.http.common.HttpMethod.GET,
            path = "/hello/{name}")
    public String hello(@ru.tinkoff.kora.http.common.annotation.Path("name") String name) {
        return "Hello, " + name;
    }
}
```

### Minimal Kotlin example
```kotlin
@Component
@HttpController
class GreetingController {

    @HttpRoute(method = HttpMethod.GET, path = "/hello/{name}")
    fun hello(@Path("name") name: String): String = "Hello, $name"
}
```

### Production-style example
#### Embedded source: JsonPostController.java
```java
package ru.tinkoff.kora.example.http.server;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;
import ru.tinkoff.kora.json.common.annotation.Json;

/**
 * @see Json - Indicates that response should be serialized as JSON
 * @see HttpMethod#POST - Indicates that POST request is expected
 */
@Component
@HttpController
public final class JsonPostController {

    @Json
    public record JsonRequest(String id) {}

    @Json
    public record JsonResponse(String name, int value) {}

    @HttpRoute(method = HttpMethod.POST, path = "/json")
    @Json
    public JsonResponse post(@Json JsonRequest request) {
        return new JsonResponse("Ivan", 100);
    }
}
```

#### Embedded source: InterceptedController.java
```java
package ru.tinkoff.kora.example.http.server;

import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.InterceptWith;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerInterceptor;
import ru.tinkoff.kora.http.server.common.HttpServerModule;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

/**
 * @see ServerInterceptor - Intercepts all controllers on HttpServer
 * @see ControllerInterceptor - Intercepts all controler methods
 * @see MethodInterceptor - Intercepts particular method
 */
@InterceptWith(InterceptedController.ControllerInterceptor.class)
@Component
@HttpController
public final class InterceptedController {

    public static final class ControllerInterceptor implements HttpServerInterceptor {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Override
        public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain)
                throws Exception {
            logger.info("Controller Level Interceptor");
            return chain.process(context, request);
        }
    }

    public static final class MethodInterceptor implements HttpServerInterceptor {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Override
        public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain)
                throws Exception {
            logger.info("Method Level Interceptor");
            return chain.process(context, request);
        }
    }

    @Tag(HttpServerModule.class)
    @Component
    public static final class ServerInterceptor implements HttpServerInterceptor {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Override
        public CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain)
                throws Exception {
            logger.info("Server Level Interceptor");
            return chain.process(context, request);
        }
    }

    @InterceptWith(MethodInterceptor.class)
    @HttpRoute(method = HttpMethod.GET, path = "/intercepted")
    public HttpServerResponse get() {
        return HttpServerResponse.of(200, HttpBody.plaintext("Hello world"));
    }
}
```

#### Embedded source: HelloWorldController.kt
```kotlin
package ru.tinkoff.kora.kotlin.example.helloworld

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.HttpResponseEntity
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.body.HttpBody
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import ru.tinkoff.kora.json.common.annotation.Json

@Component
@HttpController
class HelloWorldController {

    @Json
    data class HelloWorldResponse(val greeting: String)

    @Json
    @HttpRoute(method = HttpMethod.GET, path = "/hello/world/json")
    fun helloWorldJson(): HelloWorldResponse {
        return HelloWorldResponse("Hello World")
    }

    @Json
    @HttpRoute(method = HttpMethod.GET, path = "/hello/world/json/entity")
    fun helloWorldJsonEntity(): HttpResponseEntity<HelloWorldResponse> {
        return HttpResponseEntity.of(200, HelloWorldResponse("Hello World"))
    }

    @HttpRoute(method = HttpMethod.GET, path = "/hello/world")
    fun helloWorld(): HttpServerResponse {
        return HttpServerResponse.of(200, HttpBody.plaintext("Hello World"))
    }
}
```
### Testing approach
- Component tests for controller logic.
- Integration tests hitting HTTP endpoints with real server startup.
- Black-box tests against packaged container for external contract validation.

### Common errors and exact fixes
- Failure signature: endpoint not found (404) for seemingly correct method.
  - Fix: verify `@HttpRoute` path and method, and that controller class has both `@Component` and `@HttpController`.
- Failure signature: request body deserialization error.
  - Fix: ensure `@Json` is present where required and payload schema matches record/data class.

### Minimum runnable path
```bash
./gradlew run
curl -i http://localhost:8080/hello/world
./gradlew test
```

### Agent rules
1. Start with one controller and one route.
2. Add explicit parameter annotations (`@Path`, `@Query`, `@Header`) for clarity.
3. Keep controller logic thin; call service components.
4. Use private API port for liveness/readiness/metrics endpoints.

## HTTP Client
### What this module solves
Declarative outbound HTTP clients with typed parameters, JSON/form support, interceptor chains, per-method config overrides, and telemetry.

### Do this first
1. Choose client engine (`http-client-jdk`, `http-client-ok`, or other supported client module).
2. Define client config under `httpClient.<name>`.
3. Create interface with `@HttpClient(configPath = ...)`.

### Then this
1. Add methods with `@HttpRoute` and typed arguments.
2. Add request/response mappings as needed.
3. Add method/client interceptors for cross-cutting concerns.

### Verify this
1. Base URL and timeout are applied from config.
2. Path/query/header/body serialization is correct.
3. Error responses are handled predictably.

### Required dependencies
Java:
```groovy
dependencies {
    implementation "ru.tinkoff.kora:http-client-jdk" // or http-client-ok
    implementation "ru.tinkoff.kora:json-module"
}
```

Kotlin:
```kotlin
dependencies {
    implementation("ru.tinkoff.kora:http-client-jdk")
    implementation("ru.tinkoff.kora:json-module")
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        JsonModule,
        JdkHttpClientModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application :
    HoconConfigModule,
    LogbackModule,
    JsonModule,
    JdkHttpClientModule
```

### Configuration keys
HOCON:
```hocon
httpClient.default {
  url = ${HTTP_CLIENT_URL}
  requestTimeout = 10s
  getValuesConfig.requestTimeout = 20s
  telemetry.logging.enabled = true
}
```

YAML equivalent:
```yaml
httpClient:
  default:
    url: ${HTTP_CLIENT_URL}
    requestTimeout: 10s
    getValuesConfig:
      requestTimeout: 20s
    telemetry:
      logging:
        enabled: true
```

### Minimal Java example
```java
@ru.tinkoff.kora.http.client.common.annotation.HttpClient(configPath = "httpClient.default")
public interface GreetingClient {
    @ru.tinkoff.kora.http.common.annotation.HttpRoute(
            method = ru.tinkoff.kora.http.common.HttpMethod.GET,
            path = "/hello/{name}")
    String get(@ru.tinkoff.kora.http.common.annotation.Path String name);
}
```

### Minimal Kotlin example
```kotlin
@HttpClient(configPath = "httpClient.default")
interface GreetingClient {
    @HttpRoute(method = HttpMethod.GET, path = "/hello/{name}")
    fun get(@Path name: String): String
}
```

### Production-style example
#### Embedded source: JsonHttpClient.java
```java
package ru.tinkoff.kora.example.http.client;

import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.json.common.annotation.Json;

@HttpClient(configPath = "httpClient.default")
public interface JsonHttpClient {

    @Json
    record JsonRequest(String id) {}

    @Json
    record JsonResponse(String name, int value) {}

    @HttpRoute(method = HttpMethod.POST, path = "/json")
    @Json
    JsonResponse post(@Json JsonRequest body);
}
```

#### Embedded source: ParametersHttpClient.java
```java
package ru.tinkoff.kora.example.http.client;

import jakarta.annotation.Nullable;
import java.util.List;
import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Path;
import ru.tinkoff.kora.http.common.annotation.Query;

@HttpClient(configPath = "httpClient.default")
public interface ParametersHttpClient {

    @HttpRoute(method = HttpMethod.POST, path = "/parameters/{path}")
    HttpResponseEntity<String> post(@Path String path,
                                    @Nullable @Query String query,
                                    @Nullable @Query("queries") List<String> queries,
                                    @Nullable @Header String header,
                                    @Nullable @Header("headers") List<String> headers);
}
```

#### Embedded source: InterceptedHttpClient.java
```java
package ru.tinkoff.kora.example.http.client;

import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.InterceptWith;

@InterceptWith(InterceptedHttpClient.ClientInterceptor.class)
@HttpClient(configPath = "httpClient.default")
public interface InterceptedHttpClient {

    final class ClientInterceptor implements HttpClientInterceptor {

        private static final Logger logger = LoggerFactory.getLogger(ClientInterceptor.class);

        @Override
        public CompletionStage<HttpClientResponse> processRequest(Context ctx, InterceptChain chain, HttpClientRequest request)
                throws Exception {
            logger.info("Client Level Interceptor");
            return chain.process(ctx, request);
        }
    }

    final class MethodInterceptor implements HttpClientInterceptor {

        private static final Logger logger = LoggerFactory.getLogger(MethodInterceptor.class);

        @Override
        public CompletionStage<HttpClientResponse> processRequest(Context ctx, InterceptChain chain, HttpClientRequest request)
                throws Exception {
            logger.info("Method Level Interceptor");
            return chain.process(ctx, request);
        }
    }

    @InterceptWith(MethodInterceptor.class)
    @HttpRoute(method = HttpMethod.GET, path = "/intercepted")
    HttpResponseEntity<String> get();
}
```
### Testing approach
- Mock upstream service with Testcontainers MockServer or local stub.
- Validate serialized request shape and response mapping.
- Validate timeout behavior via controlled delayed responses.

### Common errors and exact fixes
- Failure signature: client bean not created.
  - Fix: ensure client interface has `@HttpClient` and app includes matching client module.
- Failure signature: `url` missing for config path.
  - Fix: set `httpClient.<name>.url` and confirm env variable substitution.

### Minimum runnable path
```bash
HTTP_CLIENT_URL=http://localhost:8080 ./gradlew run
./gradlew test
```

### Agent rules
1. Treat each external service as a dedicated client interface.
2. Keep client method signatures explicit (no ambiguous generic maps by default).
3. Use per-method config section only when behavior differs from default.
4. Add interceptors only for reusable cross-cutting concerns.

## OpenAPI Generator
### What this module solves
Generates typed HTTP server contracts and HTTP clients from OpenAPI specification files using the Kora OpenAPI generator.

### Do this first
1. Add OpenAPI generator classpath dependency and `org.openapi.generator` Gradle plugin.
2. Define one or more OpenAPI spec files under `src/main/resources/openapi`.
3. Configure generation tasks with `GenerateTask` and a Kora mode (`java-server`, `java-client`, etc.).

### Then this
1. Wire generated source directories into main source set.
2. Make compile depend on generation tasks.
3. Implement generated delegates (server) or inject generated client interfaces (client).

### Verify this
1. Generated code appears under build output and is compiled.
2. Server delegates are discovered and routes are available.
3. Client interfaces are generated and configured via `clientConfigPrefix`.

### Required dependencies
Java (`build.gradle`):
```groovy
buildscript {
    dependencies {
        classpath("ru.tinkoff.kora:openapi-generator:1.2.9")
    }
}

plugins {
    id "org.openapi.generator" version "7.14.0"
}

dependencies {
    implementation "ru.tinkoff.kora:http-server-undertow" // for server mode
    implementation "ru.tinkoff.kora:http-client-jdk"      // for client mode
    implementation "ru.tinkoff.kora:json-module"
    implementation "ru.tinkoff.kora:validation-module"
}
```

Kotlin (`build.gradle.kts`):
```kotlin
buildscript {
    dependencies {
        classpath("ru.tinkoff.kora:openapi-generator:1.2.9")
    }
}

plugins {
    id("org.openapi.generator") version "7.14.0"
}

// for generated Kotlin sources with KSP:
tasks.withType<com.google.devtools.ksp.gradle.KspTask> {
    dependsOn("openApiGenerateHttpServer")
}
```

### @KoraApp wiring
Server generation use-case:
```java
@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        ValidationModule,
        JsonModule,
        UndertowHttpServerModule {}
```

Client generation use-case:
```java
@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        ValidationModule,
        JsonModule,
        JdkHttpClientModule {}
```

### Configuration keys
Generated client config example:
```hocon
httpClient.petV2.PetApi {
  url = ${HTTP_CLIENT_PET_V2_URL}
  requestTimeout = 10s
  telemetry.logging.enabled = true
}

httpClient.petV3.PetApi {
  url = ${HTTP_CLIENT_PET_V3_URL}
  requestTimeout = 10s
  telemetry.logging.enabled = true
}
```

Generated auth prefix example:
```hocon
openapiAuth.apiKeyAuth = "MyAuthApiKey"
```

YAML equivalent:
```yaml
httpClient:
  petV2:
    PetApi:
      url: ${HTTP_CLIENT_PET_V2_URL}
      requestTimeout: 10s
      telemetry:
        logging:
          enabled: true
  petV3:
    PetApi:
      url: ${HTTP_CLIENT_PET_V3_URL}
      requestTimeout: 10s
      telemetry:
        logging:
          enabled: true
openapiAuth:
  apiKeyAuth: "MyAuthApiKey"
```

### Minimal Java example
```groovy
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

def openApiGenerateHttpServer = tasks.register("openApiGenerateHttpServer", GenerateTask) {
    generatorName = "kora"
    inputSpec = "$projectDir/src/main/resources/openapi/openapi.yaml"
    outputDir = "$buildDir/generated/openapi"
    def corePackage = "com.example.openapi"
    apiPackage = "${corePackage}.api"
    modelPackage = "${corePackage}.model"
    invokerPackage = "${corePackage}.invoker"
    configOptions = [
            mode: "java-server",
            enableServerValidation: "true"
    ]
}

sourceSets.main { java.srcDirs += openApiGenerateHttpServer.get().outputDir }
compileJava.dependsOn openApiGenerateHttpServer
```

### Minimal Kotlin example
```kotlin
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import com.google.devtools.ksp.gradle.KspTask

val openApiGenerateHttpClient = tasks.register<GenerateTask>("openApiGenerateHttpClient") {
    generatorName = "kora"
    inputSpec = "$projectDir/src/main/resources/openapi/openapi.yaml"
    outputDir = "$buildDir/generated/openapi"
    val corePackage = "com.example.openapi"
    apiPackage = "${corePackage}.api"
    modelPackage = "${corePackage}.model"
    invokerPackage = "${corePackage}.invoker"
    configOptions = mapOf(
        "mode" to "kotlin-client",
        "clientConfigPrefix" to "httpClient.myclient"
    )
}

kotlin.sourceSets.main { kotlin.srcDir(openApiGenerateHttpClient.get().outputDir) }
tasks.withType<KspTask> { dependsOn(openApiGenerateHttpClient) }
```

### Production-style example
#### Embedded source: build.gradle (OpenAPI server)
```groovy
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

buildscript {
    dependencies {
        classpath("ru.tinkoff.kora:openapi-generator:$koraVersion")
    }
}

plugins {
    id "java"
    id "jacoco"
    id "application"

    id "org.openapi.generator" version "7.14.0"
}

configurations {
    koraBom
    annotationProcessor.extendsFrom(koraBom); compileOnly.extendsFrom(koraBom); implementation.extendsFrom(koraBom)
    api.extendsFrom(koraBom); testImplementation.extendsFrom(koraBom); testAnnotationProcessor.extendsFrom(koraBom)
}

dependencies {
    koraBom platform("ru.tinkoff.kora:kora-parent:$koraVersion")
    annotationProcessor "ru.tinkoff.kora:annotation-processors"

    implementation "ru.tinkoff.kora:validation-module"
    implementation "ru.tinkoff.kora:http-server-undertow"
    implementation "ru.tinkoff.kora:json-module"
    implementation "io.projectreactor:reactor-core:3.6.18" // For reactive examples (optional)

    implementation "ru.tinkoff.kora:logging-logback"
    implementation "ru.tinkoff.kora:config-hocon"

    testImplementation "org.json:json:20231013"
    testImplementation "org.skyscreamer:jsonassert:1.5.1"
    testImplementation "ru.tinkoff.kora:test-junit5"
    testImplementation "org.testcontainers:junit-jupiter:1.19.8"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "application"
    mainClass = "ru.tinkoff.kora.example.openapi.http.server.Application"
    applicationDefaultJvmArgs = ["-Dfile.encoding=UTF-8"]
}

//noinspection GroovyAssignabilityCheck
run {
    environment([
            "": ""
    ])
}

// OpenAPI for V2
def openApiGeneratePetV2 = tasks.register("openApiGeneratePetV2", GenerateTask) {
    generatorName = "kora"
    group = "openapi tools"
    inputSpec = "$projectDir/src/main/resources/openapi/petstoreV2.yaml"
    outputDir = "$buildDir/generated/openapi"
    def corePackage = "ru.tinkoff.kora.example.openapi.petV2"
    apiPackage = "${corePackage}.api"
    modelPackage = "${corePackage}.model"
    invokerPackage = "${corePackage}.invoker"
    configOptions = [
            mode                  : "java-server", // java-reactive-server mode is also available for HTTP server generation
            enableServerValidation: "true",
    ]
}
sourceSets.main { java.srcDirs += openApiGeneratePetV2.get().outputDir }
compileJava.dependsOn openApiGeneratePetV2


// Additional OpenAPI Generator for V3
def openApiGeneratePetV3 = tasks.register("openApiGeneratePetV3", GenerateTask) {
    generatorName = "kora"
    group = "openapi tools"
    inputSpec = "$projectDir/src/main/resources/openapi/petstoreV3.yaml"
    outputDir = "$buildDir/generated/openapi"
    def corePackage = "ru.tinkoff.kora.example.openapi.petV3"
    apiPackage = "${corePackage}.api"
    modelPackage = "${corePackage}.model"
    invokerPackage = "${corePackage}.invoker"
    configOptions = [
            mode                  : "java-reactive-server", // java-server mode is also available for HTTP server generation
            enableServerValidation: "true",
    ]
}
sourceSets.main { java.srcDirs += openApiGeneratePetV3.get().outputDir }
compileJava.dependsOn openApiGeneratePetV3

distTar {
    archiveFileName = "application.tar"
}

test {
    dependsOn tasks.distTar

    jvmArgs += [
            "-XX:+TieredCompilation",
            "-XX:TieredStopAtLevel=1",
    ]

    environment([
            "": ""
    ])

    useJUnitPlatform()
    testLogging {
        showStandardStreams(true)
        events("passed", "skipped", "failed")
        exceptionFormat("full")
    }

    exclude("**/\$*")

    jacoco {
        excludes += ["**/generated/**", "**/Application*", "**/\$*"]
    }

    reports {
        html.required = false
        junitXml.required = false
    }
}

sourceSets {
    main {
        java.srcDirs += "$buildDir/generated/openapi"
    }
}

compileJava {
    options.encoding("UTF-8")
    options.incremental(true)
    options.fork = false
}

check.dependsOn tasks.jacocoTestReport
jacocoTestReport {
    reports {
        xml.required = true
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
    classDirectories = files(classDirectories.files.collect { fileTree(dir: it, excludes: test.jacoco.excludes) })
}

javadoc {
    options.encoding = "UTF-8"
    if (JavaVersion.current().isJava9Compatible()) {
        options.addBooleanOption("html5", true)
    }
}
```
#### Embedded source: Application.java (OpenAPI server)
```java
package ru.tinkoff.kora.example.openapi.http.server;

import java.util.concurrent.CompletableFuture;
import ru.tinkoff.kora.application.graph.KoraApplication;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.Principal;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.hocon.HoconConfigModule;
import ru.tinkoff.kora.example.openapi.petV3.api.ApiSecurity;
import ru.tinkoff.kora.http.common.auth.PrincipalWithScopes;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.auth.HttpServerPrincipalExtractor;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServerModule;
import ru.tinkoff.kora.json.module.JsonModule;
import ru.tinkoff.kora.logging.logback.LogbackModule;
import ru.tinkoff.kora.validation.module.ValidationModule;
import ru.tinkoff.kora.validation.module.http.server.ViolationExceptionHttpServerResponseMapper;

@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        ValidationModule,
        JsonModule,
        UndertowHttpServerModule {

    static void main(String[] args) {
        KoraApplication.run(ApplicationGraph::graph);
    }

    default ViolationExceptionHttpServerResponseMapper customViolationExceptionHttpServerResponseMapper() {
        return (request, exception) -> HttpServerResponseException.of(400, exception.getMessage());
    }

    @Tag(ApiSecurity.BearerAuth.class)
    default HttpServerPrincipalExtractor<Principal> bearerHttpServerPrincipalExtractor() {
        return (request, value) -> CompletableFuture.completedFuture(new UserPrincipal("name"));
    }

    @Tag(ApiSecurity.BasicAuth.class)
    default HttpServerPrincipalExtractor<Principal> basicHttpServerPrincipalExtractor() {
        return (request, value) -> CompletableFuture.completedFuture(new UserPrincipal("name"));
    }

    @Tag(ApiSecurity.ApiKeyAuth.class)
    default HttpServerPrincipalExtractor<Principal> apiKeyHttpServerPrincipalExtractor() {
        return (request, value) -> CompletableFuture.completedFuture(new UserPrincipal("name"));
    }

    @Tag(ApiSecurity.OAuth.class)
    default HttpServerPrincipalExtractor<PrincipalWithScopes> oauthHttpServerPrincipalExtractor() {
        return (request, value) -> CompletableFuture.completedFuture(new UserPrincipal("name"));
    }
}
```
#### Embedded source: PetV2Delegate.java
```java
package ru.tinkoff.kora.example.openapi.http.server;

import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.example.openapi.petV2.api.PetApiController;
import ru.tinkoff.kora.example.openapi.petV2.api.PetApiDelegate;
import ru.tinkoff.kora.example.openapi.petV2.api.PetApiResponses;
import ru.tinkoff.kora.example.openapi.petV2.model.Message;
import ru.tinkoff.kora.example.openapi.petV2.model.Pet;

@Component
public final class PetV2Delegate implements PetApiDelegate {

    private final Map<Long, Pet> petMap = new ConcurrentHashMap<>();

    @Override
    public PetApiResponses.AddPetApiResponse addPet(Pet body) {
        petMap.put(body.id(), body);
        return new PetApiResponses.AddPetApiResponse.AddPet200ApiResponse(new Message("OK"));
    }

    @Override
    public PetApiResponses.DeletePetApiResponse deletePet(long petId, @Nullable String apiKey) {
        petMap.remove(petId);
        return new PetApiResponses.DeletePetApiResponse.DeletePet200ApiResponse(new Message("OK"));
    }

    @Override
    public PetApiResponses.FindPetsByStatusApiResponse findPetsByStatus(List<String> status) {
        final Set<Pet.StatusEnum> petStatuses = status.stream()
                .map(Pet.StatusEnum::valueOf)
                .collect(Collectors.toSet());

        final List<Pet> pets = petMap.values().stream()
                .filter(p -> petStatuses.contains(p.status()))
                .toList();

        return new PetApiResponses.FindPetsByStatusApiResponse.FindPetsByStatus200ApiResponse(pets);
    }

    @Override
    public PetApiResponses.FindPetsByTagsApiResponse findPetsByTags(List<String> tags) {
        final Set<String> petTags = new HashSet<>(tags);
        final List<Pet> pets = petMap.values().stream()
                .filter(p -> p.tags() != null)
                .filter(p -> p.tags().stream().allMatch(tag -> petTags.contains(tag.name())))
                .toList();

        if (pets.isEmpty()) {
            return new PetApiResponses.FindPetsByTagsApiResponse.FindPetsByTags400ApiResponse();
        } else {
            return new PetApiResponses.FindPetsByTagsApiResponse.FindPetsByTags200ApiResponse(pets);
        }
    }

    @Override
    public PetApiResponses.GetPetByIdApiResponse getPetById(long petId) {
        if (petId < 0) {
            return new PetApiResponses.GetPetByIdApiResponse.GetPetById400ApiResponse();
        }

        final Pet pet = petMap.get(petId);
        if (pet == null) {
            return new PetApiResponses.GetPetByIdApiResponse.GetPetById404ApiResponse();
        } else {
            return new PetApiResponses.GetPetByIdApiResponse.GetPetById200ApiResponse(pet);
        }
    }

    @Override
    public PetApiResponses.UpdatePetApiResponse updatePet(Pet body) {
        if (!petMap.containsKey(body.id())) {
            return new PetApiResponses.UpdatePetApiResponse.UpdatePet404ApiResponse();
        }

        petMap.put(body.id(), body);
        return new PetApiResponses.UpdatePetApiResponse.UpdatePet200ApiResponse(new Message("OK"));
    }

    @Override
    public PetApiResponses.UpdatePetWithFormApiResponse updatePetWithForm(long petId,
                                                                          PetApiController.UpdatePetWithFormFormParam form) {
        final Pet pet = petMap.get(petId);
        if (pet == null) {
            return new PetApiResponses.UpdatePetWithFormApiResponse.UpdatePetWithForm404ApiResponse();
        }

        final Pet updated = pet
                .withName(form.name())
                .withStatus(Pet.StatusEnum.valueOf(form.status()));
        petMap.put(updated.id(), updated);

        return new PetApiResponses.UpdatePetWithFormApiResponse.UpdatePetWithForm200ApiResponse(new Message("OK"));
    }
}
```
#### Embedded source: PetV3Delegate.java
```java
package ru.tinkoff.kora.example.openapi.http.server;

import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.example.openapi.petV3.api.PetApiDelegate;
import ru.tinkoff.kora.example.openapi.petV3.api.PetApiResponses;
import ru.tinkoff.kora.example.openapi.petV3.model.Message;
import ru.tinkoff.kora.example.openapi.petV3.model.Pet;

@Component
public final class PetV3Delegate implements PetApiDelegate {

    private final Map<Long, Pet> petMap = new ConcurrentHashMap<>();

    @Override
    public Mono<PetApiResponses.AddPetApiResponse> addPet(Pet body) {
        petMap.put(body.id(), body);
        return Mono.just(new PetApiResponses.AddPetApiResponse.AddPet200ApiResponse(body));
    }

    @Override
    public Mono<PetApiResponses.DeletePetApiResponse> deletePet(long petId, @Nullable String apiKey) {
        petMap.remove(petId);
        return Mono.just(new PetApiResponses.DeletePetApiResponse.DeletePet200ApiResponse(new Message("OK")));
    }

    @Override
    public Mono<PetApiResponses.FindPetsByStatusApiResponse> findPetsByStatus(@Nullable String status) {
        if (status == null) {
            return Mono.just(new PetApiResponses.FindPetsByStatusApiResponse.FindPetsByStatus400ApiResponse());
        }

        final Pet.StatusEnum statusEnum = Pet.StatusEnum.valueOf(status);
        final List<Pet> pets = petMap.values().stream()
                .filter(p -> statusEnum.equals(p.status()))
                .toList();

        return Mono.just(new PetApiResponses.FindPetsByStatusApiResponse.FindPetsByStatus200ApiResponse(pets));
    }

    @Override
    public Mono<PetApiResponses.FindPetsByTagsApiResponse> findPetsByTags(List<String> tags) {
        final Set<String> petTags = new HashSet<>(tags);
        final List<Pet> pets = petMap.values().stream()
                .filter(p -> p.tags() != null)
                .filter(p -> p.tags().stream().allMatch(tag -> petTags.contains(tag.name())))
                .toList();

        if (pets.isEmpty()) {
            return Mono.just(new PetApiResponses.FindPetsByTagsApiResponse.FindPetsByTags400ApiResponse());
        } else {
            return Mono.just(new PetApiResponses.FindPetsByTagsApiResponse.FindPetsByTags200ApiResponse(pets));
        }
    }

    @Override
    public Mono<PetApiResponses.GetPetByIdApiResponse> getPetById(long petId) {
        if (petId < 0) {
            return Mono.just(new PetApiResponses.GetPetByIdApiResponse.GetPetById400ApiResponse());
        }

        final Pet pet = petMap.get(petId);
        if (pet == null) {
            return Mono.just(new PetApiResponses.GetPetByIdApiResponse.GetPetById404ApiResponse());
        } else {
            return Mono.just(new PetApiResponses.GetPetByIdApiResponse.GetPetById200ApiResponse(pet));
        }
    }

    @Override
    public Mono<PetApiResponses.UpdatePetApiResponse> updatePet(Pet body) {
        if (!petMap.containsKey(body.id())) {
            return Mono.just(new PetApiResponses.UpdatePetApiResponse.UpdatePet404ApiResponse());
        }

        petMap.put(body.id(), body);
        return Mono.just(new PetApiResponses.UpdatePetApiResponse.UpdatePet200ApiResponse(body));
    }

    @Override
    public Mono<PetApiResponses.UpdatePetWithFormApiResponse>
            updatePetWithForm(long petId, @Nullable String name, @Nullable String status) {
        final Pet pet = petMap.get(petId);
        if (pet == null) {
            return Mono.just(new PetApiResponses.UpdatePetWithFormApiResponse.UpdatePetWithForm404ApiResponse());
        }

        Pet updated = pet;
        if (name != null) {
            updated = pet.withName(name);
        }
        if (status != null) {
            updated = pet.withStatus(Pet.StatusEnum.valueOf(status));
        }

        petMap.put(updated.id(), updated);
        return Mono.just(new PetApiResponses.UpdatePetWithFormApiResponse.UpdatePetWithForm200ApiResponse(new Message("OK")));
    }
}
```
#### Embedded source: UserPrincipal.java
```java
package ru.tinkoff.kora.example.openapi.http.server;

import java.util.Collection;
import java.util.List;
import ru.tinkoff.kora.http.common.auth.PrincipalWithScopes;

public record UserPrincipal(String name) implements PrincipalWithScopes {

    @Override
    public Collection<String> scopes() {
        return List.of("read", "write");
    }
}
```
#### Embedded source: application.conf (OpenAPI server)
```hocon
httpServer {
  publicApiHttpPort = 8080
  privateApiHttpPort = 8085
  telemetry.logging.enabled = true
}


logging.level {
  "root": "WARN"
  "ru.tinkoff.kora": "INFO"
  "ru.tinkoff.kora.example": "INFO"
}
```
#### Embedded source: petstoreV2.yaml
```yaml
swagger: '2.0'
info:
  description: 'This is a sample server Petstore server. For this sample, you can use the api key `special-key` to test the authorization filters.'
  version: 1.0.0
  title: OpenAPI Petstore
  license:
    name: Apache-2.0
    url: 'https://www.apache.org/licenses/LICENSE-2.0.html'
host: petstore.swagger.io
basePath: /v2
tags:
  - name: pet
    description: Everything about your Pets
schemes:
  - http
paths:
  /v2/pet:
    post:
      tags:
        - pet
      summary: Add a new pet to the store
      operationId: addPet
      consumes:
        - application/json
      produces:
        - application/json
      parameters:
        - in: body
          name: body
          description: Pet object that needs to be added to the store
          required: true
          schema:
            $ref: '#/definitions/Pet'
      responses:
        '200':
          description: successful operation
          schema:
            $ref: '#/definitions/Message'
        '400':
          description: Validation exception
    put:
      tags:
        - pet
      summary: Update an existing pet
      operationId: updatePet
      consumes:
        - application/json
      produces:
        - application/json
      parameters:
        - in: body
          name: body
          description: Pet object that needs to be added to the store
          required: true
          schema:
            $ref: '#/definitions/Pet'
      responses:
        '200':
          description: successful operation
          schema:
            $ref: '#/definitions/Message'
        '400':
          description: Validation exception
        '404':
          description: Pet not found
  /v2/pet/findByStatus:
    get:
      tags:
        - pet
      summary: Finds Pets by status
      description: Multiple status values can be provided with comma separated strings
      operationId: findPetsByStatus
      produces:
        - application/json
      parameters:
        - name: status
          in: query
          description: Status values that need to be considered for filter
          required: true
          type: array
          items:
            type: string
            enum:
              - available
              - pending
              - sold
            default: available
          collectionFormat: csv
      responses:
        '200':
          description: successful operation
          schema:
            type: array
            items:
              $ref: '#/definitions/Pet'
        '400':
          description: Validation exception
  /v2/pet/findByTags:
    get:
      tags:
        - pet
      summary: Finds Pets by tags
      description: 'Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.'
      operationId: findPetsByTags
      produces:
        - application/json
      parameters:
        - name: tags
          in: query
          description: Tags to filter by
          required: true
          type: array
          items:
            type: string
          collectionFormat: csv
      responses:
        '200':
          description: successful operation
          schema:
            type: array
            items:
              $ref: '#/definitions/Pet'
        '400':
          description: Validation exception
      deprecated: true
  '/v2/pet/{petId}':
    get:
      tags:
        - pet
      summary: Find pet by ID
      description: Returns a single pet
      operationId: getPetById
      produces:
        - application/json
      parameters:
        - name: petId
          in: path
          description: ID of pet to return
          required: true
          type: integer
          format: int64
      responses:
        '200':
          description: successful operation
          schema:
            $ref: '#/definitions/Pet'
        '400':
          description: Validation exception
        '404':
          description: Pet not found
    post:
      tags:
        - pet
      summary: Updates a pet in the store with form data
      operationId: updatePetWithForm
      produces:
        - application/json
      consumes:
        - multipart/form-data
      parameters:
        - name: petId
          in: path
          description: ID of pet that needs to be updated
          required: true
          type: integer
          format: int64
        - name: name
          in: formData
          description: Updated name of the pet
          required: true
          type: string
        - name: status
          in: formData
          description: Updated status of the pet
          required: false
          type: string
      responses:
        '200':
          description: successful operation
          schema:
            $ref: '#/definitions/Message'
        '400':
          description: Validation exception
        '404':
          description: Pet not found
    delete:
      tags:
        - pet
      summary: Deletes a pet
      operationId: deletePet
      produces:
        - application/json
      parameters:
        - name: api_key
          in: header
          required: false
          type: string
        - name: petId
          in: path
          description: Pet id to delete
          required: true
          type: integer
          format: int64
      responses:
        '200':
          description: successful operation
          schema:
            $ref: '#/definitions/Message'
        '400':
          description: Validation exception
definitions:
  Category:
    title: Pet category
    description: A category for a pet
    type: object
    properties:
      id:
        type: integer
        format: int64
      name:
        type: string
    xml:
      name: Category
  Tag:
    title: Pet Tag
    description: A tag for a pet
    type: object
    properties:
      id:
        type: integer
        format: int64
      name:
        type: string
    xml:
      name: Tag
  Pet:
    title: a Pet
    description: A pet for sale in the pet store
    type: object
    required:
      - id
      - name
    properties:
      id:
        type: integer
        format: int64
      category:
        $ref: '#/definitions/Category'
      name:
        type: string
        example: doggie
        minLength: 1
        maxLength: 50
      tags:
        type: array
        xml:
          name: tag
          wrapped: true
        items:
          $ref: '#/definitions/Tag'
      status:
        type: string
        description: pet status in the store
        enum:
          - available
          - pending
          - sold
  Message:
    title: Success response
    type: object
    properties:
      message:
        type: string
```
#### Embedded source: petstoreV3.yaml
```yaml
openapi: 3.0.3
info:
  title: Swagger Petstore - OpenAPI 3.0
  description: |-
    This is a sample Pet Store Server based on the OpenAPI 3.0 specification.  You can find out more about
    Swagger at [https://swagger.io](https://swagger.io).
  license:
    name: Apache 2.0
    url: http://www.apache.org/licenses/LICENSE-2.0.html
  version: 1.0.11
externalDocs:
  description: Find out more about Swagger
  url: http://swagger.io
servers:
  - url: https://petstore3.swagger.io/api/v3
tags:
  - name: pet
    description: Everything about your Pets
    externalDocs:
      description: Find out more
      url: http://swagger.io
paths:
  /v3/pet:
    put:
      tags:
        - pet
      summary: Update an existing pet
      description: Update an existing pet by Id
      operationId: updatePet
      requestBody:
        description: Update an existent pet in the store
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Pet'
        required: true
      responses:
        '200':
          description: Successful operation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Pet'
        '400':
          description: Validation exception
        '404':
          description: Pet not found
    post:
      tags:
        - pet
      summary: Add a new pet to the store
      description: Add a new pet to the store
      operationId: addPet
      requestBody:
        required: true
        description: Create a new pet in the store
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Pet'
      responses:
        '200':
          description: Successful operation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Pet'
        '400':
          description: Validation exception
  /v3/pet/findByStatus:
    get:
      tags:
        - pet
      summary: Finds Pets by status
      description: Multiple status values can be provided with comma separated strings
      operationId: findPetsByStatus
      parameters:
        - name: status
          in: query
          description: Status values that need to be considered for filter
          required: false
          explode: true
          schema:
            type: string
            default: available
            enum:
              - available
              - pending
              - sold
      responses:
        '200':
          description: successful operation
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Pet'
            application/xml:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Pet'
        '400':
          description: Invalid status value
  /v3/pet/findByTags:
    get:
      tags:
        - pet
      summary: Finds Pets by tags
      description: Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.
      operationId: findPetsByTags
      parameters:
        - name: tags
          in: query
          description: Tags to filter by
          required: false
          explode: true
          schema:
            type: array
            items:
              type: string
      responses:
        '200':
          description: successful operation
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Pet'
        '400':
          description: Invalid tag value
  /v3/pet/{petId}:
    get:
      tags:
        - pet
      summary: Find pet by ID
      description: Returns a single pet
      operationId: getPetById
      parameters:
        - name: petId
          in: path
          description: ID of pet to return
          required: true
          schema:
            type: integer
            format: int64
            nullable: false
            minimum: 1
      responses:
        '200':
          description: successful operation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Pet'
        '400':
          description: Validation exception
        '404':
          description: Pet not found
    post:
      tags:
        - pet
      summary: Updates a pet in the store with form data
      description: ''
      operationId: updatePetWithForm
      parameters:
        - name: petId
          in: path
          description: ID of pet that needs to be updated
          required: true
          schema:
            nullable: false
            type: integer
            format: int64
        - name: name
          in: query
          description: Name of pet that needs to be updated
          schema:
            type: string
            nullable: false
            minLength: 1
            maxLength: 50
        - name: status
          in: query
          description: Status of pet that needs to be updated
          schema:
            type: string
      responses:
        '200':
          description: successful operation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Message'
        '400':
          description: Validation exception
        '404':
          description: Pet not found
    delete:
      tags:
        - pet
      summary: Deletes a pet
      description: delete a pet
      operationId: deletePet
      parameters:
        - name: api_key
          in: header
          description: ''
          required: false
          schema:
            type: string
        - name: petId
          in: path
          description: Pet id to delete
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: successful operation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Message'
        '400':
          description: Validation exception
        '404':
          description: Pet not found
components:
  schemas:
    Category:
      type: object
      properties:
        id:
          type: integer
          format: int64
          example: 1
        name:
          type: string
          example: Dogs
    Tag:
      type: object
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string
    Message:
      type: object
      properties:
        message:
          type: string
    Pet:
      required:
        - id
        - name
      type: object
      properties:
        id:
          type: integer
          format: int64
          example: 10
          nullable: false
        name:
          type: string
          example: doggie
          nullable: false
          minLength: 1
          maxLength: 50
        category:
          $ref: '#/components/schemas/Category'
        tags:
          type: array
          items:
            $ref: '#/components/schemas/Tag'
        status:
          type: string
          description: pet status in the store
          enum:
            - available
            - pending
            - sold
  requestBodies:
    Pet:
      description: Pet object that needs to be added to the store
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Pet'
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
    apiKeyAuth:
      type: apiKey
      in: header
      name: X-API-KEY
    basicAuth:
      type: http
      scheme: basic
    oAuth:
      type: oauth2
      description: This API uses OAuth 2 with the implicit grant flow. [More info](https://api.example.com/docs/auth)
      flows:
        implicit:
          authorizationUrl: https://api.example.com/oauth2/authorize
          scopes:
            read_pets: read your pets
            write_pets: modify pets in your account
security:
  - bearerAuth: []
  - apiKeyAuth: []
  - basicAuth: []
  - oAuth:
      - write_pets
      - read_pets
```
#### Embedded source: build.gradle (OpenAPI client)
```groovy
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

buildscript {
    dependencies {
        classpath("ru.tinkoff.kora:openapi-generator:$koraVersion")
    }
}

plugins {
    id "java"
    id "jacoco"
    id "application"

    id "org.openapi.generator" version "7.14.0"
}

configurations {
    koraBom
    annotationProcessor.extendsFrom(koraBom); compileOnly.extendsFrom(koraBom); implementation.extendsFrom(koraBom)
    api.extendsFrom(koraBom); testImplementation.extendsFrom(koraBom); testAnnotationProcessor.extendsFrom(koraBom)
}

dependencies {
    koraBom platform("ru.tinkoff.kora:kora-parent:$koraVersion")
    annotationProcessor "ru.tinkoff.kora:annotation-processors"

    implementation "ru.tinkoff.kora:validation-module"
    implementation "ru.tinkoff.kora:http-client-jdk"
    implementation "ru.tinkoff.kora:json-module"
    implementation "io.projectreactor:reactor-core:3.6.18" // For reactive examples (optional)

    implementation "ru.tinkoff.kora:logging-logback"
    implementation "ru.tinkoff.kora:config-hocon"

    testImplementation "org.json:json:20231013"
    testImplementation "org.skyscreamer:jsonassert:1.5.1"
    testImplementation "ru.tinkoff.kora:test-junit5"
    testImplementation "io.goodforgod:testcontainers-extensions-mockserver:0.12.2"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    applicationName = "application"
    mainClass = "ru.tinkoff.kora.example.openapi.http.client.Application"
    applicationDefaultJvmArgs = ["-Dfile.encoding=UTF-8"]
}

//noinspection GroovyAssignabilityCheck
run {
    environment([
            "HTTP_CLIENT_PET_V2_URL": "$httpClientUrlPetV2",
            "HTTP_CLIENT_PET_V3_URL": "$httpClientUrlPetV3",
    ])
}

// OpeAPI for V2
def openApiGeneratePetV2 = tasks.register("openApiGeneratePetV2", GenerateTask) {
    generatorName = "kora"
    group = "openapi tools"
    inputSpec = "$projectDir/src/main/resources/openapi/petstoreV2.yaml"
    outputDir = "$buildDir/generated/openapi"
    def corePackage = "ru.tinkoff.kora.example.openapi.petV2"
    apiPackage = "${corePackage}.api"
    modelPackage = "${corePackage}.model"
    invokerPackage = "${corePackage}.invoker"
    configOptions = [
            mode              : "java-client",
            clientConfigPrefix: "httpClient.petV2",
    ]
}
sourceSets.main { java.srcDirs += openApiGeneratePetV2.get().outputDir }
compileJava.dependsOn openApiGeneratePetV2


// Additional OpenAPI Generator for V3
def openApiGeneratePetV3 = tasks.register("openApiGeneratePetV3", GenerateTask) {
    generatorName = "kora"
    group = "openapi tools"
    inputSpec = "$projectDir/src/main/resources/openapi/petstoreV3.yaml"
    outputDir = "$buildDir/generated/openapi"
    def corePackage = "ru.tinkoff.kora.example.openapi.petV3"
    apiPackage = "${corePackage}.api"
    modelPackage = "${corePackage}.model"
    invokerPackage = "${corePackage}.invoker"
    configOptions = [
            mode                : "java-reactive-client",
            clientConfigPrefix  : "httpClient.petV3",
            securityConfigPrefix: "openapiAuth",
            primaryAuth         : "apiKeyAuth",
    ]
}
sourceSets.main { java.srcDirs += openApiGeneratePetV3.get().outputDir }
compileJava.dependsOn openApiGeneratePetV3

distTar {
    archiveFileName = "application.tar"
}

test {
    dependsOn tasks.distTar

    jvmArgs += [
            "-XX:+TieredCompilation",
            "-XX:TieredStopAtLevel=1",
    ]

    environment([
            "": ""
    ])

    useJUnitPlatform()
    testLogging {
        showStandardStreams(true)
        events("passed", "skipped", "failed")
        exceptionFormat("full")
    }

    exclude("**/\$*")

    jacoco {
        excludes += ["**/generated/**", "**/Application*", "**/\$*"]
    }

    reports {
        html.required = false
        junitXml.required = false
    }
}

compileJava {
    options.encoding("UTF-8")
    options.incremental(true)
    options.fork = false
}

check.dependsOn tasks.jacocoTestReport
jacocoTestReport {
    reports {
        xml.required = true
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
    classDirectories = files(classDirectories.files.collect { fileTree(dir: it, excludes: test.jacoco.excludes) })
}

javadoc {
    options.encoding = "UTF-8"
    if (JavaVersion.current().isJava9Compatible()) {
        options.addBooleanOption("html5", true)
    }
}
```
#### Embedded source: Application.java (OpenAPI client)
```java
package ru.tinkoff.kora.example.openapi.http.client;

import ru.tinkoff.kora.application.graph.KoraApplication;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.hocon.HoconConfigModule;
import ru.tinkoff.kora.http.client.jdk.JdkHttpClientModule;
import ru.tinkoff.kora.json.module.JsonModule;
import ru.tinkoff.kora.logging.logback.LogbackModule;
import ru.tinkoff.kora.validation.module.ValidationModule;

@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        ValidationModule,
        JsonModule,
        JdkHttpClientModule {

    static void main(String[] args) {
        KoraApplication.run(ApplicationGraph::graph);
    }
}
```
#### Embedded source: RootService.java (OpenAPI client)
```java
package ru.tinkoff.kora.example.openapi.http.client;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.example.openapi.petV2.api.PetApi;

@Root
@Component
public final class RootService {

    private final ru.tinkoff.kora.example.openapi.petV2.api.PetApi petApiV2;
    private final ru.tinkoff.kora.example.openapi.petV3.api.PetApi petApiV3;

    public RootService(PetApi petApiV2, ru.tinkoff.kora.example.openapi.petV3.api.PetApi petApiV3) {
        this.petApiV2 = petApiV2;
        this.petApiV3 = petApiV3;
    }
}
```
#### Embedded source: application.conf (OpenAPI client)
```hocon
httpClient.petV2.PetApi {
  url = ${HTTP_CLIENT_PET_V2_URL}
  requestTimeout = 10s
  getValuesConfig {
    requestTimeout = 20s
  }
  telemetry.logging.enabled = true
}

httpClient.petV3.PetApi {
  url = ${HTTP_CLIENT_PET_V3_URL}
  requestTimeout = 10s
  getValuesConfig {
    requestTimeout = 20s
  }
  telemetry.logging.enabled = true
}

openapiAuth.apiKeyAuth = "MyAuthApiKey"

logging.level {
  "root": "WARN"
  "ru.tinkoff.kora": "INFO"
  "ru.tinkoff.kora.example": "INFO"
}
```

### Testing approach
- Run generation tasks in CI before compile/tests.
- For server mode: test generated routes via delegate implementations.
- For client mode: test generated clients against mock HTTP upstream.
- Validate generated auth behavior when security schemes are present.

### Common errors and exact fixes
- Failure signature: generated classes are not found during compile.
  - Fix: register generated source dirs and wire compile/KSP dependency on generation tasks.
- Failure signature: wrong API style (sync/reactive/suspend) in generated code.
  - Fix: set the correct `mode` in `configOptions` for server/client generation.
- Failure signature: generated client fails due to missing auth config.
  - Fix: configure `securityConfigPrefix` and the expected `${prefix}.<schemeName>` value.
- Failure signature: server delegates compile but validation behavior is missing.
  - Fix: enable `enableServerValidation = "true"` in server generation task.

### Minimum runnable path
```bash
./gradlew openApiGeneratePetV2
./gradlew openApiGeneratePetV3
./gradlew run
./gradlew test
```

### Agent rules
1. Treat OpenAPI specs as the single source of truth for transport contracts.
2. Regenerate before compile whenever specs change.
3. Keep generated code out of manual edits; customize behavior in delegate implementations/modules.
4. Lock generator mode/config in build scripts and test it in CI.


## gRPC Server
### What this module solves
Hosts gRPC services generated from protobuf contracts, with interceptor support and telemetry integration.

### Do this first
1. Add protobuf plugin and grpc dependencies.
2. Define `.proto` contracts.
3. Add `grpc-server` module and include `GrpcServerModule` in app.

### Then this
1. Implement generated `*ImplBase` service class as `@Component`.
2. Configure server port and telemetry.
3. Add optional `ServerInterceptor` components.

### Verify this
1. Protobuf code generation runs successfully.
2. Service starts on configured gRPC port.
3. Request-response flow works with generated client/stub.

### Required dependencies
Java:
```groovy
plugins {
    id "com.google.protobuf" version "0.9.5"
}

dependencies {
    implementation "ru.tinkoff.kora:grpc-server"
    implementation "io.grpc:grpc-protobuf:1.74.0"
    implementation "javax.annotation:javax.annotation-api:1.3.2"
}
```

Kotlin (`build.gradle.kts`, using Java stubs from protobuf plugin):
```kotlin
plugins {
    id("com.google.protobuf") version "0.9.5"
}

dependencies {
    implementation("ru.tinkoff.kora:grpc-server")
    implementation("io.grpc:grpc-protobuf:1.74.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends HoconConfigModule, LogbackModule, GrpcServerModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application : HoconConfigModule, LogbackModule, GrpcServerModule
```

### Configuration keys
HOCON:
```hocon
grpcServer {
  port = ${GRPC_PORT}
  telemetry.logging.enabled = true
}
```

YAML equivalent:
```yaml
grpcServer:
  port: ${GRPC_PORT}
  telemetry:
    logging:
      enabled: true
```

### Minimal Java example
```java
@Component
public final class UserService extends UserServiceGrpc.UserServiceImplBase {
    @Override
    public void createUser(Message.RequestEvent request,
                           io.grpc.stub.StreamObserver<Message.ResponseEvent> responseObserver) {
        responseObserver.onNext(Message.ResponseEvent.newBuilder()
                .setStatus(Message.ResponseEvent.StatusType.SUCCESS)
                .build());
        responseObserver.onCompleted();
    }
}
```

### Minimal Kotlin example
```kotlin
@Component
class UserService : UserServiceGrpc.UserServiceImplBase() {
    override fun createUser(
        request: Message.RequestEvent,
        responseObserver: io.grpc.stub.StreamObserver<Message.ResponseEvent>
    ) {
        responseObserver.onNext(
            Message.ResponseEvent.newBuilder()
                .setStatus(Message.ResponseEvent.StatusType.SUCCESS)
                .build()
        )
        responseObserver.onCompleted()
    }
}
```

### Production-style example
#### Embedded source: message.proto
```proto
syntax = "proto3";

package ru.tinkoff.kora.generated.grpc;

import "google/protobuf/timestamp.proto";

service UserService {
  rpc createUser(RequestEvent) returns (ResponseEvent) {}
}

message RequestEvent {
  string name = 1;
  string code = 2;
}

message ResponseEvent {
  bytes id = 1;
  StatusType status = 2;
  Type type = 3;
  google.protobuf.Timestamp created_at = 4;

  enum StatusType {
    SUCCESS = 0;
    FAILED = 1;
  }

  enum Type {
    CLOSED = 0;
    OPENED = 1;
    REOPENED = 2;
  }
}
```

#### Embedded source: UserService.java
```java
package ru.tinkoff.kora.example.grpc.server;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.generated.grpc.Message;
import ru.tinkoff.kora.generated.grpc.UserServiceGrpc;

@Component
public final class UserService extends UserServiceGrpc.UserServiceImplBase {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void createUser(Message.RequestEvent request, StreamObserver<Message.ResponseEvent> responseObserver) {
        logger.info("Received request for name {} and code {}", request.getName(), request.getCode());

        responseObserver.onNext(Message.ResponseEvent.newBuilder()
                .setId(ByteString.copyFromUtf8(UUID.randomUUID().toString()))
                .setStatus(Message.ResponseEvent.StatusType.SUCCESS)
                .setType(Message.ResponseEvent.Type.OPENED)
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(OffsetDateTime.now().toEpochSecond())
                        .build())
                .build());

        logger.info("Processed request for name {} and code {}", request.getName(), request.getCode());
        responseObserver.onCompleted();
    }
}
```

#### Embedded source: MyServerInterceptor.java
```java
package ru.tinkoff.kora.example.grpc.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;

@Component
public final class MyServerInterceptor implements ServerInterceptor {

    private final Logger logger = LoggerFactory.getLogger(MyServerInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT>
            interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        logger.info("INTERCEPTED");
        return next.startCall(call, headers);
    }
}
```
### Testing approach
- Integration tests with generated blocking stubs.
- Validate interceptor side effects and metadata processing.

### Common errors and exact fixes
- Failure signature: generated classes missing.
  - Fix: configure protobuf plugin and ensure `generateProto` runs before compile.
- Failure signature: port binding failure.
  - Fix: change `GRPC_PORT` or stop conflicting process.

### Minimum runnable path
```bash
GRPC_PORT=8090 ./gradlew run
./gradlew test
```

### Agent rules
1. Treat proto as contract-first source of truth.
2. Regenerate stubs after any proto change.
3. Keep service implementations stateless where possible.
4. Use interceptors for cross-cutting concerns only.

## gRPC Client
### What this module solves
Creates and injects generated gRPC stubs configured by service name/path, with optional tagged interceptors.

### Do this first
1. Add protobuf plugin and `grpc-client` dependency.
2. Keep client proto in sync with server contract.
3. Add gRPC client URL config.

### Then this
1. Inject generated stub into component.
2. Call remote methods from application service.
3. Add optional `ClientInterceptor` and tag by stub type when needed.

### Verify this
1. Stub bean is generated and injected.
2. Client can connect and execute method successfully.
3. Interceptor executes for tagged client.

### Required dependencies
Java:
```groovy
plugins {
    id "com.google.protobuf" version "0.9.5"
}

dependencies {
    implementation "ru.tinkoff.kora:grpc-client"
    implementation "io.grpc:grpc-protobuf:1.74.0"
    implementation "javax.annotation:javax.annotation-api:1.3.2"
}
```

Kotlin:
```kotlin
plugins {
    id("com.google.protobuf") version "0.9.5"
}

dependencies {
    implementation("ru.tinkoff.kora:grpc-client")
    implementation("io.grpc:grpc-protobuf:1.74.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends HoconConfigModule, LogbackModule, ru.tinkoff.grpc.client.GrpcClientModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application : HoconConfigModule, LogbackModule, ru.tinkoff.grpc.client.GrpcClientModule
```

### Configuration keys
HOCON:
```hocon
grpcClient {
  UserService {
    url = ${GRPC_URL}
    telemetry.logging.enabled = true
  }
}
```

YAML equivalent:
```yaml
grpcClient:
  UserService:
    url: ${GRPC_URL}
    telemetry:
      logging:
        enabled: true
```

### Minimal Java example
```java
@ru.tinkoff.kora.common.annotation.Root
@ru.tinkoff.kora.common.Component
public final class RootService {
    private final UserServiceGrpc.UserServiceBlockingStub userService;

    public RootService(UserServiceGrpc.UserServiceBlockingStub userService) {
        this.userService = userService;
    }

    public Message.ResponseEvent call(String name, String code) {
        return userService.createUser(Message.RequestEvent.newBuilder().setName(name).setCode(code).build());
    }
}
```

### Minimal Kotlin example
```kotlin
@Root
@Component
class RootService(private val userService: UserServiceGrpc.UserServiceBlockingStub) {
    fun call(name: String, code: String): Message.ResponseEvent {
        return userService.createUser(
            Message.RequestEvent.newBuilder().setName(name).setCode(code).build()
        )
    }
}
```

### Production-style example
#### Embedded source: RootService.java
```java
package ru.tinkoff.kora.example.grpc.client;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.generated.grpc.UserServiceGrpc;

@Root
@Component
public final class RootService {

    private final UserServiceGrpc.UserServiceBlockingStub userService;

    public RootService(UserServiceGrpc.UserServiceBlockingStub userService) {
        this.userService = userService;
    }

    public UserServiceGrpc.UserServiceBlockingStub service() {
        return userService;
    }
}
```

#### Embedded source: MyClientInterceptor.java
```java
package ru.tinkoff.kora.example.grpc.client;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.generated.grpc.UserServiceGrpc;

@Tag(UserServiceGrpc.class)
@Component
public final class MyClientInterceptor implements ClientInterceptor {

    private final Logger logger = LoggerFactory.getLogger(MyClientInterceptor.class);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT>
            interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        logger.info("INTERCEPTED");
        return next.newCall(method, callOptions);
    }
}
```
### Testing approach
- Run integration tests with in-test server or containerized server.
- Verify connection URL resolution and timeout/retry behavior if configured.

### Common errors and exact fixes
- Failure signature: stub not injected.
  - Fix: ensure generated gRPC classes are in source set and `GrpcClientModule` is included.
- Failure signature: `UNAVAILABLE` status at runtime.
  - Fix: verify `grpcClient.<Service>.url`, server availability, and network reachability.

### Minimum runnable path
```bash
GRPC_URL=http://localhost:8090 ./gradlew run
./gradlew test
```

### Agent rules
1. Keep proto contracts synchronized with server.
2. Inject stubs into root/service components only.
3. Use tagged interceptors for service-specific concerns.
4. Validate connectivity in integration tests before feature work.

## OpenTelemetry Integration
### What this module solves
Exports traces and enriches telemetry context across modules (HTTP, gRPC, Kafka, DB), enabling distributed tracing and correlation.

### Do this first
1. Choose exporter transport: gRPC or HTTP.
2. Add exporter module dependency.
3. Set exporter endpoint and service attributes.

### Then this
1. Ensure module-level telemetry tracing is enabled where needed.
2. Use context APIs only when manual span handling is required.
3. Verify exported traces in collector.

### Verify this
1. Spans are visible in tracing backend.
2. Service attributes are attached.
3. Trace IDs correlate across outbound/inbound requests.

### Required dependencies
Java:
```groovy
dependencies {
    implementation "ru.tinkoff.kora:opentelemetry-tracing-exporter-grpc"
    // or
    implementation "ru.tinkoff.kora:opentelemetry-tracing-exporter-http"
    implementation "ru.tinkoff.kora:micrometer-module"
}
```

Kotlin:
```kotlin
dependencies {
    implementation("ru.tinkoff.kora:opentelemetry-tracing-exporter-grpc")
    // or http exporter
    implementation("ru.tinkoff.kora:micrometer-module")
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        MetricsModule,
        UndertowHttpServerModule,
        OpentelemetryGrpcExporterModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application :
    HoconConfigModule,
    LogbackModule,
    MetricsModule,
    UndertowHttpServerModule,
    OpentelemetryGrpcExporterModule
```

### Configuration keys
HOCON:
```hocon
tracing {
  exporter {
    endpoint = ${METRIC_COLLECTOR_ENDPOINT}
    exportTimeout = "250s"
    scheduleDelay = "50ms"
    maxExportBatchSize = 10000
  }
  attributes {
    "service.name" = "my-service"
    "service.namespace" = "kora"
  }
}
```

YAML equivalent:
```yaml
tracing:
  exporter:
    endpoint: ${METRIC_COLLECTOR_ENDPOINT}
    exportTimeout: "250s"
    scheduleDelay: "50ms"
    maxExportBatchSize: 10000
  attributes:
    service.name: "my-service"
    service.namespace: "kora"
```

### Minimal Java example
```java
@ru.tinkoff.kora.common.Component
public final class TraceAwareService {
    public String currentTraceId() {
        return ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext.getTraceId();
    }
}
```

### Minimal Kotlin example
```kotlin
@Component
class TraceAwareService {
    fun currentTraceId(): String =
        ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext.getTraceId()
}
```

### Production-style example
#### Embedded source: Application.java (Telemetry)
```java
package ru.tinkoff.kora.example.telemetry;

import ru.tinkoff.kora.application.graph.KoraApplication;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.hocon.HoconConfigModule;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServerModule;
import ru.tinkoff.kora.logging.logback.LogbackModule;
import ru.tinkoff.kora.micrometer.module.MetricsModule;
import ru.tinkoff.kora.opentelemetry.tracing.exporter.grpc.OpentelemetryGrpcExporterModule;

@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        MetricsModule,
        UndertowHttpServerModule,
        OpentelemetryGrpcExporterModule {

    static void main(String[] args) {
        KoraApplication.run(ApplicationGraph::graph);
    }
}
```

#### Embedded source: application.conf (Telemetry)
```hocon
httpServer {
  publicApiHttpPort = 8080
  privateApiHttpPort = 8085
  privateApiHttpLivenessPath = "/liveness"
  privateApiHttpReadinessPath = "/readiness"
  telemetry.logging.enabled = true
}

tracing {
  exporter {
    endpoint = ${METRIC_COLLECTOR_ENDPOINT}
    exportTimeout = "250s"
    scheduleDelay = "50ms"
    maxExportBatchSize = 10000
  }
  attributes {
    "service.name" = "kora-java-telemetry"
    "service.namespace" = "kora"
  }
}


logging.level {
  "root": "WARN"
  "ru.tinkoff.kora": "INFO"
  "ru.tinkoff.kora.example": "INFO"
}
```

#### Embedded source: OpentelemetryTracingModule.java
```java
package ru.tinkoff.kora.opentelemetry.tracing;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.function.Supplier;

public interface OpentelemetryTracingModule {

    default OpentelemetryResourceConfig opentelemetryResourceConfig(Config config, ConfigValueExtractor<OpentelemetryResourceConfig> extractor) {
        return extractor.extract(config.get("tracing"));
    }

    default Resource opentelemetryTracingResource(OpentelemetryResourceConfig config) {
        var resource = Resource.builder();
        for (var attribute : config.attributes().entrySet()) {
            resource.put(attribute.getKey(), attribute.getValue());
        }
        return resource.build();
    }

    @DefaultComponent
    default IdGenerator opentelemetryTracingIdGenerator() {
        return IdGenerator.random();
    }

    @DefaultComponent
    default Supplier<SpanLimits> opentelemetryTracingSpanLimitsSupplier() {
        return SpanLimits::getDefault;
    }

    @DefaultComponent
    default Sampler opentelemetryTracingSampler() {
        return Sampler.parentBased(Sampler.alwaysOn());
    }

    default LifecycleWrapper<SdkTracerProvider> opentelemetryTracerProvider(IdGenerator idGenerator, Supplier<SpanLimits> spanLimits, Sampler sampler, @Nullable SpanProcessor spanProcessor, Resource resource) {
        if (spanProcessor == null) {
            spanProcessor = SpanProcessor.composite();
        }
        return new LifecycleWrapper<>(
            SdkTracerProvider.builder()
                .setIdGenerator(idGenerator)
                .setSpanLimits(spanLimits)
                .setSampler(sampler)
                .addSpanProcessor(spanProcessor)
                .setResource(resource)
                .build(),
            p -> {},
            SdkTracerProvider::close
        );
    }

    default Tracer opentelemetryTracer(TracerProvider tracerProvider) {
        return tracerProvider
            .tracerBuilder("kora")
            .build();
    }
}
```
### Testing approach
- Integration test with local collector or mocked exporter endpoint.
- Validate that at least one span is emitted per request path.
- Validate service attributes in exported spans.

### Common errors and exact fixes
- Failure signature: no traces exported.
  - Fix: ensure exporter module is wired in `@KoraApp` and `tracing.exporter.endpoint` is set.
- Failure signature: traces exist but missing service metadata.
  - Fix: set `tracing.attributes.service.name` and related attributes explicitly.

### Minimum runnable path
```bash
METRIC_COLLECTOR_ENDPOINT=http://localhost:4317 ./gradlew run
```

### Agent rules
1. Never enable tracing partially without endpoint/config verification.
2. Keep service identity attributes stable across environments.
3. Prefer automatic instrumentation from Kora modules; use manual spans only for custom critical sections.
4. Validate trace flow early in integration testing.

## Kafka
### What this module solves
Defines declarative consumers and producers with typed payloads, commit strategies, transactions, and telemetry.

### Do this first
1. Add `kafka` dependency and configure producer/consumer blocks.
2. Implement one listener and one publisher first.
3. Ensure broker bootstrap servers are injected from env.

### Then this
1. Choose auto or manual commit handling.
2. Add JSON/mappers for payload conversion if required.
3. Introduce transactional publisher only when needed.

### Verify this
1. Listener receives records from configured topic.
2. Publisher sends records to expected topic.
3. Commit behavior matches processing guarantees.

### Required dependencies
Java:
```groovy
dependencies {
    implementation "ru.tinkoff.kora:kafka"
    implementation "ru.tinkoff.kora:json-module"
}
```

Kotlin:
```kotlin
dependencies {
    implementation("ru.tinkoff.kora:kafka")
    implementation("ru.tinkoff.kora:json-module")
}
```

### @KoraApp wiring
Java:
```java
@KoraApp
public interface Application extends
        HoconConfigModule,
        LogbackModule,
        JsonModule,
        KafkaModule {}
```

Kotlin:
```kotlin
@KoraApp
interface Application :
    HoconConfigModule,
    LogbackModule,
    JsonModule,
    KafkaModule
```

### Configuration keys
HOCON:
```hocon
kafka {
  producer {
    my-topic.topic = "my-topic-producer"
    my-publisher {
      driverProperties."bootstrap.servers" = ${KAFKA_BOOTSTRAP}
      telemetry.logging.enabled = true
    }
  }
  consumer {
    my-listener {
      pollTimeout = 250ms
      topics = "my-topic-consumer"
      driverProperties."bootstrap.servers" = ${KAFKA_BOOTSTRAP}
      driverProperties."group.id" = "my-group-id"
      driverProperties."enable.auto.commit" = true
      telemetry.logging.enabled = true
    }
  }
}
```

YAML equivalent:
```yaml
kafka:
  producer:
    my-topic:
      topic: "my-topic-producer"
    my-publisher:
      driverProperties:
        bootstrap.servers: ${KAFKA_BOOTSTRAP}
      telemetry:
        logging:
          enabled: true
  consumer:
    my-listener:
      pollTimeout: 250ms
      topics: "my-topic-consumer"
      driverProperties:
        bootstrap.servers: ${KAFKA_BOOTSTRAP}
        group.id: "my-group-id"
        enable.auto.commit: true
      telemetry:
        logging:
          enabled: true
```

### Minimal Java example
```java
@ru.tinkoff.kora.common.Component
public final class Listener {
    @ru.tinkoff.kora.kafka.common.annotation.KafkaListener("kafka.consumer.my-listener")
    void process(String value) {
        System.out.println("Consumed: " + value);
    }
}

@ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("kafka.producer.my-publisher")
interface Producer {
    void send(org.apache.kafka.clients.producer.ProducerRecord<String, String> record);
}
```

### Minimal Kotlin example
```kotlin
@Component
class Listener {
    @KafkaListener("kafka.consumer.my-listener")
    fun process(value: String) {
        println("Consumed: $value")
    }
}

@KafkaPublisher("kafka.producer.my-publisher")
interface Producer {
    fun send(record: org.apache.kafka.clients.producer.ProducerRecord<String, String>)
}
```

### Production-style example
#### Embedded source: AutoCommitValueListener.java
```java
package ru.tinkoff.kora.example.kafka.listener;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.kafka.common.annotation.KafkaListener;

@Component
public final class AutoCommitValueListener extends AbstractListener<String> {

    @KafkaListener("kafka.consumer.my-listener")
    void process(String value) {
        success(value);
    }
}
```

#### Embedded source: ManualCommitRecordListener.java
```java
package ru.tinkoff.kora.example.kafka.listener;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.kafka.common.annotation.KafkaListener;

@Component
public final class ManualCommitRecordListener extends AbstractListener<String> {

    @KafkaListener("kafka.consumer.my-listener")
    void process(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        success(record.value());
        consumer.commitSync();
    }
}
```

#### Embedded source: ProducerPublisher.java
```java
package ru.tinkoff.kora.example.kafka.publisher;

import org.apache.kafka.clients.producer.ProducerRecord;
import ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher;

@KafkaPublisher("kafka.producer.my-publisher")
public interface ProducerPublisher {

    void send(ProducerRecord<String, String> record);
}
```

#### Embedded source: MyTransactionalPublisher.java
```java
package ru.tinkoff.kora.example.kafka.publisher;

import ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher;
import ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher.Topic;
import ru.tinkoff.kora.kafka.common.producer.TransactionalPublisher;

@KafkaPublisher("kafka.producer.my-transactional")
public interface MyTransactionalPublisher extends TransactionalPublisher<MyTransactionalPublisher.TopicPublisher> {

    @KafkaPublisher("kafka.producer.my-publisher")
    interface TopicPublisher {

        @Topic("kafka.producer.my-topic")
        void send(String value);
    }
}
```

#### Embedded source: application.conf (Kafka)
```hocon
kafka {
  producer {
    my-topic {
      topic: "my-topic-producer"
    }
    my-publisher {
      driverProperties {
        "bootstrap.servers": ${KAFKA_BOOTSTRAP}
      }
      telemetry.logging.enabled = true
    }
    my-transactional {
      idPrefix: "my-transaction"
      maxPoolSize: 10
      telemetry.logging.enabled = true
    }
  }
  consumer {
    my-listener {
      pollTimeout: 250ms
      topics: "my-topic-consumer"
      driverProperties {
        "bootstrap.servers": ${KAFKA_BOOTSTRAP}
        "group.id": "my-group-id"
        "auto.offset.reset" = "latest"
        "enable.auto.commit" = true
      }
      telemetry.logging.enabled = true
    }
  }
}


logging.level {
  "root": "WARN"
  "ru.tinkoff.kora": "INFO"
  "ru.tinkoff.kora.example": "INFO"
}
```
### Testing approach
- Use Kafka Testcontainers for integration tests.
- Validate both publish and consume paths.
- Assert manual commit behavior where enabled.

### Common errors and exact fixes
- Failure signature: listener receives nothing.
  - Fix: verify topic name, group id, and bootstrap server config path.
- Failure signature: serialization/deserialization errors.
  - Fix: align value type with listener/publisher signature and JSON mapper configuration.

### Minimum runnable path
```bash
KAFKA_BOOTSTRAP=localhost:9092 ./gradlew run
./gradlew test
```

### Agent rules
1. Start from one topic and one consumer group.
2. Use manual commit only when idempotency/replay semantics require it.
3. Keep producer interfaces focused and explicit.
4. Validate broker config at startup.

## End-to-End Microservice Assembly Recipes
### Recipe A: CRUD HTTP service (sync)
Modules: Configuration + Components + DB (JDBC) + Caching + OpenAPI Generator (server) + HTTP Server + Testing + OpenTelemetry.

1. Start from baseline setup.
2. Add dependencies:
   - `http-server-undertow`
   - `database-jdbc`
   - `cache-caffeine` (or `cache-redis`)
   - `json-module`
   - `validation-module`
   - `config-hocon`
   - `opentelemetry-tracing-exporter-grpc`
   - `test-junit5`
3. Add OpenAPI server generation task and wire it to compile.
4. Wire modules in `@KoraApp`.
5. Define `db { ... }`, `httpServer { ... }`, cache and telemetry config.
6. Implement one generated delegate, one service, one repository, one cache contract.
7. Add migration in `db/migration`.
8. Add component test and integration test.
9. Run:
```bash
./gradlew openApiGeneratePetV3
./gradlew flywayMigrate
./gradlew run
./gradlew test
```

### Recipe B: API service with outbound call
Modules: Configuration + Components + HTTP Server + HTTP Client + Testing + OpenTelemetry.

1. Add HTTP server and HTTP client dependencies.
2. Add outbound client config `httpClient.default`.
3. Build service component that calls client.
4. Expose controller endpoint that delegates to service.
5. Add tests with mocked upstream.
6. Run:
```bash
HTTP_CLIENT_URL=http://localhost:8081 ./gradlew run
./gradlew test
```

### Recipe C: Event-driven worker
Modules: Configuration + Components + Kafka + DB + OpenTelemetry + Testing.

1. Add `kafka` and chosen DB dependency.
2. Configure `kafka.consumer.*` and `kafka.producer.*`.
3. Implement one listener and optional publisher.
4. Persist consumed payload through repository.
5. Add integration test with Kafka + DB Testcontainers.
6. Run:
```bash
KAFKA_BOOTSTRAP=localhost:9092 ./gradlew run
./gradlew test
```

### Recipe D: OpenAPI-first transport flow
Modules: OpenAPI Generator (client/server) + HTTP Server/Client + Validation + Testing.

1. Author OpenAPI spec under `src/main/resources/openapi`.
2. Add generation tasks for server and/or client with required mode.
3. Wire generated output directories into source sets.
4. Make compile (and KSP for Kotlin) depend on generation tasks.
5. Implement generated delegates (server) or inject generated clients (client).
6. Add configuration for generated clients and security prefixes.
7. Add transport-level integration tests.
8. Run:
```bash
./gradlew openApiGeneratePetV2
./gradlew openApiGeneratePetV3
./gradlew test
```


## Troubleshooting and Failure Modes
### Build-time failures
- Symptom: generated `ApplicationGraph` missing.
  - Cause: annotation processors/KSP misconfigured.
  - Fix: ensure Java uses `annotation-processors`; Kotlin uses `ksp("ru.tinkoff.kora:symbol-processors")` and generated source dirs are configured.

- Symptom: gRPC generated classes missing.
  - Cause: protobuf plugin/config not applied.
  - Fix: configure protobuf plugin and generated source sets.

- Symptom: OpenAPI-generated classes are missing at compile time.
  - Cause: generation tasks are not connected to compile/KSP.
  - Fix: add source dirs from generation output and make compile/KSP depend on corresponding `GenerateTask`.

- Symptom: generated API style is not the expected one.
  - Cause: wrong `mode` in OpenAPI generator config options.
  - Fix: set an explicit mode (`java-server`, `java-reactive-server`, `java-client`, `kotlin-client`, etc.) and regenerate.

### Startup failures
- Symptom: missing config value at startup.
  - Cause: required env/config key absent.
  - Fix: provide env variable or file value for required path.

- Symptom: DB pool init fails.
  - Cause: wrong URL/credentials.
  - Fix: verify `db.jdbcUrl`/`db.r2dbcUrl`/`db.connectionUri` and credentials.

- Symptom: port already in use.
  - Cause: conflict on HTTP/gRPC ports.
  - Fix: change configured ports or stop conflicting process.

- Symptom: Redis cache connection fails on startup.
  - Cause: invalid `lettuce` URI/credentials/TLS settings.
  - Fix: validate `lettuce.uri`, auth fields, timeouts, and cluster/TLS parameters.

### Runtime failures
- Symptom: HTTP 404 for implemented route.
  - Cause: missing/mismatched `@HttpRoute` method/path.
  - Fix: align path and method exactly.

- Symptom: Kafka listener idle.
  - Cause: wrong topic/group/bootstrap settings.
  - Fix: validate consumer config keys and broker availability.

- Symptom: no traces in collector.
  - Cause: exporter module/config missing.
  - Fix: wire OpenTelemetry exporter module and set endpoint.

- Symptom: cache annotations seem ignored.
  - Cause: aspect weaving constraints (class/method finality) or wrong method signatures.
  - Fix: follow AOP constraints (Java non-final patterns, Kotlin `open` where required) and supported signatures.

- Symptom: cache key collisions or misses for same logical request.
  - Cause: unstable key mapping or parameter ordering mismatch.
  - Fix: define explicit `CacheKeyMapper`/`parameters` ordering and test key derivation.

- Symptom: generated client fails authorization unexpectedly.
  - Cause: `securityConfigPrefix` / scheme-name config mismatch.
  - Fix: align OpenAPI security scheme names with runtime config keys and primary auth selection.

### Field-tested pitfalls (Diary app postmortem)
These issues happened in a real Kora + Postgres + Kafka + OpenAPI setup and are worth codifying.

- Symptom: `Could not find method blackboxTestImplementation(...)`.
  - Cause: dependency configuration for custom source set was referenced before source-set/configuration wiring.
  - Fix: define `sourceSets { blackboxTest { ... } }` first, then extend configs, then declare dependencies.
  - Reference pattern (Gradle Groovy):
```groovy
sourceSets {
    blackboxTest {
        java.srcDir("src/blackboxTest/java")
        resources.srcDir("src/blackboxTest/resources")
        compileClasspath += sourceSets.main.output + configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

configurations {
    blackboxTestImplementation.extendsFrom(testImplementation)
    blackboxTestRuntimeOnly.extendsFrom(testRuntimeOnly)
}

dependencies {
    blackboxTestImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
    blackboxTestImplementation "org.skyscreamer:jsonassert:1.5.3"
}
```

- Symptom: `Could not get unknown property 'ru' for root project ...` when registering OpenAPI task type.
  - Cause: using a fully-qualified class in task declaration without proper plugin/type import.
  - Fix: use `org.openapi.generator` plugin + `GenerateTask` import; avoid unbound `ru.tinkoff...` type reference in Gradle DSL.
  - Reference pattern:
```groovy
plugins {
    id "org.openapi.generator" version "7.14.0"
}

import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

tasks.register("openApiGenerateDiaryServer", GenerateTask) {
    generatorName = "kora"
    inputSpec = "$rootDir/openapi/diary-api.yaml"
    outputDir = "$buildDir/generated/openapi"
    configOptions = [mode: "java-server", interfaceOnly: "true", delegatePattern: "true"]
}
compileJava.dependsOn(tasks.named("openApiGenerateDiaryServer"))
```

- Symptom: app startup fails with `ConfigValueExtractionException ... openapi.management.file`.
  - Cause: `openapi.management.file` is required as an array; null is not accepted by extractor.
  - Fix: define non-empty list and ensure spec file is present in app resources/jar.
  - Reference `application.conf` snippet:
```hocon
openapi {
  management {
    enabled = true
    endpoint = "/openapi"
    swaggerui {
      enabled = true
      endpoint = "/swagger-ui"
    }
    rapidoc {
      enabled = true
      endpoint = "/rapidoc"
    }
    file = ["openapi/diary-api.yaml"]
  }
}
```
  - Build note: if spec is stored outside `src/main/resources`, copy it in `processResources`.

- Symptom: Docker Compose fails to pull Kafka image (`bitnami/kafka:3.8.0` or `:latest` not found).
  - Cause: unpinned or unavailable tags.
  - Fix: pin known-valid image/tag and avoid `latest` in infra docs.
  - Practical default: `apache/kafka:3.9.1` (KRaft single-node) with explicit listener/env config.

- Symptom: Kafka Testcontainers starts but Kora consumer/producer tests are unstable with non-default image.
  - Cause: client/container compatibility check expects canonical image lineage.
  - Fix: for alternate Kafka images, mark compatibility in tests:
```java
new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1")
    .asCompatibleSubstituteFor("confluentinc/cp-kafka"));
```

- Symptom: integration/blackbox tests fail on developer machines without Docker.
  - Cause: unconditional container startup in static initializers.
  - Fix: gate Docker-dependent tests (`@EnabledIf(...)`) and avoid hard-failing class initialization.

- Symptom: frontend shows `Unexpected token 'V' ... is not valid JSON` for API errors.
  - Cause: frontend assumes all non-2xx payloads are JSON while backend may return plain text validation errors.
  - Fix: parse response by `Content-Type`, fallback to raw text, then construct error message.

### Debugging playbook
1. Confirm module dependency exists.
2. Confirm module exists in `@KoraApp` inheritance.
3. Confirm required config path exists and env vars resolve.
4. Confirm generated classes exist (`ApplicationGraph`, repo/client stubs, OpenAPI generated APIs/models).
5. Run module-specific integration test before full suite.


## Coding-Agent Execution Checklist
Use this as strict completion criteria.

1. Initialize baseline build config with Kora BOM `1.2.9`.
2. Add annotation processing (`annotation-processors` or `symbol-processors`).
3. Create `@KoraApp` entrypoint and include only required modules.
4. Add config contracts (`@ConfigSource`/`@ConfigValueExtractor`) before business logic.
5. Implement components with constructor injection only.
6. Implement cache contracts and policies when caching is part of business behavior:
   - define `@Cache` interfaces,
   - apply `@Cacheable`/`@CachePut`/`@CacheInvalidate`,
   - verify key mapping strategy.
7. Implement transport layers:
   - HTTP server controllers or OpenAPI-generated delegates,
   - HTTP clients or OpenAPI-generated clients,
   - gRPC handlers/stubs,
   - Kafka listeners/publishers.
8. Implement persistence via repositories and migrations.
9. Add OpenAPI generation pipeline when contract-first transport is used:
   - generation tasks,
   - source set wiring,
   - compile/KSP task dependency.
10. Add observability (tracing exporter and module telemetry flags).
11. Add tests in this order:
   - component test,
   - integration test,
   - optional black-box test.
12. Validate minimum runnable command path for each used module.
13. Validate no hardcoded secrets/hostnames in committed config.
14. Validate failure scenarios and exact fixes are documented in PR notes.

### Done definition for coding agents
A task is done only if all are true:
- Build succeeds.
- Service starts with expected module wiring.
- Critical path tests pass.
- Required telemetry signals are visible.
- Config contracts are typed and validated.
- Codegen artifacts are reproducible from build tasks (if OpenAPI mode is used).
- No manual runtime DI or hidden side effects are introduced.
