package me.finlight.examples;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import me.finlight.client.AdmittedInfo;
import me.finlight.client.FinlightApiException;
import me.finlight.client.FinlightClient;
import me.finlight.client.FinlightException;
import me.finlight.client.FinlightStream;
import me.finlight.client.IncludeBlock;
import me.finlight.client.PreemptedException;
import me.finlight.client.ReconnectInfo;
import me.finlight.client.StreamEvent;
import me.finlight.client.StreamEventKind;
import me.finlight.client.StreamListener;
import me.finlight.client.StreamParams;
import org.jspecify.annotations.Nullable;

/** Live articles over WebSocket. Pings, reconnects and rotation are handled by the SDK. */
public final class Stream {
  private Stream() {}

  public static void main(String[] args) throws InterruptedException {
    try (FinlightClient client = Env.client()) {
      run(client, System.out, 5);
    }
  }

  static void run(FinlightClient client, PrintStream out, int maxItems)
      throws InterruptedException {
    StreamParams params =
        StreamParams.builder()
            .query("ticker:NVDA OR ticker:AMD")
            .language("en")
            .include(IncludeBlock.COMPANIES)
            .includeUpdates(true)
            .build();
    AtomicInteger seen = new AtomicInteger();
    FinlightStream[] stream = new FinlightStream[1];
    StreamListener listener =
        new StreamListener() {
          @Override
          public void onEvent(StreamEvent event) {
            if (event.kind() == StreamEventKind.OTHER) {
              return; // an event this SDK version does not know
            }
            out.println(event.kind() + ": " + event.data().title());
            if (seen.incrementAndGet() >= maxItems) {
              stream[0].close();
            }
          }

          @Override
          public void onAdmitted(AdmittedInfo info) {
            out.println("admitted, lease " + info.leaseId());
          }

          @Override
          public void onReconnect(ReconnectInfo info) {
            // The SDK does not backfill: fill gaps through REST from lastSeenAt if you need them.
            out.println("reconnected (" + info.reason() + "), last seen " + info.lastSeenAt());
          }

          @Override
          public void onError(FinlightException error) {
            out.println("stream problem: " + error.getMessage());
          }

          @Override
          public void onClosed(@Nullable FinlightException terminal) {
            if (terminal instanceof PreemptedException p) {
              out.println("preempted (" + p.reason() + ")");
            } else if (terminal instanceof FinlightApiException a) {
              out.println("stream refused: " + a.status() + " " + a.detail());
            }
          }
        };
    stream[0] = client.stream(params, listener);
    stream[0].awaitTermination();
  }
}
