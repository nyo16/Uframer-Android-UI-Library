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
import android.widget.Scroller;
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

    public static enum HeaderLayoutStyle {
        BOUNDED,
        TOWED,
    }

    // header
    private int mCustomHeaderId;
    private View mHeader;
    private String mTitle;
    private int mTitleColor;
    private Drawable mTitleIcon;
    private HeaderLayoutStyle mHeaderLayoutStyle;

    // background
    private Drawable mBackgroundDrawable;
    private View mBackground;

    // touching facilities
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mOverscrollDistance;
    private int mOverflingDistance;

    private boolean mIsBeingDragged;
    private int mActivePointerId;

    private float mLastMotionX;
    private float mLastMotionY;

    private FlipOutAnimation mPanoramaTitleAnimation;
    private FlipOutAnimation mSectionTitleAnimation;
    private FlipOutAnimation mContentAnimation;

    // mirage views are all lazy
    private MirageView mHeaderMirage;
    private MirageView mSectionTitleMirage;
    private MirageView mLastSectionMirage;
    private MirageView mFirstSectionMirage;

    private int mLastWidthMeasureSpec;
    private int mLastHeightMeasureSpec;

    private ArrayList<PanoramaSection> mSectionList;

    private DisplayMetrics mDisplayMetrics;
    private UIContext mUIContext;

    private Scroller mScroller;
    private boolean mIsScrollingCache;
    private int mScrollingOffsetCache;


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
        mScroller = new Scroller(context);
        mHeaderLayoutStyle = HeaderLayoutStyle.TOWED;
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
        final float viewportWidth = getMeasuredWidth();
        final float viewportHeight = getMeasuredHeight();
        final float contentWidth = getMeasuredContentWidth();
        final float headerWidth = mHeader.getMeasuredWidth();
        final float headerHeight = mHeader.getMeasuredHeight();
        float viewportOffsetX;
        float viewportLeft;

        // TODO onLayout may be called several times
        if (mIsScrollingCache) {
            mIsScrollingCache = mScroller.computeScrollOffset();
            if (mIsScrollingCache) {
                mScrollingOffsetCache = mScroller.getCurrX();
                viewportLeft = mScrollingOffsetCache;
            }
            else {
                viewportLeft = getScrollX();
            }
        }
        else {
            viewportLeft = getScrollX();
        }

        // 1. layout background layer
        if (mBackground != null) {
            float backgroundWidth = mBackground.getMeasuredWidth() * viewportHeight / mBackground.getMeasuredHeight();
            float backgroundHeight = viewportHeight;
            viewportOffsetX = viewportLeft * (contentWidth + DEFAULT_PEEKING_WIDTH - backgroundWidth) / (contentWidth + DEFAULT_PEEKING_WIDTH - viewportWidth);
            mBackground.layout((int) viewportOffsetX, 0, (int)(backgroundWidth + viewportOffsetX), (int)backgroundHeight);
        }

        // 2. layout header
        switch (mHeaderLayoutStyle) {
        case BOUNDED:
            viewportOffsetX = viewportLeft * (contentWidth - headerWidth) / (contentWidth - viewportWidth);
            break;
        case TOWED:
            viewportOffsetX = viewportLeft * (contentWidth - viewportWidth + DEFAULT_PEEKING_WIDTH / 2) / contentWidth;
            break;
        default:
            viewportOffsetX = 0;
        }
        mHeader.layout((int) viewportOffsetX, 0, (int)(headerWidth + viewportOffsetX), (int)headerHeight);

        // 3. layout sections
        int sectionOffsetX = 0;
        viewportOffsetX = 0;
        for (PanoramaSection ps : mSectionList) {
            final int childWidth = ps.getMeasuredWidth();
            ps.layout((int)(sectionOffsetX + viewportOffsetX), (int)headerHeight, (int)(childWidth + sectionOffsetX + viewportOffsetX), (int)(ps.getMeasuredHeight() + headerHeight));
            sectionOffsetX += childWidth;
        }

        // 4. layout mirages
        if (mSectionTitleMirage != null) {
            View v = mSectionTitleMirage.getView();
            final int left = v.getLeft() + ((View) v.getParent()).getLeft();
            final int top = v.getTop() + ((View) v.getParent()).getTop();
            mSectionTitleMirage.layout(left,
                                       top,
                                       left + mSectionTitleMirage.getMeasuredWidth(),
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
        // save measure specs for mirages
        mLastWidthMeasureSpec = widthMeasureSpec;
        mLastHeightMeasureSpec = heightMeasureSpec;
        // the metrics of PanoramaView has no relation with its children
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        ViewGroup.LayoutParams lp = getLayoutParams();
        int height = (int) (mDisplayMetrics.heightPixels / mDisplayMetrics.density);
        int width = (int) (mDisplayMetrics.widthPixels / mDisplayMetrics.density);

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
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

//        dumpMotionEvent("onInterceptTouchEvent", ev);

        switch (ev.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            mLastMotionX = ev.getX();
            mActivePointerId = ev.getPointerId(0);
            mIsBeingDragged = !mScroller.isFinished();
            break;

        case MotionEvent.ACTION_MOVE: {
            if (mIsBeingDragged) {
                return true;
            }

            // sanity check in case ACTION_DOWN is missed
            if (mActivePointerId == INVALID_POINTER) {
                break;
            }

            final float x = ev.getX(ev.findPointerIndex(mActivePointerId));
            final int deltaX = (int) Math.abs(x - mLastMotionX);
            if (deltaX > mTouchSlop) {
                mIsBeingDragged = true;
                mLastMotionX = x;
                final ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
            }
            break;
        }
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            mIsBeingDragged = false;
            mActivePointerId = INVALID_POINTER;
            break;
        case MotionEvent.ACTION_POINTER_UP:
            // change primary pointer and clear the trace
            onSecondaryPointerUp(ev);
            break;
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

//        dumpMotionEvent("onTouchEvent", ev);

        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
            // Don't handle edge touches immediately -- they may actually belong to one of our
            // descendants. (uframer: AbsListView for example)
           return false;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final boolean canScroll = canScroll();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mIsBeingDragged = canScroll;
                if (!mIsBeingDragged) {
                    return false;
                }

                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }

                // Remember where the motion event started
                mLastMotionX = ev.getX();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    final float x = ev.getX(ev.findPointerIndex(mActivePointerId));
                    final int deltaX = (int) (x - mLastMotionX);
                    mLastMotionX = x;
                    super.scrollBy(-deltaX, 0);
                    //scrollBy(-deltaX);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) mVelocityTracker.getXVelocity(mActivePointerId);

                    if (canScroll) {
                        if ((Math.abs(initialVelocity) > mMinimumVelocity)) {
                            // TODO trigger animation
                            fling(initialVelocity);
                        } else {
                            invalidate();
                        }
                    }
                    
                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;

                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && canScroll) {
                    invalidate();
                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            mLastMotionX = ev.getX(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    private int getContentWidth() {
        int contentWidth = 0;
        for (PanoramaSection ps : mSectionList) {
            contentWidth += ps.getWidth();
        }
        return contentWidth;
    }

    private int getMeasuredContentWidth() {
        int contentWidth = 0;
        for (PanoramaSection ps : mSectionList) {
            contentWidth += ps.getMeasuredWidth();
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
        addView(mSectionTitleMirage);
        // make sure the original view is measured
        measure(mLastWidthMeasureSpec, mLastHeightMeasureSpec);
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

    // =================================== scrolling ======================================

    public boolean canScroll() {
        return getContentWidth() > getWidth();
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(x, 0);
        requestLayout();
    }

    public void scrollTo(int x) {
        scrollTo(x, 0);
    }

    @Override
    public void scrollBy(int x, int y) {
        scrollTo(getScrollX() + x, 0);
    }

    public void scrollBy(int x) {
        scrollBy(x, 0);
    }

    private void fling(int velocity) {
        mScroller.fling(getScrollX(), 0, -velocity, 0, 0, Math.max(0, getContentWidth() - getWidth() + DEFAULT_PEEKING_WIDTH), 0, 0);
        mIsScrollingCache = mScroller.computeScrollOffset();
        mScrollingOffsetCache = mScroller.getCurrX();
        invalidate();
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
//            h.setVisibility(VISIBLE);
        }
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void computeScroll() {
        if (mIsScrollingCache) {
            scrollTo(mScrollingOffsetCache, 0);
            invalidate();
        }
        else {
        }
    }

    /*
     * i haven't found the usage of this method, put it under surveillance.
     */
    int getSuggestedSectionWidth() {
        return mDisplayMetrics.widthPixels - DEFAULT_PEEKING_WIDTH;
    }

    public View getHeader() {
        return mHeader;
    }

    public HeaderLayoutStyle getHeaderLayoutStyle() {
        return mHeaderLayoutStyle;
    }

    public void setHeaderLayoutStyle(HeaderLayoutStyle s) {
        mHeaderLayoutStyle = s;
    }

    // TODO if we keep sections ordered then we can save half of the work
    PanoramaSection findPreviousSection() {
        PanoramaSection result = null;
        int minDistance = Integer.MAX_VALUE;
        final int viewportLeft = getScrollX();
        for (PanoramaSection ps : mSectionList) {
            final int distance = ps.getLeft() - viewportLeft;
            if (distance > 0 && distance < minDistance) {
                minDistance = distance;
                result = ps;
            }
        }
        return result;
    }

    PanoramaSection findNextSection() {
        PanoramaSection result = null;
        int minDistance = Integer.MAX_VALUE;
        final int viewportLeft = getScrollX();
        for (PanoramaSection ps : mSectionList) {
            final int distance = viewportLeft - ps.getLeft();
            if (distance > 0 && distance < minDistance) {
                minDistance = distance;
                result = ps;
            }
        }
        return result;
    }

    // ============================= Debug Facilities ===========================

    @SuppressWarnings("unused")
    private void dumpMotionEvent(String tag, MotionEvent ev) {
        Log.v(tag, "==================================================");
        Log.v(tag, "Action:" + Integer.toHexString(ev.getAction()));
        Log.v(tag, "ActionIndex:" + Integer.toHexString(ev.getActionIndex()));
        Log.v(tag, "ActionMasked:" + actionToString(ev.getActionMasked()));
        final int pointerCount = ev.getPointerCount();
        Log.v(tag, "At time " + ev.getEventTime() + ":");
        for (int p = 0; p < pointerCount; p++) {
            Log.v(tag, "  pointer " + ev.getPointerId(p) + ":(" + ev.getX(p) + "," + ev.getY(p) + ")");
        }
    }

    private static String actionToString(int action) {
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            return "ACTION_DOWN";
        case MotionEvent.ACTION_MOVE:
            return "ACTION_MOVE";
        case MotionEvent.ACTION_UP:
            return "ACTION_UP";
        case MotionEvent.ACTION_CANCEL:
            return "ACTION_CANCEL";
        case MotionEvent.ACTION_OUTSIDE:
            return "ACTION_OUTSIDE";
        case MotionEvent.ACTION_POINTER_UP:
            return "ACTION_POINTER_UP";
        case MotionEvent.ACTION_POINTER_DOWN:
            return "ACTION_POINTER_DOWN";
        }
        return "UNKNOWN";
    }
}
