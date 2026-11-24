package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Every kit case has exactly one test (R-TEST-1), and CONFORMANCE.md maps each case to it. */
class ConformanceCoverageTest {
  @Test
  void everyKitCaseHasATest() {
    Set<String> kit = new TreeSet<>(Kit.cases().keySet());
    Set<String> covered = new TreeSet<>();
    for (Set<String> ids :
        java.util.List.of(
            ConformanceVocabularyTest.ids(),
            ConformanceDecodeTest.ids(),
            ConformanceRestTest.ids(),
            ConformanceWebhookTest.ids(),
            ConformanceSessionTest.ids())) {
      for (String id : ids) {
        assertTrue(covered.add(id), id + " has more than one test");
      }
    }
    assertEquals(kit, covered, "kit cases and tests");
    assertTrue(Kit.text("conformance.md").contains("Total: " + kit.size() + " cases."));
  }

  @Test
  void caseTableNamesEveryCase() throws Exception {
    String table = Files.readString(Path.of("CONFORMANCE.md"));
    for (String id : Kit.cases().keySet()) {
      assertTrue(table.contains("| " + id + " |"), "CONFORMANCE.md lists " + id);
    }
    assertTrue(
        table.contains(Kit.text("CONTRACT_VERSION").trim()),
        "CONFORMANCE.md names the kit version");
  }
}
