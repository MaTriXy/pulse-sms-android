/*
 * Copyright (C) 2016 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger.util;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import xyz.klinker.messenger.R;

/**
 * Helper for handling all animations such as expanding and contracting conversations so that we
 * can keep those classes cleaner.
 */
public class AnimationUtil {

    private static final int EXPAND_CONVERSATION_DURATION = 150;
    private static final int PERIPHERAL_DURATION = EXPAND_CONVERSATION_DURATION;

    private static int originalRecyclerHeight = -1;
    private static int originalFragmentContainerHeight = -1;

    /**
     * Animates a lines item to the full height of the view.
     *
     * @param itemView the item to animate.
     */
    public static void expandConversationListItem(View itemView) {
        int extraExpand = itemView.getResources()
                .getDimensionPixelSize(R.dimen.extra_expand_distance);
        animateConversationListItem(itemView, 0, itemView.getRootView().getHeight(),
                0, (int) (-1 * (itemView.getHeight() + itemView.getY() + extraExpand)),
                new AccelerateInterpolator());
    }

    /**
     * Animates a line item back to its original height.
     *
     * @param itemView the item to animate.
     */
    public static void contractConversationListItem(View itemView) {
        final RecyclerView recyclerView = (RecyclerView) itemView.getParent();
        AnimationUtil.animateConversationListItem(itemView, itemView.getRootView().getHeight(), 0,
                (int) recyclerView.getTranslationY(), 0,
                new DecelerateInterpolator());
    }

    /**
     * Animates a conversation list item from the ConversationListAdapter from the bottom so that
     * it fills the whole screen, giving the impression that the conversation is coming out of this
     * item.
     *
     * @param itemView the view holder item view.
     * @param fromBottom the starting bottom margin for the view.
     * @param toBottom the ending bottom margin for the view.
     * @param startY the starting y position for the view.
     * @param translateY the amount to move the view in the Y direction.
     * @param interpolator the interpolator to animate with.
     */
    private static void animateConversationListItem(final View itemView,
                                                   final int fromBottom, final int toBottom,
                                                   final int startY, final int translateY,
                                                   Interpolator interpolator) {
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                itemView.getLayoutParams();

        ValueAnimator animator = ValueAnimator.ofInt(fromBottom, toBottom);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                params.bottomMargin = (int) valueAnimator.getAnimatedValue();
                itemView.requestLayout();
            }
        });
        animator.setInterpolator(interpolator);
        animator.setDuration(EXPAND_CONVERSATION_DURATION);
        animator.start();

        final RecyclerView recyclerView = (RecyclerView) itemView.getParent();
        final ViewGroup.MarginLayoutParams recyclerParams = (ViewGroup.MarginLayoutParams)
                recyclerView.getLayoutParams();

        if (originalRecyclerHeight == -1) {
            originalRecyclerHeight = recyclerView.getHeight();
        }

        ValueAnimator recyclerAnimator = ValueAnimator.ofInt(startY, translateY);
        recyclerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                recyclerView.setTranslationY((int) valueAnimator.getAnimatedValue());
                recyclerParams.height = originalRecyclerHeight +
                        (-1 * (int) valueAnimator.getAnimatedValue());
                recyclerView.requestLayout();
            }
        });
        recyclerAnimator.setInterpolator(interpolator);
        recyclerAnimator.setDuration(EXPAND_CONVERSATION_DURATION);
        recyclerAnimator.start();
    }

    /**
     * Expands the activity peripherals to be off of the screen so that the new conversation can
     * fill this entire space instead. This includes 3 different pieces:
     *
     * 1. Raise the toolbar off the top of the screen.
     * 2. Raise the fragment container to the top of the screen and expand the height so that it
     *    stays matching the bottom.
     * 3. Lower the FAB off the bottom.
     *
     * @param activity the activity to find the views at.
     */
    public static void expandActivityForConversation(Activity activity) {
        View toolbar = activity.findViewById(R.id.app_bar_layout);
        View fragmentContainer = activity.findViewById(R.id.conversation_list_container);
        View fab = activity.findViewById(R.id.fab);

        int extraDistance = activity.getResources()
                .getDimensionPixelSize(R.dimen.extra_expand_distance);
        int toolbarTranslate = -1 * (toolbar.getHeight() + extraDistance);
        int fragmentContainerTranslate = toolbarTranslate;
        int fabTranslate = fab.getHeight() + extraDistance +
                activity.getResources().getDimensionPixelSize(R.dimen.fab_margin);

        animateActivityWithConversation(toolbar, fragmentContainer, fab,
                toolbarTranslate, 0, fragmentContainerTranslate, fabTranslate,
                new AccelerateInterpolator());
    }

    /**
     * Contracts the activity so that the original peripherals are shown again. This includes 3
     * pieces:
     *
     * 1. Lower the toolbar back to it's original spot under the status bar
     * 2. Lower the top of the fragment container to under the toolbar and contract it's height so
     *    that it stays matching the bottom.
     * 3. Raise the FAB back onto the screen.
     *
     * @param activity the activity to find the views in.
     */
    public static void contractActivityFromConversation(Activity activity) {
        View toolbar = activity.findViewById(R.id.app_bar_layout);
        View fragmentContainer = activity.findViewById(R.id.conversation_list_container);
        View fab = activity.findViewById(R.id.fab);

        animateActivityWithConversation(toolbar, fragmentContainer, fab, 0,
                (int) fragmentContainer.getTranslationY(), 0, 0,
                new DecelerateInterpolator());
    }

    /**
     * Animates peripheral items on the screen to a given ending point.
     *
     * @param toolbar the toolbar to animate.
     * @param fragmentContainer the fragment container to animate.
     * @param fab the floating action button to animate.
     * @param toolbarTranslate the distance to translate the toolbar.
     * @param containerStart the start point of the container.
     * @param containerTranslate the distance the container should translate.
     * @param fabTranslate the distance the fab should translate.
     * @param interpolator the interpolator to use.
     */
    private static void animateActivityWithConversation(View toolbar, final View fragmentContainer,
                                                        View fab, int toolbarTranslate,
                                                        int containerStart, int containerTranslate,
                                                        int fabTranslate,
                                                        Interpolator interpolator) {
        toolbar.animate().translationY(toolbarTranslate)
                .setDuration(PERIPHERAL_DURATION)
                .setInterpolator(interpolator)
                .setListener(null);

        final ViewGroup.MarginLayoutParams containerParams = (ViewGroup.MarginLayoutParams)
                fragmentContainer.getLayoutParams();

        if (originalFragmentContainerHeight == -1) {
            originalFragmentContainerHeight = fragmentContainer.getHeight();
        }

        ValueAnimator containerAnimator = ValueAnimator.ofInt(containerStart, containerTranslate);
        containerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                fragmentContainer.setTranslationY((int) valueAnimator.getAnimatedValue());
                containerParams.height = originalFragmentContainerHeight +
                        (-1 * (int) valueAnimator.getAnimatedValue());
                fragmentContainer.requestLayout();
            }
        });
        containerAnimator.setInterpolator(interpolator);
        containerAnimator.setDuration(PERIPHERAL_DURATION);
        containerAnimator.start();

        fab.animate().translationY(fabTranslate)
                .setDuration(PERIPHERAL_DURATION)
                .setInterpolator(interpolator)
                .setListener(null);
    }

}
