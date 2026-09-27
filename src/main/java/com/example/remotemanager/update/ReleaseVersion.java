package com.example.remotemanager.update;

import java.util.ArrayList;
import java.util.List;

/** Comparison for stable numeric release tags such as v1.2.3. */
final class ReleaseVersion implements Comparable<ReleaseVersion> {
  private final List<Integer> parts;

  private ReleaseVersion(List<Integer> parts) {
    this.parts = parts;
  }

  static ReleaseVersion parse(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.startsWith("v") || normalized.startsWith("V")) normalized = normalized.substring(1);
    if (!normalized.matches("\\d+(\\.\\d+)*"))
      throw new IllegalArgumentException("Unsupported release version: " + text);
    List<Integer> parts = new ArrayList<>();
    for (String part : normalized.split("\\.")) parts.add(Integer.parseInt(part));
    return new ReleaseVersion(List.copyOf(parts));
  }

  @Override public int compareTo(ReleaseVersion other) {
    int count = Math.max(parts.size(), other.parts.size());
    for (int i = 0; i < count; i++) {
      int left = i < parts.size() ? parts.get(i) : 0;
      int right = i < other.parts.size() ? other.parts.get(i) : 0;
      if (left != right) return Integer.compare(left, right);
    }
    return 0;
  }
}
