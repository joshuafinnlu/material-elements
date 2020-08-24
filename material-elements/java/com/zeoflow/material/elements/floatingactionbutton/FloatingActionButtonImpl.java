

package com.zeoflow.material.elements.floatingactionbutton;

import com.google.android.material.R;

import static androidx.core.util.Preconditions.checkNotNull;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.util.Preconditions;
import androidx.core.view.ViewCompat;
import android.view.View;
import android.view.ViewTreeObserver;
import com.zeoflow.material.elements.animation.AnimationUtils;
import com.zeoflow.material.elements.animation.AnimatorSetCompat;
import com.zeoflow.material.elements.animation.ImageMatrixProperty;
import com.zeoflow.material.elements.animation.MatrixEvaluator;
import com.zeoflow.material.elements.animation.MotionSpec;
import com.zeoflow.material.elements.internal.StateListAnimator;
import com.zeoflow.material.elements.ripple.RippleDrawableCompat;
import com.zeoflow.material.elements.ripple.RippleUtils;
import com.zeoflow.material.elements.shadow.ShadowViewDelegate;
import com.zeoflow.material.elements.shape.MaterialShapeDrawable;
import com.zeoflow.material.elements.shape.MaterialShapeUtils;
import com.zeoflow.material.elements.shape.ShapeAppearanceModel;
import com.zeoflow.material.elements.shape.Shapeable;
import java.util.ArrayList;
import java.util.List;

class FloatingActionButtonImpl {

  static final TimeInterpolator ELEVATION_ANIM_INTERPOLATOR =
      AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR;
  static final long ELEVATION_ANIM_DURATION = 100;
  static final long ELEVATION_ANIM_DELAY = 100;

  static final int ANIM_STATE_NONE = 0;
  static final int ANIM_STATE_HIDING = 1;
  static final int ANIM_STATE_SHOWING = 2;
  static final float SHADOW_MULTIPLIER = 1.5f;

  private static final float HIDE_OPACITY = 0f;
  private static final float HIDE_SCALE = 0f;
  private static final float HIDE_ICON_SCALE = 0f;
  private static final float SHOW_OPACITY = 1f;
  private static final float SHOW_SCALE = 1f;
  private static final float SHOW_ICON_SCALE = 1f;

  @Nullable ShapeAppearanceModel shapeAppearance;
  @Nullable MaterialShapeDrawable shapeDrawable;
  @Nullable Drawable rippleDrawable;
  @Nullable BorderDrawable borderDrawable;
  @Nullable Drawable contentBackground;

  boolean ensureMinTouchTargetSize;
  boolean shadowPaddingEnabled = true;
  float elevation;
  float hoveredFocusedTranslationZ;
  float pressedTranslationZ;
  int minTouchTargetSize;

  @NonNull private final StateListAnimator stateListAnimator;

  @Nullable private MotionSpec defaultShowMotionSpec;
  @Nullable private MotionSpec defaultHideMotionSpec;
  @Nullable private Animator currentAnimator;
  @Nullable private MotionSpec showMotionSpec;
  @Nullable private MotionSpec hideMotionSpec;

  private float rotation;
  private float imageMatrixScale = 1f;
  private int maxImageSize;
  private int animState = ANIM_STATE_NONE;

  private ArrayList<AnimatorListener> showListeners;
  private ArrayList<AnimatorListener> hideListeners;
  private ArrayList<InternalTransformationCallback> transformationCallbacks;

  interface InternalTransformationCallback {

    void onTranslationChanged();

    void onScaleChanged();
  }

  interface InternalVisibilityChangedListener {
    void onShown();

    void onHidden();
  }

  static final int[] PRESSED_ENABLED_STATE_SET = {
      android.R.attr.state_pressed, android.R.attr.state_enabled
  };
  static final int[] HOVERED_FOCUSED_ENABLED_STATE_SET = {
      android.R.attr.state_hovered, android.R.attr.state_focused, android.R.attr.state_enabled
  };
  static final int[] FOCUSED_ENABLED_STATE_SET = {
      android.R.attr.state_focused, android.R.attr.state_enabled
  };
  static final int[] HOVERED_ENABLED_STATE_SET = {
      android.R.attr.state_hovered, android.R.attr.state_enabled
  };
  static final int[] ENABLED_STATE_SET = {android.R.attr.state_enabled};
  static final int[] EMPTY_STATE_SET = new int[0];

  final FloatingActionButton view;
  final ShadowViewDelegate shadowViewDelegate;

  private final Rect tmpRect = new Rect();
  private final RectF tmpRectF1 = new RectF();
  private final RectF tmpRectF2 = new RectF();
  private final Matrix tmpMatrix = new Matrix();

  @Nullable
  private ViewTreeObserver.OnPreDrawListener preDrawListener;

  @SuppressWarnings("nullness")
  FloatingActionButtonImpl(FloatingActionButton view, ShadowViewDelegate shadowViewDelegate) {
    this.view = view;
    this.shadowViewDelegate = shadowViewDelegate;

    stateListAnimator = new StateListAnimator();

    
    stateListAnimator.addState(
        PRESSED_ENABLED_STATE_SET,
        createElevationAnimator(new ElevateToPressedTranslationZAnimation()));
    stateListAnimator.addState(
        HOVERED_FOCUSED_ENABLED_STATE_SET,
        createElevationAnimator(new ElevateToHoveredFocusedTranslationZAnimation()));
    stateListAnimator.addState(
        FOCUSED_ENABLED_STATE_SET,
        createElevationAnimator(new ElevateToHoveredFocusedTranslationZAnimation()));
    stateListAnimator.addState(
        HOVERED_ENABLED_STATE_SET,
        createElevationAnimator(new ElevateToHoveredFocusedTranslationZAnimation()));
    
    stateListAnimator.addState(
        ENABLED_STATE_SET, createElevationAnimator(new ResetElevationAnimation()));
    
    stateListAnimator.addState(
        EMPTY_STATE_SET, createElevationAnimator(new DisabledElevationAnimation()));

    rotation = this.view.getRotation();
  }

  void initializeBackgroundDrawable(
      ColorStateList backgroundTint,
      @Nullable PorterDuff.Mode backgroundTintMode,
      ColorStateList rippleColor,
      int borderWidth) {
    
    
    shapeDrawable = createShapeDrawable();
    shapeDrawable.setTintList(backgroundTint);
    if (backgroundTintMode != null) {
      shapeDrawable.setTintMode(backgroundTintMode);
    }

    shapeDrawable.setShadowColor(Color.DKGRAY);
    shapeDrawable.initializeElevationOverlay(view.getContext());

    
    RippleDrawableCompat touchFeedbackShape =
        new RippleDrawableCompat(shapeDrawable.getShapeAppearanceModel());
    touchFeedbackShape.setTintList(RippleUtils.sanitizeRippleDrawableColor(rippleColor));
    rippleDrawable = touchFeedbackShape;

    final Drawable[] layers = new Drawable[]{
        checkNotNull(shapeDrawable),
        touchFeedbackShape};
    contentBackground = new LayerDrawable(layers);
  }

  void setBackgroundTintList(@Nullable ColorStateList tint) {
    if (shapeDrawable != null) {
      shapeDrawable.setTintList(tint);
    }
    if (borderDrawable != null) {
      borderDrawable.setBorderTint(tint);
    }
  }

  void setBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
    if (shapeDrawable != null) {
      shapeDrawable.setTintMode(tintMode);
    }
  }

  void setMinTouchTargetSize(int minTouchTargetSize) {
    this.minTouchTargetSize = minTouchTargetSize;
  }

  void setRippleColor(@Nullable ColorStateList rippleColor) {
    if (rippleDrawable != null) {
      DrawableCompat.setTintList(
          rippleDrawable, RippleUtils.sanitizeRippleDrawableColor(rippleColor));
    }
  }

  final void setElevation(float elevation) {
    if (this.elevation != elevation) {
      this.elevation = elevation;
      onElevationsChanged(this.elevation, hoveredFocusedTranslationZ, pressedTranslationZ);
    }
  }

  float getElevation() {
    return elevation;
  }

  float getHoveredFocusedTranslationZ() {
    return hoveredFocusedTranslationZ;
  }

  float getPressedTranslationZ() {
    return pressedTranslationZ;
  }

  final void setHoveredFocusedTranslationZ(float translationZ) {
    if (hoveredFocusedTranslationZ != translationZ) {
      hoveredFocusedTranslationZ = translationZ;
      onElevationsChanged(elevation, hoveredFocusedTranslationZ, pressedTranslationZ);
    }
  }

  final void setPressedTranslationZ(float translationZ) {
    if (pressedTranslationZ != translationZ) {
      pressedTranslationZ = translationZ;
      onElevationsChanged(elevation, hoveredFocusedTranslationZ, pressedTranslationZ);
    }
  }

  final void setMaxImageSize(int maxImageSize) {
    if (this.maxImageSize != maxImageSize) {
      this.maxImageSize = maxImageSize;
      updateImageMatrixScale();
    }
  }

  
  final void updateImageMatrixScale() {
    
    setImageMatrixScale(imageMatrixScale);
  }

  final void setImageMatrixScale(float scale) {
    this.imageMatrixScale = scale;

    Matrix matrix = tmpMatrix;
    calculateImageMatrixFromScale(scale, matrix);
    view.setImageMatrix(matrix);
  }

  private void calculateImageMatrixFromScale(float scale, @NonNull Matrix matrix) {
    matrix.reset();

    Drawable drawable = view.getDrawable();
    if (drawable != null && maxImageSize != 0) {
      
      RectF drawableBounds = tmpRectF1;
      RectF imageBounds = tmpRectF2;
      drawableBounds.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
      imageBounds.set(0, 0, maxImageSize, maxImageSize);
      matrix.setRectToRect(drawableBounds, imageBounds, ScaleToFit.CENTER);

      
      matrix.postScale(scale, scale, maxImageSize / 2f, maxImageSize / 2f);
    }
  }

  final void setShapeAppearance(@NonNull ShapeAppearanceModel shapeAppearance) {
    this.shapeAppearance = shapeAppearance;
    if (shapeDrawable != null) {
      shapeDrawable.setShapeAppearanceModel(shapeAppearance);
    }

    if (rippleDrawable instanceof Shapeable) {
      ((Shapeable) rippleDrawable).setShapeAppearanceModel(shapeAppearance);
    }

    if (borderDrawable != null) {
      borderDrawable.setShapeAppearanceModel(shapeAppearance);
    }
  }

  @Nullable
  final ShapeAppearanceModel getShapeAppearance() {
    return shapeAppearance;
  }

  @Nullable
  final MotionSpec getShowMotionSpec() {
    return showMotionSpec;
  }

  final void setShowMotionSpec(@Nullable MotionSpec spec) {
    showMotionSpec = spec;
  }

  @Nullable
  final MotionSpec getHideMotionSpec() {
    return hideMotionSpec;
  }

  final void setHideMotionSpec(@Nullable MotionSpec spec) {
    hideMotionSpec = spec;
  }

  final boolean shouldExpandBoundsForA11y() {
    return !ensureMinTouchTargetSize || view.getSizeDimension() >= minTouchTargetSize;
  }

  boolean getEnsureMinTouchTargetSize() {
    return ensureMinTouchTargetSize;
  }

  void setEnsureMinTouchTargetSize(boolean flag) {
    ensureMinTouchTargetSize = flag;
  }

  void setShadowPaddingEnabled(boolean shadowPaddingEnabled) {
    this.shadowPaddingEnabled = shadowPaddingEnabled;
    updatePadding();
  }

  void onElevationsChanged(
      float elevation, float hoveredFocusedTranslationZ, float pressedTranslationZ) {
    updatePadding();
    updateShapeElevation(elevation);
  }

  void updateShapeElevation(float elevation) {
    if (shapeDrawable != null) {
      shapeDrawable.setElevation(elevation);
    }
  }

  void onDrawableStateChanged(int[] state) {
    stateListAnimator.setState(state);
  }

  void jumpDrawableToCurrentState() {
    stateListAnimator.jumpToCurrentState();
  }

  void addOnShowAnimationListener(@NonNull AnimatorListener listener) {
    if (showListeners == null) {
      showListeners = new ArrayList<>();
    }
    showListeners.add(listener);
  }

  void removeOnShowAnimationListener(@NonNull AnimatorListener listener) {
    if (showListeners == null) {
      
      
      return;
    }
    showListeners.remove(listener);
  }

  public void addOnHideAnimationListener(@NonNull AnimatorListener listener) {
    if (hideListeners == null) {
      hideListeners = new ArrayList<>();
    }
    hideListeners.add(listener);
  }

  public void removeOnHideAnimationListener(@NonNull AnimatorListener listener) {
    if (hideListeners == null) {
      
      
      return;
    }
    hideListeners.remove(listener);
  }

  void hide(@Nullable final InternalVisibilityChangedListener listener, final boolean fromUser) {
    if (isOrWillBeHidden()) {
      
      return;
    }

    if (currentAnimator != null) {
      currentAnimator.cancel();
    }

    if (shouldAnimateVisibilityChange()) {
      AnimatorSet set =
          createAnimator(
              hideMotionSpec != null ? hideMotionSpec : getDefaultHideMotionSpec(),
              HIDE_OPACITY,
              HIDE_SCALE,
              HIDE_ICON_SCALE);
      set.addListener(
          new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationStart(Animator animation) {
              view.internalSetVisibility(View.VISIBLE, fromUser);

              animState = ANIM_STATE_HIDING;
              currentAnimator = animation;
              cancelled = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
              cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
              animState = ANIM_STATE_NONE;
              currentAnimator = null;

              if (!cancelled) {
                view.internalSetVisibility(fromUser ? View.GONE : View.INVISIBLE, fromUser);
                if (listener != null) {
                  listener.onHidden();
                }
              }
            }
          });
      if (hideListeners != null) {
        for (AnimatorListener l : hideListeners) {
          set.addListener(l);
        }
      }
      set.start();
    } else {
      
      view.internalSetVisibility(fromUser ? View.GONE : View.INVISIBLE, fromUser);
      if (listener != null) {
        listener.onHidden();
      }
    }
  }

  void show(@Nullable final InternalVisibilityChangedListener listener, final boolean fromUser) {
    if (isOrWillBeShown()) {
      
      return;
    }

    if (currentAnimator != null) {
      currentAnimator.cancel();
    }

    if (shouldAnimateVisibilityChange()) {
      if (view.getVisibility() != View.VISIBLE) {
        
        view.setAlpha(0f);
        view.setScaleY(0f);
        view.setScaleX(0f);
        setImageMatrixScale(0f);
      }

      AnimatorSet set =
          createAnimator(
              showMotionSpec != null ? showMotionSpec : getDefaultShowMotionSpec(),
              SHOW_OPACITY,
              SHOW_SCALE,
              SHOW_ICON_SCALE);
      set.addListener(
          new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
              view.internalSetVisibility(View.VISIBLE, fromUser);

              animState = ANIM_STATE_SHOWING;
              currentAnimator = animation;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
              animState = ANIM_STATE_NONE;
              currentAnimator = null;

              if (listener != null) {
                listener.onShown();
              }
            }
          });
      if (showListeners != null) {
        for (AnimatorListener l : showListeners) {
          set.addListener(l);
        }
      }
      set.start();
    } else {
      view.internalSetVisibility(View.VISIBLE, fromUser);
      view.setAlpha(1f);
      view.setScaleY(1f);
      view.setScaleX(1f);
      setImageMatrixScale(1f);
      if (listener != null) {
        listener.onShown();
      }
    }
  }

  private MotionSpec getDefaultShowMotionSpec() {
    if (defaultShowMotionSpec == null) {
      defaultShowMotionSpec =
          MotionSpec.createFromResource(view.getContext(), R.animator.design_fab_show_motion_spec);
    }

    return checkNotNull(defaultShowMotionSpec);
  }

  private MotionSpec getDefaultHideMotionSpec() {
    if (defaultHideMotionSpec == null) {
      defaultHideMotionSpec =
          MotionSpec.createFromResource(view.getContext(), R.animator.design_fab_hide_motion_spec);
    }

    return checkNotNull(defaultHideMotionSpec);
  }

  @NonNull
  private AnimatorSet createAnimator(
      @NonNull MotionSpec spec, float opacity, float scale, float iconScale) {
    List<Animator> animators = new ArrayList<>();

    ObjectAnimator animatorOpacity = ObjectAnimator.ofFloat(view, View.ALPHA, opacity);
    spec.getTiming("opacity").apply(animatorOpacity);
    animators.add(animatorOpacity);

    ObjectAnimator animatorScaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, scale);
    spec.getTiming("scale").apply(animatorScaleX);
    workAroundOreoBug(animatorScaleX);
    animators.add(animatorScaleX);

    ObjectAnimator animatorScaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, scale);
    spec.getTiming("scale").apply(animatorScaleY);
    workAroundOreoBug(animatorScaleY);
    animators.add(animatorScaleY);

    calculateImageMatrixFromScale(iconScale, tmpMatrix);
    ObjectAnimator animatorIconScale =
        ObjectAnimator.ofObject(
            view,
            new ImageMatrixProperty(),
            new MatrixEvaluator() {
              @Override
              public Matrix evaluate(
                  float fraction, @NonNull Matrix startValue, @NonNull Matrix endValue) {
                
                
                imageMatrixScale = fraction;
                return super.evaluate(fraction, startValue, endValue);
              }
            },
            new Matrix(tmpMatrix));
    spec.getTiming("iconScale").apply(animatorIconScale);
    animators.add(animatorIconScale);

    AnimatorSet set = new AnimatorSet();
    AnimatorSetCompat.playTogether(set, animators);
    return set;
  }

  
  private void workAroundOreoBug(final ObjectAnimator animator) {
    if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
      return;
    }

    animator.setEvaluator(new TypeEvaluator<Float>() {
      FloatEvaluator floatEvaluator = new FloatEvaluator();
      @Override
      public Float evaluate(float fraction, Float startValue, Float endValue) {
        float evaluated = floatEvaluator.evaluate(fraction, startValue, endValue);
        return evaluated < 0.1f ? 0.0f : evaluated;
      }
    });
  }

  void addTransformationCallback(@NonNull InternalTransformationCallback listener) {
    if (transformationCallbacks == null) {
      transformationCallbacks = new ArrayList<>();
    }
    transformationCallbacks.add(listener);
  }

  void removeTransformationCallback(@NonNull InternalTransformationCallback listener) {
    if (transformationCallbacks == null) {
      
      
      return;
    }
    transformationCallbacks.remove(listener);
  }

  void onTranslationChanged() {
    if (transformationCallbacks != null) {
      for (InternalTransformationCallback l : transformationCallbacks) {
        l.onTranslationChanged();
      }
    }
  }

  void onScaleChanged() {
    if (transformationCallbacks != null) {
      for (InternalTransformationCallback l : transformationCallbacks) {
        l.onScaleChanged();
      }
    }
  }

  @Nullable
  final Drawable getContentBackground() {
    return contentBackground;
  }

  void onCompatShadowChanged() {
    
  }

  final void updatePadding() {
    Rect rect = tmpRect;
    getPadding(rect);
    onPaddingUpdated(rect);
    shadowViewDelegate.setShadowPadding(rect.left, rect.top, rect.right, rect.bottom);
  }

  void getPadding(@NonNull Rect rect) {
    final int minPadding = ensureMinTouchTargetSize
        ? (minTouchTargetSize - view.getSizeDimension()) / 2
        : 0;

    final float maxShadowSize = shadowPaddingEnabled ? (getElevation() + pressedTranslationZ) : 0;
    final int hPadding = Math.max(minPadding, (int) Math.ceil(maxShadowSize));
    final int vPadding = Math.max(minPadding, (int) Math.ceil(maxShadowSize * SHADOW_MULTIPLIER));
    rect.set(hPadding, vPadding, hPadding, vPadding);
  }

  void onPaddingUpdated(@NonNull Rect padding) {
    Preconditions.checkNotNull(contentBackground, "Didn't initialize content background");
    if (shouldAddPadding()) {
      InsetDrawable insetDrawable = new InsetDrawable(
          contentBackground, padding.left, padding.top, padding.right, padding.bottom);
      shadowViewDelegate.setBackgroundDrawable(insetDrawable);
    } else {
      shadowViewDelegate.setBackgroundDrawable(contentBackground);
    }
  }

  boolean shouldAddPadding() {
    return true;
  }

  void onAttachedToWindow() {
    if (shapeDrawable != null) {
      MaterialShapeUtils.setParentAbsoluteElevation(view, shapeDrawable);
    }

    if (requirePreDrawListener()) {
      view.getViewTreeObserver().addOnPreDrawListener(getOrCreatePreDrawListener());
    }
  }

  void onDetachedFromWindow() {
    ViewTreeObserver viewTreeObserver = view.getViewTreeObserver();
    if (preDrawListener != null) {
      viewTreeObserver.removeOnPreDrawListener(preDrawListener);
      preDrawListener = null;
    }
  }

  boolean requirePreDrawListener() {
    return true;
  }

  void onPreDraw() {
    final float rotation = view.getRotation();
    if (this.rotation != rotation) {
      this.rotation = rotation;
      updateFromViewRotation();
    }
  }

  @NonNull
  private ViewTreeObserver.OnPreDrawListener getOrCreatePreDrawListener() {
    if (preDrawListener == null) {
      preDrawListener =
          new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
              FloatingActionButtonImpl.this.onPreDraw();
              return true;
            }
          };
    }

    return preDrawListener;
  }

  MaterialShapeDrawable createShapeDrawable() {
    ShapeAppearanceModel shapeAppearance = checkNotNull(this.shapeAppearance);
    return new MaterialShapeDrawable(shapeAppearance);
  }

  boolean isOrWillBeShown() {
    if (view.getVisibility() != View.VISIBLE) {
      
      return animState == ANIM_STATE_SHOWING;
    } else {
      
      return animState != ANIM_STATE_HIDING;
    }
  }

  boolean isOrWillBeHidden() {
    if (view.getVisibility() == View.VISIBLE) {
      
      return animState == ANIM_STATE_HIDING;
    } else {
      
      return animState != ANIM_STATE_SHOWING;
    }
  }

  @NonNull
  private ValueAnimator createElevationAnimator(@NonNull ShadowAnimatorImpl impl) {
    final ValueAnimator animator = new ValueAnimator();
    animator.setInterpolator(ELEVATION_ANIM_INTERPOLATOR);
    animator.setDuration(ELEVATION_ANIM_DURATION);
    animator.addListener(impl);
    animator.addUpdateListener(impl);
    animator.setFloatValues(0, 1);
    return animator;
  }

  private abstract class ShadowAnimatorImpl extends AnimatorListenerAdapter
      implements ValueAnimator.AnimatorUpdateListener {

    private boolean validValues;
    private float shadowSizeStart;
    private float shadowSizeEnd;

    @Override
    public void onAnimationUpdate(@NonNull ValueAnimator animator) {
      if (!validValues) {
        shadowSizeStart = shapeDrawable == null ? 0 : shapeDrawable.getElevation();
        shadowSizeEnd = getTargetShadowSize();
        validValues = true;
      }

      updateShapeElevation(
          (int)
              (shadowSizeStart
                  + ((shadowSizeEnd - shadowSizeStart) * animator.getAnimatedFraction())));
    }

    @Override
    public void onAnimationEnd(Animator animator) {
      updateShapeElevation((int) shadowSizeEnd);
      validValues = false;
    }

    
    protected abstract float getTargetShadowSize();
  }

  private class ResetElevationAnimation extends ShadowAnimatorImpl {
    ResetElevationAnimation() {}

    @Override
    protected float getTargetShadowSize() {
      return elevation;
    }
  }

  private class ElevateToHoveredFocusedTranslationZAnimation extends ShadowAnimatorImpl {
    ElevateToHoveredFocusedTranslationZAnimation() {}

    @Override
    protected float getTargetShadowSize() {
      return elevation + hoveredFocusedTranslationZ;
    }
  }

  private class ElevateToPressedTranslationZAnimation extends ShadowAnimatorImpl {
    ElevateToPressedTranslationZAnimation() {}

    @Override
    protected float getTargetShadowSize() {
      return elevation + pressedTranslationZ;
    }
  }

  private class DisabledElevationAnimation extends ShadowAnimatorImpl {
    DisabledElevationAnimation() {}

    @Override
    protected float getTargetShadowSize() {
      return 0f;
    }
  }

  private boolean shouldAnimateVisibilityChange() {
    return ViewCompat.isLaidOut(view) && !view.isInEditMode();
  }

  void updateFromViewRotation() {
    if (Build.VERSION.SDK_INT == 19) {
      
      
      if ((rotation % 90) != 0) {
        if (view.getLayerType() != View.LAYER_TYPE_SOFTWARE) {
          view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
      } else {
        if (view.getLayerType() != View.LAYER_TYPE_NONE) {
          view.setLayerType(View.LAYER_TYPE_NONE, null);
        }
      }
    }

    
    if (shapeDrawable != null) {
      shapeDrawable.setShadowCompatRotation((int) rotation);
    }
  }
}
