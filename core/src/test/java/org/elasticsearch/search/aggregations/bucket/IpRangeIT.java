/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.aggregations.bucket;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.cluster.health.ClusterHealthStatus;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.containsString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.inject.internal.Nullable;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.AbstractSearchScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.NativeScriptFactory;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.test.ESIntegTestCase;

@ESIntegTestCase.SuiteScopeTestCase
public class IpRangeIT extends ESIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(DummyScriptPlugin.class);
    }

    @Override
    public void setupSuiteScopeCluster() throws Exception {
        assertAcked(prepareCreate("idx")
                .addMapping("type", "ip", "type=ip", "ips", "type=ip"));
        waitForRelocation(ClusterHealthStatus.GREEN);

        indexRandom(true,
                client().prepareIndex("idx", "type", "1").setSource(
                        "ip", "192.168.1.7",
                        "ips", Arrays.asList("192.168.0.13", "192.168.1.2")),
                client().prepareIndex("idx", "type", "2").setSource(
                        "ip", "192.168.1.10",
                        "ips", Arrays.asList("192.168.1.25", "192.168.1.28")),
                client().prepareIndex("idx", "type", "3").setSource(
                        "ip", "2001:db8::ff00:42:8329",
                        "ips", Arrays.asList("2001:db8::ff00:42:8329", "2001:db8::ff00:42:8380")));

        assertAcked(prepareCreate("idx_unmapped"));
        waitForRelocation(ClusterHealthStatus.GREEN);
        refresh();
    }

    public void testSingleValuedField() {
        SearchResponse rsp = client().prepareSearch("idx").addAggregation(
                AggregationBuilders.ipRange("my_range")
                    .field("ip")
                    .addUnboundedTo("192.168.1.0")
                    .addRange("192.168.1.0", "192.168.1.10")
                    .addUnboundedFrom("192.168.1.10")).get();
        assertSearchResponse(rsp);
        Range range = rsp.getAggregations().get("my_range");
        assertEquals(3, range.getBuckets().size());

        Range.Bucket bucket1 = range.getBuckets().get(0);
        assertNull(bucket1.getFrom());
        assertEquals("192.168.1.0", bucket1.getTo());
        assertEquals(0, bucket1.getDocCount());

        Range.Bucket bucket2 = range.getBuckets().get(1);
        assertEquals("192.168.1.0", bucket2.getFrom());
        assertEquals("192.168.1.10", bucket2.getTo());
        assertEquals(1, bucket2.getDocCount());

        Range.Bucket bucket3 = range.getBuckets().get(2);
        assertEquals("192.168.1.10", bucket3.getFrom());
        assertNull(bucket3.getTo());
        assertEquals(2, bucket3.getDocCount());
    }

    public void testMultiValuedField() {
        SearchResponse rsp = client().prepareSearch("idx").addAggregation(
                AggregationBuilders.ipRange("my_range")
                    .field("ips")
                    .addUnboundedTo("192.168.1.0")
                    .addRange("192.168.1.0", "192.168.1.10")
                    .addUnboundedFrom("192.168.1.10")).get();
        assertSearchResponse(rsp);
        Range range = rsp.getAggregations().get("my_range");
        assertEquals(3, range.getBuckets().size());

        Range.Bucket bucket1 = range.getBuckets().get(0);
        assertNull(bucket1.getFrom());
        assertEquals("192.168.1.0", bucket1.getTo());
        assertEquals(1, bucket1.getDocCount());

        Range.Bucket bucket2 = range.getBuckets().get(1);
        assertEquals("192.168.1.0", bucket2.getFrom());
        assertEquals("192.168.1.10", bucket2.getTo());
        assertEquals(1, bucket2.getDocCount());

        Range.Bucket bucket3 = range.getBuckets().get(2);
        assertEquals("192.168.1.10", bucket3.getFrom());
        assertNull(bucket3.getTo());
        assertEquals(2, bucket3.getDocCount());
    }

    public void testIpMask() {
        SearchResponse rsp = client().prepareSearch("idx").addAggregation(
                AggregationBuilders.ipRange("my_range")
                    .field("ips")
                    .addMaskRange("::/0")
                    .addMaskRange("0.0.0.0/0")
                    .addMaskRange("2001:db8::/64")).get();
        assertSearchResponse(rsp);
        Range range = rsp.getAggregations().get("my_range");
        assertEquals(3, range.getBuckets().size());

        Range.Bucket bucket1 = range.getBuckets().get(0);
        assertEquals("::/0", bucket1.getKey());
        assertEquals(3, bucket1.getDocCount());

        Range.Bucket bucket2 = range.getBuckets().get(1);
        assertEquals("0.0.0.0/0", bucket2.getKey());
        assertEquals(2, bucket2.getDocCount());

        Range.Bucket bucket3 = range.getBuckets().get(2);
        assertEquals("2001:db8::/64", bucket3.getKey());
        assertEquals(1, bucket3.getDocCount());
    }

    public void testPartiallyUnmapped() {
        SearchResponse rsp = client().prepareSearch("idx", "idx_unmapped").addAggregation(
                AggregationBuilders.ipRange("my_range")
                    .field("ip")
                    .addUnboundedTo("192.168.1.0")
                    .addRange("192.168.1.0", "192.168.1.10")
                    .addUnboundedFrom("192.168.1.10")).get();
        assertSearchResponse(rsp);
        Range range = rsp.getAggregations().get("my_range");
        assertEquals(3, range.getBuckets().size());

        Range.Bucket bucket1 = range.getBuckets().get(0);
        assertNull(bucket1.getFrom());
        assertEquals("192.168.1.0", bucket1.getTo());
        assertEquals(0, bucket1.getDocCount());

        Range.Bucket bucket2 = range.getBuckets().get(1);
        assertEquals("192.168.1.0", bucket2.getFrom());
        assertEquals("192.168.1.10", bucket2.getTo());
        assertEquals(1, bucket2.getDocCount());

        Range.Bucket bucket3 = range.getBuckets().get(2);
        assertEquals("192.168.1.10", bucket3.getFrom());
        assertNull(bucket3.getTo());
        assertEquals(2, bucket3.getDocCount());
    }

    public void testUnmapped() {
        SearchResponse rsp = client().prepareSearch("idx_unmapped").addAggregation(
                AggregationBuilders.ipRange("my_range")
                    .field("ip")
                    .addUnboundedTo("192.168.1.0")
                    .addRange("192.168.1.0", "192.168.1.10")
                    .addUnboundedFrom("192.168.1.10")).get();
        assertSearchResponse(rsp);
        Range range = rsp.getAggregations().get("my_range");
        assertEquals(3, range.getBuckets().size());

        Range.Bucket bucket1 = range.getBuckets().get(0);
        assertNull(bucket1.getFrom());
        assertEquals("192.168.1.0", bucket1.getTo());
        assertEquals(0, bucket1.getDocCount());

        Range.Bucket bucket2 = range.getBuckets().get(1);
        assertEquals("192.168.1.0", bucket2.getFrom());
        assertEquals("192.168.1.10", bucket2.getTo());
        assertEquals(0, bucket2.getDocCount());

        Range.Bucket bucket3 = range.getBuckets().get(2);
        assertEquals("192.168.1.10", bucket3.getFrom());
        assertNull(bucket3.getTo());
        assertEquals(0, bucket3.getDocCount());
    }

    /**
     * Make sure the `missing` option is supported for IP range aggregations
     */
    public void testMissing() throws Exception {
        assertAcked(prepareCreate("idx_missing")
                .addMapping("type", "ip", "type=ip"));
        List<IndexRequestBuilder> builders = new ArrayList<>();
        int numDocs = randomIntBetween(10, 20);
        int numDocsMissing = randomIntBetween(1, numDocs - 1);
        for(int i = 0; i < numDocs; i++) {
            builders.add(client().prepareIndex("idx_missing", "type", "" + i).setSource(jsonBuilder()
                .startObject()
                .field("ip", "192.168.1."+i)
                .endObject()));
        }
        for(int i = numDocs; i < numDocs + numDocsMissing; i++){
            builders.add(client().prepareIndex("idx_missing", "type", ""+i).setSource(jsonBuilder()
                .startObject()
                .endObject()));
        }
        indexRandom(true, builders);
        ensureSearchable();

        SearchResponse rsp = client().prepareSearch("idx_missing").addAggregation(
            AggregationBuilders.ipRange("range")
                .field("ip")
                .addUnboundedTo("192.168.1.0")
                .addRange("192.168.1.0", "192.168.1.10")
                .addUnboundedFrom("192.168.1.10"))
            .addAggregation(
                AggregationBuilders.ipRange("range_missing")
                    .missing("192.168.1.11")
                    .field("ip")
                    .addUnboundedTo("192.168.1.0")
                    .addRange("192.168.1.0", "192.168.1.10")
                    .addUnboundedFrom("192.168.1.10")).get();
        assertSearchResponse(rsp);
        Range range = rsp.getAggregations().get("range");
        assertEquals(3, range.getBuckets().size());

        Range rangeMissing = rsp.getAggregations().get("range_missing");
        assertEquals(3, rangeMissing.getBuckets().size());

        Range.Bucket bucket1 = range.getBuckets().get(0);
        assertNull(bucket1.getFrom());
        assertEquals("192.168.1.0", bucket1.getTo());

        Range.Bucket bucket1Missing = rangeMissing.getBuckets().get(0);
        assertNull(bucket1Missing.getFrom());
        assertEquals("192.168.1.0", bucket1Missing.getTo());
        assertEquals(bucket1.getDocCount(), bucket1Missing.getDocCount());

        Range.Bucket bucket2 = range.getBuckets().get(1);
        assertEquals("192.168.1.0", bucket2.getFrom());
        assertEquals("192.168.1.10", bucket2.getTo());

        Range.Bucket bucket2Missing = rangeMissing.getBuckets().get(1);
        assertEquals("192.168.1.0", bucket2Missing.getFrom());
        assertEquals("192.168.1.10", bucket2Missing.getTo());
        assertEquals(bucket2.getDocCount(), bucket2Missing.getDocCount());

        Range.Bucket bucket3 = range.getBuckets().get(2);
        assertEquals("192.168.1.10", bucket3.getFrom());
        assertNull(bucket3.getTo());

        Range.Bucket bucket3Missing = rangeMissing.getBuckets().get(2);
        assertEquals("192.168.1.10", bucket3Missing.getFrom());
        assertNull(bucket3Missing.getTo());
        assertEquals(bucket3.getDocCount() + numDocsMissing, bucket3Missing.getDocCount());
    }

    public void testRejectsScript() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> client().prepareSearch("idx").addAggregation(
                        AggregationBuilders.ipRange("my_range")
                        .script(new Script(ScriptType.INLINE, "native", DummyScript.NAME, Collections.emptyMap())) ).get());
        assertThat(e.getMessage(), containsString("[ip_range] does not support scripts"));
    }

    public void testRejectsValueScript() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> client().prepareSearch("idx").addAggregation(
                        AggregationBuilders.ipRange("my_range")
                        .field("ip")
                        .script(new Script(ScriptType.INLINE, "native", DummyScript.NAME, Collections.emptyMap())) ).get());
        assertThat(e.getMessage(), containsString("[ip_range] does not support scripts"));
    }

    public static class DummyScriptPlugin extends Plugin implements ScriptPlugin {
        @Override
        public List<NativeScriptFactory> getNativeScripts() {
            return Collections.singletonList(new DummyScriptFactory());
        }
    }

    public static class DummyScriptFactory implements NativeScriptFactory {
        public DummyScriptFactory() {}
        @Override
        public ExecutableScript newScript(@Nullable Map<String, Object> params) {
            return new DummyScript();
        }
        @Override
        public boolean needsScores() {
            return false;
        }

        @Override
        public String getName() {
            return DummyScript.NAME;
        }
    }

    private static class DummyScript extends AbstractSearchScript {

        public static final String NAME = "dummy";

        @Override
        public Object run() {
            return null;
        }
    }

}
