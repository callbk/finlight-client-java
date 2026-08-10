import net.ltgt.gradle.errorprone.errorprone

plugins {
  `java-library`
  `maven-publish`
  signing
  id("com.diffplug.spotless") version "7.0.2"
  id("net.ltgt.errorprone") version "5.1.0"
}

description = "Official JVM client for the finlight.me financial news API"

// Error Prone 2.50+ ships class-file 65 bytecode and requires JDK 21+ to run.
// javac service-loads every plugin on the processor path even when unused, so
// the dependency itself must stay off the classpath on older JDKs.
// options.release=17 still guarantees Java 17 compatibility; the JDK 17 CI job
// compiles and tests without linting, the JDK 21 job and local builds lint.
val errorProneSupported = JavaVersion.current() >= JavaVersion.VERSION_21

java {
  withSourcesJar()
  withJavadocJar()
}

repositories {
  mavenCentral()
}

dependencies {
  api("org.jspecify:jspecify:1.0.0")

  implementation(platform("com.fasterxml.jackson:jackson-bom:2.18.3"))
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("org.slf4j:slf4j-api:2.0.17")

  testImplementation(platform("org.junit:junit-bom:5.11.4"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.java-websocket:Java-WebSocket:1.5.7")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")

  if (errorProneSupported) {
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
  }
}

spotless {
  java {
    // local/ holds untracked verification runners; lint only tracked sources
    target("src/**/*.java")
    googleJavaFormat("1.28.0")
    formatAnnotations()
  }
  kotlinGradle {
    ktlint()
  }
}

// Local-only verification runners against the real API (local/ is gitignored,
// mirroring the Go and .NET clients): `FINLIGHT_API_KEY=... ./gradlew smoke|soak`
val localSourceSet =
  sourceSets.create("local") {
    java.srcDir("local/src")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
  }
configurations["localImplementation"].extendsFrom(configurations.implementation.get())
dependencies {
  "localRuntimeOnly"("org.slf4j:slf4j-simple:2.0.17")
}

tasks.register<JavaExec>("smoke") {
  group = "verification"
  description = "Runs local/src/Smoke.java against the real API (needs FINLIGHT_API_KEY)"
  classpath = localSourceSet.runtimeClasspath
  mainClass.set("Smoke")
}

tasks.register<JavaExec>("soak") {
  group = "verification"
  description = "Runs local/src/Soak.java: long-running stream stability check (needs FINLIGHT_API_KEY)"
  classpath = localSourceSet.runtimeClasspath
  mainClass.set("Soak")
}

tasks.withType<JavaCompile>().configureEach {
  // Target Java 17 (LTS) regardless of the JDK running the build.
  options.release.set(17)
  options.encoding = "UTF-8"
  options.errorprone {
    enabled.set(errorProneSupported)
    disableWarningsInGeneratedCode.set(true)
  }
}

tasks.named<JavaCompile>("compileLocalJava") {
  // local/ holds throwaway verification scripts in the default package.
  options.errorprone.enabled.set(false)
}

tasks.processResources {
  filesMatching("me/finlight/client/version.properties") {
    expand("version" to project.version.toString())
  }
}

tasks.test {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
  }
}

tasks.javadoc {
  (options as StandardJavadocDocletOptions).apply {
    addBooleanOption("Xdoclint:all,-missing", true)
    links("https://docs.oracle.com/en/java/javase/17/docs/api/")
    encoding = "UTF-8"
  }
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
      pom {
        name.set("finlight-client")
        description.set(project.description)
        url.set("https://finlight.me")
        licenses {
          license {
            name.set("MIT License")
            url.set("https://opensource.org/licenses/MIT")
          }
        }
        developers {
          developer {
            name.set("finlight.me")
            email.set("info@finlight.me")
          }
        }
        scm {
          url.set("https://github.com/callbk/finlight-client-java")
          connection.set("scm:git:https://github.com/callbk/finlight-client-java.git")
        }
      }
    }
  }
}

// Sign only when a key is configured (e.g. in the release workflow).
signing {
  val signingKey = System.getenv("SIGNING_KEY")
  val signingPassword = System.getenv("SIGNING_PASSWORD")
  if (!signingKey.isNullOrBlank()) {
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
  }
}
