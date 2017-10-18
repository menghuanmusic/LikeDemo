package com.xm.likedemo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class LikeView extends View {

    private static final String TAG = LikeView.class.getName();
    private static final int SPACE = 10; // 间隙
    private static final float DEFAULT_SCALE = 0.5f; // 默认点击缩放比例
    private static final int DEFAULT_TEXT_COLOR = Color.parseColor("#CCCCCC");
    private static final int DEFAULT_CIRCLE_COLOR = Color.parseColor("#E4583E");

    private int mSelectedResId;
    private int mShiningResId;
    private int mUnSelectedResId;

    private Bitmap mSelectedBitmap;
    private Bitmap mShiningBitmap;
    private Bitmap mUnSelectedBitmap;

    /**
     * 当前view处于选中或未选中的状态
     */
    private boolean mSelected = false;

    /**
     * 点击时缩放的比例0~1f
     */
    private float mScale;

    /**
     * 数量
     */
    private int mTextNum;

    /**
     * 不变的前部文本内容
     */
    private String mPreText;

    /**
     * 旧文本
     */
    private String mOldText;

    /**
     * 数字切换的方向，1->向上 -1->向下
     */
    private int mDirection;

    private Paint mBitmapPaint;
    private Paint mTextPaint;
    private Paint mTextChangePaint;
    private Paint mCirclePaint;

    /**
     * 文本开始绘制的起始坐标
     */
    private float mStartTextX;
    private float mStartTextY;

    private boolean isChange;

    private boolean isNumAnim;

    /**
     * 速率
     */
    private float fraction;

    private ObjectAnimator mAnimator;

    public LikeView(Context context) {
        this(context, null);
    }

    public LikeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LikeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        int textColor = DEFAULT_TEXT_COLOR;
        int circleColor = DEFAULT_CIRCLE_COLOR;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LikeView);

            mSelectedResId = a.getResourceId(R.styleable.LikeView_selectedSrc, R.drawable.ic_messages_like_selected);
            mShiningResId = a.getResourceId(R.styleable.LikeView_shining, R.drawable.ic_messages_like_selected_shining);
            mUnSelectedResId = a.getResourceId(R.styleable.LikeView_unselectedSrc, R.drawable.ic_messages_like_unselected);
            mScale = a.getFloat(R.styleable.LikeView_scale, DEFAULT_SCALE);
            if (mScale > 1) {
                mScale = 1f;
            }
            mTextNum = a.getInt(R.styleable.LikeView_num, 0);
            textColor = a.getColor(R.styleable.LikeView_numTextColor, DEFAULT_TEXT_COLOR);
            circleColor = a.getColor(R.styleable.LikeView_clickCircleColor, DEFAULT_CIRCLE_COLOR);
            a.recycle();
        }

        mBitmapPaint = new Paint();
        mBitmapPaint.setAntiAlias(true);
        mTextPaint = new Paint();
        mTextPaint.setTextSize(DensityUtils.sp2px(context, 12));
        mTextPaint.setAntiAlias(true);
        mTextPaint.setColor(textColor);
        mTextChangePaint = new Paint();
        mTextChangePaint.setTextSize(DensityUtils.sp2px(context, 11));
        mTextChangePaint.setAntiAlias(true);
        mTextChangePaint.setColor(textColor);
        mCirclePaint = new Paint();
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setColor(circleColor);
        mCirclePaint.setStyle(Paint.Style.STROKE);

        mSelectedBitmap = BitmapFactory.decodeResource(getResources(), mSelectedResId);
        mUnSelectedBitmap = BitmapFactory.decodeResource(getResources(), mUnSelectedResId);
        if (mShiningResId != 0) {
            mShiningBitmap = BitmapFactory.decodeResource(getContext().getResources(), mShiningResId);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int shiningHeight = mShiningBitmap != null ? mShiningBitmap.getHeight() : 0;
        int height = mSelectedBitmap.getHeight() + shiningHeight / 2 + getPaddingTop() + getPaddingBottom()
                + SPACE/*若shiningHeight=0，上下部间隙共用，反之，该间隙用于下部*/;
        int width = mSelectedBitmap.getWidth() + (int) measureTextWidth(String.valueOf(mTextNum))
                + SPACE * 3/*左右间隙，图片和文本内容间隙，总共三个 */ + getPaddingLeft() + getPaddingRight();

        mStartTextX = mSelectedBitmap.getWidth() + SPACE;
        mStartTextY = mSelectedBitmap.getHeight() + shiningHeight / 2 - measureTextBaseHeight() / 2 + (shiningHeight == 0 ? SPACE / 4 : 0);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mSelectedBitmap == null || mUnSelectedBitmap == null) {
            throw new IllegalArgumentException("mSelectedBitmap or mUnSelectedBitmap is null.");
        }

        if (mShiningBitmap == null) {
            canvas.translate(SPACE + getPaddingLeft(), SPACE / 2 + getPaddingTop());
        } else {
            canvas.translate(SPACE + getPaddingLeft(), getPaddingTop());
        }
        if (mSelected) {
            drawSelected(canvas);
        } else {
            drawUnSelected(canvas);
        }

        drawText(canvas);
    }

    private void drawSelected(Canvas canvas) {
        drawBitmap(canvas, mSelectedBitmap, mBitmapPaint);

        if (mShiningBitmap != null) {
            drawShiningBitmap(canvas, mShiningBitmap, mBitmapPaint);
        }

        // 画圆圈
        if (isNumAnim) {
            float circleX = mSelectedBitmap.getWidth() / 2;
            float circleY = mShiningBitmap != null ? mSelectedBitmap.getHeight() / 2 + mSelectedBitmap.getHeight() / 2 : mSelectedBitmap.getHeight() / 2;
            float r = mSelectedBitmap.getWidth() / 2 * mScale + mSelectedBitmap.getWidth() / 2 * (1 - mScale) * fraction;
            canvas.drawCircle(circleX, circleY, r, mCirclePaint);
        }
    }

    private void drawUnSelected(Canvas canvas) {
        drawBitmap(canvas, mUnSelectedBitmap, mBitmapPaint);
    }

    private void drawBitmap(Canvas canvas, Bitmap bitmap, Paint Paint) {
        int shiningHeight = mShiningBitmap != null ? mShiningBitmap.getHeight() : 0;

        Bitmap drawBitmap;
        float top = shiningHeight / 2;
        float left = 0;
        if (isChange) {
            Matrix matrix = new Matrix();
            matrix.postScale(mScale, mScale);
            drawBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            top = top * mScale + bitmap.getWidth() * (1 - mScale) / 2; // 由于bitmap缩放了，因此需要修改top的位置
            left = bitmap.getWidth() * (1 - mScale) / 2;
        } else {
            drawBitmap = bitmap;
        }

        if (mShiningBitmap != null) {
            canvas.drawBitmap(drawBitmap, left, top, Paint);
        } else {
            canvas.drawBitmap(drawBitmap, left, 0, Paint);
        }
    }

    private void drawShiningBitmap(Canvas canvas, Bitmap bitmap, Paint paint) {
        canvas.save();

        Bitmap drawBitmap;
        float top = 0;
        if (isChange) {
            Matrix matrix = new Matrix();
            matrix.postScale(mScale, mScale);
            drawBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            top = bitmap.getHeight() * (1 - mScale) / 2;
        } else {
            drawBitmap = bitmap;
        }
        canvas.drawBitmap(drawBitmap, (mSelectedBitmap.getWidth() - drawBitmap.getWidth()) / 2, top, paint);
        canvas.restore();
    }

    private void drawText(Canvas canvas) {
        canvas.save();
        canvas.translate(mStartTextX, mStartTextY);
        if (isNumAnim) {
            // 画前部分
            canvas.drawText(mPreText == null ? "" : mPreText, 0, 0, mTextPaint);

            float preTextWidth = measureTextWidth(mPreText);
            float textHeight = measureTextHeight();
            // 画旧的后部分
            canvas.drawText(handleAfterText(mOldText, mPreText), preTextWidth, -1 * mDirection * textHeight * fraction, mTextChangePaint);
            // 画新的后部分
            canvas.drawText(handleAfterText(String.valueOf(mTextNum), mPreText), preTextWidth, mDirection * textHeight * (1 - fraction), mTextChangePaint);
        } else {
            canvas.drawText(String.valueOf(mTextNum), 0, 0, mTextPaint);
        }
        canvas.restore();
    }

    public void setSelected(boolean selected) {
        this.mSelected = selected;

        if (mSelected) {
            setNum(mTextNum + 1);
            return;
        }
        setNum(mTextNum - 1);
    }

    public boolean isSelected() {
        return mSelected;
    }

    /**
     *
     * @param scale 0~1f
     */
    public void setScale(float scale) {
        if (scale < 0 || scale > 1) {
            throw new IllegalArgumentException("scale is error. need to scale > 0 and scale < 1.");
        }
        this.mScale = scale;
    }

    public void setNum(int num) {
        mDirection = num > mTextNum ? 1 : -1;
        mPreText = handlePreText(mTextNum, num);
        mOldText = String.valueOf(mTextNum);
        mTextNum = num;

        updateAnim();

        if (String.valueOf(mTextNum).length() != mOldText.length()) {
            // 文本内容长度变化，控件宽度需要修改
            requestLayout();
            return;
        }
        postInvalidate();
    }

    private void updateAnim() {
        mAnimator = ObjectAnimator.ofFloat(this, "numUpdate", 0f, 1f);
        mAnimator.setDuration(200);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                fraction = animation.getAnimatedFraction();
                if (fraction != 0) {
                    postInvalidate();
                }
            }
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isNumAnim = false;
            }
        });
        isNumAnim = true;
        mAnimator.start();
    }

    private String handlePreText(int oldNum, int newNum) {
        String oldStr = String.valueOf(oldNum);
        String newStr = String.valueOf(newNum);

        char[] oldCharArray = oldStr.toCharArray();
        char[] newCharArray = newStr.toCharArray();

        int minLength = oldCharArray == null || newCharArray == null ? 0 : (oldCharArray.length < newCharArray.length ? oldCharArray.length : newCharArray.length);
        if (minLength > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < minLength; i++) {
                if (oldCharArray[i] != newCharArray[i]) {
                    break;
                }
                sb.append(oldCharArray[i]);
            }
            return sb.toString();
        }

        return null;
    }

    private String handleAfterText(String text, String preText) {
        if (text == null) {
            throw new IllegalArgumentException("text is null.");
        }
        if (TextUtils.isEmpty(preText)) {
            return text;
        }
        int start = text.indexOf(preText);
        if (start == -1) {
            return text;
        }
        return text.substring(start + preText.length(), text.length());
    }

    private float measureTextWidth(String text) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        return mTextPaint.measureText(text);
    }

    private float measureTextHeight() {
        Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
        return metrics.bottom - metrics.top;
    }

    private float measureTextBaseHeight() {
        Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
        return -metrics.top;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.i(TAG, "ACTION_DOWN");
                isChange = true;
                postInvalidate();
                break;
            case MotionEvent.ACTION_UP:
                Log.i(TAG, "ACTION_UP");
                mSelected = !mSelected;
                isChange = false;
                int num = mSelected ?  mTextNum + 1 : mTextNum - 1;
                setNum(num);
                postInvalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.i(TAG, "ACTION_CANCEL");
                isChange = false;
                postInvalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                Log.i(TAG, "ACTION_MOVE");
                break;
        }
        return true;
    }
}
