package org.robolectric.shadows;

import static org.robolectric.shadow.api.Shadow.invokeConstructor;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.annotation.Beta;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadow.api.Shadow;

/**
 * A new variant of a Looper shadow that is active when {@link
 * ShadowBaseLooper#useRealisticLooper()} is enabled.
 *
 * This shadow differs from the legacy {@link ShadowLooper} in the following ways:\
 *   - Has no connection to {@link org.robolectric.util.Scheduler}. Its APIs are standalone
 *   - The main looper is always paused. Posted messages are not executed unless {@link #idle()} is
 *     called.
 *   - Just like in real Android, each looper has its own thread, and posted tasks get executed in
 *   that thread. -
 *   - There is only a single {@link SystemClock} value that all loopers read from. Unlike legacy
 *     behavior where each {@link org.robolectric.util.Scheduler} kept their own clock value.
 *
 * This is beta API, and will very likely be renamed in a future Robolectric release.
 * Its recommended to use ShadowBaseLooper instead of this type directly.
 */
@Implements(
    value = Looper.class,
    shadowPicker = ShadowBaseLooper.Picker.class,
    // TODO: turn off shadowOf generation. Figure out why this is needed
    isInAndroidSdk = false)
@SuppressWarnings("NewApi")
@Beta
public class ShadowRealisticLooper extends ShadowBaseLooper {

  // Keep reference to all created Loopers so they can be torn down after test
  private static Set<Looper> loopingLoopers =
      Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Looper, Boolean>()));

  @RealObject private Looper realLooper;

  @Implementation
  protected void __constructor__(boolean quitAllowed) {
    invokeConstructor(Looper.class, realLooper, from(boolean.class, quitAllowed));

    loopingLoopers.add(realLooper);
  }

  @Override
  public void idle() {
    ShadowPausedMessageQueue shadowQueue = Shadow.extract(realLooper.getQueue());
    IdlingRunnable idlingRunnable = new IdlingRunnable(shadowQueue);
    if (Thread.currentThread() == realLooper.getThread()) {
      idlingRunnable.run();
    } else {
      if (realLooper.equals(Looper.getMainLooper())) {
        throw new UnsupportedOperationException("main looper can only be idled from main thread");
      }
      new Handler(realLooper).post(idlingRunnable);
      idlingRunnable.waitTillIdle();
    }
  }

  @Override
  public void idleFor(long time, TimeUnit timeUnit) {
    ShadowSystemClock.advanceBy(Duration.ofMillis(timeUnit.toMillis(time)));
    idle();
  }

  @Override
  public void idleIfPaused() {
    idle();
  }

  @Override
  public boolean isIdle() {
    ShadowPausedMessageQueue shadowQueue = Shadow.extract(realLooper.getQueue());
    return shadowQueue.isIdle();
  }

  @Override
  public void runPaused(Runnable runnable) {
    if (realLooper != Looper.getMainLooper()) {
      throw new UnsupportedOperationException("only the main looper can be paused");
    }
    // directly run, looper is always paused
    runnable.run();
    idle();
  }

  @Override
  public void pause() {
    if (realLooper != Looper.getMainLooper()) {
      throw new UnsupportedOperationException("only the main looper can be paused");
    }
  }

  @Override
  public Duration getNextScheduledTaskTime() {
    ShadowPausedMessageQueue shadowQueue = Shadow.extract(realLooper.getQueue());
    return shadowQueue.getNextScheduledTaskTime();
  }

  @Override
  public Duration getLastScheduledTaskTime() {
    ShadowPausedMessageQueue shadowQueue = Shadow.extract(realLooper.getQueue());
    return shadowQueue.getLastScheduledTaskTime();
  }

  public static boolean isMainLooperIdle() {
    Looper mainLooper = Looper.getMainLooper();
    if (mainLooper != null) {
      ShadowPausedMessageQueue shadowPausedMessageQueue = Shadow.extract(mainLooper.getQueue());
      return shadowPausedMessageQueue.isIdle();
    }
    return true;
  }

  @Resetter
  public static synchronized void reset() {
    if (!ShadowBaseLooper.useRealisticLooper()) {
      // ignore if not realistic looper
      return;
    }

    Collection<Looper> loopersCopy = new ArrayList(loopingLoopers);
    for (Looper looper : loopersCopy) {
      ShadowPausedMessageQueue shadowPausedMessageQueue = Shadow.extract(looper.getQueue());
      if (shadowPausedMessageQueue.isQuitAllowed()) {
        looper.quit();
        loopingLoopers.remove(looper);
      } else {
        shadowPausedMessageQueue.reset();
      }
    }
  }

  private static class IdlingRunnable implements Runnable {

    private final CountDownLatch runLatch = new CountDownLatch(1);
    private final ShadowPausedMessageQueue shadowQueue;

    public IdlingRunnable(ShadowPausedMessageQueue shadowQueue) {
      this.shadowQueue = shadowQueue;
    }

    public void waitTillIdle() {
      try {
        runLatch.await();
      } catch (InterruptedException e) {
        Log.w("ShadowRealisticLooper", "wait till idle interrupted");
      }
    }

    @Override
    public void run() {
      while (!shadowQueue.isIdle()) {
        Message msg = shadowQueue.getNext();
        msg.getTarget().dispatchMessage(msg);
        ShadowPausedMessage shadowMsg = Shadow.extract(msg);
        shadowMsg.recycleUnchecked();
      }
      runLatch.countDown();
    }
  }
}
