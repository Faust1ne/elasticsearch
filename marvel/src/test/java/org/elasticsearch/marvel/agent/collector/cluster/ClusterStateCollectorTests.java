/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.marvel.agent.collector.cluster;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.marvel.agent.exporter.MarvelDoc;
import org.elasticsearch.marvel.agent.settings.MarvelSettingsService;
import org.elasticsearch.test.ElasticsearchSingleNodeTest;
import org.junit.Test;

import java.util.Collection;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.hamcrest.Matchers.*;

public class ClusterStateCollectorTests extends ElasticsearchSingleNodeTest {

    @Test
    public void testClusterStateCollectorNoIndices() throws Exception {
        Collection<MarvelDoc> results = newClusterStateCollector().doCollect();
        assertThat(results, hasSize(1));

        MarvelDoc marvelDoc = results.iterator().next();
        assertNotNull(marvelDoc);
        assertThat(marvelDoc, instanceOf(ClusterStateMarvelDoc.class));

        ClusterStateMarvelDoc clusterStateMarvelDoc = (ClusterStateMarvelDoc) marvelDoc;
        assertThat(clusterStateMarvelDoc.clusterName(), equalTo(client().admin().cluster().prepareHealth().get().getClusterName()));
        assertThat(clusterStateMarvelDoc.timestamp(), greaterThan(0L));
        assertThat(clusterStateMarvelDoc.type(), equalTo(ClusterStateCollector.TYPE));

        ClusterStateMarvelDoc.Payload payload = clusterStateMarvelDoc.payload();
        assertNotNull(payload);
        assertNotNull(payload.getClusterState());

        ClusterState clusterState = payload.getClusterState();
        assertThat(clusterState.getRoutingTable().allShards(), hasSize(0));
    }

    @Test
    public void testClusterStateCollectorOneIndex() throws Exception {
        int nbShards = randomIntBetween(1, 5);
        createIndex("test", Settings.settingsBuilder().put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, nbShards).build());

        int nbDocs = randomIntBetween(1, 20);
        for (int i = 0; i < nbDocs; i++) {
            client().prepareIndex("test", "test").setSource("num", i).get();
        }
        client().admin().indices().prepareRefresh().get();
        assertHitCount(client().prepareCount().get(), nbDocs);

        Collection<MarvelDoc> results = newClusterStateCollector().doCollect();
        assertThat(results, hasSize(1));

        MarvelDoc marvelDoc = results.iterator().next();
        assertNotNull(marvelDoc);
        assertThat(marvelDoc, instanceOf(ClusterStateMarvelDoc.class));

        ClusterStateMarvelDoc clusterStateMarvelDoc = (ClusterStateMarvelDoc) marvelDoc;
        assertThat(clusterStateMarvelDoc.clusterName(), equalTo(client().admin().cluster().prepareHealth().get().getClusterName()));
        assertThat(clusterStateMarvelDoc.timestamp(), greaterThan(0L));
        assertThat(clusterStateMarvelDoc.type(), equalTo(ClusterStateCollector.TYPE));

        ClusterStateMarvelDoc.Payload payload = clusterStateMarvelDoc.payload();
        assertNotNull(payload);
        assertNotNull(payload.getClusterState());

        ClusterState clusterState = payload.getClusterState();
        assertThat(clusterState.getRoutingTable().allShards("test"), hasSize(nbShards));
    }

    @Test
    public void testClusterStateCollectorMultipleIndices() throws Exception {
        int nbIndices = randomIntBetween(1, 5);
        int[] docsPerIndex = new int[nbIndices];
        int[] shardsPerIndex = new int[nbIndices];

        for (int i = 0; i < nbIndices; i++) {
            shardsPerIndex[i] = randomIntBetween(1, 5);
            createIndex("test-" + i, Settings.settingsBuilder().put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, shardsPerIndex[i]).build());

            docsPerIndex[i] = randomIntBetween(1, 20);
            for (int j = 0; j < docsPerIndex[i]; j++) {
                client().prepareIndex("test-" + i, "test").setSource("num", i).get();
            }
        }

        client().admin().indices().prepareRefresh().get();
        for (int i = 0; i < nbIndices; i++) {
            assertHitCount(client().prepareCount("test-" + i).get(), docsPerIndex[i]);
        }

        Collection<MarvelDoc> results = newClusterStateCollector().doCollect();
        MarvelDoc marvelDoc = results.iterator().next();
        assertNotNull(marvelDoc);
        assertThat(marvelDoc, instanceOf(ClusterStateMarvelDoc.class));

        ClusterStateMarvelDoc clusterStateMarvelDoc = (ClusterStateMarvelDoc) marvelDoc;
        assertThat(clusterStateMarvelDoc.clusterName(), equalTo(client().admin().cluster().prepareHealth().get().getClusterName()));
        assertThat(clusterStateMarvelDoc.timestamp(), greaterThan(0L));
        assertThat(clusterStateMarvelDoc.type(), equalTo(ClusterStateCollector.TYPE));

        ClusterStateMarvelDoc.Payload payload = clusterStateMarvelDoc.payload();
        assertNotNull(payload);
        assertNotNull(payload.getClusterState());

        ClusterState clusterState = payload.getClusterState();
        for (int i = 0; i < nbIndices; i++) {
            assertThat(clusterState.getRoutingTable().allShards("test-" + i), hasSize(shardsPerIndex[i]));
        }
    }

    private ClusterStateCollector newClusterStateCollector() {
        return new ClusterStateCollector(getInstanceFromNode(Settings.class),
                getInstanceFromNode(ClusterService.class),
                getInstanceFromNode(ClusterName.class),
                getInstanceFromNode(MarvelSettingsService.class),
                client());
    }
}
