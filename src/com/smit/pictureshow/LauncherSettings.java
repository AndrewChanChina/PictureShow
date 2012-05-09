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


import android.provider.BaseColumns;
import android.net.Uri;

/**
 * Settings related utilities.
 */
class LauncherSettings {
    static interface BaseLauncherColumns extends BaseColumns {
        /**
         * Descriptive name of the gesture that can be displayed to the user.
         * <P>Type: TEXT</P>
         */
        static final String TITLE = "title";

        /**
         * The Intent URL of the gesture, describing what it points to. This
         * value is given to {@link android.content.Intent#parseUri(String, int)} to create
         * an Intent that can be launched.
         * <P>Type: TEXT</P>
         */
        static final String INTENT = "intent";

        /**
         * The type of the gesture
         *
         * <P>Type: INTEGER</P>
         */
        static final String ITEM_TYPE = "itemType";

        /**
         * The gesture is an application
         */
        static final int ITEM_TYPE_APPLICATION = 0;

        /**
         * The gesture is an application created shortcut
         */
        static final int ITEM_TYPE_SHORTCUT = 1;

        /**
         * The icon type.
         * <P>Type: INTEGER</P>
         */
        static final String ICON_TYPE = "iconType";

        /**
         * The icon is a resource identified by a package name and an integer id.
         */
        static final int ICON_TYPE_RESOURCE = 0;

        /**
         * The icon is a bitmap.
         */
        static final int ICON_TYPE_BITMAP = 1;

        /**
         * The icon package name, if icon type is ICON_TYPE_RESOURCE.
         * <P>Type: TEXT</P>
         */
        static final String ICON_PACKAGE = "iconPackage";

        /**
         * The icon resource id, if icon type is ICON_TYPE_RESOURCE.
         * <P>Type: TEXT</P>
         */
        static final String ICON_RESOURCE = "iconResource";

        /**
         * The custom icon bitmap, if icon type is ICON_TYPE_BITMAP.
         * <P>Type: BLOB</P>
         */
        static final String ICON = "icon";
    }

    /**
     * Favorites.
     */
    static final class Favorites implements BaseLauncherColumns {
        /**
         * The content:// style URL for this table
         */
        static final Uri CONTENT_URI = Uri.parse("content://" +
                Launcher2defineProvider.AUTHORITY + "/" + Launcher2defineProvider.TABLE_FAVORITES +
                "?" + Launcher2defineProvider.PARAMETER_NOTIFY + "=true");

        static final Uri CONTENT_URI_LATEST = Uri.parse("content://" +
                Launcher2defineProvider.AUTHORITY + "/" + Launcher2defineProvider.TABLE_LATEST +
                "?" + Launcher2defineProvider.PARAMETER_NOTIFY + "=true");
        
        /**
         * The content:// style URL for this table. When this Uri is used, no notification is
         * sent if the content changes.
         */
        static final Uri CONTENT_URI_NO_NOTIFICATION = Uri.parse("content://" +
                Launcher2defineProvider.AUTHORITY + "/" + Launcher2defineProvider.TABLE_FAVORITES +
                "?" + Launcher2defineProvider.PARAMETER_NOTIFY + "=false");

        static final Uri CONTENT_URI_NO_NOTIFICATION_POSITION = Uri.parse("content://" +
        		Launcher2defineProvider.AUTHORITY + "/" + Launcher2defineProvider.TABLE_POSITIONORDER +
                "?" + Launcher2defineProvider.PARAMETER_NOTIFY + "=false");
        
        static final Uri CONTENT_URI_POSITIONORDER = Uri.parse("content://" +
        		Launcher2defineProvider.AUTHORITY + "/" + Launcher2defineProvider.TABLE_POSITIONORDER +
                "?" + Launcher2defineProvider.PARAMETER_NOTIFY + "=true");
        /**
         * The content:// style URL for a given row, identified by its id.
         *
         * @param id The row id.
         * @param notify True to send a notification is the content changes.
         *
         * @return The unique content URL for the specified row.
         */
        static Uri getContentUri(long id, boolean notify) {
            return Uri.parse("content://" + Launcher2defineProvider.AUTHORITY +
                    "/" + Launcher2defineProvider.TABLE_FAVORITES + "/" + id + "?" +
                    Launcher2defineProvider.PARAMETER_NOTIFY + "=" + notify);
        }
        static Uri getContentUri(long id, boolean notify,String tablename) {
            return Uri.parse("content://" + Launcher2defineProvider.AUTHORITY +
                    "/" + tablename + "/" + id + "?" +
                    Launcher2defineProvider.PARAMETER_NOTIFY + "=" + notify);
        }

        static Uri getContentUri_Latest(long id, boolean notify) {
            return Uri.parse("content://" + Launcher2defineProvider.AUTHORITY +
                    "/" + Launcher2defineProvider.TABLE_LATEST + "/" + id + "?" +
                    Launcher2defineProvider.PARAMETER_NOTIFY + "=" + notify);
        }
        
        /**
         * The container holding the favorite
         * <P>Type: INTEGER</P>
         */
        static final String CONTAINER = "container";

        /**
         * The icon is a resource identified by a package name and an integer id.
         */
        static final int CONTAINER_DESKTOP = -100;

        /**
         * The screen holding the favorite (if container is CONTAINER_DESKTOP)
         * <P>Type: INTEGER</P>
         */
        static final String SCREEN = "screen";

        /**
         * The X coordinate of the cell holding the favorite
         * (if container is CONTAINER_DESKTOP or CONTAINER_DOCK)
         * <P>Type: INTEGER</P>
         */
        static final String CELLX = "cellX";

        /**
         * The Y coordinate of the cell holding the favorite
         * (if container is CONTAINER_DESKTOP)
         * <P>Type: INTEGER</P>
         */
        static final String CELLY = "cellY";

        /**
         * The X span of the cell holding the favorite
         * <P>Type: INTEGER</P>
         */
        static final String SPANX = "spanX";

        /**
         * The Y span of the cell holding the favorite
         * <P>Type: INTEGER</P>
         */
        static final String SPANY = "spanY";

        /**
         * The favorite is a user created folder
         */
        static final int ITEM_TYPE_USER_FOLDER = 2;

        /**
         * The favorite is a live folder
         */
        static final int ITEM_TYPE_LIVE_FOLDER = 3;

        /**
         * The favorite is a widget
         */
        static final int ITEM_TYPE_APPWIDGET = 4;

        /**
         * The favorite is a clock
         */
        static final int ITEM_TYPE_WIDGET_CLOCK = 1000;

        /**
         * The favorite is a search widget
         */
        static final int ITEM_TYPE_WIDGET_SEARCH = 1001;

        /**
         * The favorite is a photo frame
         */
        static final int ITEM_TYPE_WIDGET_PHOTO_FRAME = 1002;
        
        static final int ITEM_TYPE_BUTTON = 1003;

        /**
         * The appWidgetId of the widget
         *
         * <P>Type: INTEGER</P>
         */
        static final String APPWIDGET_ID = "appWidgetId";
        
        /**
         * Indicates whether this favorite is an application-created shortcut or not.
         * If the value is 0, the favorite is not an application-created shortcut, if the
         * value is 1, it is an application-created shortcut.
         * <P>Type: INTEGER</P>
         */
        @Deprecated
        static final String IS_SHORTCUT = "isShortcut";

        /**
         * The URI associated with the favorite. It is used, for instance, by
         * live folders to find the content provider.
         * <P>Type: TEXT</P>
         */
        static final String URI = "uri";

        /**
         * The display mode if the item is a live folder.
         * <P>Type: INTEGER</P>
         *
         * @see android.provider.LiveFolders#DISPLAY_MODE_GRID
         * @see android.provider.LiveFolders#DISPLAY_MODE_LIST
         */
        static final String DISPLAY_MODE = "displayMode";
        
        //added by ghchen
        static final String  FAVSORT   = "favsort";
        static final String  FREQUENCY = "frequency";
        static final String  DATETIME  = "datetime";
        static final String  PACKAGENAME  = "packagename";
        static final String  ORDERINDEX   = "orderindex";
        static final String  TYPES        = "types";
          
        //应用类型分类
        static final int SORT_OFFICE    = 1;
        static final int SORT_MESHWORK  = 2;
        static final int SORT_AMUSEMENT = 3;
        static final int SORT_PERSONAL  = 4;
        static final int SORT_TOOLS     = 5;
        
        static final int SORT_MEDIA     = 6;
        static final int SORT_LATEST    = 7;  
        static final int SORT_HOME      = 8;
        static final int SORT_TV        = 9;
        static final int SORT_QQ        = 10;
        static final int SORT_GAME      = 11;
        static final int SORT_WEBSITE   = 12;
        static final int SORT_SETTING   = 13;
        
        static final int SORT_APPVIEWS    = 0;
        static final int SORT_USUALLY  = -1;
    }
}
