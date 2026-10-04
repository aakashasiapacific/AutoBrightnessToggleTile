package com.kasana.autobrightness;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.PathInterpolator;
import android.widget.Switch;

/** iOS-style switch: 51x31 track, white thumb that stretches while pressed. */
final class IosSwitch extends View {

    interface Listener {
        void onToggle(IosSwitch view, boolean checked);
    }

    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final int onColor;
    private final int offColor;

    private boolean checked;
    private float position;   // 0 = off, 1 = on
    private float press;      // 0..1, thumb stretch while finger is down
    private ValueAnimator positionAnim;
    private ValueAnimator pressAnim;
    private Listener listener;

    IosSwitch(Context context, int onColor, int offColor) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.onColor = onColor;
        this.offColor = offColor;
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setShadowLayer(3f * density, 0f, 1.5f * density, 0x29000000);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleByUser();
            }
        });
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setChecked(boolean value, boolean animate) {
        if (checked == value) return;
        checked = value;
        float target = value ? 1f : 0f;
        if (positionAnim != null) positionAnim.cancel();
        if (!animate || !isLaidOut()) {
            position = target;
            invalidate();
        } else {
            positionAnim = ValueAnimator.ofFloat(position, target);
            positionAnim.setDuration(300);
            positionAnim.setInterpolator(new PathInterpolator(0.32f, 0.72f, 0f, 1f));
            positionAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    position = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            positionAnim.start();
        }
    }

    private void toggleByUser() {
        setChecked(!checked, true);
        if (Build.VERSION.SDK_INT >= 34) {
            performHapticFeedback(checked ? HapticFeedbackConstants.TOGGLE_ON
                                          : HapticFeedbackConstants.TOGGLE_OFF);
        } else {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        if (listener != null) listener.onToggle(this, checked);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animatePress(1f);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animatePress(0f);
                break;
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    private void animatePress(float target) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, target);
        pressAnim.setDuration(160);
        pressAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                press = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        pressAnim.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(Math.round(59 * density), widthSpec),
                resolveSize(Math.round(44 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float p = Math.max(0f, Math.min(1f, position));
        float trackW = 51 * density;
        float trackH = 31 * density;
        float left = (getWidth() - trackW) / 2f;
        float top = (getHeight() - trackH) / 2f;

        trackPaint.setColor((Integer) ARGB.evaluate(p, offColor, onColor));
        rect.set(left, top, left + trackW, top + trackH);
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, trackPaint);

        float margin = 2 * density;
        float d = trackH - 2 * margin;
        float thumbW = d + press * 7 * density;
        float x = left + margin + p * (trackW - 2 * margin - thumbW);
        rect.set(x, top + margin, x + thumbW, top + margin + d);
        canvas.drawRoundRect(rect, d / 2f, d / 2f, thumbPaint);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Switch.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(checked);
    }
}
