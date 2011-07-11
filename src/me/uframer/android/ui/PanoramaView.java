/**
 * @auther jiaoye
 * @email uframer@gmail.com
 */
package me.uframer.android.ui;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * This class provides a horizontal canvas that can exceed the screen limit. 
 * Users can pan or flick horizontally to navigate across the sections. Common section is 
 * slightly narrower than the screen so that users can have a peek at the next
 * page, and this can be used as a visual clue to tell users that there are more
 * contents. Once users navigate across the boundaries of the canvas, the canvas 
 * will wrap back.
 *
 * @author jiaoye
 */
public class PanoramaView extends ViewGroup implements AnimationListener {

	private static final String LOG_TAG = PanoramaView.class.toString();

	private static final int INVALID_POINTER = -1;
	private static final int INVALID_RESOURCE_ID = -1;
	
	private static final int DEFAULT_TITLE_COLOR = Color.WHITE;
	private static final int DEFAULT_TITLE_SIZE = 125;
	private static final int DEFAULT_TITLE_PADDING_LEFT = 10;
	private static final int DEFAULT_TITLE_PADDING_RIGHT = 10;
	private static final int DEFAULT_TITLE_PADDING_TOP = -70;
	private static final int DEFAULT_TITLE_PADDING_BOTTOM = 11;
	static final int DEFAULT_PEEKING_WIDTH = 48;

	
	/**
	 * This class provides width suggestion for panorama section.
	 * @author jiaoye
	 *
	 */
	public static class LayoutParams extends ViewGroup.LayoutParams {
		
		int sectionWidth = -1;
		
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);            
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.PanoramaSection);
            // do nothing
            a.recycle();
        }

        public LayoutParams(int width, int height) {
			super(width, height);
		}

		public LayoutParams(int width, int height, boolean useCustomHeader) {
			super(width, height);
		}
		
        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
        }

        public String toString() {
            return "PanoramaView.LayoutParams={ width="
                    + Integer.toString(width) + ", height="
                    + Integer.toString(height) + ", sectionWidth="
                    + Integer.toString(sectionWidth) + "}";
        }
	}

	// header
	private int mCustomHeaderId;
	private View mHeader;
	private String mTitle;
	private int mTitleColor;
	private Drawable mTitleIcon;

	// background
	private Drawable mBackgroundDrawable;
	private View mBackground;

	// touching facilities
	private VelocityTracker mVelocityTracker;
	
	// internal states
    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mOverscrollDistance;
    private int mOverflingDistance;

    private int mViewportLeft;
	private int mOldViewportLeft;
    private boolean mIsBeingDragged;
	private int mActivePointerId;

	private float mLastMotionX;
	private float mLastMotionY;
	private float mCurrentViewPortLeft;

	private FlipOutAnimation mPanoramaTitleAnimation;
	private FlipOutAnimation mSectionTitleAnimation;
	private FlipOutAnimation mContentAnimation;
	
	// mirage views are all lazy
	private MirageView mHeaderMirage;
	private MirageView mSectionTitleMirage;
	private MirageView mLastSectionMirage;
	private MirageView mFirstSectionMirage;
	
	private ArrayList<PanoramaSection> mSectionList;

	private DisplayMetrics mDisplayMetrics;
    private UIContext mUIContext;


    public PanoramaView(Context context) {
		this(context, null, 0);
	}

	public PanoramaView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public PanoramaView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initializePanoramaView(context, attrs, defStyle);
	}

	/**
	 * initialize PanoramaView internally
	 */
	private void initializePanoramaView(Context context, AttributeSet attrs, int defStyle) {
		mSectionList = new ArrayList<PanoramaSection>();

		if (attrs != null) {
			TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PanoramaView, defStyle, 0);
			mCustomHeaderId = ta.getResourceId(R.styleable.PanoramaView_customHeader, INVALID_RESOURCE_ID);
			if (mCustomHeaderId == INVALID_RESOURCE_ID) {
				mTitle = ta.getString(R.styleable.PanoramaView_title);
				mTitleColor = ta.getColor(R.styleable.PanoramaView_titleColor, DEFAULT_TITLE_COLOR);
				mTitleIcon = ta.getDrawable(R.styleable.PanoramaView_icon);
			}
			ta.recycle();
		}
		
        setWillNotDraw(false);
        mDisplayMetrics = new DisplayMetrics();
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
        mUIContext = UIContext.getUIContext(context);
        
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
        mOverflingDistance = configuration.getScaledOverflingDistance();
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
		
		// collect sections
		for (int i = start; i < childCount; ++i) {
			try {
				PanoramaSection ps = (PanoramaSection) getChildAt(i);
				mSectionList.add(ps);
			}
			catch (ClassCastException e) {
				Log.e(LOG_TAG, "PanoramaSection is expected but we've got " + e.getMessage());
				throw e;
			}
		}

		// generate a header if no custom header provided
		if (mCustomHeaderId == INVALID_RESOURCE_ID || mHeader == null)
			generateHeader();

		// initialize background
		mBackgroundDrawable = getBackground();
		setBackgroundDrawable(null);
		
		// FIXME
		if (mBackgroundDrawable != null) {
            generateBackground();
        }
        else {
			throw new Error("FIXME: add support for null background");
        }
	}
	
	/**
	 * Generate background view.
	 */
	private void generateBackground() {
		ImageView iv = new ImageView(getContext());
		iv.setImageDrawable(mBackgroundDrawable);
		mBackground = iv;
		addView(mBackground, 0, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	}
	
	/**
	 * Generate header panel according to mTitle and mDrawable. The generated 
	 * header panel will be inserted into PanoramaView as the first child.
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
			tv.setTypeface(mUIContext.lightTypeface);
			tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TITLE_SIZE);
			tv.setPadding(DEFAULT_TITLE_PADDING_LEFT, DEFAULT_TITLE_PADDING_TOP, DEFAULT_TITLE_PADDING_RIGHT, DEFAULT_TITLE_PADDING_BOTTOM);
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
		final int panoramaWidth = getMeasuredWidth();
		final int panoramaHeight = getMeasuredHeight();
		final int contentWidth = getContentWidth();
		final int headerWidth = mHeader.getMeasuredWidth();
		final int headerHeight = mHeader.getMeasuredHeight();
		final int backgroundWidth = mBackground.getMeasuredWidth();
		final int backgroundHeight = mBackground.getMeasuredHeight();
		int viewportOffsetX;

		// 1. layout background layer
		if (mBackground != null) {
			viewportOffsetX = -mViewportLeft * (backgroundWidth - panoramaWidth) / (contentWidth + DEFAULT_PEEKING_WIDTH - panoramaWidth);
			mBackground.layout(viewportOffsetX, 0, backgroundWidth + viewportOffsetX, backgroundHeight);
		}
		
		// 2. layout header
		viewportOffsetX = -mViewportLeft * (headerWidth - panoramaWidth) / (contentWidth - panoramaWidth);
		mHeader.layout(viewportOffsetX, 0, headerWidth + viewportOffsetX, headerHeight);
		
		// 3. layout sections
		int sectionOffsetX = 0;
		viewportOffsetX = -mViewportLeft;
		for (PanoramaSection ps : mSectionList) {
			final int childWidth = ps.getMeasuredWidth();
			ps.layout(sectionOffsetX + viewportOffsetX, headerHeight, childWidth + sectionOffsetX + viewportOffsetX, ps.getMeasuredHeight() + headerHeight);
			sectionOffsetX += childWidth;
		}
		
		// 4. layout mirages
		if (mSectionTitleMirage != null) {
			View v = mSectionTitleMirage.getView();
			final int left = v.getLeft() + ((View) v.getParent()).getLeft();
			final int top = v.getTop() + ((View) v.getParent()).getTop();
			mSectionTitleMirage.layout(left + viewportOffsetX, 
									   top,
									   left + mSectionTitleMirage.getMeasuredWidth() + viewportOffsetX,
									   top + mSectionTitleMirage.getMeasuredHeight());
		}
	}

	// ======================== manipulating layout parameters ===============================
    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }
	
    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof PanoramaSection.LayoutParams;
    }

    @Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// the metrics of PanoramaView has no relation with its children
		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		ViewGroup.LayoutParams lp = getLayoutParams();
		int height = (int) (mDisplayMetrics.heightPixels / mDisplayMetrics.density);
		int width = (int) (mDisplayMetrics.widthPixels / mDisplayMetrics.density);
		Log.v(LOG_TAG, "Display Metrics Provided: " + height + ", " + width);

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
				// use default value
			}
			break;
		default:
			throw new Error("Unsupported measure spec mode.");
		}
		
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
				// use default value
			}
			break;
		default:
			throw new Error("Unsupported measure spec mode.");
		}
		
		// measure children, we do NOT depend on the order of children
		// 1. measure background
		if (mBackground != null) {
			// scale to fit the height of PanoramaView with aspect ratio maintained
			measureChild(mBackground,
					MeasureSpec.makeMeasureSpec(mBackgroundDrawable.getIntrinsicWidth() * height / mBackgroundDrawable.getIntrinsicHeight(),
												MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
		}

		// 2. measure header panel
		measureChild(mHeader,
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

		// 3. measure sections
		final int minimumSectionWidth = width - DEFAULT_PEEKING_WIDTH;
		final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height - mHeader.getMeasuredHeight(), MeasureSpec.AT_MOST);
		final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		for (PanoramaSection ps : mSectionList) {
			((PanoramaView.LayoutParams) ps.getLayoutParams()).sectionWidth = minimumSectionWidth;
			measureChild(ps, childWidthMeasureSpec, childHeightMeasureSpec);
		}
		
		// 4. measure mirages
		if (mSectionTitleMirage != null) {
			measureChild(mSectionTitleMirage,
					MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
					MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		}
		
		setMeasuredDimension(width, height);
	}


    // =========================== processing touch events ==================================
//    @Override
//    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        final int action = ev.getAction();
//
//        // Shortcut.
//        if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
//            return true;
//        }
//
//        switch (action & MotionEvent.ACTION_MASK) {
//        case MotionEvent.ACTION_DOWN: {
//            final float x = ev.getX();
//            if (!inPanoramaSection((int) x, (int) ev.getY())) {
//                mIsBeingDragged = false;
//                break;
//            }
//            
//            /*
//             * Remember location of down touch.
//             * ACTION_DOWN always refers to pointer index 0.
//             */
//            mLastMotionX = x;
//            mActivePointerId = ev.getPointerId(0);
//
//            /*
//            * If being flinged and user touches the screen, initiate drag;
//            * otherwise don't.  mScroller.isFinished should be false when
//            * being flinged.
//            */
////            mIsBeingDragged = !mScroller.isFinished();
//            break;
//        }
//        case MotionEvent.ACTION_MOVE: {
//            /*
//             * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
//             * whether the user has moved far enough from his original down touch.
//             */
//
//            /*
//            * Locally do absolute value. mLastMotionX is set to the x value
//            * of the down event.
//            */
//            final int activePointerId = mActivePointerId;
//            if (activePointerId == INVALID_POINTER) {
//                // If we don't have a valid id, the touch down wasn't on content.
//                break;
//            }
//
//            final int pointerIndex = ev.findPointerIndex(activePointerId);
//            final float x = ev.getX(pointerIndex);
//            final int xDiff = (int) Math.abs(x - mLastMotionX);
//            if (xDiff > mTouchSlop) {
//                mIsBeingDragged = true;
//                mLastMotionX = x;
//                final ViewParent parent = getParent();
//                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
//            }
//            break;
//        }
//
//        case MotionEvent.ACTION_CANCEL:
//        case MotionEvent.ACTION_UP:
//            mIsBeingDragged = false;
//            mActivePointerId = INVALID_POINTER;
//            break;
//        case MotionEvent.ACTION_POINTER_UP:
////            onSecondaryPointerUp(ev);
//            break;
//        }
//
//        return mIsBeingDragged;
//    }

    private boolean inPanoramaSection(int x, int y) {
		// TODO Auto-generated method stub
		return false;
	}

//	@Override
//    public boolean onTouchEvent(MotionEvent ev) {
//
//        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
//            // Don't handle edge touches immediately -- they may actually belong to one of our
//            // descendants.
//           return false;
//        }
//
//        if (mVelocityTracker == null) {
//            mVelocityTracker = VelocityTracker.obtain();
//        }
//        mVelocityTracker.addMovement(ev);
//
//        final int action = ev.getAction();
//
//        switch (action & MotionEvent.ACTION_MASK) {
//            case MotionEvent.ACTION_DOWN: {
//                mIsBeingDragged = getChildCount() != 0;
//                if (!mIsBeingDragged) {
//                    return false;
//                }
//
//                /*
//                 * If being flinged and user touches, stop the fling. isFinished
//                 * will be false if being flinged.
//                 */
////                if (!mScroller.isFinished()) {
////                    mScroller.abortAnimation();
////                }
//
//                // Remember where the motion event started
//                mLastMotionX = ev.getX();
//                mActivePointerId = ev.getPointerId(0);
//                break;
//            }
//            case MotionEvent.ACTION_MOVE:
//                if (mIsBeingDragged) {
//                    // Scroll to follow the motion event
//                    final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
//                    final float x = ev.getX(activePointerIndex);
//                    final int deltaX = (int) (mLastMotionX - x);
//                    mLastMotionX = x;
//
////                    final int oldX = mScrollX;
////                    final int oldY = mScrollY;
////                    final int range = getScrollRange();
////                    if (overScrollBy(deltaX, 0, mScrollX, 0, range, 0,
////                            mOverscrollDistance, 0, true)) {
////                        // Break our velocity if we hit a scroll barrier.
////                        mVelocityTracker.clear();
////                    }
//                    onScrollChanged(mScrollX, mScrollY, oldX, oldY);
//
//                    final int overscrollMode = getOverScrollMode();
//                    if (overscrollMode == OVER_SCROLL_ALWAYS ||
//                            (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0)) {
//                        final int pulledToX = oldX + deltaX;
//                        if (pulledToX < 0) {
//                            mEdgeGlowLeft.onPull((float) deltaX / getWidth());
//                            if (!mEdgeGlowRight.isFinished()) {
//                                mEdgeGlowRight.onRelease();
//                            }
//                        } else if (pulledToX > range) {
//                            mEdgeGlowRight.onPull((float) deltaX / getWidth());
//                            if (!mEdgeGlowLeft.isFinished()) {
//                                mEdgeGlowLeft.onRelease();
//                            }
//                        }
//                        if (mEdgeGlowLeft != null
//                                && (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished())) {
//                            invalidate();
//                        }
//                    }
//                }
//                break;
//            case MotionEvent.ACTION_UP:
//                if (mIsBeingDragged) {
//                    final VelocityTracker velocityTracker = mVelocityTracker;
//                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
//                    int initialVelocity = (int) velocityTracker.getXVelocity(mActivePointerId);
//
//                    if (getChildCount() > 0) {
//                        if ((Math.abs(initialVelocity) > mMinimumVelocity)) {
//                            fling(-initialVelocity);
//                        } else {
//                            final int right = getScrollRange();
//                            if (mScroller.springBack(mScrollX, mScrollY, 0, right, 0, 0)) {
//                                invalidate();
//                            }
//                        }
//                    }
//                    
//                    mActivePointerId = INVALID_POINTER;
//                    mIsBeingDragged = false;
//
//                    if (mVelocityTracker != null) {
//                        mVelocityTracker.recycle();
//                        mVelocityTracker = null;
//                    }
//                    if (mEdgeGlowLeft != null) {
//                        mEdgeGlowLeft.onRelease();
//                        mEdgeGlowRight.onRelease();
//                    }
//                }
//                break;
//            case MotionEvent.ACTION_CANCEL:
//                if (mIsBeingDragged && getChildCount() > 0) {
//                    if (mScroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0)) {
//                        invalidate();
//                    }
//                    mActivePointerId = INVALID_POINTER;
//                    mIsBeingDragged = false;
//                    if (mVelocityTracker != null) {
//                        mVelocityTracker.recycle();
//                        mVelocityTracker = null;
//                    }
//                    if (mEdgeGlowLeft != null) {
//                        mEdgeGlowLeft.onRelease();
//                        mEdgeGlowRight.onRelease();
//                    }
//                }
//                break;
//            case MotionEvent.ACTION_POINTER_UP:
//                onSecondaryPointerUp(ev);
//                break;
//        }
//        return true;
//    }
	
	private int getContentWidth() {
		final int count = getChildCount();
		int contentWidth = 0;
		for (int i = 2; i < count; ++i) {
			final View c = getChildAt(i);
			// there may be temporary views here (MirageView for example)
			if (c instanceof PanoramaSection)
				contentWidth += c.getMeasuredWidth();
		}
		return contentWidth;
	}
	
	public void flipOut(final PanoramaSection v) {
		final FlipOutAnimation panoramaTitleAnimation = mPanoramaTitleAnimation = new FlipOutAnimation(-getMeasuredWidth()/4, this.getMeasuredHeight()/2);
		final FlipOutAnimation sectionTitleAnimation = mSectionTitleAnimation = new FlipOutAnimation(-getMeasuredWidth()/4, this.getMeasuredHeight()/2);
		final FlipOutAnimation contentAnimation = mContentAnimation = new FlipOutAnimation(-getMeasuredWidth()/4, this.getMeasuredHeight()/2);
		contentAnimation.setAnimationListener(this);
		final MirageView mv = new MirageView(getContext(), v.getHeader());
		if (mSectionTitleMirage != null) {
			// this one is lazy cleaned
			removeView(mSectionTitleMirage);
		}
		mSectionTitleMirage = mv;
		this.addView(mSectionTitleMirage);
		mSectionTitleMirage.freeze();
		mSectionTitleMirage.getView().setVisibility(GONE);
		mHeader.startAnimation(panoramaTitleAnimation);
		postDelayed(new Runnable() {
			@Override
			public void run() {
				mSectionTitleMirage.startAnimation(sectionTitleAnimation);
			}
		}, 150);
		postDelayed(new Runnable() {
			@Override
			public void run() {
				v.startAnimation(contentAnimation);
			}
		}, 300);
	}
	
	// FIXME concepts
	void concepts() {
		// view port width
		final int panoramaWidth = getWidth();
		// sum of widths of all PanoramaItems
		final int contentWidth = getContentWidth();
		// header width
		final int headerWidth = mHeader.getMeasuredWidth();
		// background width
		final int backgroundWidth = mBackgroundDrawable.getIntrinsicWidth();
		// viewportLeft <= contentWidth - panoramaWidth && viewportLeft >= 0
		int viewportLeft = 0;
		
		// first, let us assume this: headerWidth <= backgroundWidth <= contentWidth
		if (headerWidth < panoramaWidth && contentWidth < panoramaWidth) {
			// NO SCROLLING
		}
		else if (headerWidth <= backgroundWidth && backgroundWidth <= contentWidth) {
			int backgroundLeft = -(viewportLeft * (backgroundWidth - panoramaWidth) / (contentWidth - panoramaWidth));
			int headerLeft = -(viewportLeft * (headerWidth - panoramaWidth) / (contentWidth - panoramaWidth));
		}
		else {
			// TODO
		}
	}

	// =================================== scrolling ======================================
	@Override
	public void scrollTo(int x, int y) {
		Log.v(LOG_TAG, "PanoramaView cannot scroll vertically, axis y is ignored");
		scrollTo(x);
	}
	
	public void scrollTo(int x) {
		if (x != mViewportLeft) {
			mOldViewportLeft = mViewportLeft;
			mViewportLeft = x;
			invalidate();
		}
	}
	
	@Override
	public void scrollBy(int x, int y) {
		Log.v(LOG_TAG, "PanoramaView cannot scroll vertically, axis y is ignored");
		scrollBy(x);
	}

	public void scrollBy(int x) {
		scrollTo(mViewportLeft + x);
	}
	
	@Override
	public void onAnimationStart(Animation animation) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onAnimationEnd(Animation animation) {
		if (animation == mContentAnimation) {
			mSectionTitleMirage.unfreeze();
			final View h = mSectionTitleMirage.getView();
//			h.setVisibility(VISIBLE);
		}
	}

	@Override
	public void onAnimationRepeat(Animation animation) {
		// TODO Auto-generated method stub
		
	}

	/*
	 * i haven't found the usage of this method, put it under surveillance.
	 */
	int getSuggestedSectionWidth() {
		return mDisplayMetrics.widthPixels - DEFAULT_PEEKING_WIDTH;
	}
}
