package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R-DOCS-1: README.md follows the shared skeleton; translations keep its levels and code. */
class ReadmeTest {
  private static final List<String> SKELETON =
      List.of(
          "# finlight client for Java and Kotlin",
          "## Versions",
          "## Requirements",
          "## Installation",
          "## Quick start",
          "## Search articles",
          "## Look up articles, companies and sources",
          "## Absent, null and empty",
          "## Real-time streams",
          "## Webhooks",
          "## Errors and retries",
          "## Configuration",
          "## Logging",
          "## Data notes",
          "## Migrating from 0.1.x",
          "## Development",
          "## License");

  private static List<String> headings(String text) {
    List<String> out = new ArrayList<>();
    boolean code = false;
    for (String line : text.split("\n")) {
      if (line.startsWith("```")) {
        code = !code;
      } else if (!code && line.startsWith("#")) {
        out.add(line);
      }
    }
    return out;
  }

  private static List<String> codeBlocks(String text) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = null;
    for (String line : text.split("\n")) {
      if (line.startsWith("```")) {
        if (cur == null) {
          cur = new StringBuilder(line).append('\n');
        } else {
          out.add(cur.append(line).toString());
          cur = null;
        }
      } else if (cur != null) {
        cur.append(line).append('\n');
      }
    }
    return out;
  }

  @Test
  void englishFollowsTheSkeleton() throws Exception {
    String en = Files.readString(Path.of("README.md"));
    assertEquals(SKELETON, headings(en));
    assertTrue(en.contains("| SDK version | API version | Status |"), "version table");
    assertTrue(en.contains("> **Breaking release.**"), "breaking notice");
    assertTrue(
        en.contains(
            "implementation(\"me.finlight:finlight-client:" + FinlightClient.VERSION + "\")"));
    assertTrue(en.contains("docs.finlight.me"));
    assertTrue(en.indexOf("> **Breaking release.**") < en.indexOf("## Versions"));
  }

  @Test
  void translationsKeepLevelsAndCode() throws Exception {
    String en = Files.readString(Path.of("README.md"));
    for (String lang : List.of("zh-CN", "ja", "ko")) {
      String t = Files.readString(Path.of("README." + lang + ".md"));
      List<String> levels = headings(t).stream().map(h -> h.substring(0, h.indexOf(' '))).toList();
      List<String> enLevels = SKELETON.stream().map(h -> h.substring(0, h.indexOf(' '))).toList();
      assertEquals(enLevels, levels, lang + " heading levels");
      assertEquals(codeBlocks(en), codeBlocks(t), lang + " code blocks");
      assertTrue(t.contains("[English](README.md)"), lang + " language links");
      assertTrue(t.contains("README.md#migrating-from-01x"), lang + " migration link");
      assertTrue(t.contains("| SDK version | API version | Status |"), lang + " version table");
    }
  }
}
