/**
 * 
 */
package me.uframer.android.ui;

import android.graphics.Camera;
import android.graphics.Matrix;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * <p>
 * This class will flip the view out as if it's a page of book.
 * </p>
 * 
 * @author jiaoye
 *
 */
public class FlipOutAnimation extends Animation {
    
    private class Interpolator implements android.view.animation.Interpolator {
        private final float TENSION = 1.0f;

        @Override
        public float getInterpolation(float t) {
            return t * t * t * ((TENSION + 1) * t - TENSION);
        }
    }

    private static final float FROM_DEGREES = -0.0f;
    private static final float TO_DEGREES = -45.0f;
    private static final float FROM_DEPTH = 0.0f;
    private static final float TO_DEPTH = -100.0f;
    private static final int DURATION = 400;
    private float mPivotX;
    private float mPivotY;
    private Camera mCamera;

    public FlipOutAnimation(float pivotX, float pivotY) {        
        mPivotX = pivotX;
        mPivotY = pivotY;
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mCamera = new Camera();
        setDuration(DURATION);
        setInterpolator(new Interpolator());
        setFillBefore(true);
        setFillAfter(true);
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {

        final float degrees = FROM_DEGREES + (TO_DEGREES - FROM_DEGREES) * interpolatedTime;
        final float depthZ = FROM_DEPTH + (TO_DEPTH - FROM_DEPTH) * interpolatedTime;        
        final float transparency = 1.0f - interpolatedTime;

        t.setAlpha(transparency);
        Matrix matrix = t.getMatrix();

        mCamera.save();
        mCamera.translate(0, 0, depthZ);
        mCamera.rotateY(degrees);
        mCamera.getMatrix(matrix);
        mCamera.restore();

        matrix.preTranslate(-mPivotX, -mPivotY);
        matrix.postTranslate(mPivotX, mPivotY);
    }

}
