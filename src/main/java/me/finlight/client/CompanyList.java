package me.finlight.client;

import java.util.AbstractList;
import java.util.List;

/** Result of {@code companies().search} and {@code getMany}: the records plus skipped entries. */
public final class CompanyList extends AbstractList<CompanyRecord> {
  private final List<CompanyRecord> items;
  private final List<SkippedEntry> skipped;

  CompanyList(List<CompanyRecord> items, List<SkippedEntry> skipped) {
    this.items = List.copyOf(items);
    this.skipped = List.copyOf(skipped);
  }

  @Override
  public CompanyRecord get(int index) {
    return items.get(index);
  }

  @Override
  public int size() {
    return items.size();
  }

  /** Entries that could not be read (logged as a warning). */
  public List<SkippedEntry> skipped() {
    return skipped;
  }
}
