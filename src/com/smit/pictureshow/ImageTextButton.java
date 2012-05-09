package com.smit.pictureshow;

import com.smit.pictureshow.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;

public class ImageTextButton extends ImageButton {
	
	 private Paint mPaint;
	 private String str;

	 private final String namespace = "http://www.smit.com.cn";  
	 private int resourceId =0;  
	 private Bitmap bitmap;  
	 
	 public ImageTextButton(Context context, AttributeSet attrs, int defStyle) {
	  super(context, attrs, defStyle);
	  // TODO Auto-generated constructor stub
	 }

	 public ImageTextButton(Context context, AttributeSet attrs) {
	  super(context, attrs);
	  // TODO Auto-generated constructor stub
	  mPaint = new Paint();
	  mPaint.setColor(Color.WHITE);
	  mPaint.setTextSize(15);
	  TypedArray a = context
	    .obtainStyledAttributes(attrs, R.styleable.MyView);
	  str = a.getString(R.styleable.MyView_text);
	
	  Drawable dr = this.getDrawable().getCurrent();
	  bitmap = ((BitmapDrawable)dr).getBitmap();
	  a.recycle();
	  
	  
	  /*resourceId = attrs.getAttributeResourceValue(namespace, "src", R.drawable.test6);  
	  bitmap = BitmapFactory.decodeResource(getResources(), resourceId);
	  Log.e("ImageTextButton","bitmap.getWidth()=="+bitmap.getWidth()+"   bitmap.getHeight()=="+bitmap.getHeight());*/
	 }

	 public ImageTextButton(Context context) {
	  super(context);
	  // TODO Auto-generated constructor stub
	 }

	@Override
	protected void onDraw(Canvas canvas) {	
		//Í¼Æ¬¶¥²¿¾ÓÖÐÏÔÊ¾
		int x = (this.getMeasuredWidth() - bitmap.getWidth()) >> 1;
		int y = 0;
		canvas.drawBitmap(bitmap, x, y, null);
		canvas.drawText(str,20,88,mPaint);
	}
	 
	 public void setIcon(Bitmap bitmap){  
	        this.bitmap=bitmap;  
		    invalidate(); 
		    }
	 
	 public void setIcon(int resourceId){  
		   this.bitmap=BitmapFactory.decodeResource(getResources(), resourceId);  
		   invalidate();  
		 }
}
