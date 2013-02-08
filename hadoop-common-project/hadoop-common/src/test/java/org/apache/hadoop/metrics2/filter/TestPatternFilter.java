/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.metrics2.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.configuration.SubsetConfiguration;
import org.junit.Test;

import static org.junit.Assert.*;

import org.apache.hadoop.metrics2.AbstractMetric;
import org.apache.hadoop.metrics2.MetricsFilter;
import org.apache.hadoop.metrics2.MetricsRecord;
import org.apache.hadoop.metrics2.MetricsTag;
import org.apache.hadoop.metrics2.impl.ConfigBuilder;
import static org.apache.hadoop.metrics2.lib.Interns.*;
import static org.mockito.Mockito.*;

public class TestPatternFilter {

  /**
   * Filters should default to accept
   */
  @Test public void emptyConfigShouldAccept() {
    SubsetConfiguration empty = new ConfigBuilder().subset("");
    shouldAccept(empty, "anything");
    shouldAccept(empty, Arrays.asList(tag("key", "desc", "value")));
  }

  /**
   * Filters should handle white-listing correctly
   */
  @Test public void includeOnlyShouldOnlyIncludeMatched() {
    SubsetConfiguration wl = new ConfigBuilder()
        .add("p.include", "foo")
        .add("p.include.tags", "foo:f").subset("p");
    shouldAccept(wl, "foo");
    shouldAccept(wl, Arrays.asList(tag("bar", "", ""),
                                   tag("foo", "", "f")));
    shouldReject(wl, "bar");
    shouldReject(wl, Arrays.asList(tag("bar", "", "")));
    shouldReject(wl, Arrays.asList(tag("foo", "", "boo")));
  }

  /**
   * Filters should handle black-listing correctly
   */
  @Test public void excludeOnlyShouldOnlyExcludeMatched() {
    SubsetConfiguration bl = new ConfigBuilder()
        .add("p.exclude", "foo")
        .add("p.exclude.tags", "foo:f").subset("p");
    shouldAccept(bl, "bar");
    shouldAccept(bl, Arrays.asList(tag("bar", "", "")));
    shouldReject(bl, "foo");
    shouldReject(bl, Arrays.asList(tag("bar", "", ""),
                                   tag("foo", "", "f")));
  }

  /**
   * Filters should accepts unmatched item when both include and
   * exclude patterns are present.
   */
  @Test public void shouldAcceptUnmatchedWhenBothAreConfigured() {
    SubsetConfiguration c = new ConfigBuilder()
        .add("p.include", "foo")
        .add("p.include.tags", "foo:f")
        .add("p.exclude", "bar")
        .add("p.exclude.tags", "bar:b").subset("p");
    shouldAccept(c, "foo");
    shouldAccept(c, Arrays.asList(tag("foo", "", "f")));
    shouldReject(c, "bar");
    shouldReject(c, Arrays.asList(tag("bar", "", "b")));
    shouldAccept(c, "foobar");
    shouldAccept(c, Arrays.asList(tag("foobar", "", "")));
  }

  /**
   * Include patterns should take precedence over exclude patterns
   */
  @Test public void includeShouldOverrideExclude() {
    SubsetConfiguration c = new ConfigBuilder()
        .add("p.include", "foo")
        .add("p.include.tags", "foo:f")
        .add("p.exclude", "foo")
        .add("p.exclude.tags", "foo:f").subset("p");
    shouldAccept(c, "foo");
    shouldAccept(c, Arrays.asList(tag("foo", "", "f")));
  }
  
  static void shouldAccept(SubsetConfiguration conf, String s) {
    assertTrue("accepts "+ s, newGlobFilter(conf).accepts(s));
    assertTrue("accepts "+ s, newRegexFilter(conf).accepts(s));
  }

  static void shouldAccept(SubsetConfiguration conf, List<MetricsTag> tags) {
    shouldAcceptImpl(true, conf, tags);
  }

  static void shouldReject(SubsetConfiguration conf, List<MetricsTag> tags) {
    shouldAcceptImpl(false, conf, tags);
  }
  
  private static void shouldAcceptImpl(final boolean expectAccept, 
      SubsetConfiguration conf, List<MetricsTag> tags) {
    final MetricsFilter globFilter = newGlobFilter(conf);
    final MetricsFilter regexFilter = newRegexFilter(conf);
    
    assertTrue("accepts "+ tags, expectAccept == globFilter.accepts(tags));
    assertTrue("accepts "+ tags, expectAccept == regexFilter.accepts(tags));
    
    // Test that MetricsRecord composed of the tags is also accepted:
    final MetricsRecord mr = createMetricsRecord("", tags, null);
    assertTrue("accepts "+ tags, expectAccept == globFilter.accepts(mr));
    assertTrue("accepts "+ tags, expectAccept == regexFilter.accepts(mr));
    
    // Test results on each of the individual tag:
    int acceptedCount = 0, rejectedCount = 0;
    for (MetricsTag tag: tags) {
      if (globFilter.accepts(tag)) {
        acceptedCount++;
      } else {
        rejectedCount++;
      }
    }
    if (expectAccept) {
      assertTrue("No tag of the following accepted: " + tags, acceptedCount > 0);
    } else {
      assertTrue("No tag of the following rejected: " + tags, rejectedCount > 0);
    }
  }
  
  private static MetricsRecord createMetricsRecord(String name, 
      Collection<MetricsTag> tags, Collection<AbstractMetric> metrics) {
    MetricsRecord mr = mock(MetricsRecord.class);
    when(mr.name()).thenReturn(name);
    when(mr.tags()).thenReturn(tags);
    when(mr.metrics()).thenReturn(metrics);
    return mr;
  }

  static void shouldReject(SubsetConfiguration conf, String s) {
    assertTrue("rejects "+ s, !newGlobFilter(conf).accepts(s));
    assertTrue("rejects "+ s, !newRegexFilter(conf).accepts(s));
  }

  /**
   * Create a new glob filter with a config object
   * @param conf  the config object
   * @return the filter
   */
  public static GlobFilter newGlobFilter(SubsetConfiguration conf) {
    GlobFilter f = new GlobFilter();
    f.init(conf);
    return f;
  }

  /**
   * Create a new regex filter with a config object
   * @param conf  the config object
   * @return the filter
   */
  public static RegexFilter newRegexFilter(SubsetConfiguration conf) {
    RegexFilter f = new RegexFilter();
    f.init(conf);
    return f;
  }
}
