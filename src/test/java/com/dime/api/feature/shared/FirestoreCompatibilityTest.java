package com.dime.api.feature.shared;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.core.ApiFunction;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOpenTelemetryOptions;
import com.google.cloud.firestore.FirestoreOptions;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FirestoreCompatibilityTest {

    @Test
    void firestoreServiceCreation_doesNotThrowTelemetryLinkageError() {
        FirestoreOptions options = firestoreOptionsWithOpenTelemetry();

        assertDoesNotThrow(() -> {
            try (Firestore firestore = options.getService()) {
                assertNotNull(firestore);
            }
        });
    }

    @Test
    void firestoreGrpcTelemetryApi_matchesFirestoreBytecode() {
        assertDoesNotThrow(() -> GrpcTelemetry.class.getMethod("newClientInterceptor"));
    }

    @Test
    void firestoreGrpcTelemetryCompletion_doesNotThrowSemconvLinkageError() {
        assertDoesNotThrow(() -> {
            Class<?> getterClass = Class.forName(
                    "io.opentelemetry.instrumentation.grpc.v1_6.GrpcRpcAttributesGetter");
            Field instanceField = getterClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);

            Class<?> extractorClass = Class.forName(
                    "io.opentelemetry.instrumentation.grpc.v1_6.GrpcAttributesExtractor");
            Constructor<?> constructor = extractorClass.getDeclaredConstructor(getterClass, List.class);
            constructor.setAccessible(true);
            Object extractor = constructor.newInstance(instanceField.get(null), List.of());

            Class<?> requestClass = Class.forName(
                    "io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest");
            Method onEnd = extractorClass.getDeclaredMethod("onEnd", AttributesBuilder.class,
                    Context.class, requestClass, Status.class, Throwable.class);
            onEnd.setAccessible(true);

            onEnd.invoke(extractor, Attributes.builder(), Context.root(), null, Status.OK, null);
        });
    }

    @Test
    void firestoreOpenTelemetryChannelConfigurator_doesNotThrowLinkageError() {
        FirestoreOptions options = firestoreOptionsWithOpenTelemetry();

        assertDoesNotThrow(() -> {
            Class<?> traceUtilClass = Class.forName("com.google.cloud.firestore.telemetry.EnabledTraceUtil");
            Constructor<?> constructor = traceUtilClass.getDeclaredConstructor(FirestoreOptions.class);
            constructor.setAccessible(true);
            Object traceUtil = constructor.newInstance(options);

            Method getChannelConfigurator = traceUtilClass.getDeclaredMethod("getChannelConfigurator");
            @SuppressWarnings("unchecked")
            ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder> configurator =
                    (ApiFunction<ManagedChannelBuilder, ManagedChannelBuilder>) getChannelConfigurator.invoke(traceUtil);

            ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forTarget("localhost:1").usePlaintext();
            assertNotNull(configurator.apply(channelBuilder));
        });
    }

    private FirestoreOptions firestoreOptionsWithOpenTelemetry() {
        FirestoreOpenTelemetryOptions openTelemetryOptions = FirestoreOpenTelemetryOptions.newBuilder()
                .setOpenTelemetry(OpenTelemetrySdk.builder().build())
                .build();

        return FirestoreOptions.newBuilder()
                .setProjectId("test-project")
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("test-token", new Date(Long.MAX_VALUE))))
                .setOpenTelemetryOptions(openTelemetryOptions)
                .build();
    }
}
