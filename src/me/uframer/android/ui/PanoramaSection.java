/**
 * @auther jiaoye
 * @email uframer@gmail.comb
 */
package me.uframer.android.ui;

import me.uframer.android.ui.PanoramaView.LayoutParams;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author jiaoye
 *
 */
public class PanoramaSection extends ViewGroup {

	private static final String LOG_TAG = PanoramaSection.class.toString();

	private static final int INVALID_RESOURCE_ID = -1;

	private static final int DEFAULT_TITLE_COLOR = Color.WHITE;
	private static final int DEFAULT_TITLE_SIZE = 48;
	private static final int DEFAULT_TITLE_PADDING_LEFT = 22;

	private int mCustomHeaderId;
	private View mHeader;
	private View mContent;
	private String mTitle;
	private int mTitleColor;
	private Drawable mTitleIcon;
    private UIContext mUIContext;
        

	public PanoramaSection(Context context) {
		super(context);
		initializePanoramaItem(context, null, 0);
	}

	public PanoramaSection(Context context, AttributeSet attrs) {
		super(context, attrs);
		initializePanoramaItem(context, attrs, 0);
	}

	public PanoramaSection(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initializePanoramaItem(context, attrs, defStyle);
	}

	/**
	 * initialize PanoramaSection internally
	 */
	private void initializePanoramaItem(Context context, AttributeSet attrs, int defStyle) {
		if (attrs != null) {
			TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PanoramaSection, defStyle, 0);
			mCustomHeaderId = ta.getResourceId(R.styleable.PanoramaSection_customHeader, -1);
			mTitle = ta.getString(R.styleable.PanoramaSection_title);
			mTitleColor = ta.getColor(R.styleable.PanoramaSection_titleColor, DEFAULT_TITLE_COLOR);
			mTitleIcon = ta.getDrawable(R.styleable.PanoramaSection_icon);
			ta.recycle();
		}
		
        setWillNotDraw(false);
        setWillNotCacheDrawing(false);
        setDrawingCacheEnabled(true);
        mUIContext = UIContext.getUIContext(context);
	}
	
	@Override
	protected void onFinishInflate() {
		final int childCount = getChildCount();
		int start = 0;
		if (mCustomHeaderId != INVALID_RESOURCE_ID) {
			View ch = findViewById(mCustomHeaderId);
			if (childCount == 0 || ch == null)
				throw new Error("no valid custom header found in layout");
			else if (ch != getChildAt(0))
				throw new Error("custom header must be the first child of PanoramaView");
			else {
				mHeader = ch;
				start = 1;
			}
		}
		
		final int left = childCount - start;
		if (left > 1)
			throw new Error("PanoramaSection supports only one content view");
		else if (left == 1) {
			mContent = getChildAt(start);
		}

		// generate a header if no custom header provided
		if (mCustomHeaderId == INVALID_RESOURCE_ID || mHeader == null)
			generateHeader();
	}	

	/**
	 * Generate header panel according to mTitle. The generated header panel
	 *  will be inserted into PanoramaSection as the first child.
	 */
	private void generateHeader() {
		TextView tv = null;
		ImageView iv = null;
		LayoutParams lp = null;
		
		if (mTitle != null) {
			tv = new TextView(getContext());
			tv.setText(mTitle);
			tv.setTextColor(mTitleColor);
			tv.setSingleLine(true);
			tv.setHorizontallyScrolling(true);
			tv.setEllipsize(null);
			// FIXME android sdk does not allow using assets from Library Project, WTF
			tv.setTypeface(mUIContext.semilightTypeface);
			tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TITLE_SIZE);
			tv.setPadding(DEFAULT_TITLE_PADDING_LEFT, 0, 0, 0);
		}
		
		if (mTitleIcon != null) {
			iv = new ImageView(getContext());
			iv.setImageDrawable(mTitleIcon);
		}
		
		if (tv != null && iv != null) {
			LinearLayout ll = new LinearLayout(getContext());
			ll.setOrientation(LinearLayout.HORIZONTAL);
			lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			ll.addView(iv, lp);
			ll.addView(tv, lp);
			mHeader = ll;
		}
		else if (tv != null) {
			lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			mHeader = tv;
		}
		else if (iv != null) {
			lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			mHeader = iv;
		}
		else { // mTitle == null && mDrawable == null
			// use a plain View as a place holder
			mHeader = new View(getContext());
			lp = new LayoutParams(1, DEFAULT_TITLE_SIZE);
		}

		addView(mHeader, 0, lp);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		Log.v(LOG_TAG, "left padding is " + Integer.toString(getPaddingLeft()));
		mHeader.layout(getPaddingLeft(), getPaddingTop(), mHeader.getMeasuredWidth() + getPaddingLeft(), mHeader.getMeasuredHeight() + getPaddingTop());
		int offsetY = mHeader.getMeasuredHeight() + getPaddingTop();
		if (mContent != null) {
			mContent.layout(getPaddingLeft(), offsetY, mContent.getMeasuredWidth() + getPaddingLeft(), mContent.getMeasuredHeight() + offsetY);
		}
	}
	
    @Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		PanoramaView.LayoutParams lp = (PanoramaView.LayoutParams) this.getLayoutParams();
		int height;
		int width;

		// determine height
		switch (heightMode) {
		case MeasureSpec.AT_MOST:
			if (lp.height >= 0 && lp.height < heightSize) {
				height = lp.height;
			}
			else {
				height = heightSize;
			}
			break;
		case MeasureSpec.EXACTLY:
			height = heightSize;
			break;
		case MeasureSpec.UNSPECIFIED:
			if (lp.height >= 0) {
				height = lp.height;
			}
			else {
				// imaginary default value
				height = 416;
			}
			break;
		default:
			throw new Error("Unsupported measure spec mode.");
		}
		
    	// 1. measure header
		measureChild(mHeader,
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

		// 2. measure contents
		if (mContent != null) {
	 		measureChild(mContent,
					MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
					MeasureSpec.makeMeasureSpec(height - mHeader.getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.AT_MOST));
		}
		
		final int childrenMeasuredWidth = mContent == null ? mHeader.getMeasuredWidth() : Math.max(mHeader.getMeasuredWidth(), mContent.getMeasuredWidth());
		
		// determine width
		switch (widthMode) {
		case MeasureSpec.AT_MOST:
			if (lp.width >= 0 && lp.width < widthSize) {
				width = lp.width;
			}
			else {
				width = widthSize;
			}
			break;
		case MeasureSpec.EXACTLY:
			width = widthSize;
			break;
		case MeasureSpec.UNSPECIFIED:
			if (lp.width >= 0) {
				width = lp.width;
			}
			else {
				// if width is less than the suggested minimum, than take the minimum instead
				width = Math.max(childrenMeasuredWidth + getPaddingLeft() + getPaddingRight(), ((PanoramaView.LayoutParams) getLayoutParams()).sectionWidth);
			}
			break;
		default:
			throw new Error("Unsupported measure spec mode.");
		}

		setMeasuredDimension(width, height);
    }
    
    public View getHeader() {
    	return mHeader;
    }

}
