package com.dime.api.feature.shared.health;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FirestoreHealthCheckTest {

    FirestoreHealthCheck check;
    Firestore firestoreMock;
    Instance<Firestore> firestoreInstanceMock;

    @BeforeEach
    void setup() {
        check = new FirestoreHealthCheck();
        firestoreMock = mock(Firestore.class);
        firestoreInstanceMock = mock(Instance.class);
        when(firestoreInstanceMock.get()).thenReturn(firestoreMock);
        check.firestoreInstance = firestoreInstanceMock;
        check.cachedResponse = null;
        check.lastCheckedAt = 0;
    }

    @Test
    void testUp() throws Exception {
        CollectionReference collection = mock(CollectionReference.class);
        Query query = mock(Query.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);

        when(firestoreMock.collection("users")).thenReturn(collection);
        when(collection.limit(1)).thenReturn(query);
        when(query.get()).thenReturn(future);
        when(future.get(2, TimeUnit.SECONDS)).thenReturn(snapshot);

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

        when(firestoreMock.collection("users")).thenReturn(collection);
        when(collection.limit(1)).thenReturn(query);
        when(query.get()).thenReturn(future);
        when(future.get(2, TimeUnit.SECONDS)).thenThrow(new RuntimeException("connection refused"));

        HealthCheckResponse response = check.doCheck();

        assertEquals("firestore", response.getName());
        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertEquals("connection refused", response.getData().get().get("error"));
    }

    @Test
    void testDown_onFirestoreUnavailable() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore unavailable"));

        HealthCheckResponse response = check.doCheck();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertNotNull(response.getData().get().get("error"));
    }

    @Test
    void testLinkageErrorDuringBeanInitialization_isNotSwallowed() {
        when(firestoreInstanceMock.get()).thenThrow(new NoSuchMethodError("GrpcTelemetry.newClientInterceptor"));

        assertThrows(NoSuchMethodError.class, () -> check.doCheck());
    }

    @Test
    void testCacheReturnsCachedResponse() throws Exception {
        CollectionReference collection = mock(CollectionReference.class);
        Query query = mock(Query.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);

        when(firestoreMock.collection("users")).thenReturn(collection);
        when(collection.limit(1)).thenReturn(query);
        when(query.get()).thenReturn(future);
        when(future.get(2, TimeUnit.SECONDS)).thenReturn(snapshot);

        check.call();
        check.call();

        verify(firestoreMock, times(1)).collection("users");
    }
}
