

package com.zeoflow.material.elements.transition;

import androidx.transition.Transition;

abstract class TransitionListenerAdapter implements Transition.TransitionListener {

  @Override
  public void onTransitionStart(Transition transition) {}

  @Override
  public void onTransitionEnd(Transition transition) {}

  @Override
  public void onTransitionCancel(Transition transition) {}

  @Override
  public void onTransitionPause(Transition transition) {}

  @Override
  public void onTransitionResume(Transition transition) {}
}
