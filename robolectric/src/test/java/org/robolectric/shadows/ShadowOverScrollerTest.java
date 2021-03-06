package org.robolectric.shadows;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.shadows.ShadowBaseLooper.shadowMainLooper;

import android.view.animation.LinearInterpolator;
import android.widget.OverScroller;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ShadowOverScrollerTest {
  private OverScroller overScroller;

  @Before
  public void setUp() {
    overScroller =
        new OverScroller(ApplicationProvider.getApplicationContext(), new LinearInterpolator());
  }

  @Test
  public void shouldScrollOverTime() {
    overScroller.startScroll(0, 0, 100, 200, 1000);

    assertThat(overScroller.getStartX()).isEqualTo(0);
    assertThat(overScroller.getStartY()).isEqualTo(0);
    assertThat(overScroller.getFinalX()).isEqualTo(100);
    assertThat(overScroller.getFinalY()).isEqualTo(200);
    assertThat(overScroller.isScrollingInDirection(1, 1)).isTrue();
    assertThat(overScroller.isScrollingInDirection(-1, -1)).isFalse();

    assertThat(overScroller.getCurrX()).isEqualTo(0);
    assertThat(overScroller.getCurrY()).isEqualTo(0);
    assertThat(overScroller.timePassed()).isEqualTo(0);
    assertThat(overScroller.isFinished()).isFalse();

    shadowMainLooper().idleFor(100, TimeUnit.MILLISECONDS);
    assertThat(overScroller.getCurrX()).isEqualTo(10);
    assertThat(overScroller.getCurrY()).isEqualTo(20);
    assertThat(overScroller.timePassed()).isEqualTo(100);
    assertThat(overScroller.isFinished()).isFalse();

    shadowMainLooper().idleFor(401, TimeUnit.MILLISECONDS);
    assertThat(overScroller.getCurrX()).isEqualTo(50);
    assertThat(overScroller.getCurrY()).isEqualTo(100);
    assertThat(overScroller.timePassed()).isEqualTo(501);
    assertThat(overScroller.isFinished()).isFalse();

    shadowMainLooper().idleFor(1000, TimeUnit.MILLISECONDS);
    assertThat(overScroller.getCurrX()).isEqualTo(100);
    assertThat(overScroller.getCurrY()).isEqualTo(200);
    assertThat(overScroller.timePassed()).isEqualTo(1501);
    assertThat(overScroller.isFinished()).isEqualTo(true);
    assertThat(overScroller.isScrollingInDirection(1, 1)).isFalse();
    assertThat(overScroller.isScrollingInDirection(-1, -1)).isFalse();
  }

  @Test
  public void computeScrollOffsetShouldCalculateWhetherScrollIsFinished() {
    assertThat(overScroller.computeScrollOffset()).isFalse();

    overScroller.startScroll(0, 0, 100, 200, 1000);
    assertThat(overScroller.computeScrollOffset()).isTrue();

    shadowMainLooper().idleFor(500, TimeUnit.MILLISECONDS);
    assertThat(overScroller.computeScrollOffset()).isTrue();

    shadowMainLooper().idleFor(500, TimeUnit.MILLISECONDS);
    assertThat(overScroller.computeScrollOffset()).isTrue();
    assertThat(overScroller.computeScrollOffset()).isFalse();
  }

  @Test
  public void abortAnimationShouldMoveToFinalPositionImmediately() {
    overScroller.startScroll(0, 0, 100, 200, 1000);
    shadowMainLooper().idleFor(500, TimeUnit.MILLISECONDS);
    overScroller.abortAnimation();

    assertThat(overScroller.getCurrX()).isEqualTo(100);
    assertThat(overScroller.getCurrY()).isEqualTo(200);
    assertThat(overScroller.timePassed()).isEqualTo(500);
    assertThat(overScroller.isFinished()).isTrue();
  }

  @Test
  public void forceFinishedShouldFinishWithoutMovingFurther() {
    overScroller.startScroll(0, 0, 100, 200, 1000);
    shadowMainLooper().idleFor(500, TimeUnit.MILLISECONDS);
    overScroller.forceFinished(true);

    assertThat(overScroller.getCurrX()).isEqualTo(50);
    assertThat(overScroller.getCurrY()).isEqualTo(100);
    assertThat(overScroller.timePassed()).isEqualTo(500);
    assertThat(overScroller.isFinished()).isTrue();
  }
}
