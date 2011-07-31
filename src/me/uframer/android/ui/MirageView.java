/**
 *
 */
package me.uframer.android.ui;

import android.content.Context;
import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;

/**
 * <p>
 * This view clones the size and look of another view but accept no inputs.
 * </p>
 * TODO: add support for clipping
 * @author jiaoye
 *
 */
class MirageView extends View {

    private static final String LOG_TAG = MirageView.class.toString();

    public static enum ClippingDirection {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
    }

    private static enum ClippingType {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        RECT,
        NONE,
    }

    final private View mView;

    private Canvas mCanvas;
    private Bitmap mBitmap;
    private boolean mFrozen;
    private Rect mClippingRect;
    private int mClippingOffset;
    private ClippingType mClippingType;

    /**
     * @param context
     * @param view the original view
     */
    public MirageView(Context context, View view) {
        super(context);
        mView = view;
        mClippingType = ClippingType.NONE;
    }

    /**
     * @param context
     * @param view the original view
     * @param clip the clipping area
     */
    public MirageView(Context context, View view, Rect clip) {
        super(context);
        mView = view;
        mClippingRect = clip;
        mClippingType = ClippingType.RECT;
    }

    /**
     * @param context
     * @param view the original view
     * @param direction from which edge we apply the {@offset}
     * @param offset the offset in pixels
     */
    public MirageView(Context context, View view, ClippingDirection direction, int offset) {
        super(context);
        mView = view;
        if (offset <= 0) throw new Error("offset can only be positive");
        mClippingOffset = offset;
        switch(direction) {
        case LEFT:
            mClippingType = ClippingType.LEFT;
            break;
        case RIGHT:
            mClippingType = ClippingType.RIGHT;
            break;
        case TOP:
            mClippingType = ClippingType.TOP;
            break;
        case BOTTOM:
            mClippingType = ClippingType.BOTTOM;
            break;
        }
    }

    View getView() {
        return mView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mFrozen) {
            setMeasuredDimension(mBitmap.getWidth(), mBitmap.getHeight());
        }
        else {
            final View v = getView();
            constructClippingRect(v.getMeasuredWidth(), v.getMeasuredHeight());
            setMeasuredDimension(mClippingRect.width(), mClippingRect.height());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mFrozen) {
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
        else {
            canvas.save();
            canvas.clipRect(mClippingRect.left, mClippingRect.top, mClippingRect.right, mClippingRect.bottom);
            canvas.translate(mClippingRect.left, mClippingRect.top);
            getView().draw(canvas);
            canvas.restore();
        }
    }

    /*
     * <p>Take a snapshot of the original view and freeze the state.</p>
     * <p>NOTE: The original view must have been measured before you call this method.</p>
     */
    public void freeze() {
        final View v = getView();
        constructClippingRect(v.getMeasuredWidth(), v.getMeasuredHeight());
        mCanvas = new Canvas();
        mBitmap = Bitmap.createBitmap(mClippingRect.width(), mClippingRect.height(), Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(mBitmap);
        // TODO test this
        mCanvas.translate(-mClippingRect.left, -mClippingRect.top);
        v.draw(mCanvas);
        mCanvas.translate(mClippingRect.left, mClippingRect.top);
        mFrozen = true;
    }

    /*
     * <p>Release the resources.</p>
     */
    public void unfreeze() {
        mCanvas = null;
        mBitmap.recycle();
        mBitmap = null;
        mFrozen = false;
    }

    private void constructClippingRect(int mw, int mh) {
        switch (mClippingType) {
        case LEFT:
            mClippingRect = new Rect(0, 0, (mClippingOffset < mw ? mClippingOffset : mw), mh);
            break;
        case RIGHT:
            mClippingRect = new Rect((mClippingOffset < mw ? mw - mClippingOffset : 0), 0, mw, mh);
            break;
        case TOP:
            mClippingRect = new Rect(0, 0, mw, (mClippingOffset < mh ? mClippingOffset : mh));
            break;
        case BOTTOM:
            mClippingRect = new Rect(0, (mClippingOffset < mh ? mh - mClippingOffset : 0), mw, mh);
            break;
        case NONE:
            mClippingRect = new Rect(0, 0, mw, mh);
            break;
        }
    }
}
