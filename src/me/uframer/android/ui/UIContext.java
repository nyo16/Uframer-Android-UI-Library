/**
 *
 */
package me.uframer.android.ui;

import android.content.Context;
import android.graphics.Typeface;

/**
 * <p>
 * NOTE: Due to the limits of Android ADK and Microsoft license of Segoe fonts, the user has
 * to provide the following fonts in their applications assets/fonts/ directory:
 * <table>
 * <tr><td>SegoeWP-Black.ttf</td></tr>
 * <tr><td>SegoeWP-Bold.ttf</td></tr>
 * <tr><td>SegoeWP-Semibold.ttf</td></tr>
 * <tr><td>SegoeWP.ttf</td></tr>
 * <tr><td>SegoeWP-Semilight.ttf</td></tr>
 * <tr><td>SegoeWP-Light.ttf</td></tr>
 * </table>
 * </p>
 * @author jiaoye
 *
 */
public class UIContext {
    public final Typeface blackTypeface;
    public final Typeface boldTypeface;
    public final Typeface semiboldTypeface;
    public final Typeface normalTypeface;
    public final Typeface semilightTypeface;
    public final Typeface lightTypeface;

    private static UIContext mInstance = null;

    private UIContext(Context context) {
        blackTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/SegoeWP-Black.ttf");
        boldTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/SegoeWP-Bold.ttf");
        semiboldTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/SegoeWP-Semibold.ttf");
        normalTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/SegoeWP.ttf");
        semilightTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/SegoeWP-Semilight.ttf");
        lightTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/SegoeWP-Light.ttf");
    }

    public static UIContext getUIContext(Context context) {
        if (mInstance == null) {
            mInstance = new UIContext(context);
        }

        return mInstance;
    }
}
