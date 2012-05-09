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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import com.smit.pictureshow.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.LiveFolders;
import android.telephony.PhoneNumberUtils;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Default launcher application.
 */
public final class Launcher extends Activity implements View.OnClickListener,
		OnLongClickListener, LauncherModel.Callbacks {
	static final String TAG = "Launcher";
	static final boolean LOGD = false;

	static final boolean PROFILE_STARTUP = false;
	static final boolean PROFILE_ROTATE = false;
	static final boolean DEBUG_USER_INTERFACE = false;

	private static final int WALLPAPER_SCREENS_SPAN = 2;

	private static final int MENU_GROUP_ADD = 1;
	private static final int MENU_GROUP_WALLPAPER = MENU_GROUP_ADD + 1;

	private static final int MENU_ADD = Menu.FIRST + 1;
	private static final int MENU_WALLPAPER_SETTINGS = MENU_ADD + 1;
	private static final int MENU_SEARCH = MENU_WALLPAPER_SETTINGS + 1;
	private static final int MENU_SORT = MENU_SEARCH + 1;
	private static final int MENU_SETTINGS = MENU_SORT + 1;

	private static final int REQUEST_CREATE_SHORTCUT = 1;
	private static final int REQUEST_CREATE_LIVE_FOLDER = 4;
	private static final int REQUEST_CREATE_APPWIDGET = 5;
	private static final int REQUEST_PICK_APPLICATION = 6;
	private static final int REQUEST_PICK_SHORTCUT = 7;
	private static final int REQUEST_PICK_LIVE_FOLDER = 8;
	private static final int REQUEST_PICK_APPWIDGET = 9;
	private static final int REQUEST_PICK_WALLPAPER = 10;

	static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

	static final String EXTRA_CUSTOM_WIDGET = "custom_widget";
	static final String SEARCH_WIDGET = "search_widget";

	static final int SCREEN_COUNT = 5;
	static final int DEFAULT_SCREEN = 2;
	static final int NUMBER_CELLS_X = 4;
	static final int NUMBER_CELLS_Y = 4;

	static final int DIALOG_CREATE_SHORTCUT = 1;
	static final int DIALOG_RENAME_FOLDER = 2;
	static final int DIALOG_RENAME_TYPE = 3;

	private static final String PREFERENCES = "launcher.preferences";

	// Type: int
	private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
	// Type: boolean
	private static final String RUNTIME_STATE_ALL_APPS_FOLDER = "launcher.all_apps_folder";
	// Type: long
	private static final String RUNTIME_STATE_USER_FOLDERS = "launcher.user_folder";
	// Type: int
	private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
	// Type: int
	private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cellX";
	// Type: int
	private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cellY";
	// Type: int
	private static final String RUNTIME_STATE_PENDING_ADD_SPAN_X = "launcher.add_spanX";
	// Type: int
	private static final String RUNTIME_STATE_PENDING_ADD_SPAN_Y = "launcher.add_spanY";
	// Type: int
	private static final String RUNTIME_STATE_PENDING_ADD_COUNT_X = "launcher.add_countX";
	// Type: int
	private static final String RUNTIME_STATE_PENDING_ADD_COUNT_Y = "launcher.add_countY";
	// Type: int[]
	private static final String RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS = "launcher.add_occupied_cells";
	// Type: boolean
	private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
	// Type: long
	private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID = "launcher.rename_folder_id";

	static final int APPWIDGET_HOST_ID = 1024;

	private static final Object sLock = new Object();
	private static int sScreen = DEFAULT_SCREEN;

	private static int mValideScreenNum = 1;// mod:lsj,WORKSAPCE 当前空间下屏幕数据

	private final BroadcastReceiver mCloseSystemDialogsReceiver = new CloseSystemDialogsIntentReceiver();
	// private final ContentObserver mWidgetObserver = new
	// AppWidgetResetObserver();

	private LayoutInflater mInflater;
	private DragLayer dragLayer;
	private DragController mDragController;
	private DragController dragController;
	public Workspace mWorkspace;

	private CellLayout.CellInfo mAddItemCellInfo;
	private CellLayout.CellInfo mMenuAddInfo;
	private final int[] mCellCoordinates = new int[2];
	// private FolderInfo mFolderInfo;

	private Bundle mSavedState;

	private SpannableStringBuilder mDefaultKeySsb = null;

	private boolean mIsNewIntent;

	private boolean mWorkspaceLoading = true;

	private boolean mPaused = true;
	private boolean mRestoring;
	private boolean mWaitingForResult;

	private Bundle mSavedInstanceState;

	public LauncherModel mModel;
	private IconCache mIconCache;

	private ArrayList<ItemInfo> mDesktopItems = new ArrayList<ItemInfo>();
	// private static HashMap<Long, FolderInfo> mFolders = new HashMap<Long,
	// FolderInfo>();

	private boolean mUtsTestMode;

	private ImageButton mImageButtonOne;
	private ImageButton mImageButtonTwo;
	private ImageButton mImageButtonThree;
	private ImageButton mImageButtonFour;
	private ImageButton mImageButtonFive;
	private View mViewSort;// 分类和表格整体VIEW
	private View mFivescreen;// 五分屏整体VIEW

	// in launcher added WORDSAPCE 五个CELLLAYOUT
	private CellLayout mCellLayout;
	private CellLayout mCellLayout1;
	private CellLayout mCellLayout2;
	private CellLayout mCellLayout3;
	private CellLayout mCellLayout4;

	public static Vibrator mVibrator;

	// 表格中分类初始化默认为所有应用和点击分类BUTTON次数为0
	public static int mApplicationType = LauncherSettings.Favorites.SORT_APPVIEWS;

	// added by liuw
	private final int[] mBitmapResId = new int[] { R.drawable.shortcut,
			R.drawable.shortcut1, R.drawable.shortcut2, R.drawable.shortcut3,
			R.drawable.shortcut4, R.drawable.shortcut5, R.drawable.shortcut6,
			R.drawable.shortcut7 };
	private Random random = new Random();

	// enter into application
	public void ExcuteApk(String pkg) {
		PackageManager packageManager = this.getPackageManager();
		Intent intent = new Intent();
		intent = packageManager.getLaunchIntentForPackage(pkg);
		if (intent != null) {
			startActivity(intent);
		}
	}

	public CoordParameter CalculateCoordinate(Context context, int nType) {
		return mModel.CalculateCoordinate(context, nType);
	}

	/**
	 * 功能说明：得到当前CELLLAYOUT的屏幕数
	 * 
	 * @return
	 */
	public static int GetValideScreenNum() {
		return mValideScreenNum;
	}

	/**
	 * 功能说明：根据从数据库中的数据设置当前CELLLAYOUT的屏幕数
	 * 
	 * @return
	 */
	public static void SetValideScreenNum(int nvalidnum) {
		mValideScreenNum = nvalidnum;
	}

	private static DisplayMetrics mdm = null;

	public static int dip2px(float dipValue) {
		int nPx = 0;
		if (mdm != null) {
			nPx = (int) (dipValue * mdm.density + 0.5f);
		}
		return nPx;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mdm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(mdm);
		LauncherApplication app = ((LauncherApplication) getApplication());
		mModel = app.setLauncher(this);
		mIconCache = app.getIconCache();
		mDragController = new DragController(this);
		mDragController.setLauncher(this);
		mInflater = getLayoutInflater();

		if (PROFILE_STARTUP) {
			android.os.Debug.startMethodTracing("/sdcard/launcher");
		}

		checkForLocaleChange();
		setWallpaperDimension();

		setContentView(R.layout.launcher);
		setupViews();

		lockAllApps();

		mSavedState = savedInstanceState;
		restoreState(mSavedState);

		if (PROFILE_STARTUP) {
			android.os.Debug.stopMethodTracing();
		}

		// We have a new AllAppsView, we need to re-bind everything, and it
		// could have
		// changed in our absence.
		mModel.setAllAppsDirty();
		mModel.setWorkspaceDirty();

		if (!mRestoring) {

			mModel.startLoader(this, true);
		}

		// For handling default keys
		mDefaultKeySsb = new SpannableStringBuilder();
		Selection.setSelection(mDefaultKeySsb, 0);

		IntentFilter filter = new IntentFilter(
				Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
		registerReceiver(mCloseSystemDialogsReceiver, filter);
		mVibrator = (Vibrator) getApplicationContext().getSystemService(
				Context.VIBRATOR_SERVICE);// add lsj

	}

	private void checkForLocaleChange() {
		final LocaleConfiguration localeConfiguration = new LocaleConfiguration();
		readConfiguration(this, localeConfiguration);

		final Configuration configuration = getResources().getConfiguration();

		final String previousLocale = localeConfiguration.locale;
		final String locale = configuration.locale.toString();

		final int previousMcc = localeConfiguration.mcc;
		final int mcc = configuration.mcc;
		final int previousMnc = localeConfiguration.mnc;
		final int mnc = configuration.mnc;

		boolean localeChanged = !locale.equals(previousLocale)
				|| mcc != previousMcc || mnc != previousMnc;

		if (localeChanged) {
			localeConfiguration.locale = locale;
			localeConfiguration.mcc = mcc;
			localeConfiguration.mnc = mnc;

			writeConfiguration(this, localeConfiguration);
			mIconCache.flush();
		}
	}

	private static class LocaleConfiguration {
		public String locale;
		public int mcc = -1;
		public int mnc = -1;
	}

	private static void readConfiguration(Context context,
			LocaleConfiguration configuration) {
		DataInputStream in = null;
		try {
			in = new DataInputStream(context.openFileInput(PREFERENCES));
			configuration.locale = in.readUTF();
			configuration.mcc = in.readInt();
			configuration.mnc = in.readInt();
		} catch (FileNotFoundException e) {
			// Ignore
		} catch (IOException e) {
			// Ignore
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		}
	}

	private static void writeConfiguration(Context context,
			LocaleConfiguration configuration) {
		DataOutputStream out = null;
		try {
			out = new DataOutputStream(context.openFileOutput(PREFERENCES,
					MODE_PRIVATE));
			out.writeUTF(configuration.locale);
			out.writeInt(configuration.mcc);
			out.writeInt(configuration.mnc);
			out.flush();
		} catch (FileNotFoundException e) {
			// Ignore
		} catch (IOException e) {
			// noinspection ResultOfMethodCallIgnored
			context.getFileStreamPath(PREFERENCES).delete();
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		}
	}

	static int getScreen() {
		synchronized (sLock) {
			return sScreen;
		}
	}

	static void setScreen(int screen) {
		synchronized (sLock) {
			sScreen = screen;
		}

	}

	private void setWallpaperDimension() {
		WallpaperManager wpm = (WallpaperManager) getSystemService(WALLPAPER_SERVICE);

		Display display = getWindowManager().getDefaultDisplay();
		boolean isPortrait = display.getWidth() < display.getHeight();

		final int width = isPortrait ? display.getWidth() : display.getHeight();
		final int height = isPortrait ? display.getHeight() : display
				.getWidth();
		wpm.suggestDesiredDimensions(width * WALLPAPER_SCREENS_SPAN, height);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		mWaitingForResult = false;

		// The pattern used here is that a user PICKs a specific application,
		// which, depending on the target, might need to CREATE the actual
		// target.

		// For example, the user would PICK_SHORTCUT for "Music playlist", and
		// we
		// launch over to the Music app to actually CREATE_SHORTCUT.

		if (resultCode == RESULT_OK && mAddItemCellInfo != null) {
			switch (requestCode) {
			case REQUEST_PICK_SHORTCUT:
				processShortcut(data, REQUEST_PICK_APPLICATION,
						REQUEST_CREATE_SHORTCUT);
				break;
			case REQUEST_PICK_WALLPAPER:
				// We just wanted the activity result here so we can clear
				// mWaitingForResult
				break;
			}
		}

	}

	@Override
	protected void onResume() {
		super.onResume();

		mPaused = false;
		mUtsTestMode = SystemProperties.getInt("persist.sys.uts-test-mode", 0) == 1;

		if (mRestoring) {
			mWorkspaceLoading = true;
			mModel.startLoader(this, true);
			mRestoring = false;
		}

		mIsNewIntent = false;
	}

	@Override
	protected void onPause() {
		super.onPause();
		mDragController.cancelDrag();
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		// Flag the loader to stop early before switching
		mModel.stopLoader();

		if (PROFILE_ROTATE) {
			android.os.Debug.startMethodTracing("/sdcard/launcher-rotate");
		}
		return null;
	}

	private boolean acceptFilter() {
		final InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		return !inputManager.isFullscreenMode();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mUtsTestMode) {
			return handleUtsTestModeKeyDown(keyCode, event);
		}

		boolean handled = super.onKeyDown(keyCode, event);
		if (!handled && acceptFilter() && keyCode != KeyEvent.KEYCODE_ENTER) {
			boolean gotKey = TextKeyListener.getInstance().onKeyDown(
					mWorkspace, mDefaultKeySsb, keyCode, event);
			if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
				return onSearchRequested();
			}
		}

		// Eat the long press event so the keyboard doesn't come up.
		if (keyCode == KeyEvent.KEYCODE_MENU && event.isLongPress()) {
			return true;
		}

		return handled;
	}

	public boolean handleUtsTestModeKeyDown(int keyCode, KeyEvent event) {
		// Log.d(TAG, "UTS-TEST-MODE");
		boolean handled = super.onKeyDown(keyCode, event);
		if (!handled && acceptFilter() && keyCode != KeyEvent.KEYCODE_ENTER) {
			boolean gotKey = TextKeyListener.getInstance().onKeyDown(
					mWorkspace, mDefaultKeySsb, keyCode, event);
			if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
				// something usable has been typed - dispatch it now.
				final String str = mDefaultKeySsb.toString();

				boolean isDialable = true;
				final int count = str.length();
				for (int i = 0; i < count; i++) {
					if (!PhoneNumberUtils.isReallyDialable(str.charAt(i))) {
						isDialable = false;
						break;
					}
				}
				Intent intent;
				if (isDialable) {
					intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts(
							"tel", str, null));
				} else {
					intent = new Intent(
							ContactsContract.Intents.UI.FILTER_CONTACTS_ACTION);
					intent.putExtra(
							ContactsContract.Intents.UI.FILTER_TEXT_EXTRA_KEY,
							str);
				}

				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
						| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

				try {
					startActivity(intent);
				} catch (android.content.ActivityNotFoundException ex) {
					// Oh well... no one knows how to filter/dial. Life goes on.
				}

				mDefaultKeySsb.clear();
				mDefaultKeySsb.clearSpans();
				Selection.setSelection(mDefaultKeySsb, 0);

				return true;
			}
		}

		return handled;
	}

	private String getTypedText() {
		return mDefaultKeySsb.toString();
	}

	private void clearTypedText() {
		mDefaultKeySsb.clear();
		mDefaultKeySsb.clearSpans();
		Selection.setSelection(mDefaultKeySsb, 0);
	}

	/**
	 * Restores the previous state, if it exists.
	 * 
	 * @param savedState
	 *            The previous state.
	 */
	private void restoreState(Bundle savedState) {
		if (savedState == null) {
			return;
		}

		final boolean allApps = savedState.getBoolean(
				RUNTIME_STATE_ALL_APPS_FOLDER, false);

		final int currentScreen = savedState.getInt(
				RUNTIME_STATE_CURRENT_SCREEN, -1);
		if (currentScreen > -1) {
			mWorkspace.setCurrentScreen(currentScreen);
		}
		// updateScrrenFlag(currentScreen);
		final int addScreen = savedState.getInt(
				RUNTIME_STATE_PENDING_ADD_SCREEN, -1);
		if (addScreen > -1) {
			mAddItemCellInfo = new CellLayout.CellInfo();
			final CellLayout.CellInfo addItemCellInfo = mAddItemCellInfo;
			addItemCellInfo.valid = true;
			addItemCellInfo.screen = addScreen;
			addItemCellInfo.cellX = savedState
					.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
			addItemCellInfo.cellY = savedState
					.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
			addItemCellInfo.spanX = savedState
					.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_X);
			addItemCellInfo.spanY = savedState
					.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y);
			addItemCellInfo.findVacantCellsFromOccupied(savedState
					.getBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS),
					savedState.getInt(RUNTIME_STATE_PENDING_ADD_COUNT_X),
					savedState.getInt(RUNTIME_STATE_PENDING_ADD_COUNT_Y));
			mRestoring = true;
		}

		boolean renameFolder = savedState.getBoolean(
				RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
	}

	/**
	 * Finds all the views we need and configure them properly.
	 */
	private void setupViews() {
		dragController = mDragController;

		dragLayer = (DragLayer) findViewById(R.id.drag_layer);
		dragLayer.setDragController(dragController);

		mWorkspace = (Workspace) dragLayer.findViewById(R.id.workspace);
		final Workspace workspace = mWorkspace;
		workspace.setHapticFeedbackEnabled(false);

		workspace.setOnLongClickListener(this);
		workspace.setDragController(dragController);
		workspace.setLauncher(this);

		dragController.setDragScoller(workspace);
		dragController.setScrollView(dragLayer);
		dragController.setMoveTarget(workspace);

		// The order here is bottom to top.
		dragController.addDropTarget(workspace);

		mCellLayout = (CellLayout) mWorkspace.getChildAt(0);
	}

	@SuppressWarnings( { "UnusedDeclaration" })
	public void previousScreen(View v) {
		// mod:lsj
		mWorkspace.scrollLeft();

	}

	@SuppressWarnings( { "UnusedDeclaration" })
	public void nextScreen(View v) {
		/*
		 * if (!isAllAppsVisible()) { mWorkspace.scrollRight(); }
		 */

		// mod:lsj
		mWorkspace.scrollRight();
	}


	/**
	 * create a radom bitmap resid.
	 * 
	 * @auther liuw
	 * 
	 */

	private int produceResid() {
		int index = 0;
		int len = mBitmapResId.length;
		index = random.nextInt(len);
		return mBitmapResId[index];
	}



	void closeSystemDialogs() {
		getWindow().closeAllPanels();

		try {
			dismissDialog(DIALOG_CREATE_SHORTCUT);
			// Unlock the workspace if the dialog was showing
		} catch (Exception e) {
			// An exception is thrown if the dialog is not visible, which is
			// fine
		}

		try {
			dismissDialog(DIALOG_RENAME_FOLDER);
			// Unlock the workspace if the dialog was showing
		} catch (Exception e) {
			// An exception is thrown if the dialog is not visible, which is
			// fine
		}

		// Whatever we were doing is hereby canceled.
		mWaitingForResult = false;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		// Close the menu
		if (Intent.ACTION_MAIN.equals(intent.getAction())) {
			// also will cancel mWaitingForResult.
			closeSystemDialogs();

			// Set this flag so that onResume knows to close the search dialog
			// if it's open,
			// because this was a new intent (thus a press of 'home' or some
			// such) rather than
			// for example onResume being called when the user pressed the
			// 'back' button.
			mIsNewIntent = true;

			boolean alreadyOnHome = ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
			// boolean allAppsVisible = isAllAppsVisible();//mod:lsj
			if (!mWorkspace.isDefaultScreenShowing()) {
				// mWorkspace.moveToDefaultScreen(alreadyOnHome &&
				// !allAppsVisible);//mod:lsj
				mWorkspace.moveToDefaultScreen(alreadyOnHome);
			}

			final View v = getWindow().peekDecorView();
			if (v != null && v.getWindowToken() != null) {
				InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			}
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		// Do not call super here
		mSavedInstanceState = savedInstanceState;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace
				.getCurrentScreen());

		super.onSaveInstanceState(outState);

		// TODO should not do this if the drawer is currently closing.
		outState.putBoolean(RUNTIME_STATE_ALL_APPS_FOLDER, true);

		if (mAddItemCellInfo != null && mAddItemCellInfo.valid
				&& mWaitingForResult) {
			final CellLayout.CellInfo addItemCellInfo = mAddItemCellInfo;
			final CellLayout layout = (CellLayout) mWorkspace
					.getChildAt(addItemCellInfo.screen);

			outState.putInt(RUNTIME_STATE_PENDING_ADD_SCREEN,
					addItemCellInfo.screen);
			outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X,
					addItemCellInfo.cellX);
			outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y,
					addItemCellInfo.cellY);
			outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_X,
					addItemCellInfo.spanX);
			outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y,
					addItemCellInfo.spanY);
			outState.putInt(RUNTIME_STATE_PENDING_ADD_COUNT_X, layout
					.getCountX());
			outState.putInt(RUNTIME_STATE_PENDING_ADD_COUNT_Y, layout
					.getCountY());
			outState.putBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS,
					layout.getOccupiedCells());
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		TextKeyListener.getInstance().release();

		mModel.stopLoader();

		unbindDesktopItems();

		unregisterReceiver(mCloseSystemDialogsReceiver);
	}

	@Override
	public void startActivityForResult(Intent intent, int requestCode) {
		if (requestCode >= 0)
			mWaitingForResult = true;
		super.startActivityForResult(intent, requestCode);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (isWorkspaceLocked()) {
			return false;
		}
		super.onCreateOptionsMenu(menu);
		menu.add(MENU_GROUP_ADD, MENU_ADD, 0, R.string.menu_add).setIcon(
				android.R.drawable.ic_menu_add).setAlphabeticShortcut('A');
		menu.add(MENU_GROUP_WALLPAPER, MENU_WALLPAPER_SETTINGS, 0,
				R.string.menu_wallpaper).setIcon(
				android.R.drawable.ic_menu_gallery).setAlphabeticShortcut('W');
		menu.add(0, MENU_SEARCH, 0, R.string.menu_search).setIcon(
				android.R.drawable.ic_search_category_default)
				.setAlphabeticShortcut(SearchManager.MENU_KEY);
		menu.add(0, MENU_SORT, 0, R.string.menu_sort).setIcon(
				com.android.internal.R.drawable.ic_menu_notifications)
				.setAlphabeticShortcut('S');

		final Intent settings = new Intent(
				android.provider.Settings.ACTION_SETTINGS);
		settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

		menu.add(0, MENU_SETTINGS, 0, R.string.menu_settings).setIcon(
				android.R.drawable.ic_menu_preferences).setAlphabeticShortcut(
				'P').setIntent(settings);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		menu.setGroupVisible(MENU_GROUP_ADD, true);
		menu.setGroupVisible(MENU_GROUP_WALLPAPER, true);

		// Disable add if the workspace is full.
		if (true) {
			mMenuAddInfo = mWorkspace.findAllVacantCells(null);
			menu.setGroupEnabled(MENU_GROUP_ADD, mMenuAddInfo != null
					&& mMenuAddInfo.valid);
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_ADD:
			// addItems();
			return true;
		case MENU_WALLPAPER_SETTINGS:
			startWallpaper();
			return true;
		case MENU_SEARCH:
			onSearchRequested();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Indicates that we want global search for this activity by setting the
	 * globalSearch argument for {@link #startSearch} to true.
	 */

	@Override
	public boolean onSearchRequested() {
		startSearch(null, false, null, true);
		return true;
	}

	public boolean isWorkspaceLocked() {
		return mWorkspaceLoading || mWaitingForResult;
	}

	void processShortcut(Intent intent, int requestCodeApplication,
			int requestCodeShortcut) {
		// Handle case where user selected "Applications"
		String applicationName = getResources().getString(
				R.string.group_applications);
		String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

		if (applicationName != null && applicationName.equals(shortcutName)) {
			Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
			mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

			Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
			pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
			startActivityForResult(pickIntent, requestCodeApplication);
		} else {
			startActivityForResult(intent, requestCodeShortcut);
		}
	}

	private boolean findSingleSlot(CellLayout.CellInfo cellInfo) {
		final int[] xy = new int[2];
		if (findSlot(cellInfo, xy, 1, 1)) {
			cellInfo.cellX = xy[0];
			cellInfo.cellY = xy[1];
			return true;
		}
		return false;
	}

	private boolean findSlot(CellLayout.CellInfo cellInfo, int[] xy, int spanX,
			int spanY) {
		if (!cellInfo.findCellForSpan(xy, spanX, spanY)) {
			boolean[] occupied = mSavedState != null ? mSavedState
					.getBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS)
					: null;
			cellInfo = mWorkspace.findAllVacantCells(occupied);
			if (!cellInfo.findCellForSpan(xy, spanX, spanY)) {
				Toast.makeText(this, getString(R.string.out_of_space),
						Toast.LENGTH_SHORT).show();
				return false;
			}
		}
		return true;
	}

	private void showNotifications() {
		final StatusBarManager statusBar = (StatusBarManager) getSystemService(STATUS_BAR_SERVICE);
		if (statusBar != null) {
			statusBar.expand();
		}
	}

	private void startWallpaper() {
		// closeAllApps(true);//mod:lsj
		final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
		Intent chooser = Intent.createChooser(pickWallpaper,
				getText(R.string.chooser_wallpaper));
		startActivityForResult(chooser, REQUEST_PICK_WALLPAPER);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			Log.i("=====dispatchKeyEvent====", "========ACTION_DOWN====");
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_HOME:
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				Log.i("=====dispatchKeyEvent====", "========ACTION_UP====");
				if (SystemProperties.getInt("debug.launcher2.dumpstate", 0) != 0) {
					dumpState();
					return true;
				}
				break;
			}
		} else if (event.getAction() == KeyEvent.ACTION_UP) {
			Log.i("=====dispatchKeyEvent====", "========ACTION_UP====");
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_HOME:
				return true;
			}
		}

		return super.dispatchKeyEvent(event);
	}

	/**
	 * Go through the and disconnect any of the callbacks in the drawables and
	 * the views or we leak the previous Home screen on orientation change.
	 */
	private void unbindDesktopItems() {
		for (ItemInfo item : mDesktopItems) {
			item.unbind();
		}
	}

	/**
	 * Launches the intent referred by the clicked shortcut.
	 * 
	 * @param v
	 *            The view representing the clicked shortcut.
	 */
	public void onClick(View v) {
		Object tag = v.getTag();
//		if (tag instanceof ShortcutInfo) {
//			// Open shortcut
//			final Intent intent = ((ShortcutInfo) tag).intent;
//			int[] pos = new int[2];
//			v.getLocationOnScreen(pos);
//			intent.setSourceBounds(new Rect(pos[0], pos[1], pos[0]
//					+ v.getWidth(), pos[1] + v.getHeight()));
//			startActivitySafely(intent);
//
//		}

	}

	void startActivitySafely(Intent intent) {
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			startActivity(intent);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, R.string.activity_not_found,
					Toast.LENGTH_SHORT).show();
		} catch (SecurityException e) {
			Toast.makeText(this, R.string.activity_not_found,
					Toast.LENGTH_SHORT).show();
			Log
					.e(
							TAG,
							"Launcher does not have the permission to launch "
									+ intent
									+ ". Make sure to create a MAIN intent-filter for the corresponding activity "
									+ "or use the exported attribute for this activity.",
							e);
		}
	}

	public boolean onLongClick(View v) {

		if (!(v instanceof CellLayout)) {
			// Log.i("======Launcher========",
			// "======v instanceof CellLayout============");
			v = (View) v.getParent();
		}

		CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) v.getTag();

		// This happens when long clicking an item with the dpad/trackball
		if (cellInfo == null) {
			// Log.i("======Launcher========",
			// "======cellInfo == null============");
			return true;
		}

		if (mWorkspace.allowLongPress()) {
			if (cellInfo.cell != null) {
				// Log.i("======Launcher========",
				// "======mWorkspace.allowLongPress() ======false====");
				// if (!(cellInfo.cell instanceof Folder)) {
				// Log.i("======Launcher========",
				// "======cellInfo.cell instanceof Folder==========");
				// User long pressed on an item
				mWorkspace.performHapticFeedback(
						HapticFeedbackConstants.LONG_PRESS,
						HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
				mWorkspace.startDrag(cellInfo);
				// }
			}
			mVibrator.vibrate(500);
		}
		return true;
	}

	@SuppressWarnings( { "unchecked" })
	private void dismissPreview(final View v) {
		final PopupWindow window = (PopupWindow) v.getTag();
		if (window != null) {
			window.setOnDismissListener(new PopupWindow.OnDismissListener() {
				public void onDismiss() {
					ViewGroup group = (ViewGroup) v.getTag(R.id.workspace);
					int count = group.getChildCount();
					for (int i = 0; i < count; i++) {
						((ImageView) group.getChildAt(i))
								.setImageDrawable(null);
					}
					ArrayList<Bitmap> bitmaps = (ArrayList<Bitmap>) v
							.getTag(R.id.icon);
					for (Bitmap bitmap : bitmaps)
						bitmap.recycle();

					v.setTag(R.id.workspace, null);
					v.setTag(R.id.icon, null);
					window.setOnDismissListener(null);
				}
			});
			window.dismiss();
		}
		v.setTag(null);
	}

	private void showPreviews(View anchor) {
		showPreviews(anchor, 0, mWorkspace.getChildCount());
	}

	private void showPreviews(final View anchor, int start, int end) {
		final Resources resources = getResources();
		final Workspace workspace = mWorkspace;

		CellLayout cell = ((CellLayout) workspace.getChildAt(start));

		float max = workspace.getChildCount();

		final Rect r = new Rect();
		resources.getDrawable(R.drawable.preview_background).getPadding(r);
		int extraW = (int) ((r.left + r.right) * max);
		int extraH = r.top + r.bottom;
		// Log.i("==Launcher=showPreviews=", "r.left"+r.left+"= r.right="+
		// r.right+"==r.top=="+r.top+"==r.bottom="+r.bottom);
		int aW = cell.getWidth() - extraW;
		// Log.i("==Launcher====showPreviews=",
		// "cell.getWidth()"+cell.getWidth()+"==cell.getHeight()="+cell.getHeight());
		float w = aW / max;

		int width = cell.getWidth();
		int height = cell.getHeight();
		int x = 65;
		int y = 5;
		width -= (x + cell.getRightPadding());
		height -= (y + cell.getBottomPadding());

		float scale = w / width;

		int count = end - start;

		final float sWidth = width * scale;
		float sHeight = height * scale;

		LinearLayout preview = new LinearLayout(this);

		PreviewTouchHandler handler = new PreviewTouchHandler(anchor);
		ArrayList<Bitmap> bitmaps = new ArrayList<Bitmap>(count);

		for (int i = start; i < end; i++) {
			ImageView image = new ImageView(this);
			cell = (CellLayout) workspace.getChildAt(i);

			final Bitmap bitmap = Bitmap.createBitmap((int) sWidth,
					(int) sHeight, Bitmap.Config.ARGB_8888);

			final Canvas c = new Canvas(bitmap);
			c.scale(scale, scale);
			c.translate(-cell.getLeftPadding(), -cell.getTopPadding());
			cell.dispatchDraw(c);

			image.setBackgroundDrawable(resources
					.getDrawable(R.drawable.preview_background));
			image.setImageBitmap(bitmap);
			image.setTag(i);
			image.setOnClickListener(handler);
			image.setOnFocusChangeListener(handler);
			image.setFocusable(true);
			if (i == mWorkspace.getCurrentScreen())
				image.requestFocus();

			preview.addView(image, LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);

			bitmaps.add(bitmap);
		}

		final PopupWindow p = new PopupWindow(this);
		p.setContentView(preview);
		p.setWidth((int) (sWidth * count + extraW));
		p.setHeight((int) (sHeight + extraH));
		p.setAnimationStyle(R.style.AnimationPreview);
		p.setOutsideTouchable(true);
		p.setFocusable(true);
		p.setBackgroundDrawable(new ColorDrawable(0));
		p.showAsDropDown(anchor, 0, 0);

		p.setOnDismissListener(new PopupWindow.OnDismissListener() {
			public void onDismiss() {
				dismissPreview(anchor);
			}
		});

		anchor.setTag(p);
		anchor.setTag(R.id.workspace, preview);
		anchor.setTag(R.id.icon, bitmaps);
	}

	class PreviewTouchHandler implements View.OnClickListener, Runnable,
			View.OnFocusChangeListener {
		private final View mAnchor;

		public PreviewTouchHandler(View anchor) {
			mAnchor = anchor;
		}

		public void onClick(View v) {
			mWorkspace.snapToScreen((Integer) v.getTag(), false);
			v.post(this);
		}

		public void run() {
			dismissPreview(mAnchor);
		}

		public void onFocusChange(View v, boolean hasFocus) {
			if (hasFocus) {
				mWorkspace.snapToScreen((Integer) v.getTag(), false);
			}
		}
	}

	Workspace getWorkspace() {
		return mWorkspace;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_CREATE_SHORTCUT:
			return new CreateShortcut().createDialog();
		case DIALOG_RENAME_FOLDER:
			return new RenameFolder().createDialog();
		case DIALOG_RENAME_TYPE:
			return new RenameType().createDialog();
		}

		return super.onCreateDialog(id);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case DIALOG_CREATE_SHORTCUT:
			mWaitingForResult = false;// 控制横竖屏切换时候的快捷截面不出来的现象
			break;

		case DIALOG_RENAME_TYPE:
			break;
		}
	}

	void showRenameTypeDialog() {
		mWaitingForResult = true;
		showDialog(DIALOG_RENAME_TYPE);
	}

	private void pickShortcut(int requestCode, int title) {
		Bundle bundle = new Bundle();

		ArrayList<String> shortcutNames = new ArrayList<String>();
		shortcutNames.add(getString(R.string.group_applications));
		bundle.putStringArrayList(Intent.EXTRA_SHORTCUT_NAME, shortcutNames);

		ArrayList<ShortcutIconResource> shortcutIcons = new ArrayList<ShortcutIconResource>();
		shortcutIcons.add(ShortcutIconResource.fromContext(Launcher.this,
				R.drawable.ic_launcher_application));
		bundle.putParcelableArrayList(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
				shortcutIcons);
		Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
		pickIntent.putExtra(Intent.EXTRA_INTENT, new Intent(
				Intent.ACTION_CREATE_SHORTCUT));
		pickIntent.putExtra(Intent.EXTRA_TITLE, getText(title));
		pickIntent.putExtras(bundle);

		startActivityForResult(pickIntent, requestCode);
	}

	private class RenameFolder {
		private EditText mInput;

		Dialog createDialog() {
			mWaitingForResult = true;
			final View layout = View.inflate(Launcher.this,
					R.layout.rename_folder, null);
			mInput = (EditText) layout.findViewById(R.id.folder_name);

			AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
			builder.setIcon(0);
			builder.setTitle(getString(R.string.rename_folder_title));
			builder.setCancelable(true);
			builder.setOnCancelListener(new Dialog.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					cleanup();
				}
			});
			builder.setNegativeButton(getString(R.string.cancel_action),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							cleanup();
						}
					});
			builder.setPositiveButton(getString(R.string.rename_action),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// changeFolderName();
						}
					});
			builder.setView(layout);

			final AlertDialog dialog = builder.create();
			dialog.setOnShowListener(new DialogInterface.OnShowListener() {
				public void onShow(DialogInterface dialog) {
					mInput.requestFocus();
					InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					inputManager.showSoftInput(mInput, 0);
				}
			});

			return dialog;
		}

		private void cleanup() {
			dismissDialog(DIALOG_RENAME_FOLDER);
			mWaitingForResult = false;
		}
	}

	private class RenameType {
		private EditText mInput;

		Dialog createDialog() {
			mWaitingForResult = true;
			final View layout = View.inflate(Launcher.this,
					R.layout.rename_type, null);
			mInput = (EditText) layout.findViewById(R.id.type_name);

			AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
			builder.setIcon(0);
			builder.setTitle(getString(R.string.rename_type_title));
			builder.setCancelable(true);
			builder.setOnCancelListener(new Dialog.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					cleanup();
				}
			});
			builder.setNegativeButton(getString(R.string.cancel_action),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							cleanup();
						}
					});
			builder.setPositiveButton(getString(R.string.rename_action),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// changeFolderName();
						}
					});
			builder.setView(layout);

			final AlertDialog dialog = builder.create();
			dialog.setOnShowListener(new DialogInterface.OnShowListener() {
				public void onShow(DialogInterface dialog) {
					mInput.requestFocus();
					InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					inputManager.showSoftInput(mInput, 0);
				}
			});

			return dialog;
		}

		private void cleanup() {
			removeDialog(DIALOG_RENAME_TYPE);
			mWaitingForResult = false;
		}
	}

	void lockAllApps() {
		// TODO
	}

	void unlockAllApps() {
		// TODO
	}

	/**
	 * Displays the shortcut creation dialog and launches, if necessary, the
	 * appropriate activity.
	 */
	private class CreateShortcut implements DialogInterface.OnClickListener,
			DialogInterface.OnCancelListener,
			DialogInterface.OnDismissListener, DialogInterface.OnShowListener {

		private AddAdapter mAdapter;

		Dialog createDialog() {
			mWaitingForResult = true;

			mAdapter = new AddAdapter(Launcher.this);

			final AlertDialog.Builder builder = new AlertDialog.Builder(
					Launcher.this);
			builder.setTitle(getString(R.string.menu_item_add_item));
			builder.setAdapter(mAdapter, this);

			builder.setInverseBackgroundForced(true);

			AlertDialog dialog = builder.create();
			dialog.setOnCancelListener(this);
			dialog.setOnDismissListener(this);
			dialog.setOnShowListener(this);

			return dialog;
		}

		public void onCancel(DialogInterface dialog) {
			mWaitingForResult = false;
			cleanup();
		}

		public void onDismiss(DialogInterface dialog) {
		}

		private void cleanup() {
			try {
				dismissDialog(DIALOG_CREATE_SHORTCUT);
			} catch (Exception e) {
				// An exception is thrown if the dialog is not visible, which is
				// fine
			}
		}

		/**
		 * Handle the action clicked in the "Add to home" dialog.
		 */
		public void onClick(DialogInterface dialog, int which) {
			Resources res = getResources();
			cleanup();

			switch (which) {
			case AddAdapter.ITEM_SHORTCUT: {
				// Insert extra item to handle picking application
				pickShortcut(REQUEST_PICK_SHORTCUT,
						R.string.title_select_shortcut);
				break;
			}

			case AddAdapter.ITEM_LIVE_FOLDER: {
				// Insert extra item to handle inserting folder
				Bundle bundle = new Bundle();

				ArrayList<String> shortcutNames = new ArrayList<String>();
				shortcutNames.add(res.getString(R.string.group_folder));
				bundle.putStringArrayList(Intent.EXTRA_SHORTCUT_NAME,
						shortcutNames);

				ArrayList<ShortcutIconResource> shortcutIcons = new ArrayList<ShortcutIconResource>();
				shortcutIcons.add(ShortcutIconResource.fromContext(
						Launcher.this, R.drawable.ic_launcher_folder));
				bundle.putParcelableArrayList(
						Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcons);

				Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
				pickIntent.putExtra(Intent.EXTRA_INTENT, new Intent(
						LiveFolders.ACTION_CREATE_LIVE_FOLDER));
				pickIntent.putExtra(Intent.EXTRA_TITLE,
						getText(R.string.title_select_live_folder));
				pickIntent.putExtras(bundle);

				startActivityForResult(pickIntent, REQUEST_PICK_LIVE_FOLDER);
				break;
			}

			case AddAdapter.ITEM_WALLPAPER: {
				startWallpaper();
				break;
			}
			}
		}

		public void onShow(DialogInterface dialog) {
		}
	}

	/**
	 * Receives notifications when applications are added/removed.
	 */
	private class CloseSystemDialogsIntentReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			closeSystemDialogs();
			String reason = intent.getStringExtra("reason");
			if (!"homekey".equals(reason)) {
				boolean animate = true;
				if (mPaused || "lock".equals(reason)) {
					animate = false;
				}
				// closeAllApps(animate);//mod:lsj

			}
		}
	}

	/**
	 * Implementation of the method from LauncherModel.Callbacks.
	 */
	public int getCurrentWorkspaceScreen() {
		return mWorkspace.getCurrentScreen();
	}

	/**
	 * Refreshes the shortcuts shown on the workspace.
	 * 
	 * Implementation of the method from LauncherModel.Callbacks.
	 */
	public void startBinding() {
		final Workspace workspace = mWorkspace;
		int count = workspace.getChildCount();
		for (int i = 0; i < count; i++) {
			// Use removeAllViewsInLayout() to avoid an extra requestLayout()
			// and invalidate().
			((ViewGroup) workspace.getChildAt(i)).removeAllViewsInLayout();
		}

		if (DEBUG_USER_INTERFACE) {
			android.widget.Button finishButton = new android.widget.Button(this);
			finishButton.setText("Finish");
			workspace.addInScreen(finishButton, 1, 0, 0, 1, 1);

			finishButton
					.setOnClickListener(new android.widget.Button.OnClickListener() {
						public void onClick(View v) {
							finish();
						}
					});
		}
	}


	/**
	 * Callback saying that there aren't any more items to bind.
	 * 
	 * Implementation of the method from LauncherModel.Callbacks.
	 */
	public void finishBindingItems() {
		if (mSavedState != null) {
			if (!mWorkspace.hasFocus()) {
				mWorkspace.getChildAt(mWorkspace.getCurrentScreen())
						.requestFocus();
			}

			mSavedState = null;
		}

		if (mSavedInstanceState != null) {
			super.onRestoreInstanceState(mSavedInstanceState);
			mSavedInstanceState = null;
		}

		mWorkspaceLoading = false;
	}

	/**
	 * Prints out out state for debugging.
	 */
	public void dumpState() {
		mModel.dumpState();
		// mAllAppsGrid.dumpState();
		Log.d(TAG, "END launcher2 dump state");
	}

	@Override
	public void bindButtons(ArrayList<ItemInfo> buttons) {
		// TODO Auto-generated method stub
		for (ItemInfo itemInfo : buttons) {
			Button btn = (Button) mInflater.inflate(R.layout.button,
					(ViewGroup) mWorkspace.getChildAt(mWorkspace
							.getCurrentScreen()), false);
			btn.setTag(itemInfo);
			btn.setText(itemInfo.title);
			mWorkspace.addInScreen(btn, itemInfo.screen, itemInfo.cellX,
					itemInfo.cellY, 1, 1, false);
		}
	}

}
