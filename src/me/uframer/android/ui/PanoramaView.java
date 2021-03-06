/**
 * @auther jiaoye
 * @email uframer@gmail.com
 */
package me.uframer.android.ui;

import java.util.ArrayList;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * <p>
 * This class provides a horizontal canvas that can exceed the screen limit.
 * Users can pan or flick horizontally to navigate across the sections. Common
 * section is slightly narrower than the screen so that users can have a peek at
 * the next page, and this can be used as a visual clue to tell users that there
 * are more contents. Once users navigate across the boundaries of the canvas,
 * the canvas will wrap back.
 * </p>
 * <p>
 * children layout must conforms to:
 * </p>
 * <table> <tr> <td>header</td> <td>section*</td> <td>mirage*</td> </tr> </table>
 *
 * @author jiaoye
 */
public class PanoramaView extends ViewGroup {

    private static final String LOG_TAG = PanoramaView.class.toString();

    private static final int INVALID_POINTER = -1;
    private static final int INVALID_RESOURCE_ID = -1;

    private static final int DEFAULT_TITLE_COLOR = Color.WHITE;
    private static final int DEFAULT_TITLE_SIZE = 125;
    private static final int DEFAULT_TITLE_PADDING_TOP = -70;
    private static final int DEFAULT_TITLE_PADDING_BOTTOM = 2;
    private static final int DEFAULT_PEEKING_WIDTH = 48;
    private static final int DEFAULT_BACKGROUND_TRAILING_WIDTH = 210;
    private static final int DEFAULT_TRAPPING_RADIUS = 128;
    private static final int DEFAULT_SCROLLING_TRIGGER = 200;
    private static final int DEFAULT_SECTION_LEFT_MARGIN = 12;
    private static final int DEFAULT_HEADER_LEFT_MARGIN = 10;
    private static final int DEFAULT_HEADER_RIGHT_MARGIN = 10;
    private static final int DEFAULT_HEADER_BOTTOM_MARGIN = 9;
    private static final int DEFAULT_HEADER_MIRAGE_INTERVAL = 348;

    // sliding style for header and background
    private SlidingStyle mSlidingStyle;

    // header
    private int mCustomHeaderId;
    private View mHeader;
    private String mTitle;
    private int mTitleColor;
    private Drawable mTitleIcon;
    private int mLastHeaderLeft;

    // viewport
    private int mLastViewportLeft;

    // background
    private Drawable mBackgroundDrawable;
    private int mBackgroundLeft;
    private int mLastBackgroundLeft;
    private int mBackgroundWidth;
    private int mBackgroundHeight;
    private BackgroundScalingStyle mBackgroundScalingStyle;

    // touching facilities
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMaximumVelocity;
    private int mFlingVelocity;
    private boolean mIsBeingDragged;
    private int mActivePointerId;
    private float mLastMotionX = -1;
    private float mLastMotionY = -1;
    private float mFirstMotionX = -1;

    // mirage views are all lazy
    private MirageView mHeaderMirage;

    // the order of items in mSectionList is the same in children list
    private ArrayList<PanoramaSection> mSectionList;

    private DisplayMetrics mDisplayMetrics;
    private UIContext mUIContext;

    private Scroller mScroller;
    private boolean mIsScrolling;
    private int mScrollingOffset;

    private PanoramaSection mOriginalSection;

    private boolean mIsWrappingToHead;
    private boolean mIsWrappingToTail;

    private boolean mDebugMode;
    private boolean mDemoMode;

    private int mInitialHeaderVisibility;

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
                // visibility of the default header, custom header's visibility is specified in its xml node
                String initialHeaderVisibility = ta.getString(R.styleable.PanoramaView_headerVisibility);
                if (initialHeaderVisibility == null) {
                    mInitialHeaderVisibility = View.VISIBLE;
                }
                else if (initialHeaderVisibility.equals("visible")) {
                    mInitialHeaderVisibility = View.VISIBLE;
                }
                else if (initialHeaderVisibility.equals("invisible")) {
                    mInitialHeaderVisibility = View.INVISIBLE;
                }
                else if (initialHeaderVisibility.equals("gone")) {
                    mInitialHeaderVisibility = View.GONE;
                }
                else {
                    throw new Error("invalid header visibility");
                }
            }

            mBackgroundDrawable = ta.getDrawable(R.styleable.PanoramaView_background);

            // slidingStyle
            String slidingStyle = ta.getString(R.styleable.PanoramaView_slidingStyle);
            if (slidingStyle == null) {
                mSlidingStyle = SlidingStyle.TOWED;
            }
            else if (slidingStyle.equals("bounded")) {
                mSlidingStyle = SlidingStyle.BOUNDED;
            }
            else if (slidingStyle.equals("towed")) {
                mSlidingStyle = SlidingStyle.TOWED;
            }
            else if (slidingStyle.equals("synced")) {
                mSlidingStyle = SlidingStyle.SYNCED;
            }
            else {
                throw new Error("invalid sliding style");
            }
            // backgroundScalingStyle
            String backgroundScalingStyle = ta.getString(R.styleable.PanoramaView_backgroundScalingStyle);
            if (backgroundScalingStyle == null) {
                mBackgroundScalingStyle = BackgroundScalingStyle.VERTICAL_STRETCH;
            }
            else if (backgroundScalingStyle.equals("none")) {
                mBackgroundScalingStyle = BackgroundScalingStyle.NONE;
            }
            else if (backgroundScalingStyle.equals("vertical_fill")) {
                mBackgroundScalingStyle = BackgroundScalingStyle.VERTICAL_FILL;
            }
            else if (backgroundScalingStyle.equals("vertical_stretch")) {
                mBackgroundScalingStyle = BackgroundScalingStyle.VERTICAL_STRETCH;
            }
            else {
                throw new Error("invalid background scaling style");
            }
            // debug
            String debug = ta.getString(R.styleable.PanoramaView_debug);
            if (debug == null) {
                mDebugMode = false;
            }
            else if (debug.equals("true")) {
                mDebugMode = true;
            }
            else if (debug.equals("false")) {
                mDebugMode = false;
            }
            else {
                throw new Error("invalid debug mode");
            }
            // demo
            String demo = ta.getString(R.styleable.PanoramaView_demo);
            if (demo == null) {
                mDemoMode = false;
            }
            else if (demo.equals("true")) {
                mDemoMode = true;
            }
            else if (demo.equals("false")) {
                mDemoMode = false;
            }
            else {
                throw new Error("invalid demo mode");
            }
            ta.recycle();
        }

        setWillNotDraw(false);
        mDisplayMetrics = new DisplayMetrics();
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
        mUIContext = UIContext.getUIContext(context);

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop(); // 24
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity(); // 6000
        mFlingVelocity = 1500;
        mScroller = new Scroller();
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
        if (mCustomHeaderId == INVALID_RESOURCE_ID || mHeader == null) {
            generateHeader();
            mHeader.setVisibility(mInitialHeaderVisibility);
        }

        mHeaderMirage = new MirageView(getContext(), mHeader);
        addView(mHeaderMirage, mHeader.getLayoutParams());
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
            tv.setTypeface(mUIContext.lightTypeface);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TITLE_SIZE);
            tv.setPadding(0, DEFAULT_TITLE_PADDING_TOP, 0, DEFAULT_TITLE_PADDING_BOTTOM);
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
        final float effectiveViewportWidth = viewportWidth - DEFAULT_PEEKING_WIDTH;
        final float viewportHeight = getMeasuredHeight();
        final float contentWidth = getMeasuredContentWidth();
        final float headerWidth = mHeader.getMeasuredWidth();
        final float effectiveHeaderWidth = headerWidth + DEFAULT_HEADER_LEFT_MARGIN + DEFAULT_HEADER_RIGHT_MARGIN;
        final float headerHeight = mHeader.getMeasuredHeight();
        float viewportOffsetX = 0;
        float viewportOffsetY = 0;
        float viewportLeft = 0;

        // FIXME onLayout may be called several times
        if (mIsScrolling) {
            mIsScrolling = mScroller.computeScrollOffset();
            mScrollingOffset = mScroller.getCurrX();
            viewportLeft = mScrollingOffset;
        }
        else {
            viewportLeft = getScrollX();
        }

        final int validSectionCount = getValidSectionCount();
        final boolean wrapToTail = (validSectionCount > 1) && (viewportLeft < 0);
        final boolean wrapToHead = (validSectionCount > 1) && (viewportLeft > contentWidth - viewportWidth);

        // 1. layout background
        if (mBackgroundDrawable != null) {
            // determine width and height
            switch (mBackgroundScalingStyle) {
            case VERTICAL_FILL:
                mBackgroundWidth = (int) (mBackgroundDrawable.getIntrinsicWidth() * viewportHeight / mBackgroundDrawable.getIntrinsicHeight());
                mBackgroundHeight = (int) viewportHeight;
                break;
            case VERTICAL_STRETCH:
                mBackgroundWidth = mBackgroundDrawable.getIntrinsicWidth();
                mBackgroundHeight = (int) viewportHeight;
                break;
            case NONE:
            default:
                mBackgroundWidth = mBackgroundDrawable.getIntrinsicWidth();
                mBackgroundHeight = mBackgroundDrawable.getIntrinsicHeight();
            }
            // calculate left edge offset
            switch (mSlidingStyle) {
            case BOUNDED:
                mBackgroundLeft = (int) (viewportLeft * (contentWidth - headerWidth) / (contentWidth - viewportWidth));
                break;
            case TOWED:
                if (mIsWrappingToHead) {
                    final float progress = (viewportLeft - mLastViewportLeft) / (contentWidth - mLastViewportLeft);
                    mBackgroundLeft = (int) (mLastBackgroundLeft + progress * (contentWidth - mBackgroundWidth - mLastBackgroundLeft));
                }
                else if (mIsWrappingToTail) {
                    final float lastSectionWidth = getLastValidSectionWidth() + DEFAULT_SECTION_LEFT_MARGIN;
                    final float destinationViewportLeft = -lastSectionWidth;
                    final float progress = (viewportLeft - mLastViewportLeft) / (destinationViewportLeft - mLastViewportLeft);
                    float destination = (contentWidth - lastSectionWidth) * (contentWidth - viewportWidth + DEFAULT_PEEKING_WIDTH - DEFAULT_BACKGROUND_TRAILING_WIDTH) / contentWidth;
                    destination = destination + mBackgroundWidth - contentWidth;
                    mBackgroundLeft = (int) (mLastBackgroundLeft + progress * (destination - mLastBackgroundLeft));
                }
                else {
                    mBackgroundLeft = (int) (viewportLeft * (contentWidth - viewportWidth + DEFAULT_PEEKING_WIDTH - DEFAULT_BACKGROUND_TRAILING_WIDTH) / contentWidth);
                }
                break;
            case SYNCED:
            default:
                mBackgroundLeft = 0;
            }
        }

        // 2. layout header
        if (mHeader.getVisibility() != View.GONE) {
            if (validSectionCount > 0) {
                switch (mSlidingStyle) {
                case BOUNDED:
                    viewportOffsetX = viewportLeft * (contentWidth - effectiveHeaderWidth) / (contentWidth - viewportWidth) + DEFAULT_HEADER_LEFT_MARGIN;
                    break;
                case TOWED:
                    if (mIsWrappingToHead) {
                        final float progress = (viewportLeft - mLastViewportLeft) / (contentWidth - mLastViewportLeft);
                        viewportOffsetX = mLastHeaderLeft + progress * (DEFAULT_HEADER_LEFT_MARGIN - mLastHeaderLeft);
                    }
                    else if (mIsWrappingToTail) {
                        final float lastSectionWidth = getLastValidSectionWidth() + DEFAULT_SECTION_LEFT_MARGIN;
                        final float destinationViewportLeft = -lastSectionWidth;
                        final float progress = (viewportLeft - mLastViewportLeft) / (destinationViewportLeft - mLastViewportLeft);
                        final float destination = DEFAULT_HEADER_LEFT_MARGIN + DEFAULT_PEEKING_WIDTH;
                        viewportOffsetX = mLastHeaderLeft + progress * (destination - mLastHeaderLeft);
                    }
                    else {
                        viewportOffsetX = viewportLeft * (contentWidth - headerWidth + contentWidth / effectiveViewportWidth + 80.0f) / contentWidth + DEFAULT_HEADER_LEFT_MARGIN;
                    }
                    break;
                case SYNCED:
                default:
                    viewportOffsetX = 0;
                }
            }
            else {
                viewportOffsetX = DEFAULT_HEADER_LEFT_MARGIN;
            }
            mHeader.layout((int) (viewportOffsetX), 0, (int) (headerWidth + viewportOffsetX), (int) headerHeight);
            viewportOffsetY += headerHeight + DEFAULT_HEADER_BOTTOM_MARGIN;
        }

        // 3. layout sections
        int sectionOffsetX = DEFAULT_SECTION_LEFT_MARGIN;
        final int sectionCount = mSectionList.size();
        if (sectionCount > 0) {
            viewportOffsetX = 0;
            // FIXME layout section title according to SlidingStyle
            // TODO add support for GONE property
            if (wrapToTail) {
                PanoramaSection ps;
                int childWidth;
                final int lastValidSectionIndex = getLastValidSectionIndex();
                for (int i = 0; i < lastValidSectionIndex - 1; ++i) {
                    ps = mSectionList.get(i);
                    if (ps.getVisibility() != View.GONE) {
                        childWidth = ps.getMeasuredWidth();
                        ps.layout((int) (sectionOffsetX + viewportOffsetX),
                                  (int) viewportOffsetY,
                                  (int) (childWidth + sectionOffsetX + viewportOffsetX),
                                  (int) (ps.getMeasuredHeight() + viewportOffsetY));
                        sectionOffsetX += childWidth + DEFAULT_SECTION_LEFT_MARGIN;
                    }
                }
                ps = mSectionList.get(lastValidSectionIndex);
                childWidth = ps.getMeasuredWidth();
                ps.layout((int) (viewportOffsetX - childWidth),
                          (int) (viewportOffsetY),
                          (int) (viewportOffsetX),
                          (int) (ps.getMeasuredHeight() + viewportOffsetY));
            } else if (wrapToHead) {
                PanoramaSection ps;
                int childWidth;
                final int firstValidSectionIndex = getFirstValidSectionIndex();
                sectionOffsetX += mSectionList.get(firstValidSectionIndex).getMeasuredWidth() + DEFAULT_SECTION_LEFT_MARGIN;
                for (int i = firstValidSectionIndex + 1; i < sectionCount; ++i) {
                    ps = mSectionList.get(i);
                    if (ps.getVisibility() != View.GONE) {
                        childWidth = ps.getMeasuredWidth();
                        ps.layout((int) (sectionOffsetX + viewportOffsetX),
                                  (int) viewportOffsetY,
                                  (int) (childWidth + sectionOffsetX + viewportOffsetX),
                                  (int) (ps.getMeasuredHeight() + viewportOffsetY));
                        sectionOffsetX += childWidth + DEFAULT_SECTION_LEFT_MARGIN;
                    }
                }
                ps = mSectionList.get(firstValidSectionIndex);
                childWidth = ps.getMeasuredWidth();
                ps.layout((int) (sectionOffsetX + viewportOffsetX),
                          (int) (viewportOffsetY),
                          (int) (childWidth + sectionOffsetX + viewportOffsetX),
                          (int) (ps.getMeasuredHeight() + viewportOffsetY));
            } else {
                for (int i = 0; i < sectionCount; ++i) {
                    final PanoramaSection ps = mSectionList.get(i);
                    if (ps.getVisibility() != View.GONE) {
                        final int childWidth = ps.getMeasuredWidth();
                        ps.layout((int) (sectionOffsetX + viewportOffsetX),
                                  (int) viewportOffsetY,
                                  (int) (childWidth + sectionOffsetX + viewportOffsetX),
                                  (int) (ps.getMeasuredHeight() + viewportOffsetY));
                        sectionOffsetX += childWidth + DEFAULT_SECTION_LEFT_MARGIN;
                    }
                }
            }
        }

        // 4. layout mirages
        if (mHeader.getVisibility() != View.GONE) {
            if (wrapToTail) {
                switch (mSlidingStyle) {
                case TOWED:
                    if (mIsWrappingToTail) {
                        final float lastSectionWidth = getLastValidSectionWidth() + DEFAULT_SECTION_LEFT_MARGIN;
                        final float destinationViewportLeft = -lastSectionWidth;
                        final float progress = (viewportLeft - mLastViewportLeft) / (destinationViewportLeft - mLastViewportLeft);
                        float destination = (contentWidth - lastSectionWidth) * (contentWidth - headerWidth + contentWidth / effectiveViewportWidth + 80.0f) / contentWidth + DEFAULT_HEADER_LEFT_MARGIN;
                        destination = destination - contentWidth;
                        viewportOffsetX = mLastHeaderLeft - contentWidth + progress * (destination - mLastHeaderLeft + contentWidth);
                    }
                    else {
                        viewportOffsetX = mHeader.getLeft() - DEFAULT_HEADER_MIRAGE_INTERVAL - headerWidth;
                    }
                    break;
                case BOUNDED:
                    viewportOffsetX = viewportLeft - headerWidth - DEFAULT_HEADER_RIGHT_MARGIN;
                case SYNCED:
                default:
                    viewportOffsetX = viewportLeft - contentWidth + DEFAULT_HEADER_LEFT_MARGIN;
                }
                mHeaderMirage.layout((int) (viewportOffsetX), 0, (int) (headerWidth + viewportOffsetX), (int) headerHeight);
            }
            else if (wrapToHead) {
                switch (mSlidingStyle) {
                case TOWED:
                    if (mIsWrappingToHead) {
                        final float offset = headerWidth + DEFAULT_HEADER_MIRAGE_INTERVAL;
                        final float progress = (viewportLeft - mLastViewportLeft) / (contentWidth - mLastViewportLeft);
                        viewportOffsetX = mLastHeaderLeft + offset + progress * (contentWidth + DEFAULT_HEADER_LEFT_MARGIN - mLastHeaderLeft - offset);
                    }
                    else {
                        viewportOffsetX = mHeader.getRight() + DEFAULT_HEADER_MIRAGE_INTERVAL;
                    }
                    break;
                case BOUNDED:
                case SYNCED:
                default:
                    viewportOffsetX = contentWidth + DEFAULT_HEADER_LEFT_MARGIN;
                }
                mHeaderMirage.layout((int) (viewportOffsetX), 0, (int) (headerWidth + viewportOffsetX), (int) headerHeight);
            }
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
        // 1. measure header panel
        if (mHeader.getVisibility() != View.GONE) {
            measureChild(mHeader,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }

        // 2. measure sections
        final int minimumSectionWidth = width - DEFAULT_SECTION_LEFT_MARGIN - DEFAULT_PEEKING_WIDTH;
        final int headerMeasuredHeight = mHeader.getVisibility() == View.GONE ? 0 : mHeader.getMeasuredHeight() + DEFAULT_HEADER_BOTTOM_MARGIN ;
        final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height - headerMeasuredHeight, MeasureSpec.AT_MOST);
        final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (PanoramaSection ps : mSectionList) {
            if (ps.getVisibility() != View.GONE) {
                ((PanoramaView.LayoutParams) ps.getLayoutParams()).sectionWidth = minimumSectionWidth;
                measureChild(ps, childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }

        // 3. measure mirages
        if (mHeader.getVisibility() != View.GONE) {
            measureChild(mHeaderMirage,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }

        setMeasuredDimension(width, height);
    }

    // =========================== processing touch events ==================================
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mDebugMode) {
            dumpMotionEvent("onInterceptTouchEvent", ev);
        }

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
        if (mDebugMode) {
            dumpMotionEvent("onTouchEvent", ev);
        }

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
        final int effectiveViewportWidth = getWidth() - DEFAULT_PEEKING_WIDTH;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mIsBeingDragged = canScroll;
                if (!mIsBeingDragged) {
                    return false;
                }

                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                    // TODO process wrapping
                }

                // Remember where the motion event started
                mLastMotionX = ev.getX();
                mLastMotionY = ev.getY();
                mFirstMotionX = mLastMotionX;
                mOriginalSection = findSectionUnderPoint(getScrollX());
                mActivePointerId = ev.getPointerId(0);
                invalidate();
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    final float x = ev.getX(ev.findPointerIndex(mActivePointerId));
                    final int deltaX = (int) (x - mLastMotionX);
                    scrollBy(-deltaX);
                    mLastMotionX = x;
                    mLastMotionY = ev.getY(ev.findPointerIndex(mActivePointerId));
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) mVelocityTracker.getXVelocity(mActivePointerId);

                    if (canScroll) { // fling
                        int currentSectionIndex = findCurrentSectionIndex();
                        if (currentSectionIndex == INVALID_POINTER) {
                            if (mOriginalSection != null) {
                                smoothScrollTo(mOriginalSection.getLeft() - DEFAULT_SECTION_LEFT_MARGIN, 200);
                            }
                            else {
                                Log.e(LOG_TAG, "original section is null whild flinging back");
                                smoothScrollTo(0, 200);
                            }
                        }
                        else {
                            final PanoramaSection currentSection = mSectionList.get(currentSectionIndex);
                            final int currentSectionLeftEdge = currentSection.getLeft() - DEFAULT_SECTION_LEFT_MARGIN;
                            final int currentSectionRightEdge = currentSection.getRight();
                            final int viewportLeft = getScrollX();
                            final int viewportRight = viewportLeft + effectiveViewportWidth;
                            final int distance = (int) (ev.getX(ev.findPointerIndex(mActivePointerId)) - mFirstMotionX);
                            final int validSectionCount = getValidSectionCount();
                            final int contentWidth = getContentWidth();
                            if ((Math.abs(initialVelocity) > mFlingVelocity)) { // fling
                                if (initialVelocity < 0) { // jump to next section
                                    if (viewportLeft > contentWidth - getLastValidSectionWidth()) {
                                        if (validSectionCount > 1) {
                                            mIsWrappingToHead = true;
                                            mLastViewportLeft = viewportLeft;
                                            mLastHeaderLeft = mHeader.getLeft();
                                            mLastBackgroundLeft = mBackgroundLeft;
                                            smoothScrollTo(contentWidth, 300);
                                        }
                                        else {
                                            smoothScrollTo(contentWidth - effectiveViewportWidth, 200);
                                        }
                                    }
                                    else {
                                        smoothScrollTo(currentSectionRightEdge, 200);
                                    }
                                } else { // jump to previous section
                                    if (viewportLeft < 0) {
                                        mIsWrappingToTail = true;
                                        mLastViewportLeft = viewportLeft;
                                        mLastHeaderLeft = mHeader.getLeft();
                                        mLastBackgroundLeft = mBackgroundLeft;
                                        smoothScrollTo((int) (-getLastValidSectionWidth() - DEFAULT_SECTION_LEFT_MARGIN), 300);
                                    }
                                    else {
                                        smoothScrollTo(currentSectionLeftEdge, 200);
                                    }
                                }
                            } else { // snap to edge
                                if (currentSection.getWidth() > effectiveViewportWidth) {
                                    // wide section
                                    final int rrDistance = currentSectionRightEdge - viewportRight;
                                    final int rlDistance = currentSectionRightEdge - viewportLeft;
                                    if (viewportLeft - currentSectionLeftEdge < DEFAULT_TRAPPING_RADIUS) { // snap to left edge
                                        if (viewportLeft < 0) {
                                            mIsWrappingToTail = true;
                                            mLastViewportLeft = viewportLeft;
                                            mLastHeaderLeft = mHeader.getLeft();
                                            mLastBackgroundLeft = mBackgroundLeft;
                                            smoothScrollTo((int) (-getLastValidSectionWidth() - DEFAULT_SECTION_LEFT_MARGIN), 300);
                                        }
                                        else {
                                            smoothScrollTo(currentSectionLeftEdge, 200);
                                        }
                                    } else if ((Math.abs(rrDistance) < DEFAULT_TRAPPING_RADIUS)
                                               || (distance > 0 && rlDistance >= DEFAULT_TRAPPING_RADIUS && rrDistance < 0)) { // snap to right edge
                                        smoothScrollTo(currentSectionRightEdge - effectiveViewportWidth - DEFAULT_SECTION_LEFT_MARGIN, 200);
                                    } else if (rlDistance < DEFAULT_TRAPPING_RADIUS
                                               || (distance < 0 && rrDistance < 0)) { // snap to next section
                                        if (viewportLeft > contentWidth-getLastValidSectionWidth()) {
                                            if (validSectionCount > 1) {
                                                mIsWrappingToHead = true;
                                                mLastViewportLeft = viewportLeft;
                                                mLastHeaderLeft = mHeader.getLeft();
                                                mLastBackgroundLeft = mBackgroundLeft;
                                                smoothScrollTo(contentWidth, 300);
                                            }
                                            else {
                                                smoothScrollTo(contentWidth - effectiveViewportWidth, 200);
                                            }
                                        }
                                        else {
                                            smoothScrollTo(currentSectionRightEdge, 200);
                                        }
                                    } else { // simply stay here
                                        invalidate();
                                    }
                                } else {
                                    // standard section
                                    if (distance > DEFAULT_SCROLLING_TRIGGER) { // snap to previous section
                                        if (viewportLeft < 0) {
                                            mIsWrappingToTail = true;
                                            mLastViewportLeft = viewportLeft;
                                            mLastHeaderLeft = mHeader.getLeft();
                                            mLastBackgroundLeft = mBackgroundLeft;
                                            smoothScrollTo((int) (-getLastValidSectionWidth() - DEFAULT_SECTION_LEFT_MARGIN), 300);
                                        }
                                        else {
                                            smoothScrollTo(currentSectionRightEdge, 200);
                                        }
                                    } else if (distance < -DEFAULT_SCROLLING_TRIGGER) { // snap to next section
                                        if (viewportLeft > contentWidth-getLastValidSectionWidth()) {
                                            if (validSectionCount > 1) {
                                                mIsWrappingToHead = true;
                                                mLastViewportLeft = viewportLeft;
                                                mLastHeaderLeft = mHeader.getLeft();
                                                mLastBackgroundLeft = mBackgroundLeft;
                                                smoothScrollTo(contentWidth, 300);
                                            }
                                            else {
                                                smoothScrollTo(contentWidth - effectiveViewportWidth, 200);
                                            }
                                        }
                                        else {
                                            smoothScrollTo(currentSectionRightEdge, 200);
                                        }
                                    } else { // jump back to original section
                                        if (mOriginalSection != null) {
                                            smoothScrollTo(mOriginalSection.getLeft() - DEFAULT_SECTION_LEFT_MARGIN, 200);
                                        }
                                        else {
                                            Log.e(LOG_TAG, "original section is null whild snapping back to it");
                                            smoothScrollTo(0, 200);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;

                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }

                    mLastMotionX = -1;
                    mLastMotionY = -1;
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
                mLastMotionX = -1;
                mLastMotionY = -1;
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
            mLastMotionY = ev.getY(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    /*
     * Calculate the sum of the measured widths of all sections and their corresponding margins.
     * NOTE: Sections with View.GONE visibility are not included.
     */
    private int getMeasuredContentWidth() {
        int contentWidth = 0;
        for (PanoramaSection ps : mSectionList) {
            if (ps.getVisibility() != View.GONE)
                contentWidth += ps.getMeasuredWidth() + DEFAULT_SECTION_LEFT_MARGIN;
        }
        return contentWidth;
    }

    /*
     * Calculate the sum of the layout widths of all sections and their corresponding margins.
     * NOTE: Sections with View.GONE visibility are not included.
     */
    private int getContentWidth() {
        int contentWidth = 0;
        for (PanoramaSection ps : mSectionList) {
            if (ps.getVisibility() != View.GONE)
                contentWidth += ps.getWidth() + DEFAULT_SECTION_LEFT_MARGIN;
        }
        return contentWidth;
    }

    private int getValidSectionCount() {
        int count = 0;
        for (PanoramaSection ps: mSectionList) {
            if (ps.getVisibility() != View.GONE) {
                count++;
            }
        }
        return count;
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

    @Override
    public void computeScroll() {
        if (mIsScrolling) {
            scrollTo(mScrollingOffset, 0);
            invalidate();
        }
        else {
            if (mIsWrappingToHead) {
                mIsWrappingToHead = false;
                post(new Runnable() {
                    @Override
                    public void run() {
                        scrollTo(0);
                        invalidate();
                    }
                });
            }
            if (mIsWrappingToTail) {
                mIsWrappingToTail = false;
                post(new Runnable() {
                    @Override
                    public void run() {
                        scrollTo(getContentWidth() - getLastValidSectionWidth() - DEFAULT_SECTION_LEFT_MARGIN);
                        invalidate();
                    }
                });
            }
        }
    }

    private void smoothScrollTo(int endX, int duration) {
        final int startX = getScrollX();
        mScroller.startScroll(startX, 0, endX - startX, 0, duration);
        mIsScrolling = true;
        requestLayout();
        invalidate();
    }

    public View getHeader() {
        return mHeader;
    }

    public SlidingStyle getHeaderLayoutStyle() {
        return mSlidingStyle;
    }

    public void setHeaderLayoutStyle(SlidingStyle s) {
        mSlidingStyle = s;
    }

    PanoramaSection findCurrentSection() {
        int index = findCurrentSectionIndex();
        return index == INVALID_POINTER ? null : mSectionList.get(index);
    }

    PanoramaSection findSectionUnderPoint(int pointerX) {
        int index = findSectionIndexUnderPoint(pointerX);
        return index == INVALID_POINTER ? null : mSectionList.get(index);
    }

    int findCurrentSectionIndex() {
        return findSectionIndexUnderPoint(getScrollX());
    }

    int findSectionIndexUnderPoint(int pointerX) {
        final int count = mSectionList.size();
        final int viewportLeft = pointerX;
        for (int index = 0; index < count; ++index) {
            final PanoramaSection ps = mSectionList.get(index);
            if ((ps.getLeft() - DEFAULT_SECTION_LEFT_MARGIN) <= viewportLeft &&
                viewportLeft < ps.getRight() &&
                ps.getVisibility() != View.GONE) {
                return index;
            }
        }
        return INVALID_POINTER;
    }

    @Override
    public void draw (Canvas canvas) {
        super.draw(canvas);

        if (mDebugMode) {
            final int infoBarHeight = 40;
            Paint pt = new Paint();
            pt.setStyle(Paint.Style.STROKE);
            pt.setColor(Color.GREEN);
            canvas.drawRect(mBackgroundLeft, getTop(), mBackgroundLeft + mBackgroundWidth - 1.0f, getHeight() - 1.0f, pt);
            pt.setColor(Color.YELLOW);
            canvas.drawRect(mHeader.getLeft(), mHeader.getTop(), mHeader.getRight() - 1.0f, mHeader.getBottom() - 1.0f, pt);
            pt.setColor(Color.CYAN);
            canvas.drawRect(mHeaderMirage.getLeft(), mHeaderMirage.getTop(), mHeaderMirage.getRight() - 1.0f, mHeaderMirage.getBottom() - 1.0f, pt);
            pt.setColor(Color.RED);
            for (PanoramaSection ps : mSectionList) {
                canvas.drawRect(ps.getLeft(), ps.getTop(), ps.getRight() - 1.0f, ps.getBottom() - 1.0f, pt);
            }
            pt.setColor(0xaa000000);
            pt.setStyle(Paint.Style.FILL);
            pt.setAntiAlias(true);
            canvas.drawRect(getScrollX(), getScrollY() + getBottom() - infoBarHeight, getScrollX() + getRight(), getScrollY() + getBottom(), pt);
            StringBuilder sb = new StringBuilder();
            sb.append("Lv=");
            sb.append(getScrollX());
            sb.append(" Lh=");
            sb.append(mHeader.getLeft());
            sb.append(" Lb=");
            sb.append(mBackgroundLeft);
            pt.setColor(Color.WHITE);
            pt.setTextSize(24);
            canvas.drawText(sb.toString(), getScrollX() + 10, getScrollY() + getBottom() - 12, pt);
        }

        if (mDemoMode) {
            if (mLastMotionX >=0 && mLastMotionY >= 0) {
                Paint pt = new Paint();
                pt.setColor(0xaaffff00);
                pt.setAntiAlias(true);
                canvas.drawCircle(getScrollX() + mLastMotionX, getScrollY() + mLastMotionY, 60, pt);
            }
        }
    }

    // TODO add tinting and Gaussian blur effect for background
    @Override
    protected void onDraw (Canvas canvas) {
        if (mBackgroundDrawable != null) {
            final int viewportLeft = getScrollX();
            mBackgroundDrawable.setBounds(mBackgroundLeft, 0, mBackgroundLeft + mBackgroundWidth, mBackgroundHeight);
            mBackgroundDrawable.draw(canvas);

            if (viewportLeft < 0 || mBackgroundLeft < 0) { // wrap to tail
                mBackgroundDrawable.setBounds(mBackgroundLeft - mBackgroundWidth, 0, mBackgroundLeft, mBackgroundHeight);
                mBackgroundDrawable.draw(canvas);
            }

            if (mBackgroundLeft + mBackgroundWidth < viewportLeft + getWidth()) { // wrap to head
                mBackgroundDrawable.setBounds(mBackgroundLeft + mBackgroundWidth, 0, mBackgroundLeft + 2 * mBackgroundWidth, mBackgroundHeight);
                mBackgroundDrawable.draw(canvas);
            }
        }
    }

    @Override
    public void setBackgroundDrawable(Drawable d) {
        mBackgroundDrawable = d;
        invalidate();
    }

    private int getLastValidSectionWidth() {
        return mSectionList.get(getLastValidSectionIndex()).getWidth();
    }

    private int getLastValidSectionIndex() {
        final int sectionCount = mSectionList.size();
        int lastValidSectionIndex = sectionCount;
        for (int i = sectionCount - 1; i >= 0; --i) {
            if (mSectionList.get(i).getVisibility() != View.GONE) {
                lastValidSectionIndex = i;
                break;
            }
        }
        return lastValidSectionIndex;
    }

    private int getFirstValidSectionIndex() {
        final int sectionCount = mSectionList.size();
        int firstValidSectionIndex = 0;
        for (int i = 0; i < sectionCount; ++i) {
            if (mSectionList.get(i).getVisibility() != View.GONE) {
                firstValidSectionIndex = i;
                break;
            }
        }
        return firstValidSectionIndex;
    }

    // ============================= Debug Facilities ===========================

    private void dumpMotionEvent(String tag, MotionEvent ev) {
        Log.d(tag, "==================================================");
        Log.d(tag, "Action:" + Integer.toHexString(ev.getAction()));
        Log.d(tag, "ActionIndex:" + Integer.toHexString(ev.getActionIndex()));
        Log.d(tag, "ActionMasked:" + actionToString(ev.getActionMasked()));
        final int pointerCount = ev.getPointerCount();
        Log.d(tag, "At time " + ev.getEventTime() + ":");
        for (int p = 0; p < pointerCount; p++) {
            Log.d(tag, "  pointer " + ev.getPointerId(p) + ":(" + ev.getX(p) + "," + ev.getY(p) + ")");
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

    public final boolean isInDebugMode() {
        return mDebugMode;
    }

    public final void setDebugMode(boolean m) {
        mDebugMode = m;
    }

    public final boolean isInDemoMode() {
        return mDemoMode;
    }

    public final void setDemoMode(boolean m) {
        mDemoMode = m;
    }


    //================================= inner classes =====================================



    public static enum SlidingStyle {
        BOUNDED,
        TOWED,
        SYNCED,
    }

    public static enum BackgroundScalingStyle {
        NONE,
        VERTICAL_STRETCH,
        VERTICAL_FILL,
    }

    /**
     * This class provides width suggestion for panorama section.
     * @author jiaoye
     *
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {

        int sectionWidth = Integer.MIN_VALUE;
        int viewportLeft = Integer.MIN_VALUE;
        int viewportRight = Integer.MIN_VALUE;
        PanoramaSection.SlidingStyle slidingStyle = PanoramaSection.SlidingStyle.TOWED;

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
                    + width + ", height="
                    + height + ", sectionWidth="
                    + sectionWidth + ", viewportLeft="
                    + viewportLeft + ",slidingStyle="
                    + slidingStyle + "}";
        }
    }

    private class Scroller extends android.widget.Scroller {
        public Scroller() {
            super(getContext(), new Interpolator() {
                final private double scale = 1 - 1/Math.E;
                @Override
                public float getInterpolation(float input) {
                    return (float) ((1-Math.exp(-input)) / scale);
                }
            });
        }

        public boolean computeScrollOffset() {
            boolean isAnimating = super.computeScrollOffset();
            return isAnimating;
        }
    }
}
