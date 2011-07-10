/**
 * 
 */
package me.uframer.android.ui;

import android.content.Context;
import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * <p>
 * This view clones the size and look of another view but accept no inputs.
 * </p>
 * @author jiaoye
 *
 */
class MirageView extends View {

	private static final String LOG_TAG = MirageView.class.toString();

	private View mView;

	private int mViewId;
	private Canvas mCanvas;
	private Bitmap mBitmap;
	private boolean mFrozen;

	/**
	 * @param context
	 */
	public MirageView(Context context, View view) {
		super(context);
		mView = view;
	}

	/**
	 * @param context
	 * @param attrs
	 */
	public MirageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initializeMirageView(context, attrs, 0);
	}

	/**
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	public MirageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initializeMirageView(context, attrs, defStyle);
	}
	
	
	private void initializeMirageView(Context context, AttributeSet attrs, int defStyle) {
		TypedArray ta;
		// parse attributes
		if (attrs != null) {
			ta = context.obtainStyledAttributes(attrs, R.styleable.MirageView, defStyle, 0);
			mViewId = ta.getResourceId(R.styleable.MirageView_cloneView, 0);
			ta.recycle();
		}
		
		mFrozen = false;
	}
	
	void setView(View view) {
		mView = view;
	}
	
	View getView() {
		if (mView == null) {
			mView = ((Activity) getContext()).findViewById(mViewId);
		}
		return mView;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (mFrozen) {
			setMeasuredDimension(mBitmap.getWidth(), mBitmap.getHeight());
		}
		else {
			final View v = getView();
			v.measure(widthMeasureSpec, heightMeasureSpec);
			setMeasuredDimension(v.getMeasuredWidth(), v.getMeasuredHeight());
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (mFrozen) {
			canvas.drawBitmap(mBitmap, 0, 0, null);
		}
		else {
			getView().draw(canvas);
		}
	}
	
	// before you freeze this view, the original view must be measured
	public void freeze() {
		final View v = getView();
		mCanvas = new Canvas();
		mBitmap = Bitmap.createBitmap(v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
		mCanvas.setBitmap(mBitmap);
		v.draw(mCanvas);
		mFrozen = true;
	}
	
	public void unfreeze() {
		mCanvas = null;
		mBitmap.recycle();
		mBitmap = null;
		mFrozen = false;
	}
}
