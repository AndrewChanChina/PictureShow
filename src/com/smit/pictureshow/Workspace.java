/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smit.pictureshow;

import java.util.ArrayList;

import com.smit.pictureshow.R;

import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Scroller;
import android.widget.TextView;

/**
 * The workspace is a wide area with a wallpaper and a finite number of screens.
 * Each screen contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 */
public class Workspace extends ViewGroup implements DropTarget, DragSource,
		DragScroller {
	@SuppressWarnings( { "UnusedDeclaration" })
	private static final String TAG = "Launcher.Workspace";
	private static final int INVALID_SCREEN = -1;

	/**
	 * The velocity at which a fling gesture will cause us to snap to the next
	 * screen
	 */
	private static final int SNAP_VELOCITY = 1000;

	private final WallpaperManager mWallpaperManager;

	private int mDefaultScreen;

	private boolean mFirstLayout = true;

	private int mCurrentScreen;
	private int mNextScreen = INVALID_SCREEN;
	private Scroller mScroller;
	private VelocityTracker mVelocityTracker;

	/**
	 * CellInfo for the cell that is currently being dragged
	 */
	private CellLayout.CellInfo mDragInfo;

	/**
	 * Target drop area calculated during last acceptDrop call.
	 */
	private int[] mTargetCell = null;

	private float mLastMotionX;
	private float mLastMotionY;

	private final static int TOUCH_STATE_REST = 0;
	private final static int TOUCH_STATE_SCROLLING = 1;

	private int mTouchState = TOUCH_STATE_REST;

	private OnLongClickListener mLongClickListener;

	private Launcher mLauncher;
	private IconCache mIconCache;
	private DragController mDragController;

	/**
	 * Cache of vacant cells, used during drag events and invalidated as needed.
	 */
	private CellLayout.CellInfo mVacantCache = null;

	private int[] mTempCell = new int[2];
	private int[] mTempEstimate = new int[2];

	private boolean mAllowLongPress = true;

	private int mTouchSlop;
	private int mMaximumVelocity;

	/*
	 * private Drawable mPreviousIndicator; private Drawable mNextIndicator;
	 */// mod:lsj

	/**
	 * Used to inflate the Workspace from XML.
	 * 
	 * @param context
	 *            The application's context.
	 * @param attrs
	 *            The attribtues set containing the Workspace's customization
	 *            values.
	 */
	public Workspace(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	/**
	 * Used to inflate the Workspace from XML.
	 * 
	 * @param context
	 *            The application's context.
	 * @param attrs
	 *            The attribtues set containing the Workspace's customization
	 *            values.
	 * @param defStyle
	 *            Unused.
	 */
	public Workspace(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		mWallpaperManager = WallpaperManager.getInstance(context);

		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.Workspace, defStyle, 0);
		mDefaultScreen = a.getInt(R.styleable.Workspace_defaultScreen, 1);
		a.recycle();

		setHapticFeedbackEnabled(false);
		initWorkspace();
	}

	/**
	 * Initializes various states for this workspace.
	 */
	private void initWorkspace() {
		// Log.i("========", "====initWorkspace======");
		Context context = getContext();
		mScroller = new Scroller(context);
		mCurrentScreen = mDefaultScreen;
		Launcher.setScreen(mCurrentScreen);
		LauncherApplication app = (LauncherApplication) context
				.getApplicationContext();
		mIconCache = app.getIconCache();

		final ViewConfiguration configuration = ViewConfiguration
				.get(getContext());
		mTouchSlop = configuration.getScaledTouchSlop();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

	}

	@Override
	public void addView(View child, int index, LayoutParams params) {
		if (!(child instanceof CellLayout)) {
			throw new IllegalArgumentException(
					"A Workspace can only have CellLayout children.");
		}
		super.addView(child, index, params);
	}

	@Override
	public void addView(View child) {
		if (!(child instanceof CellLayout)) {
			throw new IllegalArgumentException(
					"A Workspace can only have CellLayout children.");
		}
		super.addView(child);

	}

	@Override
	public void addView(View child, int index) {
		if (!(child instanceof CellLayout)) {
			throw new IllegalArgumentException(
					"A Workspace can only have CellLayout children.");
		}
		super.addView(child, index);
	}

	@Override
	public void addView(View child, int width, int height) {
		if (!(child instanceof CellLayout)) {
			throw new IllegalArgumentException(
					"A Workspace can only have CellLayout children.");
		}
		super.addView(child, width, height);
	}

	@Override
	public void addView(View child, LayoutParams params) {
		if (!(child instanceof CellLayout)) {
			throw new IllegalArgumentException(
					"A Workspace can only have CellLayout children.");
		}
		super.addView(child, params);
	}

	boolean isDefaultScreenShowing() { //默认显示第几屏在workspace的xml属性中设置
		return mCurrentScreen == mDefaultScreen;
	}

	/**
	 * Returns the index of the currently displayed screen.
	 * 
	 * @return The index of the currently displayed screen.
	 */
	int getCurrentScreen() {
		return mCurrentScreen;
	}

	/**
	 * Sets the current screen.
	 * 
	 * @param currentScreen
	 */
	void setCurrentScreen(int currentScreen) {
		if (!mScroller.isFinished())
			mScroller.abortAnimation();
		clearVacantCache();
		mCurrentScreen = Math.max(0, Math.min(currentScreen,
				getChildCount() - 1));
		/*
		 * mPreviousIndicator.setLevel(mCurrentScreen);
		 * mNextIndicator.setLevel(mCurrentScreen);
		 */// mod:lsj
		scrollTo(mCurrentScreen * getWidth(), 0);
		invalidate();
	}

	/**
	 * Adds the specified child in the current screen. The position and
	 * dimension of the child are defined by x, y, spanX and spanY.
	 * 
	 * @param child
	 *            The child to add in one of the workspace's screens.
	 * @param x
	 *            The X position of the child in the screen's grid.
	 * @param y
	 *            The Y position of the child in the screen's grid.
	 * @param spanX
	 *            The number of cells spanned horizontally by the child.
	 * @param spanY
	 *            The number of cells spanned vertically by the child.
	 */
	void addInCurrentScreen(View child, int x, int y, int spanX, int spanY) {
		addInScreen(child, mCurrentScreen, x, y, spanX, spanY, false);
	}

	/**
	 * Adds the specified child in the current screen. The position and
	 * dimension of the child are defined by x, y, spanX and spanY.
	 * 
	 * @param child
	 *            The child to add in one of the workspace's screens.
	 * @param x
	 *            The X position of the child in the screen's grid.
	 * @param y
	 *            The Y position of the child in the screen's grid.
	 * @param spanX
	 *            The number of cells spanned horizontally by the child.
	 * @param spanY
	 *            The number of cells spanned vertically by the child.
	 * @param insert
	 *            When true, the child is inserted at the beginning of the
	 *            children list.
	 */
	void addInCurrentScreen(View child, int x, int y, int spanX, int spanY,
			boolean insert) {
		addInScreen(child, mCurrentScreen, x, y, spanX, spanY, insert);
	}

	/**
	 * Adds the specified child in the specified screen. The position and
	 * dimension of the child are defined by x, y, spanX and spanY.
	 * 
	 * @param child
	 *            The child to add in one of the workspace's screens.
	 * @param screen
	 *            The screen in which to add the child.
	 * @param x
	 *            The X position of the child in the screen's grid.
	 * @param y
	 *            The Y position of the child in the screen's grid.
	 * @param spanX
	 *            The number of cells spanned horizontally by the child.
	 * @param spanY
	 *            The number of cells spanned vertically by the child.
	 */
	void addInScreen(View child, int screen, int x, int y, int spanX, int spanY) {
		addInScreen(child, screen, x, y, spanX, spanY, false);
	}

	/**
	 * Adds the specified child in the specified screen. The position and
	 * dimension of the child are defined by x, y, spanX and spanY.
	 * 
	 * @param child
	 *            The child to add in one of the workspace's screens.
	 * @param screen
	 *            The screen in which to add the child.
	 * @param x
	 *            The X position of the child in the screen's grid.
	 * @param y
	 *            The Y position of the child in the screen's grid.
	 * @param spanX
	 *            The number of cells spanned horizontally by the child.
	 * @param spanY
	 *            The number of cells spanned vertically by the child.
	 * @param insert
	 *            When true, the child is inserted at the beginning of the
	 *            children list.
	 */
	void addInScreen(View child, int screen, int x, int y, int spanX,
			int spanY, boolean insert) {
		if (screen < 0 || screen >= getChildCount()) {
			throw new IllegalStateException("The screen must be >= 0 and < "
					+ getChildCount());
		}

		clearVacantCache();

		final CellLayout group = (CellLayout) getChildAt(screen);
		CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child
				.getLayoutParams();
		if (lp == null) {
			lp = new CellLayout.LayoutParams(x, y, spanX, spanY);
		} else {
			lp.cellX = x;
			lp.cellY = y;
			lp.cellHSpan = spanX;
			lp.cellVSpan = spanY;
		}
		group.addView(child, insert ? 0 : -1, lp); //cellLayout中添加view
		// if (!(child instanceof Folder)) {
		child.setHapticFeedbackEnabled(false);
		child.setOnLongClickListener(mLongClickListener);
		// }
		if (child instanceof DropTarget) {
			mDragController.addDropTarget((DropTarget) child);
		}
	}

	void addWidget(View view, Widget widget) {
		addInScreen(view, widget.screen, widget.cellX, widget.cellY,
				widget.spanX, widget.spanY, false);
	}

	void addWidget(View view, Widget widget, boolean insert) {
		addInScreen(view, widget.screen, widget.cellX, widget.cellY,
				widget.spanX, widget.spanY, insert);
	}

	CellLayout.CellInfo findAllVacantCells(boolean[] occupied) {
		CellLayout group = (CellLayout) getChildAt(mCurrentScreen);
		if (group != null) {
			return group.findAllVacantCells(occupied, null);
		}
		return null;
	}

	private void clearVacantCache() {
		if (mVacantCache != null) {
			mVacantCache.clearVacantCells();
			mVacantCache = null;
		}
	}

	/**
	 * Registers the specified listener on each screen contained in this
	 * workspace.
	 * 
	 * @param l
	 *            The listener used to respond to long clicks.
	 */
	@Override
	public void setOnLongClickListener(OnLongClickListener l) {
		// Log.i("======Workspace=====", "=====setOnLongClickListener======");
		mLongClickListener = l;
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			getChildAt(i).setOnLongClickListener(l);
		}
	}

	private void updateWallpaperOffset() {
		updateWallpaperOffset(getChildAt(getChildCount() - 1).getRight()
				- (mRight - mLeft));
	}

	private void updateWallpaperOffset(int scrollRange) {
		mWallpaperManager.setWallpaperOffsetSteps(1.0f / (getChildCount() - 1),
				0);
		mWallpaperManager.setWallpaperOffsets(getWindowToken(), mScrollX
				/ (float) scrollRange, 0);
	}

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			mScrollX = mScroller.getCurrX();
			mScrollY = mScroller.getCurrY();
			updateWallpaperOffset();
			postInvalidate();
		} else if (mNextScreen != INVALID_SCREEN) {
			mCurrentScreen = Math.max(0, Math.min(mNextScreen,
					getChildCount() - 1));
			// mPreviousIndicator.setLevel(mCurrentScreen);
			// mNextIndicator.setLevel(mCurrentScreen);//mod:lsj
			Launcher.setScreen(mCurrentScreen);
			mNextScreen = INVALID_SCREEN;
			clearChildrenCache();
		}
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		boolean restore = false;
		int restoreCount = 0;

		// ViewGroup.dispatchDraw() supports many features we don't need:
		// clip to padding, layout animation, animation listener, disappearing
		// children, etc. The following implementation attempts to fast-track
		// the drawing dispatch by drawing only what we know needs to be drawn.

		boolean fastDraw = mTouchState != TOUCH_STATE_SCROLLING
				&& mNextScreen == INVALID_SCREEN;
		// If we are not scrolling or flinging, draw only the current screen
		if (fastDraw) {
			drawChild(canvas, getChildAt(mCurrentScreen), getDrawingTime());
		} else {
			final long drawingTime = getDrawingTime();
			// If we are flinging, draw only the current screen and the target
			// screen
			if (mNextScreen >= 0 && mNextScreen < getChildCount()
					&& Math.abs(mCurrentScreen - mNextScreen) == 1) {
				drawChild(canvas, getChildAt(mCurrentScreen), drawingTime);
				drawChild(canvas, getChildAt(mNextScreen), drawingTime);
			} else {
				// If we are scrolling, draw all of our children
				final int count = getChildCount();
				for (int i = 0; i < count; i++) {
					drawChild(canvas, getChildAt(i), drawingTime);
				}
			}
		}

		if (restore) {
			canvas.restoreToCount(restoreCount);
		}
	}

	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mDragController.setWindowToken(getWindowToken());
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		final int width = MeasureSpec.getSize(widthMeasureSpec);
		/*
		 * final int widthMode = MeasureSpec.getMode(widthMeasureSpec); if
		 * (widthMode != MeasureSpec.EXACTLY) { throw new
		 * IllegalStateException("Workspace can only be used in EXACTLY mode.");
		 * }
		 * 
		 * final int heightMode = MeasureSpec.getMode(heightMeasureSpec); if
		 * (heightMode != MeasureSpec.EXACTLY) { throw new
		 * IllegalStateException("Workspace can only be used in EXACTLY mode.");
		 * }
		 */
		// The children are given the same width and height as the workspace
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
		}

		if (mFirstLayout) {
			setHorizontalScrollBarEnabled(false);
			scrollTo(mCurrentScreen * width, 0);
			setHorizontalScrollBarEnabled(true);
			updateWallpaperOffset(width * (getChildCount() - 1));
			mFirstLayout = false;
		}
		// Log.i("==Workspace===", "======onMeasure====");
	}

	@Override
	protected int getSuggestedMinimumHeight() {
		// TODO Auto-generated method stub
		return super.getSuggestedMinimumHeight();
	}

	@Override
	protected int getSuggestedMinimumWidth() {
		// TODO Auto-generated method stub
		return super.getSuggestedMinimumWidth();
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		int childLeft = 0;

		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			if (child.getVisibility() != View.GONE) {
				final int childWidth = child.getMeasuredWidth();
				child.layout(childLeft, 0, childLeft + childWidth, child
						.getMeasuredHeight());
				childLeft += childWidth;
			}
		}
	}

	@Override
	public boolean requestChildRectangleOnScreen(View child, Rect rectangle,
			boolean immediate) {
		int screen = indexOfChild(child);
		if (screen != mCurrentScreen || !mScroller.isFinished()) {
			if (!mLauncher.isWorkspaceLocked()) {
				snapToScreen(screen, false);
			}
			return true;
		}
		return false;
	}

	@Override
	protected boolean onRequestFocusInDescendants(int direction,
			Rect previouslyFocusedRect) {
		int focusableScreen;
		if (mNextScreen != INVALID_SCREEN) {
			focusableScreen = mNextScreen;
		} else {
			focusableScreen = mCurrentScreen;
		}
		getChildAt(focusableScreen).requestFocus(direction,
				previouslyFocusedRect);
		// }
		return false;
	}

	@Override
	public boolean dispatchUnhandledMove(View focused, int direction) {
		Log.i("======workspace===dispatchUnhandledMove=", "==");
		if (direction == View.FOCUS_LEFT) {
			if (getCurrentScreen() > 0) {
				snapToScreen(getCurrentScreen() - 1, false);
				return true;
			}
		} else if (direction == View.FOCUS_RIGHT) {
			if (getCurrentScreen() < getChildCount() - 1) {
				snapToScreen(getCurrentScreen() + 1, false);
				return true;
			}
		}
		return super.dispatchUnhandledMove(focused, direction);
	}

	@Override
	public void addFocusables(ArrayList<View> views, int direction,
			int focusableMode) {
		getChildAt(mCurrentScreen).addFocusables(views, direction);
		if (direction == View.FOCUS_LEFT) {
			if (mCurrentScreen > 0) {

				getChildAt(mCurrentScreen - 1).addFocusables(views, direction);
			}
		} else if (direction == View.FOCUS_RIGHT) {
			if (mCurrentScreen < getChildCount() - 1) {

				getChildAt(mCurrentScreen + 1).addFocusables(views, direction);
			}
		}
		/*
		 * else { openFolder.addFocusables(views, direction); }
		 */
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		// Log.i("======dispatchTouchEvent=====",
		// "=====setOnLongClickListener======");
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			if (mLauncher.isWorkspaceLocked()) {
				return false;
			}
			// Log.i("===workspace===dispatchTouchEvent=====",
			// "=====ACTION_DOWN======");
		} else if (ev.getAction() == MotionEvent.ACTION_UP) {
			// Log.i("===workspace===dispatchTouchEvent=====",
			// "=====ACTION_UP======");
		}
		return super.dispatchTouchEvent(ev);

	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		// Log.i("==worksapce====onInterceptTouchEvent=====",
		// "=====setOnLongClickListener======");
		final boolean workspaceLocked = mLauncher.isWorkspaceLocked();

		if (workspaceLocked) {
			// Log.i("======onInterceptTouchEvent=====",
			// "=====setOnLongClickListener====workspaceLocked==");
			return false; // We don't want the events. Let them fall through to
							// the all apps view.
		}

		/*
		 * This method JUST determines whether we want to intercept the motion.
		 * If we return true, onTouchEvent will be called and we do the actual
		 * scrolling there.
		 */

		/*
		 * Shortcut the most recurring case: the user is in the dragging state
		 * and he is moving his finger. We want to intercept this motion.
		 */
		final int action = ev.getAction();
		if ((action == MotionEvent.ACTION_MOVE)
				&& (mTouchState != TOUCH_STATE_REST)) {
			// Log.i("======onInterceptTouchEvent=====",
			// "=====setOnLongClickListener====(mTouchState != TOUCH_STATE_REST)==");
			return true;
		}

		final float x = ev.getX();
		final float y = ev.getY();

		switch (action) {
		case MotionEvent.ACTION_MOVE:
			/*
			 * mIsBeingDragged == false, otherwise the shortcut would have
			 * caught it. Check whether the user has moved far enough from his
			 * original down touch.
			 */

			/*
			 * Locally do absolute value. mLastMotionX is set to the y value of
			 * the down event.
			 */
			final int xDiff = (int) Math.abs(x - mLastMotionX);
			final int yDiff = (int) Math.abs(y - mLastMotionY);

			final int touchSlop = mTouchSlop;
			boolean xMoved = xDiff > touchSlop;
			boolean yMoved = yDiff > touchSlop;

			if (xMoved || yMoved) {

				if (xMoved) {
					// Scroll if the user moved far enough along the X axis
					mTouchState = TOUCH_STATE_SCROLLING;
					enableChildrenCache(mCurrentScreen - 1, mCurrentScreen + 1);
				}
				// Either way, cancel any pending longpress
				if (mAllowLongPress) {
					mAllowLongPress = false;
					// Try canceling the long press. It could also have been
					// scheduled
					// by a distant descendant, so use the mAllowLongPress flag
					// to block
					// everything
					final View currentScreen = getChildAt(mCurrentScreen);
					currentScreen.cancelLongPress();
				}
			}
			break;

		case MotionEvent.ACTION_DOWN:
			// Remember location of down touch
			mLastMotionX = x;
			mLastMotionY = y;
			mAllowLongPress = true;

			/*
			 * If being flinged and user touches the screen, initiate drag;
			 * otherwise don't. mScroller.isFinished should be false when being
			 * flinged.
			 */
			// Log.i("===workspace===onInterceptTouchEvent=====",
			// "=========ACTION_DOWN==");
			mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST
					: TOUCH_STATE_SCROLLING;
			break;

		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
			// Log.i("===workspace===onInterceptTouchEvent=====",
			// "=========ACTION_UP==");
			if (mTouchState != TOUCH_STATE_SCROLLING) {
				final CellLayout currentScreen = (CellLayout) getChildAt(mCurrentScreen);
				if (!currentScreen.lastDownOnOccupiedCell()) {
					getLocationOnScreen(mTempCell);
					// Send a tap to the wallpaper if the last down was on empty
					// space
					mWallpaperManager.sendWallpaperCommand(getWindowToken(),
							"android.wallpaper.tap", mTempCell[0]
									+ (int) ev.getX(), mTempCell[1]
									+ (int) ev.getY(), 0, null);
				}
			}

			// Release the drag
			clearChildrenCache();
			mTouchState = TOUCH_STATE_REST;
			mAllowLongPress = false;
			break;
		}

		/*
		 * The only time we want to intercept motion events is if we are in the
		 * drag mode.
		 */
		// Log.i("===workspace===onInterceptTouchEvent=====",
		// "=====setOnLongClickListener====ACTION_DOWN==");
		return mTouchState != TOUCH_STATE_REST;  //如果是workspace上的“左右”滑动事件，则会被拦截下来自己处理
	}

	/**
	 * If one of our descendant views decides that it could be focused now, only
	 * pass that along if it's on the current screen.
	 * 
	 * This happens when live folders requery, and if they're off screen, they
	 * end up calling requestFocus, which pulls it on screen.
	 */
	@Override
	public void focusableViewAvailable(View focused) {
		View current = getChildAt(mCurrentScreen);
		View v = focused;
		while (true) {
			if (v == current) {
				super.focusableViewAvailable(focused);
				return;
			}
			if (v == this) {
				return;
			}
			ViewParent parent = v.getParent();
			if (parent instanceof View) {
				v = (View) v.getParent();
			} else {
				return;
			}
		}
	}

	void enableChildrenCache(int fromScreen, int toScreen) {
		if (fromScreen > toScreen) {
			fromScreen = toScreen;
			toScreen = fromScreen;
		}

		final int count = getChildCount();

		fromScreen = Math.max(fromScreen, 0);
		toScreen = Math.min(toScreen, count - 1);

		for (int i = fromScreen; i <= toScreen; i++) {
			final CellLayout layout = (CellLayout) getChildAt(i);
			layout.setChildrenDrawnWithCacheEnabled(true);
			layout.setChildrenDrawingCacheEnabled(true);
		}
	}

	void clearChildrenCache() {
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			final CellLayout layout = (CellLayout) getChildAt(i);
			layout.setChildrenDrawnWithCacheEnabled(false);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		// Log.i("===workspace===onTouchEvent=====",
		// "=====setOnLongClickListener======");
		if (mLauncher.isWorkspaceLocked()) {
			return false; // We don't want the events. Let them fall through to
							// the all apps view.
		}

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(ev);

		final int action = ev.getAction();
		final float x = ev.getX();

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			// Log.i("===workspace===onTouchEvent=====",
			// "=====ACTION_DOWN======");
			/*
			 * If being flinged and user touches, stop the fling. isFinished
			 * will be false if being flinged.
			 */
			if (!mScroller.isFinished()) {
				mScroller.abortAnimation();
			}

			// Remember where the motion event started
			mLastMotionX = x;
			break;
		case MotionEvent.ACTION_MOVE:
			if (mTouchState == TOUCH_STATE_SCROLLING) {
				// Scroll to follow the motion event
				final int deltaX = (int) (mLastMotionX - x);
				mLastMotionX = x;

				if (deltaX < 0) {
					if (mScrollX > 0) {
						scrollBy(Math.max(-mScrollX, deltaX), 0);
						updateWallpaperOffset();
					}
				} else if (deltaX > 0) {
					final int availableToScroll = getChildAt(
							getChildCount() - 1).getRight()
							- mScrollX - getWidth();
					if (availableToScroll > 0) {
						scrollBy(Math.min(availableToScroll, deltaX), 0);
						updateWallpaperOffset();
					}
				} else {
					awakenScrollBars();
				}
			}
			break;
		case MotionEvent.ACTION_UP:
			// Log.i("===workspace===onTouchEvent=====",
			// "======ACTION_UP=====");
			if (mTouchState == TOUCH_STATE_SCROLLING) {
				final VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
				int velocityX = (int) velocityTracker.getXVelocity();
				// Log.i("==workspace====onTouchEvent===",
				// "==velocityX==="+velocityX+"=SNAP_VELOCITY="+SNAP_VELOCITY+"==mCurrentScreen=="+mCurrentScreen);
				if (velocityX > SNAP_VELOCITY && mCurrentScreen > 0) {
					// Fling hard enough to move left
					// Log.i("==workspace====onTouchEvent===",
					// "======== Fling hard enough to move left====");
					snapToScreen(mCurrentScreen - 1, false);

				} else if (velocityX < -SNAP_VELOCITY
						&& mCurrentScreen < getChildCount() - 1) {
					// Fling hard enough to move right
					// Log.i("==workspace====onTouchEvent===",
					// "======== Fling hard enough to move right====");
					snapToScreen(mCurrentScreen + 1, false);
				} else {
					// Log.i("==workspace====onTouchEvent===",
					// "======== Fling hard enough to move snapToDestination====");
					snapToDestination();
				}

				if (mVelocityTracker != null) {
					mVelocityTracker.recycle();
					mVelocityTracker = null;
				}
			}
			mTouchState = TOUCH_STATE_REST;
			break;
		case MotionEvent.ACTION_CANCEL:
			mTouchState = TOUCH_STATE_REST;
		}
		return true;
	}

	private void snapToDestination() {
		final int screenWidth = getWidth();
		final int whichScreen = (mScrollX + (screenWidth / 2)) / screenWidth;
		snapToScreen(whichScreen, false);
	}

	/**
	 * 功能说明：WORKSPACE CELLLAYOUT 之间切换的时候分为平滑移动和点击五分屏按钮切换，如果是五分屏按钮，则切换屏速度变快
	 * 
	 * @param whichScreen
	 *            切换目的屏
	 * @param onclick
	 *            是否点击五分屏
	 */
	void snapToScreen(int whichScreen, boolean onclick) {
		// if (!mScroller.isFinished()) return;
		whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() - 1));

		clearVacantCache();
		enableChildrenCache(mCurrentScreen, whichScreen);

		final int screenDelta = Math.abs(whichScreen - mCurrentScreen);
		mNextScreen = whichScreen;

		// mPreviousIndicator.setLevel(mNextScreen);
		// mNextIndicator.setLevel(mNextScreen);//mod:lsj

		View focusedChild = getFocusedChild();
		if (focusedChild != null && screenDelta != 0
				&& focusedChild == getChildAt(mCurrentScreen)) {
			focusedChild.clearFocus();
		}

		final int newX = whichScreen * getWidth();
		final int delta = newX - mScrollX;
		int duration;
		if (onclick) {
			duration = screenDelta * 1;
			awakenScrollBars(duration);
		} else {
			duration = screenDelta * 300;
			awakenScrollBars(duration);
		}
		if (!mScroller.isFinished())
			mScroller.abortAnimation();
		mScroller.startScroll(mScrollX, 0, delta, 0, duration);
		invalidate();
		// added by lsj stardard current screen imageview
		// mLauncher.updateScrrenFlag(whichScreen);
	}

	void startDrag(CellLayout.CellInfo cellInfo) {
		View child = cellInfo.cell;

		// Make sure the drag was started by a long press as opposed to a long
		// click.
		// Note that Search takes focus when clicked rather than entering touch
		// mode
		/*
		 * if (!child.isInTouchMode() && !(child instanceof Search)) { return; }
		 */

		mDragInfo = cellInfo;
		mDragInfo.screen = mCurrentScreen;

		CellLayout current = ((CellLayout) getChildAt(mCurrentScreen));

		current.onDragChild(child);
		mDragController.startDrag(child, this, child.getTag(),
				DragController.DRAG_ACTION_MOVE);
		invalidate();
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		final SavedState state = new SavedState(super.onSaveInstanceState());
		state.currentScreen = mCurrentScreen;
		return state;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		SavedState savedState = (SavedState) state;
		super.onRestoreInstanceState(savedState.getSuperState());
		if (savedState.currentScreen != -1) {
			mCurrentScreen = savedState.currentScreen;
			Launcher.setScreen(mCurrentScreen);
		}
	}

	public void onDrop(DragSource source, int x, int y, int xOffset,
			int yOffset, DragView dragView, Object dragInfo) {
		final CellLayout cellLayout = getCurrentDropLayout();
		if (source != this) {
			onDropExternal(x - xOffset, y - yOffset, dragInfo, cellLayout);
		} else {
			// Move internally
			if (mDragInfo != null) {
				final View cell = mDragInfo.cell;
				int index = mScroller.isFinished() ? mCurrentScreen
						: mNextScreen;
				if (index != mDragInfo.screen) {
					final CellLayout originalCellLayout = (CellLayout) getChildAt(mDragInfo.screen);
					originalCellLayout.removeView(cell);
					cellLayout.addView(cell);
				}
				mTargetCell = estimateDropCell(x - xOffset, y - yOffset,
						mDragInfo.spanX, mDragInfo.spanY, cell, cellLayout,
						mTargetCell);
				cellLayout.onDropChild(cell, mTargetCell);
				final ItemInfo info = (ItemInfo) cell.getTag();
				CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell
						.getLayoutParams();
				LauncherModel.moveItemInDatabase(mLauncher, info,
						LauncherSettings.Favorites.CONTAINER_DESKTOP, index,
						lp.cellX, lp.cellY, -2);
			}
		}
	}

	public void onDragEnter(DragSource source, int x, int y, int xOffset,
			int yOffset, DragView dragView, Object dragInfo) {
		// Log.i("==worksapce====onDragEnter=====",
		// "=====setOnLongClickListener======");
		clearVacantCache();
	}

	public void onDragOver(DragSource source, int x, int y, int xOffset,
			int yOffset, DragView dragView, Object dragInfo) {
	}

	public void onDragExit(DragSource source, int x, int y, int xOffset,
			int yOffset, DragView dragView, Object dragInfo) {
		clearVacantCache();
	}

	private void onDropExternal(int x, int y, Object dragInfo,
			CellLayout cellLayout) {
		onDropExternal(x, y, dragInfo, cellLayout, false);
	}

	private void onDropExternal(int x, int y, Object dragInfo,
			CellLayout cellLayout, boolean insertAtFirst) {
		// Drag from somewhere else
		ItemInfo info = (ItemInfo) dragInfo;

		View view;

		switch (info.itemType) {
		case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
		case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
//			if (info.container == NO_ID && info instanceof ApplicationInfo) {
//				// Came from all apps -- make a copy
//				info = new ShortcutInfo((ApplicationInfo) info);
//			}
//			view = mLauncher.createShortcut(R.layout.application, cellLayout,
//					(ShortcutInfo) info);
			break;
		default:
			throw new IllegalStateException("Unknown item type: "
					+ info.itemType);
		}

//		cellLayout.addView(view, insertAtFirst ? 0 : -1);
//		view.setHapticFeedbackEnabled(false);
//		view.setOnLongClickListener(mLongClickListener);
//		if (view instanceof DropTarget) {
//			mDragController.addDropTarget((DropTarget) view);
//		}
//
//		mTargetCell = estimateDropCell(x, y, 1, 1, view, cellLayout,
//				mTargetCell);
//		cellLayout.onDropChild(view, mTargetCell);
//		CellLayout.LayoutParams lp = (CellLayout.LayoutParams) view
//				.getLayoutParams();

//		LauncherModel.addOrMoveItemInDatabase(mLauncher, info,
//				LauncherSettings.Favorites.CONTAINER_DESKTOP, mCurrentScreen,
//				lp.cellX, lp.cellY, -2);
	}

	/**
	 * Return the current {@link CellLayout}, correctly picking the destination
	 * screen while a scroll is in progress.
	 */
	private CellLayout getCurrentDropLayout() {
		int index = mScroller.isFinished() ? mCurrentScreen : mNextScreen;
		return (CellLayout) getChildAt(index);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean acceptDrop(DragSource source, int x, int y, int xOffset,
			int yOffset, DragView dragView, Object dragInfo) {
		final CellLayout layout = getCurrentDropLayout();
		final CellLayout.CellInfo cellInfo = mDragInfo;
		final int spanX = cellInfo == null ? 1 : cellInfo.spanX;
		final int spanY = cellInfo == null ? 1 : cellInfo.spanY;

		if (mVacantCache == null) {
			final View ignoreView = cellInfo == null ? null : cellInfo.cell;
			mVacantCache = layout.findAllVacantCells(null, ignoreView);
		}

		return mVacantCache.findCellForSpan(mTempEstimate, spanX, spanY, false);
	}

	/**
	 * {@inheritDoc}
	 */
	public Rect estimateDropLocation(DragSource source, int x, int y,
			int xOffset, int yOffset, DragView dragView, Object dragInfo,
			Rect recycle) {
		final CellLayout layout = getCurrentDropLayout();

		final CellLayout.CellInfo cellInfo = mDragInfo;
		final int spanX = cellInfo == null ? 1 : cellInfo.spanX;
		final int spanY = cellInfo == null ? 1 : cellInfo.spanY;
		final View ignoreView = cellInfo == null ? null : cellInfo.cell;

		final Rect location = recycle != null ? recycle : new Rect();

		// Find drop cell and convert into rectangle
		int[] dropCell = estimateDropCell(x - xOffset, y - yOffset, spanX,
				spanY, ignoreView, layout, mTempCell);

		if (dropCell == null) {
			return null;
		}

		layout.cellToPoint(dropCell[0], dropCell[1], mTempEstimate);
		location.left = mTempEstimate[0];
		location.top = mTempEstimate[1];

		layout.cellToPoint(dropCell[0] + spanX, dropCell[1] + spanY,
				mTempEstimate);
		location.right = mTempEstimate[0];
		location.bottom = mTempEstimate[1];

		return location;
	}

	/**
	 * Calculate the nearest cell where the given object would be dropped.
	 */
	private int[] estimateDropCell(int pixelX, int pixelY, int spanX,
			int spanY, View ignoreView, CellLayout layout, int[] recycle) {
		// Create vacant cell cache if none exists
		if (mVacantCache == null) {
			mVacantCache = layout.findAllVacantCells(null, ignoreView);
		}

		// Find the best target drop location
		return layout.findNearestVacantArea(pixelX, pixelY, spanX, spanY,
				mVacantCache, recycle);
	}

	void setLauncher(Launcher launcher) {
		mLauncher = launcher;
	}

	public void setDragController(DragController dragController) {
		mDragController = dragController;
	}

	public void onDropCompleted(View target, boolean success) {
		clearVacantCache();
		if (success) {
			if (target != this && mDragInfo != null) {
				final CellLayout cellLayout = (CellLayout) getChildAt(mDragInfo.screen);
				cellLayout.removeView(mDragInfo.cell);
				if (mDragInfo.cell instanceof DropTarget) {
					mDragController
							.removeDropTarget((DropTarget) mDragInfo.cell);
				}
				// final Object tag = mDragInfo.cell.getTag();
			}
		} else {
			if (mDragInfo != null) {
				final CellLayout cellLayout = (CellLayout) getChildAt(mDragInfo.screen);
				cellLayout.onDropAborted(mDragInfo.cell);
			}
		}

		mDragInfo = null;
	}

	public void scrollLeft() {

		clearVacantCache();
		if (mScroller.isFinished()) {
			if (mCurrentScreen > 0)
				snapToScreen(mCurrentScreen - 1, false);
		} else {
			if (mNextScreen > 0)
				snapToScreen(mNextScreen - 1, false);
		}
	}

	public void scrollRight() {
		clearVacantCache();
		if (mScroller.isFinished()) {
			if (mCurrentScreen < getChildCount() - 1)
				snapToScreen(mCurrentScreen + 1, false);
		} else {
			if (mNextScreen < getChildCount() - 1)
				snapToScreen(mNextScreen + 1, false);
		}
	}

	public int getScreenForView(View v) {
		int result = -1;
		if (v != null) {
			ViewParent vp = v.getParent();
			int count = getChildCount();
			for (int i = 0; i < count; i++) {
				if (vp == getChildAt(i)) {
					return i;
				}
			}
		}
		return result;
	}

	public View getViewForTag(Object tag) {
		int screenCount = getChildCount();
		for (int screen = 0; screen < screenCount; screen++) {
			CellLayout currentScreen = ((CellLayout) getChildAt(screen));
			int count = currentScreen.getChildCount();
			for (int i = 0; i < count; i++) {
				View child = currentScreen.getChildAt(i);
				if (child.getTag() == tag) {
					return child;
				}
			}
		}
		return null;
	}

	/**
	 * @return True is long presses are still allowed for the current touch
	 */
	public boolean allowLongPress() {
		return mAllowLongPress;
	}

	/**
	 * Set true to allow long-press events to be triggered, usually checked by
	 * {@link Launcher} to accept or block dpad-initiated long-presses.
	 */
	public void setAllowLongPress(boolean allowLongPress) {
		mAllowLongPress = allowLongPress;
	}

	void removeItemsForPackage(final String packageName) {
		final int count = getChildCount();
		final PackageManager manager = getContext().getPackageManager();
		final AppWidgetManager widgets = AppWidgetManager
				.getInstance(getContext());

		for (int i = 0; i < count; i++) {
			final CellLayout layout = (CellLayout) getChildAt(i);

			// Avoid ANRs by treating each screen separately
			post(new Runnable() {
				public void run() {
					final ArrayList<View> childrenToRemove = new ArrayList<View>();
					childrenToRemove.clear();

					int childCount = layout.getChildCount();
					for (int j = 0; j < childCount; j++) {
						final View view = layout.getChildAt(j);
						Object tag = view.getTag();

//						if (tag instanceof ShortcutInfo) {
//							final ShortcutInfo info = (ShortcutInfo) tag;
//							// We need to check for ACTION_MAIN otherwise
//							// getComponent() might
//							// return null for some shortcuts (for instance, for
//							// shortcuts to
//							// web pages.)
//							final Intent intent = info.intent;
//							final ComponentName name = intent.getComponent();
//
//							if (Intent.ACTION_MAIN.equals(intent.getAction())
//									&& name != null
//									&& packageName
//											.equals(name.getPackageName())) {
//								// TODO: This should probably be done on a
//								// worker thread
//								LauncherModel.deleteItemFromDatabase(mLauncher,
//										info);
//								childrenToRemove.add(view);
//							}
//						}
					}

					childCount = childrenToRemove.size();
					for (int j = 0; j < childCount; j++) {
						View child = childrenToRemove.get(j);
						layout.removeViewInLayout(child);
						if (child instanceof DropTarget) {
							mDragController
									.removeDropTarget((DropTarget) child);
						}
					}

					if (childCount > 0) {
						layout.requestLayout();
						layout.invalidate();
					}
				}
			});
		}
	}

	void updateShortcutsForPackage(String packageName) {
		final PackageManager pm = mLauncher.getPackageManager();

		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			final CellLayout layout = (CellLayout) getChildAt(i);
			int childCount = layout.getChildCount();
			for (int j = 0; j < childCount; j++) {
				final View view = layout.getChildAt(j);
				Object tag = view.getTag();
//				if (tag instanceof ShortcutInfo) {
//					ShortcutInfo info = (ShortcutInfo) tag;
//					// We need to check for ACTION_MAIN otherwise getComponent()
//					// might
//					// return null for some shortcuts (for instance, for
//					// shortcuts to
//					// web pages.)
//					final Intent intent = info.intent;
//					final ComponentName name = intent.getComponent();
//					if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
//							&& Intent.ACTION_MAIN.equals(intent.getAction())
//							&& name != null
//							&& packageName.equals(name.getPackageName())) {
//
//						info.setIcon(mIconCache.getIcon(info.intent));
//						Log.i("==workspace===updateShortcutsForPackage=",
//								"=============");
//						((TextView) view)
//								.setCompoundDrawablesWithIntrinsicBounds(null,
//										new FastBitmapDrawable(info
//												.getIcon(mIconCache),
//												getResources()), null, null);
//					}
//				}
			}
		}
	}

	void moveToDefaultScreen(boolean animate) {
		if (animate) {
			snapToScreen(mDefaultScreen, false);
		} else {
			setCurrentScreen(mDefaultScreen);
		}
		getChildAt(mDefaultScreen).requestFocus();
	}

	void setIndicators(Drawable previous, Drawable next) {
		previous.setLevel(mCurrentScreen);
		next.setLevel(mCurrentScreen);
	}

	public static class SavedState extends BaseSavedState {
		int currentScreen = -1;

		SavedState(Parcelable superState) {
			super(superState);
		}

		private SavedState(Parcel in) {
			super(in);
			currentScreen = in.readInt();
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeInt(currentScreen);
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
}
