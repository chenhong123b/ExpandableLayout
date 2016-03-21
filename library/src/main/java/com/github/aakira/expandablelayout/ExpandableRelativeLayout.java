package com.github.aakira.expandablelayout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;

public class ExpandableRelativeLayout extends RelativeLayout implements ExpandableLayout {

    private int duration;
    private boolean isExpanded;
    private TimeInterpolator interpolator = new LinearInterpolator();
    private int orientation;
    /**
     * You cannot define {@link #isExpanded}, {@link #defaultChildIndex}
     * and {@link #defaultPosition} at the same time.
     * {@link #defaultPosition} has priority over {@link #isExpanded}
     * and {@link #defaultChildIndex} if you set them at the same time.
     * <p/>
     * <p/>
     * Priority
     * {@link #defaultPosition} > {@link #defaultChildIndex} > {@link #isExpanded}
     */
    private int defaultChildIndex;
    private int defaultPosition;
    /**
     * The close position is width from left of layout if orientation is horizontal.
     * The close position is height from top of layout if orientation is vertical.
     */
    private int closePosition = 0;

    private ExpandableLayoutListener listener;
    private ExpandableSavedState savedState;
    private int layoutSize = 0;
    private boolean isArranged = false;
    private boolean isCalculatedSize = false;
    private boolean isAnimating = false;
    /**
     * view size of children
     **/
    private List<Integer> childSizeList = new ArrayList<>();
    /**
     * view position top or left of children
     **/
    private List<Integer> childPositionList = new ArrayList<>();

    public ExpandableRelativeLayout(final Context context) {
        this(context, null);
    }

    public ExpandableRelativeLayout(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExpandableRelativeLayout(final Context context, final AttributeSet attrs,
                                    final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ExpandableRelativeLayout(final Context context, final AttributeSet attrs,
                                    final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr);
    }

    private void init(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.expandableLayout, defStyleAttr, 0);
        duration = a.getInteger(R.styleable.expandableLayout_ael_duration, DEFAULT_DURATION);
        isExpanded = a.getBoolean(R.styleable.expandableLayout_ael_expanded, DEFAULT_EXPANDED);
        orientation = a.getInteger(R.styleable.expandableLayout_ael_orientation, VERTICAL);
        defaultChildIndex = a.getInteger(R.styleable.expandableLayout_ael_defaultChildIndex,
                Integer.MAX_VALUE);
        defaultPosition = a.getDimensionPixelSize(R.styleable.expandableLayout_ael_defaultPosition,
                Integer.MIN_VALUE);
        final int interpolatorType = a.getInteger(R.styleable.expandableLayout_ael_interpolator,
                Utils.LINEAR_INTERPOLATOR);
        interpolator = Utils.createInterpolator(interpolatorType);
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (isCalculatedSize) return;
        // calculate a size of children
        childSizeList.clear();
        View view;
        LayoutParams params;
        for (int i = 0; i < getChildCount(); i++) {
            view = getChildAt(i);
            params = (LayoutParams) view.getLayoutParams();

            childSizeList.add(isVertical()
                    ? view.getMeasuredHeight() + params.topMargin + params.bottomMargin
                    : view.getMeasuredWidth() + params.leftMargin + params.rightMargin);
        }
        isCalculatedSize = true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (isArranged) return;
        layoutSize = getCurrentPosition();

        childPositionList.clear();
        // calculate a top position of children
        for (int i = 0; i < getChildCount(); i++) {
            childPositionList.add((int) (isVertical() ? getChildAt(i).getY() : getChildAt(i).getX()));
        }

        // adjust default position if a user set a value.
        if (!isExpanded) {
            setLayoutSize(closePosition);
        }
        final int childNumbers = childSizeList.size();
        if (childNumbers > defaultChildIndex && childNumbers > 0) {
            moveChild(defaultChildIndex, 0, null);
        }
        if (defaultPosition > 0 && layoutSize >= defaultPosition && layoutSize > 0) {
            move(defaultPosition, 0, null);
        }
        isArranged = true;

        if (savedState == null) return;
        setLayoutSize(savedState.getSize());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable parcelable = super.onSaveInstanceState();
        final ExpandableSavedState ss = new ExpandableSavedState(parcelable);
        ss.setSize(getCurrentPosition());
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        if (!(state instanceof ExpandableSavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        final ExpandableSavedState ss = (ExpandableSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        savedState = ss;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setListener(@NonNull ExpandableLayoutListener listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toggle() {
        if (closePosition < getCurrentPosition()) {
            collapse();
        } else {
            expand();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expand() {
        if (isAnimating) return;

        createExpandAnimator(getCurrentPosition(), layoutSize, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expand(final long duration, final @Nullable TimeInterpolator interpolator) {
        if (isAnimating) return;

        if (duration == 0) {
            move(layoutSize, duration, interpolator);
            return;
        }
        createExpandAnimator(getCurrentPosition(), layoutSize, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collapse() {
        if (isAnimating) return;

        createExpandAnimator(getCurrentPosition(), closePosition, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collapse(final long duration, final @Nullable TimeInterpolator interpolator) {
        if (isAnimating) return;

        if (duration == 0) {
            move(closePosition, duration, interpolator);
            return;
        }
        createExpandAnimator(getCurrentPosition(), closePosition, duration, interpolator).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initLayout(final boolean isMaintain) {
        closePosition = 0;
        layoutSize = 0;
        isArranged = isMaintain;
        isCalculatedSize = false;
        savedState = null;

        super.requestLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDuration(final int duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("Animators cannot have negative duration: " +
                    duration);
        }
        this.duration = duration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
        isArranged = false;
        requestLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isExpanded() {
        return isExpanded;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInterpolator(@NonNull final TimeInterpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * @param position
     *
     * @see #move(int, long, TimeInterpolator)
     */
    public void move(int position) {
        move(position, duration, interpolator);
    }

    /**
     * Moves to position.
     * Sets 0 to {@param duration} if you want to move immediately.
     *
     * @param position
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    public void move(int position, long duration, @Nullable TimeInterpolator interpolator) {
        if (isAnimating || 0 > position || layoutSize < position) return;

        if (duration == 0) {
            setLayoutSize(position);
            requestLayout();
            return;
        }
        createExpandAnimator(getCurrentPosition(), position, duration,
                interpolator == null ? this.interpolator : interpolator).start();
    }

    /**
     * @param index child view index
     *
     * @see #moveChild(int, long, TimeInterpolator)
     */
    public void moveChild(int index) {
        moveChild(index, duration, interpolator);
    }

    /**
     * Moves to bottom(VERTICAL) or right(HORIZONTAL) of child view
     * Sets 0 to {@param duration} if you want to move immediately.
     *
     * @param index        index child view index
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    public void moveChild(int index, long duration, @Nullable TimeInterpolator interpolator) {
        if (isAnimating) return;

        final int destination = getChildPosition(index) +
                (isVertical() ? getPaddingBottom() : getPaddingRight());
        if (duration == 0) {
            setLayoutSize(destination);
            requestLayout();
            return;
        }
        createExpandAnimator(getCurrentPosition(), destination,
                duration, interpolator == null ? this.interpolator : interpolator).start();
    }

    /**
     * Sets orientation of expanse animation.
     *
     * @param orientation Set 0 if orientation is horizontal, 1 if orientation is vertical
     */
    public void setOrientation(@Orientation final int orientation) {
        this.orientation = orientation;
    }

    /**
     * Gets the width from left of layout if orientation is horizontal.
     * Gets the height from top of layout if orientation is vertical.
     *
     * @param index index of child view
     *
     * @return position from top or left
     */
    public int getChildPosition(final int index) {
        if (0 > index || childSizeList.size() <= index) {
            throw new IllegalArgumentException("There aren't the view having this index.");
        }
        return childPositionList.get(index) + childSizeList.get(index);
    }

    /**
     * Gets the width from left of layout if orientation is horizontal.
     * Gets the height from top of layout if orientation is vertical.
     *
     * @return
     *
     * @see #closePosition
     */
    public int getClosePosition() {
        return closePosition;
    }

    /**
     * Sets the close position directly.
     *
     * @param position
     *
     * @see #closePosition
     * @see #setClosePositionIndex(int)
     */
    public void setClosePosition(final int position) {
        this.closePosition = position;
    }

    /**
     * Gets the current position.
     *
     * @return
     */
    public int getCurrentPosition() {
        return isVertical() ? getMeasuredHeight() : getMeasuredWidth();
    }

    /**
     * Sets close position using index of child view.
     *
     * @param childIndex
     *
     * @see #closePosition
     * @see #setClosePosition(int)
     */
    public void setClosePositionIndex(final int childIndex) {
        this.closePosition = getChildPosition(childIndex);
    }

    private boolean isVertical() {
        return orientation == VERTICAL;
    }

    private void setLayoutSize(int size) {
        if (isVertical()) {
            getLayoutParams().height = size;
        } else {
            getLayoutParams().width = size;
        }
    }

    /**
     * Creates value animator.
     * Expand the layout if {@param to} is bigger than {@param from}.
     * Collapse the layout if {@param from} is bigger than {@param to}.
     *
     * @param from
     * @param to
     * @param duration
     * @param interpolator
     *
     * @return
     */
    private ValueAnimator createExpandAnimator(
            final int from, final int to, final long duration, final TimeInterpolator interpolator) {
        final ValueAnimator valueAnimator = ValueAnimator.ofInt(from, to);
        valueAnimator.setDuration(duration);
        valueAnimator.setInterpolator(interpolator);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animator) {
                if (isVertical()) {
                    getLayoutParams().height = (int) animator.getAnimatedValue();
                } else {
                    getLayoutParams().width = (int) animator.getAnimatedValue();
                }
                requestLayout();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                isAnimating = true;
                if (listener == null) {
                    return;
                }
                listener.onAnimationStart();

                if (layoutSize == to) {
                    listener.onPreOpen();
                    return;
                }
                if (closePosition == to) {
                    listener.onPreClose();
                }
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                isAnimating = false;
                final int currentSize = isVertical()
                        ? getLayoutParams().height : getLayoutParams().width;
                isExpanded = currentSize > closePosition;

                if (listener == null) {
                    return;
                }
                listener.onAnimationEnd();

                if (currentSize == layoutSize) {
                    listener.onOpened();
                    return;
                }
                if (currentSize == closePosition) {
                    listener.onClosed();
                }
            }
        });
        return valueAnimator;
    }
}