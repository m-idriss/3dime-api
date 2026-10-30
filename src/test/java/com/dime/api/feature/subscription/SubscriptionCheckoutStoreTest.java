package com.dime.api.feature.subscription;

import com.dime.api.feature.converter.*;
import com.dime.api.feature.shared.exception.*;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionCheckoutStoreTest {
 @Test void concurrentCheckoutsShareOneDurableSlot() throws Exception {
  Harness h=new Harness(); ExecutorService pool=Executors.newFixedThreadPool(8);
  try {
   List<Callable<String>> tasks=new ArrayList<>();
   for(int i=0;i<30;i++) tasks.add(()->h.store.acquire("u","monthly","price","test@example.com").token);
   Set<String> tokens=new HashSet<>(); for(var r:pool.invokeAll(tasks)) tokens.add(r.get());
   assertEquals(1,tokens.size());
   h.store.saveSession("u",h.slot.token,"cs_test_saved");
   assertEquals("cs_test_saved",h.store.acquire("u","yearly","annual","other@example.com").sessionId);
   assertEquals("monthly",h.slot.billingCycle);
  } finally {pool.shutdownNow();}
 }
 @Test void unavailableDatastoreCannotOpenCheckout() {
  Harness h=new Harness(); when(h.db.collection("users")).thenThrow(new RuntimeException("offline"));
  assertThrows(DatastoreUnavailableException.class,()->h.store.acquire("u","monthly","price",null));
 }
 @Test void existingSubscriptionAndStaleReplacementAreRejected() {
  Harness h=new Harness(); var old=h.store.acquire("u","monthly","price",null);
  var next=h.store.replaceExpired("u",old.token,"yearly","annual",null);
  assertNotEquals(old.token,next.token);
  assertThrows(ValidationException.class,()->h.store.replaceExpired("u",old.token,"monthly","price",null));
  h.quota=new UserQuota(PlanType.PLUS,0,15,Timestamp.now(),Timestamp.now(),Timestamp.now());
  assertThrows(ValidationException.class,()->h.store.acquire("u","monthly","price",null));
 }
 static class Harness {
  final SubscriptionCheckoutStore store=new SubscriptionCheckoutStore();
  final Firestore db=mock(Firestore.class);
  SubscriptionCheckoutStore.Slot slot;
  UserQuota quota;
  @SuppressWarnings("unchecked") Harness() {
   Instance<Firestore> instance=mock(Instance.class); when(instance.get()).thenReturn(db); store.firestoreInstance=instance;
   CollectionReference users=mock(CollectionReference.class), slots=mock(CollectionReference.class);
   DocumentReference user=mock(DocumentReference.class), ref=mock(DocumentReference.class);
   when(db.collection("users")).thenReturn(users);when(users.document("u")).thenReturn(user);
   when(user.collection("billingCheckout")).thenReturn(slots);when(slots.document("subscription")).thenReturn(ref);
   when(db.runTransaction(any(Transaction.Function.class))).thenAnswer(inv->{synchronized(this){
    Transaction tx=mock(Transaction.class);DocumentSnapshot ud=mock(DocumentSnapshot.class),sd=mock(DocumentSnapshot.class);
    when(ud.exists()).thenReturn(quota!=null);when(ud.toObject(UserQuota.class)).thenReturn(quota);
    when(sd.exists()).thenReturn(slot!=null);when(sd.toObject(SubscriptionCheckoutStore.Slot.class)).thenReturn(slot);
    when(tx.get(user)).thenReturn(ApiFutures.immediateFuture(ud));when(tx.get(ref)).thenReturn(ApiFutures.immediateFuture(sd));
    doAnswer(a->{slot=a.getArgument(1);return tx;}).when(tx).set(eq(ref),any(SubscriptionCheckoutStore.Slot.class));
    doAnswer(a->{slot.sessionId=a.getArgument(2);return tx;}).when(tx).update(eq(ref),eq("sessionId"),anyString());
    try{return ApiFutures.immediateFuture(((Transaction.Function<?>)inv.getArgument(0)).updateCallback(tx));}
    catch(Exception e){return ApiFutures.immediateFailedFuture(e);}
   }});
  }
 }
}
