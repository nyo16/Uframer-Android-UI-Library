package me.uframer.android.ui;

import android.graphics.Camera;
import android.graphics.Matrix;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class FlipInAnimation extends Animation {

    private class Interpolator implements android.view.animation.Interpolator {

        @Override
        public float getInterpolation(float t) {
            return (1.0f - (1.0f - t) * (1.0f - t));
        }
    }

    private static final float FROM_DEGREES = 45.0f;
    private static final float TO_DEGREES = 0.0f;
    private static final float FROM_DEPTH = 100.0f;
    private static final float TO_DEPTH = 0.0f;
    private static final int DURATION = 500;
    private float mPivotX;
    private float mPivotY;
    private Camera mCamera;

    public FlipInAnimation(float pivotX, float pivotY) {
        mPivotX = pivotX;
        mPivotY = pivotY;
        setDuration(DURATION);
        setInterpolator(new Interpolator());
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        mCamera = new Camera();
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {

        final float degrees = FROM_DEGREES + (TO_DEGREES - FROM_DEGREES) * interpolatedTime;
        final float depthZ = FROM_DEPTH + (TO_DEPTH - FROM_DEPTH) * interpolatedTime;
        final float transparency = interpolatedTime;

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
