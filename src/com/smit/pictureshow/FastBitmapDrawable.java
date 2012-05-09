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

import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;

class FastBitmapDrawable extends Drawable {
    private Bitmap mBitmap;
    private Launcher mLauncher;
    FastBitmapDrawable(Bitmap b,Resources res) {
        mBitmap = b;
      // mBitmap=BitmapFactory.decodeResource(res, R.drawable.ic_launcher_alarmclock);
        Matrix matrix=new Matrix();//缩小图片大小
        //matrix.postScale(0.85f,0.85f);
        matrix.postScale(0.75f,0.75f);
        mBitmap=Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(mBitmap, 2.0f, 8.5f, null);//画图片,从空间的LEFT 2.0 ,TOP 6.0位置开始画
    	Log.i("==FastBitmapDrawable===draw=", "=============");
     /* Rect src=new Rect();
        src.set(0, 0,mBitmap.getWidth(),mBitmap.getHeight());
        Rect des=new Rect();
        Paint p=new Paint();
        p.setAntiAlias(true);
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG,0));
        
        
        des.set(0,0,mBitmap.getWidth()-6,mBitmap.getHeight()-6);
        canvas.drawBitmap(mBitmap, src, des,p);*/
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getIntrinsicWidth() {
        return mBitmap.getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mBitmap.getHeight();
    }

    @Override
    public int getMinimumWidth() {
        return mBitmap.getWidth();
    }

    @Override
    public int getMinimumHeight() {
        return mBitmap.getHeight();
    }

    public void setBitmap(Bitmap b) {
        mBitmap = b;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }
}
