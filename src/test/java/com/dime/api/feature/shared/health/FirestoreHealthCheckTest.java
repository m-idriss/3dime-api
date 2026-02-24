package com.dime.api.feature.shared.health;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FirestoreHealthCheckTest {

    FirestoreHealthCheck check;

    @BeforeEach
    void setup() {
        check = new FirestoreHealthCheck();
        check.firestore = mock(Firestore.class);
        check.cachedResponse = null;
        check.lastCheckedAt = 0;
    }

    @Test
    void testUp() throws Exception {
        CollectionReference collection = mock(CollectionReference.class);
        Query query = mock(Query.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);

        when(check.firestore.collection("users")).thenReturn(collection);
        when(collection.limit(1)).thenReturn(query);
        when(query.get()).thenReturn(future);
        when(future.get(500, TimeUnit.MILLISECONDS)).thenReturn(snapshot);

        HealthCheckResponse response = check.doCheck();

        assertEquals("firestore", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertTrue(response.getData().get().containsKey("latencyMs"));
    }

    @Test
    void testDown_onTimeout() throws Exception {
        CollectionReference collection = mock(CollectionReference.class);
        Query query = mock(Query.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);

        when(check.firestore.collection("users")).thenReturn(collection);
        when(collection.limit(1)).thenReturn(query);
        when(query.get()).thenReturn(future);
        when(future.get(500, TimeUnit.MILLISECONDS)).thenThrow(new RuntimeException("connection refused"));

        HealthCheckResponse response = check.doCheck();

        assertEquals("firestore", response.getName());
        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertEquals("connection refused", response.getData().get().get("error"));
    }

    @Test
    void testDown_onFirestoreUnavailable() {
        when(check.firestore.collection(any())).thenThrow(new RuntimeException("Firestore unavailable"));

        HealthCheckResponse response = check.doCheck();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertNotNull(response.getData().get().get("error"));
    }

    @Test
    void testCacheReturnsCachedResponse() throws Exception {
        CollectionReference collection = mock(CollectionReference.class);
        Query query = mock(Query.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);

        when(check.firestore.collection("users")).thenReturn(collection);
        when(collection.limit(1)).thenReturn(query);
        when(query.get()).thenReturn(future);
        when(future.get(500, TimeUnit.MILLISECONDS)).thenReturn(snapshot);

        check.call();
        check.call();

        verify(check.firestore, times(1)).collection("users");
    }
}
