package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/** Kit case V-01: the shipped constants equal vocab.json; response enums stay open. */
class ConformanceVocabularyTest {
  static final Map<String, Executable> CASES = new LinkedHashMap<>();

  static {
    CASES.put("V-01", ConformanceVocabularyTest::vocabulary);
  }

  static Set<String> ids() {
    return CASES.keySet();
  }

  @TestFactory
  Stream<DynamicNode> cases() {
    Map<String, String> titles = Kit.cases();
    return CASES.entrySet().stream()
        .map(e -> DynamicTest.dynamicTest(e.getKey() + " " + titles.get(e.getKey()), e.getValue()));
  }

  private static List<String> values(JsonNode node) {
    List<String> out = new ArrayList<>();
    node.forEach(v -> out.add(v.isObject() ? v.get("code").asText() : v.asText()));
    return out;
  }

  private static void vocabulary() throws Exception {
    JsonNode v = Kit.load("vocab.json");
    assertEquals(FinlightClient.PAYLOAD_VERSION, v.get("payloadVersion").asInt());
    Map<Class<?>, List<String>> expected = new LinkedHashMap<>();
    expected.put(IncludeBlock.class, values(v.at("/request/include/values")));
    expected.put(OrderBy.class, values(v.at("/request/orderBy/values")));
    expected.put(SortOrder.class, values(v.at("/request/order/values")));
    expected.put(CompanyRole.class, values(v.at("/response/roles/values")));
    expected.put(EventType.class, values(v.at("/response/eventTypes/values")));
    expected.put(EventDirection.class, values(v.at("/response/eventDirections/values")));
    expected.put(Importance.class, values(v.at("/response/importance/values")));
    expected.put(SentimentLabel.class, values(v.at("/response/sentimentLabels/values")));
    expected.put(SourceType.class, values(v.at("/response/sourceTypes/values")));
    expected.put(StreamEventName.class, values(v.at("/websocket/events/values")));
    expected.put(ConnectionErrorCode.class, values(v.at("/websocket/connectionErrorCodes/values")));
    expected.put(PreemptReason.class, values(v.at("/websocket/preemptReasons/values")));
    expected.put(WebhookEventName.class, values(v.at("/webhook/events/values")));
    expected.put(ProblemCode.class, values(v.at("/problemCodes/values")));
    List<String> limitTypes = values(v.at("/limitTypes/values"));
    limitTypes.add("unknown");
    expected.put(RateLimitType.class, limitTypes);
    // The request lists equal the response lists where both exist.
    assertEquals(values(v.at("/request/roles/values")), expected.get(CompanyRole.class));
    assertEquals(values(v.at("/request/eventTypes/values")), expected.get(EventType.class));
    assertEquals(
        values(v.at("/request/eventDirections/values")), expected.get(EventDirection.class));
    assertEquals(values(v.at("/request/minImportance/values")), expected.get(Importance.class));
    assertEquals(values(v.at("/request/sourceTypes/values")), expected.get(SourceType.class));
    assertEquals(
        values(v.at("/request/excludeSourceTypes/values")), expected.get(SourceType.class));

    for (Map.Entry<Class<?>, List<String>> e : expected.entrySet()) {
      Class<?> type = e.getKey();
      Class<?> valuesType = Class.forName(type.getName() + "Values");
      @SuppressWarnings("unchecked")
      List<String> all = (List<String>) valuesType.getField("ALL").get(null);
      assertEquals(e.getValue(), all, type.getSimpleName() + "Values.ALL");
      Method of = type.getMethod("of", String.class);
      Method value = type.getMethod("value");
      List<String> constants = new ArrayList<>();
      for (Field field : type.getFields()) {
        if (Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
          Object c = field.get(null);
          constants.add((String) value.invoke(c));
          assertSame(c, of.invoke(null, value.invoke(c)), "of() returns the constant");
          assertEquals(value.invoke(c), valuesType.getField(field.getName()).get(null));
        }
      }
      assertEquals(
          Set.copyOf(e.getValue()), Set.copyOf(constants), type.getSimpleName() + " constants");
      Object unknown = of.invoke(null, "a-value-from-the-future");
      assertEquals("a-value-from-the-future", value.invoke(unknown), "unknown values are kept");
      assertFalse((Boolean) type.getMethod("isKnown").invoke(unknown));
      assertTrue(Modifier.isFinal(type.getModifiers()) && !type.isEnum(), "open, not a Java enum");
    }
    assertEquals(
        values(v.at("/websocket/articleEvents/values")), StreamEventNameValues.ARTICLE_EVENTS);
    assertEquals(
        values(v.at("/websocket/connectionEvents/values")),
        StreamEventNameValues.CONNECTION_EVENTS);
  }
}
