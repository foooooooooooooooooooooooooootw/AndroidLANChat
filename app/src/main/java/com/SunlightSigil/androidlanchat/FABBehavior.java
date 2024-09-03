package com.SunlightSigil.androidlanchat;


import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class FABBehavior extends CoordinatorLayout.Behavior<FloatingActionButton> {

    public FABBehavior() {
        super();
    }

    public FABBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, FloatingActionButton child, int layoutDirection) {
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) child.getLayoutParams();
        params.anchorGravity = Gravity.BOTTOM | Gravity.END;
        child.setLayoutParams(params);

        return super.onLayoutChild(parent, child, layoutDirection);
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, FloatingActionButton child, View dependency) {
        if (dependency instanceof FloatingActionButton) {
            if (child.getId() == R.id.fab_add) {
                boolean isExpanded = child.getVisibility() == View.VISIBLE;
                child.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                if (isExpanded) {
                    RotateAnimation rotate = new RotateAnimation(0, 45,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f);
                    rotate.setInterpolator(new AccelerateInterpolator());
                    rotate.setDuration(200);
                    child.startAnimation(rotate);
                }
            }
        }
        return super.onDependentViewChanged(parent, child, dependency);
    }
}