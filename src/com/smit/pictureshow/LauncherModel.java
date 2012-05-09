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

import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should
 * be only one LauncherModel object held in a static. Also provide APIs for
 * updating the database state for the Launcher.
 */
public class LauncherModel extends BroadcastReceiver {
	static final boolean DEBUG_LOADERS = false;
	static final String TAG = "Launcher.Model";

	private final LauncherApplication mApp;
	private final Object mLock = new Object();
	private DeferredHandler mHandler = new DeferredHandler();
	private Loader mLoader = new Loader();
	private boolean mBeforeFirstLoad = true;
	private WeakReference<Callbacks> mCallbacks;

	private AllAppsList mAllAppsList;
	private IconCache mIconCache;

	private Bitmap mDefaultIcon;

	// added by ghchen
	static private int mCurfavSort = -2;// original -1

	public interface Callbacks {
		public int getCurrentWorkspaceScreen();

		public void startBinding();

		public void bindButtons(ArrayList<ItemInfo> buttons);

		public void finishBindingItems();

	}

	// added by ghchen
	static public int GetCurFavSort() {
		return mCurfavSort;
	}

	// added by ghchen
	public void setCurFavSort(int nFavsort) {
		mCurfavSort = nFavsort;
	}

	private boolean IsEmptysite(int cellX, int cellY,
			ArrayList<CoordParameter> paramers, int nOffset) {

		if (null == paramers)
			return false;

		int nSize = paramers.size();
		boolean bflag = true;
		for (int i = nOffset; i < nSize; i++) {
			if (paramers.get(i).cellX == cellX
					&& cellY == paramers.get(i).cellY) {
				bflag = false;
				break;
			}
		}
		return bflag;
	}

	public CoordParameter CalculateCoordinate(Context context, int nType) {
		CoordParameter retParameter = null;
		ArrayList<CoordParameter> Recordarray = GetAllRecordByType(context,
				nType);
		int nAllShortviewNum = Recordarray.size();

		if ((nAllShortviewNum % 16) == 0) {
			retParameter = new CoordParameter();
			retParameter.cellX = 0;
			retParameter.cellY = 0;
			retParameter.screen = nAllShortviewNum / 16 + 1;

			return retParameter;
		}

		int nScreenNum = (nAllShortviewNum + 16 - 1) / 16;

		retParameter = new CoordParameter();
		retParameter.screen = nScreenNum;
		int nOffset = (nScreenNum - 1) * 16;
		for (int j = 0; j <= 3; j++) {
			for (int k = 0; k <= 3; k++) {
				if (IsEmptysite(k, j, Recordarray, nOffset)) {
					retParameter.cellX = k;
					retParameter.cellY = j;
					return retParameter;
				}
			}
		}

		return retParameter;
	}

	private ArrayList<CoordParameter> GetAllRecordByType(Context context,
			int nType) {

		if (null == context)
			return null;
		ArrayList<CoordParameter> retItems = new ArrayList<CoordParameter>();

		final ContentResolver contentResolver = context.getContentResolver();
		String swhere = "favsort = ";
		swhere = swhere + nType;

		final Cursor c = contentResolver.query(
				LauncherSettings.Favorites.CONTENT_URI, null, swhere, null,
				LauncherSettings.Favorites.SCREEN + " asc");

		try {
			final int screenIndex = c
					.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
			final int cellXIndex = c
					.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
			final int cellYIndex = c
					.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);

			while (c != null && c.moveToNext()) {
				CoordParameter info = new CoordParameter();
				info.screen = c.getInt(screenIndex);
				info.cellX = c.getInt(cellXIndex);
				info.cellY = c.getInt(cellYIndex);
				retItems.add(info);

			}
		} finally {
			c.close();
		}

		return retItems;
	}

	LauncherModel(LauncherApplication app, IconCache iconCache) {
		mApp = app;
		mAllAppsList = new AllAppsList(iconCache);
		mIconCache = iconCache;

		mDefaultIcon = Utilities.createIconBitmap(app.getPackageManager()
				.getDefaultActivityIcon(), app);
	}

	public Bitmap getDefaultIcon() {
		return Bitmap.createBitmap(mDefaultIcon);
	}

	/**
	 * Adds an item to the DB if it was not created previously, or move it to a
	 * new <container, screen, cellX, cellY>
	 */
	static void addOrMoveItemInDatabase(Context context, ItemInfo item,
			long container, int screen, int cellX, int cellY, int nfavsort) {
		if (item.container == ItemInfo.NO_ID) {
			// From all apps
			addItemToDatabase(context, item, container, screen, cellX, cellY,
					false, nfavsort);
		} else {
			// From somewhere else
			moveItemInDatabase(context, item, container, screen, cellX, cellY,
					nfavsort);
		}
	}

	/**
	 * Move an item in the DB to a new <container, screen, cellX, cellY>
	 */
	static void moveItemInDatabase(Context context, ItemInfo item,
			long container, int screen, int cellX, int cellY, int nfavsort) {
		item.container = container;
		item.screen = screen;
		item.cellX = cellX;
		item.cellY = cellY;
		item.favsort = nfavsort;

		final ContentValues values = new ContentValues();
		final ContentResolver cr = context.getContentResolver();

		values.put(LauncherSettings.Favorites.CONTAINER, item.container);
		values.put(LauncherSettings.Favorites.CELLX, item.cellX);
		values.put(LauncherSettings.Favorites.CELLY, item.cellY);
		values.put(LauncherSettings.Favorites.SCREEN, item.screen);
		values.put(LauncherSettings.Favorites.FAVSORT, item.favsort);

		cr.update(LauncherSettings.Favorites.getContentUri(item.id, false,
				Launcher2defineProvider.TABLE_FAVORITES), values, null, null);
	}

	/**
	 * Returns true if the shortcuts already exists in the database. we identify
	 * a shortcut by its title and intent.
	 */
	static boolean shortcutExists(Context context, String title, Intent intent) {
		final ContentResolver cr = context.getContentResolver();
		Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI,
				new String[] { "title", "intent" }, "title=? and intent=?",
				new String[] { title, intent.toUri(0) }, null);
		boolean result = false;
		try {
			result = c.moveToFirst();
		} finally {
			c.close();
		}
		return result;
	}

	/**
	 * Add an item to the database in a specified container. Sets the container,
	 * screen, cellX and cellY fields of the item. Also assigns an ID to the
	 * item.
	 */
	static void addItemToDatabase(Context context, ItemInfo item,
			long container, int screen, int cellX, int cellY, boolean notify,
			int nfavsort) {
		item.container = container;
		item.screen = screen;
		item.cellX = cellX;
		item.cellY = cellY;
		item.favsort = nfavsort;
		item.frequency = 0;

		final ContentValues values = new ContentValues();
		final ContentResolver cr = context.getContentResolver();

		item.onAddToDatabase(values);

		Uri result = cr.insert(notify ? LauncherSettings.Favorites.CONTENT_URI
				: LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION,
				values);

		if (result != null) {
			item.id = Integer.parseInt(result.getPathSegments().get(1));
		}
	}

	/**
	 * Update an item to the database in a specified container.
	 */
	static void updateItemInDatabase(Context context, ItemInfo item) {
		final ContentValues values = new ContentValues();
		final ContentResolver cr = context.getContentResolver();

		item.onAddToDatabase(values);

		cr.update(LauncherSettings.Favorites.getContentUri(item.id, false,
				Launcher2defineProvider.TABLE_FAVORITES), values, null, null);
	}

	/**
	 * Removes the specified item from the database
	 * 
	 * @param context
	 * @param item
	 */
	static void deleteItemFromDatabase(Context context, ItemInfo item) {
		final ContentResolver cr = context.getContentResolver();

		cr.delete(LauncherSettings.Favorites.getContentUri(item.id, false,
				Launcher2defineProvider.TABLE_FAVORITES), null, null);
	}

	/**
	 * Set this as the current Launcher activity object for the loader.
	 */
	public void initialize(Callbacks callbacks) {
		synchronized (mLock) {
			mCallbacks = new WeakReference<Callbacks>(callbacks);
		}
	}

	public void startLoader(Context context, boolean isLaunching) {
		mLoader.startLoader(context, isLaunching);
	}

	public void stopLoader() {
		mLoader.stopLoader();
	}

	/**
	 * We pick up most of the changes to all apps.
	 */
	public void setAllAppsDirty() {
		mLoader.setAllAppsDirty();
	}

	public void setWorkspaceDirty() {
		mLoader.setWorkspaceDirty();
	}

	/**
	 * Call from the handler for ACTION_PACKAGE_ADDED, ACTION_PACKAGE_REMOVED
	 * and ACTION_PACKAGE_CHANGED.
	 */
	public void onReceive(Context context, Intent intent) {
		// Use the app as the context.
		context = mApp;

		final String packageName = intent.getData().getSchemeSpecificPart();

		ArrayList<ApplicationInfo> added = null;
		ArrayList<ApplicationInfo> removed = null;
		ArrayList<ApplicationInfo> modified = null;

		synchronized (mLock) {
			if (mBeforeFirstLoad) {
				// If we haven't even loaded yet, don't bother, since we'll just
				// pick
				// up the changes.
				return;
			}

			final String action = intent.getAction();
			final boolean replacing = intent.getBooleanExtra(
					Intent.EXTRA_REPLACING, false);

			if (packageName == null || packageName.length() == 0) {
				// they sent us a bad intent
				return;
			}

			if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
				mAllAppsList.updatePackage(context, packageName);
			} else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
				if (!replacing) {
					mAllAppsList.removePackage(packageName);
				}
				// else, we are replacing the package, so a PACKAGE_ADDED will
				// be sent
				// later, we will update the package at this time
			} else {
				if (!replacing) {
					mAllAppsList.addPackage(context, packageName);
				} else {
					mAllAppsList.updatePackage(context, packageName);
				}
			}

			if (mAllAppsList.added.size() > 0) {
				added = mAllAppsList.added;
				mAllAppsList.added = new ArrayList<ApplicationInfo>();
			}
			if (mAllAppsList.removed.size() > 0) {
				removed = mAllAppsList.removed;
				mAllAppsList.removed = new ArrayList<ApplicationInfo>();
				for (ApplicationInfo info : removed) {
					mIconCache.remove(info.intent.getComponent());
				}
			}
			if (mAllAppsList.modified.size() > 0) {
				modified = mAllAppsList.modified;
				mAllAppsList.modified = new ArrayList<ApplicationInfo>();
			}

			final Callbacks callbacks = mCallbacks != null ? mCallbacks.get()
					: null;
			if (callbacks == null) {
				Log
						.w(TAG,
								"Nobody to tell about the new app.  Launcher is probably loading.");
				return;
			}

		}
	}

	public class Loader {
		private static final int ITEMS_CHUNK = 6;

		private LoaderThread mLoaderThread;

		private int mLastWorkspaceSeq = 0;
		private int mWorkspaceSeq = 1;

		private int mLastAllAppsSeq = 0;
		private int mAllAppsSeq = 1;

		final ArrayList<ItemInfo> mItems = new ArrayList<ItemInfo>();
		// final HashMap<Long, FolderInfo> mFolders = new HashMap<Long,
		// FolderInfo>();
		final ArrayList<ItemInfo> mButtonItems = new ArrayList<ItemInfo>();

		/**
		 * Call this from the ui thread so the handler is initialized on the
		 * correct thread.
		 */
		public Loader() {
		}

		public void startLoader(Context context, boolean isLaunching) {
			synchronized (mLock) {
				if (DEBUG_LOADERS) {
					Log.d(TAG, "startLoader isLaunching=" + isLaunching);
				}
				// Don't bother to start the thread if we know it's not going to
				// do anything
				if (mCallbacks.get() != null) {
					LoaderThread oldThread = mLoaderThread;
					if (oldThread != null) {
						if (oldThread.isLaunching()) {
							// don't downgrade isLaunching if we're already
							// running
							isLaunching = true;
						}
						oldThread.stopLocked();
					}
					mLoaderThread = new LoaderThread(context, oldThread,
							isLaunching);
					mLoaderThread.start();
				}
			}
		}

		public void stopLoader() {
			synchronized (mLock) {
				if (mLoaderThread != null) {
					mLoaderThread.stopLocked();
				}
			}
		}

		public void setWorkspaceDirty() {
			synchronized (mLock) {
				mWorkspaceSeq++;
			}
		}

		public void setAllAppsDirty() {
			synchronized (mLock) {
				mAllAppsSeq++;
			}
		}

		/**
		 * Runnable for the thread that loads the contents of the launcher: -
		 * workspace icons - widgets - all apps icons
		 */
		private class LoaderThread extends Thread {
			private Context mContext;
			private Thread mWaitThread;
			private boolean mIsLaunching;
			private boolean mStopped;
			private boolean mWorkspaceDoneBinding;

			LoaderThread(Context context, Thread waitThread, boolean isLaunching) {
				mContext = context;
				mWaitThread = waitThread;
				mIsLaunching = isLaunching;
			}

			boolean isLaunching() {
				return mIsLaunching;
			}

			/**
			 * If another LoaderThread was supplied, we need to wait for that to
			 * finish before we start our processing. This keeps the ordering of
			 * the setting and clearing of the dirty flags correct by making
			 * sure we don't start processing stuff until they've had a chance
			 * to re-set them. We do this waiting the worker thread, not the ui
			 * thread to avoid ANRs.
			 */
			private void waitForOtherThread() {
				if (mWaitThread != null) {
					boolean done = false;
					while (!done) {
						try {
							mWaitThread.join();
							done = true;
						} catch (InterruptedException ex) {
							// Ignore
						}
					}
					mWaitThread = null;
				}
			}

			public void run() {
				waitForOtherThread();

				// Elevate priority when Home launches for the first time to
				// avoid
				// starving at boot time. Staring at a blank home is not cool.
				synchronized (mLock) {
					android.os.Process
							.setThreadPriority(mIsLaunching ? Process.THREAD_PRIORITY_DEFAULT
									: Process.THREAD_PRIORITY_BACKGROUND);
				}

				// Load the workspace only if it's dirty.
				int workspaceSeq;
				boolean workspaceDirty;
				synchronized (mLock) {
					workspaceSeq = mWorkspaceSeq;
					workspaceDirty = mWorkspaceSeq != mLastWorkspaceSeq;
				}
				if (workspaceDirty) {
					loadWorkspace();
				}
				synchronized (mLock) {
					// If we're not stopped, and nobody has incremented
					// mWorkspaceSeq.
					if (mStopped) {
						return;
					}
					if (workspaceSeq == mWorkspaceSeq) {
						mLastWorkspaceSeq = mWorkspaceSeq;
					}
				}

				// Bind the workspace
				bindWorkspace();

				// Wait until the either we're stopped or the other threads are
				// done.
				// This way we don't start loading all apps until the workspace
				// has settled
				// down.
				synchronized (LoaderThread.this) {
					mHandler.postIdle(new Runnable() {
						public void run() {
							synchronized (LoaderThread.this) {
								mWorkspaceDoneBinding = true;
								if (DEBUG_LOADERS) {
									Log.d(TAG, "done with workspace");
								}
								LoaderThread.this.notify();
							}
						}
					});
					if (DEBUG_LOADERS) {
						Log.d(TAG, "waiting to be done with workspace");
					}
					while (!mStopped && !mWorkspaceDoneBinding) {
						try {
							this.wait();
						} catch (InterruptedException ex) {
							// Ignore
						}
					}
					if (DEBUG_LOADERS) {
						Log.d(TAG, "done waiting to be done with workspace");
					}
				}

				// Load all apps if they're dirty
				int allAppsSeq;
				boolean allAppsDirty;
				synchronized (mLock) {
					allAppsSeq = mAllAppsSeq;
					allAppsDirty = mAllAppsSeq != mLastAllAppsSeq;
					if (DEBUG_LOADERS) {
						Log.d(TAG, "mAllAppsSeq=" + mAllAppsSeq
								+ " mLastAllAppsSeq=" + mLastAllAppsSeq
								+ " allAppsDirty");
					}
				}
				if (allAppsDirty) {
					loadAllApps();
				}
				synchronized (mLock) {
					// If we're not stopped, and nobody has incremented
					// mAllAppsSeq.
					if (mStopped) {
						return;
					}
					if (allAppsSeq == mAllAppsSeq) {
						mLastAllAppsSeq = mAllAppsSeq;
					}
				}

				// Clear out this reference, otherwise we end up holding it
				// until all of the
				// callback runnables are done.
				mContext = null;

				synchronized (mLock) {
					// Setting the reference is atomic, but we can't do it
					// inside the other critical
					// sections.
					mLoaderThread = null;
				}
			}

			public void stopLocked() {
				synchronized (LoaderThread.this) {
					mStopped = true;
					this.notify();
				}
			}

			/**
			 * Gets the callbacks object. If we've been stopped, or if the
			 * launcher object has somehow been garbage collected, return null
			 * instead.
			 */
			Callbacks tryGetCallbacks() {
				synchronized (mLock) {
					if (mStopped) {
						return null;
					}

					final Callbacks callbacks = mCallbacks.get();
					if (callbacks == null) {
						Log.w(TAG, "no mCallbacks");
						return null;
					}

					return callbacks;
				}
			}

			private int loadWorkspace() {
				long t = SystemClock.uptimeMillis();

				final Context context = mContext;
				final ContentResolver contentResolver = context
						.getContentResolver();
				int nShortcutNum = 0;

				mItems.clear();
				mButtonItems.clear();
				// mFolders.clear();

				final ArrayList<Long> itemsToRemove = new ArrayList<Long>();

				// added by ghchen
				String swhere = "";
				swhere = swhere + "screen >";
				swhere = swhere + " -1 ";

				swhere = swhere + " OR ";
				swhere = swhere + "itemType = ";
				swhere = swhere
						+ LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
				swhere = swhere + " OR ";
				swhere = swhere + "itemType = ";
				swhere = swhere
						+ LauncherSettings.Favorites.ITEM_TYPE_WIDGET_SEARCH;

				final Cursor c = contentResolver.query(
						LauncherSettings.Favorites.CONTENT_URI, null, swhere,
						null, null);

				try {
					final int idIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
					final int intentIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
					final int titleIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
					final int iconTypeIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_TYPE);
					final int iconIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
					final int iconPackageIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_PACKAGE);
					final int iconResourceIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_RESOURCE);
					final int containerIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
					final int itemTypeIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
					final int appWidgetIdIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.APPWIDGET_ID);
					final int screenIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
					final int cellXIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
					final int cellYIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
					final int spanXIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANX);
					final int spanYIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANY);
					final int uriIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.URI);
					final int displayModeIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.DISPLAY_MODE);
					final int favSortIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.FAVSORT);
					final int frequencyIndex = c
							.getColumnIndexOrThrow(LauncherSettings.Favorites.FREQUENCY);

					Widget widgetInfo;
					int container;
					long id;

					while (!mStopped && c.moveToNext()) {
						try {
							int itemType = c.getInt(itemTypeIndex);

							switch (itemType) {

							case LauncherSettings.Favorites.ITEM_TYPE_WIDGET_SEARCH:
								widgetInfo = Widget.makeSearch();

								container = c.getInt(containerIndex);
								if (container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
									Log
											.e(
													TAG,
													"Widget found where container "
															+ "!= CONTAINER_DESKTOP  ignoring!");
									continue;
								}

								widgetInfo.id = c.getLong(idIndex);
								widgetInfo.screen = c.getInt(screenIndex);
								widgetInfo.container = container;
								widgetInfo.cellX = c.getInt(cellXIndex);
								widgetInfo.cellY = c.getInt(cellYIndex);

								mItems.add(widgetInfo);
								break;
							case LauncherSettings.Favorites.ITEM_TYPE_BUTTON:
								id = c.getLong(idIndex);
								ItemInfo itemInfo = new ItemInfo();
								itemInfo.id = id;
								container = c.getInt(containerIndex);
								itemInfo.container = container;
								itemInfo.screen = c.getInt(screenIndex);
								itemInfo.cellX = c.getInt(cellXIndex);
								itemInfo.cellY = c.getInt(cellYIndex);
								itemInfo.spanX = c.getInt(spanXIndex);
								itemInfo.spanY = c.getInt(spanYIndex);
                                itemInfo.title = c.getString(titleIndex);
								switch (container) {
								case LauncherSettings.Favorites.CONTAINER_DESKTOP:
									mButtonItems.add(itemInfo);
									break;
								}

								break;

							}
						} catch (Exception e) {
							Log.w(TAG, "Desktop items loading interrupted:", e);
						}
					}
				} finally {
					c.close();
				}

				if (itemsToRemove.size() > 0) {
					ContentProviderClient client = contentResolver
							.acquireContentProviderClient(LauncherSettings.Favorites.CONTENT_URI);
					// Remove dead items
					for (long id : itemsToRemove) {
						if (DEBUG_LOADERS) {
							Log.d(TAG, "Removed id = " + id);
						}
						// Don't notify content observers
						try {
							client
									.delete(
											LauncherSettings.Favorites
													.getContentUri(
															id,
															false,
															Launcher2defineProvider.TABLE_FAVORITES),
											null, null);
						} catch (RemoteException e) {
							Log.w(TAG, "Could not remove id = " + id);
						}
					}
				}

				if (DEBUG_LOADERS) {
					Log.d(TAG, "loaded workspace in "
							+ (SystemClock.uptimeMillis() - t) + "ms");
				}
				return nShortcutNum;
			}

			/**
			 * Read everything out of our database.
			 */
			private void bindWorkspace() {
				final long t = SystemClock.uptimeMillis();

				// Don't use these two variables in any of the callback
				// runnables.
				// Otherwise we hold a reference to them.
				Callbacks callbacks = mCallbacks.get();
				if (callbacks == null) {
					// This launcher has exited and nobody bothered to tell us.
					// Just bail.
					Log.w(TAG, "LoaderThread running with no launcher");
					return;
				}

				// Tell the workspace that we're about to start firing items at
				// it
				mHandler.post(new Runnable() {
					public void run() {
						Callbacks callbacks = tryGetCallbacks();
						if (callbacks != null) {
							callbacks.startBinding();
						}
					}
				});
				mHandler.post(new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						Callbacks callbacks = tryGetCallbacks();
						if (callbacks != null) {
							callbacks.bindButtons(mButtonItems);
						}
					}
				});
				// Wait until the queue goes empty.
				mHandler.postIdle(new Runnable() {
					public void run() {
						if (DEBUG_LOADERS) {
							Log.d(TAG, "Going to start binding widgets soon.");
						}
					}
				});

				// Tell the workspace that we're done.
				mHandler.post(new Runnable() {
					public void run() {
						Callbacks callbacks = tryGetCallbacks();
						if (callbacks != null) {
							callbacks.finishBindingItems();
						}
					}
				});
				// If we're profiling, this is the last thing in the queue.
				mHandler.post(new Runnable() {
					public void run() {
						if (DEBUG_LOADERS) {
							Log.d(TAG, "bound workspace in "
									+ (SystemClock.uptimeMillis() - t) + "ms");
						}
						if (Launcher.PROFILE_ROTATE) {
							android.os.Debug.stopMethodTracing();
						}
					}
				});
			}

			private void loadAllApps() {
				final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
				mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

				final Callbacks callbacks = tryGetCallbacks();
				if (callbacks == null) {
					return;
				}

				final PackageManager packageManager = mContext
						.getPackageManager();
				final List<ResolveInfo> apps = packageManager
						.queryIntentActivities(mainIntent, 0);

				synchronized (mLock) {
					mBeforeFirstLoad = false;

					mAllAppsList.clear();
					if (apps != null) {
						long t = SystemClock.uptimeMillis();

						int N = apps.size();
						for (int i = 0; i < N && !mStopped; i++) {
							// This builds the icon bitmaps.
							mAllAppsList.add(new ApplicationInfo(apps.get(i),
									mIconCache));
						}
						Collections
								.sort(mAllAppsList.data, APP_NAME_COMPARATOR);
						Collections.sort(mAllAppsList.added,
								APP_NAME_COMPARATOR);
						if (DEBUG_LOADERS) {
							Log.d(TAG, "cached app icons in "
									+ (SystemClock.uptimeMillis() - t) + "ms");
						}
					}
				}
			}

			public void dumpState() {
				Log.d(TAG, "mLoader.mLoaderThread.mContext=" + mContext);
				Log.d(TAG, "mLoader.mLoaderThread.mWaitThread=" + mWaitThread);
				Log
						.d(TAG, "mLoader.mLoaderThread.mIsLaunching="
								+ mIsLaunching);
				Log.d(TAG, "mLoader.mLoaderThread.mStopped=" + mStopped);
				Log.d(TAG, "mLoader.mLoaderThread.mWorkspaceDoneBinding="
						+ mWorkspaceDoneBinding);
			}
		}

		public void dumpState() {
			Log
					.d(TAG, "mLoader.mLastWorkspaceSeq="
							+ mLoader.mLastWorkspaceSeq);
			Log.d(TAG, "mLoader.mWorkspaceSeq=" + mLoader.mWorkspaceSeq);
			Log.d(TAG, "mLoader.mLastAllAppsSeq=" + mLoader.mLastAllAppsSeq);
			Log.d(TAG, "mLoader.mAllAppsSeq=" + mLoader.mAllAppsSeq);
			Log.d(TAG, "mLoader.mItems size=" + mLoader.mItems.size());
			if (mLoaderThread != null) {
				mLoaderThread.dumpState();
			} else {
				Log.d(TAG, "mLoader.mLoaderThread=null");
			}
		}
	}

	private static final Collator sCollator = Collator.getInstance();
	public static final Comparator<ApplicationInfo> APP_NAME_COMPARATOR = new Comparator<ApplicationInfo>() {
		public final int compare(ApplicationInfo a, ApplicationInfo b) {
			return sCollator.compare(a.title.toString(), b.title.toString());
		}
	};

	public void dumpState() {
		Log.d(TAG, "mBeforeFirstLoad=" + mBeforeFirstLoad);
		Log.d(TAG, "mCallbacks=" + mCallbacks);
		ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList.data",
				mAllAppsList.data);
		ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList.added",
				mAllAppsList.added);
		ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList.removed",
				mAllAppsList.removed);
		ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList.modified",
				mAllAppsList.modified);
		mLoader.dumpState();
	}
}
