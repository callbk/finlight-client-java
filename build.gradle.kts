import net.ltgt.gradle.errorprone.errorprone
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
  `java-library`
  id("com.diffplug.spotless") version "7.0.2"
  id("net.ltgt.errorprone") version "5.1.0"
  id("com.vanniktech.maven.publish") version "0.33.0"
}

description =
  "BREAKING: 1.x speaks finlight API v3 only (REST /v3, WebSocket payloadVersion 3, v3 webhooks). " +
  "Stay on 0.1.x for API v2. Official JVM client for the finlight.me financial news API."

// Error Prone 2.50+ needs JDK 21+ to run. options.release=17 keeps the bytecode at Java 17; the
// JDK 17 CI job compiles and tests without linting.
val errorProneSupported = JavaVersion.current() >= JavaVersion.VERSION_21

// The lowest supported Jackson is 2.13. CI runs the suite with -PjacksonVersion=2.13.5.
val jacksonVersion = providers.gradleProperty("jacksonVersion").getOrElse("2.18.3")

repositories {
  mavenCentral()
}

// The SDK version has one source: `version` in gradle.properties (the release workflow checks it
// against the tag). It is compiled into BuildInfo.VERSION.
val generatedVersionDir = layout.buildDirectory.dir("generated/sources/version/java")
val generateVersion =
  tasks.register("generateVersion") {
    val v = project.version.toString()
    inputs.property("version", v)
    outputs.dir(generatedVersionDir)
    doLast {
      val file = generatedVersionDir.get().file("me/finlight/client/BuildInfo.java").asFile
      file.parentFile.mkdirs()
      file.writeText(
        """
        |package me.finlight.client;
        |
        |/** Generated from gradle.properties; do not edit. */
        |final class BuildInfo {
        |  static final String VERSION = "$v";
        |
        |  private BuildInfo() {}
        |}
        |
        """.trimMargin(),
      )
    }
  }
sourceSets.main {
  java.srcDir(generateVersion)
}

// The five standard examples (R-TEST-1). Compiled in every build and run by the test suite
// against local mocks.
val examples =
  sourceSets.create("examples") {
    java.srcDir("examples/src/main/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
  }
configurations["examplesImplementation"].extendsFrom(configurations.implementation.get())
configurations["examplesCompileOnly"].extendsFrom(configurations.compileOnly.get())

sourceSets.test {
  compileClasspath += examples.output
  runtimeClasspath += examples.output
}

// Live checks against dev. Never part of `./gradlew test`; they need FINLIGHT_API_KEY,
// FINLIGHT_BASE_URL and FINLIGHT_WSS_URL and refuse production hosts.
val live =
  sourceSets.create("live") {
    java.srcDir("src/live/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
  }
configurations["liveImplementation"].extendsFrom(configurations.implementation.get())

dependencies {
  api("org.jspecify:jspecify:1.0.0")

  implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
  implementation("com.fasterxml.jackson.core:jackson-databind")

  testImplementation(platform("org.junit:junit-bom:5.11.4"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  "examplesImplementation"("org.jspecify:jspecify:1.0.0")
  "liveImplementation"("org.jspecify:jspecify:1.0.0")

  if (errorProneSupported) {
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
  }
}

spotless {
  java {
    target("src/**/*.java", "examples/**/*.java")
    googleJavaFormat("1.28.0")
    formatAnnotations()
  }
  kotlinGradle {
    ktlint()
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(17)
  options.encoding = "UTF-8"
  options.compilerArgs.add("-Xlint:all,-serial,-processing")
  options.errorprone {
    enabled.set(errorProneSupported)
    disableWarningsInGeneratedCode.set(true)
    disable("StatementSwitchToExpressionSwitch", "CanonicalDuration", "EqualsGetClass", "ArrayRecordComponent")
    excludedPaths.set(".*/build/generated/.*")
  }
}

tasks.named<JavaCompile>("compileTestJava") {
  options.errorprone.enabled.set(false)
}

tasks.jar {
  manifest {
    attributes(
      "Automatic-Module-Name" to "me.finlight.client",
      "Implementation-Title" to "finlight-client",
      "Implementation-Version" to project.version.toString(),
    )
  }
}

tasks.test {
  useJUnitPlatform()
  // The whole suite runs under a Turkish locale: formatting must not depend on it (R-REQ-2).
  jvmArgs("-Duser.language=tr", "-Duser.country=TR")
  systemProperty("finlight.kit", file("conformance").absolutePath)
  // Unit and conformance tests never use real keys or hosts (R-TEST-2).
  environment("FINLIGHT_API_KEY", "")
  testLogging {
    events("failed")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

val liveTest =
  tasks.register<Test>("liveTest") {
    group = "verification"
    description = "Small live checks against dev (needs FINLIGHT_API_KEY, FINLIGHT_BASE_URL, FINLIGHT_WSS_URL)."
    testClassesDirs = live.output.classesDirs
    classpath = live.runtimeClasspath
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    testLogging {
      events("passed", "skipped", "failed")
      showStandardStreams = true
    }
  }
dependencies {
  "liveImplementation"(platform("org.junit:junit-bom:5.11.4"))
  "liveImplementation"("org.junit.jupiter:junit-jupiter")
  "liveRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.javadoc {
  (options as StandardJavadocDocletOptions).apply {
    addBooleanOption("Xdoclint:all,-missing", true)
    links("https://docs.oracle.com/en/java/javase/17/docs/api/")
    encoding = "UTF-8"
  }
}

// --- contract kit -------------------------------------------------------------------------------

fun sha256(f: File): String = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

fun verifyKit(dir: File): List<String> {
  val problems = mutableListOf<String>()
  val manifest = File(dir, "MANIFEST")
  if (!manifest.isFile) return listOf("missing ${manifest.path}")
  val listed = mutableSetOf<String>()
  manifest.readLines().filter { it.isNotBlank() }.forEach { line ->
    val (hash, path) = line.split(Regex("\\s+"), limit = 2)
    listed += path
    val f = File(dir, path)
    when {
      !f.isFile -> problems += "listed but missing: $path"
      sha256(f) != hash -> problems += "checksum differs: $path"
    }
  }
  dir.walkTopDown().filter { it.isFile }.forEach {
    val rel = it.relativeTo(dir).invariantSeparatorsPath
    if (rel != "MANIFEST" && rel !in listed) problems += "not in MANIFEST: $rel"
  }
  return problems
}

tasks.register("checkConformance") {
  group = "verification"
  description = "Checks conformance/ against its MANIFEST (sha256)."
  val dir = file("conformance")
  doLast {
    val problems = verifyKit(dir)
    if (problems.isNotEmpty()) throw GradleException("conformance kit is out of sync:\n" + problems.joinToString("\n"))
    logger.lifecycle("conformance kit ${File(dir, "CONTRACT_VERSION").readText().trim()} matches its MANIFEST")
  }
}

tasks.register("syncConformance") {
  group = "verification"
  description = "Copies the contract kit: ./gradlew syncConformance -PkitDir=<monorepo>/shared/contracts/api-v3"
  doLast {
    val from =
      providers.gradleProperty("kitDir").orNull?.let { file(it) }
        ?: throw GradleException("pass -PkitDir=<monorepo>/shared/contracts/api-v3")
    val problems = verifyKit(from)
    if (problems.isNotEmpty()) throw GradleException("source kit fails its MANIFEST:\n" + problems.joinToString("\n"))
    val to = file("conformance")
    to.deleteRecursively()
    from.copyRecursively(to)
    logger.lifecycle("synced conformance kit ${File(to, "CONTRACT_VERSION").readText().trim()}")
  }
}

tasks.named("check") {
  dependsOn("checkConformance")
}

// --- published artifacts (R-PKG-1) ---------------------------------------------------------------

tasks.register("checkPackage") {
  group = "verification"
  description = "Fails when a published jar contains test data, the kit or local files."
  dependsOn(tasks.jar, "sourcesJar", "plainJavadocJar")
  doLast {
    val jars =
      layout.buildDirectory
        .dir("libs")
        .get()
        .asFile
        .listFiles { f -> f.name.endsWith(".jar") }!!
        .toList()
    if (jars.size < 3) throw GradleException("expected jar, sources and javadoc jars, got $jars")
    val forbidden = Regex("(^|/)(conformance|local|fixtures|examples|live)/|\\.env|Test\\.class$")
    jars.forEach { jar ->
      ZipFile(jar).use { zip ->
        val bad =
          zip
            .entries()
            .asSequence()
            .map { it.name }
            .filter { forbidden.containsMatchIn(it) }
            .toList()
        if (bad.isNotEmpty()) throw GradleException("${jar.name} contains $bad")
        logger.lifecycle("${jar.name}: ${zip.size()} entries, clean")
      }
    }
  }
}

mavenPublishing {
  // Group/version stay overridable (-Pgroup/-Pversion) so JitPack builds work.
  coordinates(group.toString(), "finlight-client", version.toString())
  publishToMavenCentral(automaticRelease = true)
  if (providers.gradleProperty("signingInMemoryKey").isPresent) {
    signAllPublications()
  }
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
