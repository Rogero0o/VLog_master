package com.roger.shootrefreshview

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.util.AttributeSet
import android.util.Property
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.LinearInterpolator

class ShootRefreshView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr), IRefreshStatus {

    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mBounds = RectF()

    private var mRadius: Int = 0
    private var mCenterX: Int = 0
    private var mCenterY: Int = 0

    private var mStrokeColor: Int = 0
    private var mGradientStartColor: Int = 0
    private var mGradientEndColor: Int = 0

    private var mStrokeWidth: Int = 0

    private var mOutRingRotateAngle: Float = 0.toFloat()

    private var mShootLineRotateRadians: Float = 0.toFloat()
    private var mShootLineTotalRotateAngle: Float = 0.toFloat()

    private var mRefreshingShader: Shader? = null

    private var mPreShootLineTotalRotateAnimator: ValueAnimator? = null
    private var mShootLineRotateAnimator: ValueAnimator? = null
    private var mShootLineStretchAnimator: ValueAnimator? = null
    private var mOutRingRotateAnimator: ValueAnimator? = null
    private var linearInterpolator: LinearInterpolator?=null
    private var animation: ValueAnimator? = null

    init {
        resolveAttrs(context, attrs)
        initPaint()
        initAnimator()
        reset()
    }

    private fun resolveAttrs(context: Context, attrs: AttributeSet?) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.ShootRefreshView)
        mStrokeColor = ta.getColor(
            R.styleable.ShootRefreshView_strokeColor,
            DEFAULT_STROKE_COLOR
        )
        mGradientStartColor = ta.getColor(
            R.styleable.ShootRefreshView_gradientStartColor,
            DEFAULT_GRADIENT_START_COLOR
        )
        mGradientEndColor = ta.getColor(R.styleable.ShootRefreshView_gradientEndColor, DEFAULT_GRADIENT_END_COLOR)
        mStrokeWidth = ta.getDimensionPixelSize(
            R.styleable.ShootRefreshView_strokeWidth,
            DensityUtil.dp2px(getContext(), 1.0f).toInt()
        )
        ta.recycle()

        mRefreshingShader = SweepGradient(
            0f, 0f, intArrayOf(mGradientStartColor, mGradientEndColor),
            floatArrayOf(0.0f, 1.0f)
        )
    }

    private fun initPaint() {
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = mStrokeWidth.toFloat()
        mPaint.color = mStrokeColor
    }

    private fun initAnimator() {
        //Note: the following uses the 'kwai Line' represent the six lines of the shutter

        //Step1: Rotate the 'kwai Line', but the shutter does not open
        mPreShootLineTotalRotateAnimator = ValueAnimator.ofFloat(
            -(SHOOT_LINE_ROTATE_START_DEGREE / 2.0f) - 240.0f,
            -(SHOOT_LINE_ROTATE_START_DEGREE / 2.0f) - 120.0f
        )
        mPreShootLineTotalRotateAnimator!!.interpolator = LinearInterpolator()
        mPreShootLineTotalRotateAnimator!!.duration = PRE_SHOOT_LINE_TOTAL_ROTATE_DURATION.toLong()
        mPreShootLineTotalRotateAnimator!!.addUpdateListener { animation ->
            mShootLineTotalRotateAngle = animation.animatedValue as Float
            invalidate()
        }

        //Step 2: Rotate the 'Kwai Line' and open the shutter
        val shootLineIntersectHolder = PropertyValuesHolder
            .ofFloat(
                SHOOT_LINE_ROTATE_RADIANS, SHOOT_LINE_ROTATE_START_RADIANS,
                SHOOT_LINE_ROTATE_END_RADIANS
            )
        val shootLineTotalRotateAnimatorHolder = PropertyValuesHolder
            .ofFloat(
                SHOOT_LINE_TOTAL_ROTATE_DEGREE, -(SHOOT_LINE_ROTATE_START_DEGREE / 2.0f) - 120.0f,
                -(SHOOT_LINE_ROTATE_START_DEGREE / 2.0f)
            )
        mShootLineRotateAnimator = ObjectAnimator.ofPropertyValuesHolder(
            this,
            shootLineIntersectHolder, shootLineTotalRotateAnimatorHolder
        )
        mShootLineRotateAnimator!!.interpolator = LinearInterpolator()
        mShootLineRotateAnimator!!.duration = SHOOT_LINE_ROTATE_DURATION.toLong()

        //Step3: Take the center of the 'Kwai Line' as the base point, and zoom 'Kwai Line'
        mShootLineStretchAnimator = ValueAnimator.ofFloat(SHOOT_LINE_ROTATE_END_RADIANS, 0.toFloat())
        mShootLineStretchAnimator!!.interpolator = LinearInterpolator()
        mShootLineStretchAnimator!!.duration = SHOOT_LINE_STRETCH_DURATION.toLong()
        mShootLineStretchAnimator!!.addUpdateListener { animation ->
            mShootLineRotateRadians = animation.animatedValue as Float
            mShootLineTotalRotateAngle = -(Math.toDegrees(mShootLineRotateRadians.toDouble()) / 2.0f).toFloat()

            invalidate()
        }

        //Step4: Perform a refresh animation, rotate the gradient ring
        mOutRingRotateAnimator = ValueAnimator.ofFloat(0.toFloat(), DEGREE_360)
        mOutRingRotateAnimator!!.repeatMode = ValueAnimator.REVERSE
        mOutRingRotateAnimator!!.repeatCount = ValueAnimator.INFINITE
        mOutRingRotateAnimator!!.interpolator = AccelerateDecelerateInterpolator()
        mOutRingRotateAnimator!!.duration = OUT_RING_ROTATE_DURATION.toLong()
        mOutRingRotateAnimator!!.addUpdateListener { animation ->
            mOutRingRotateAngle = animation.animatedValue as Float
            invalidate()
        }
        animation = ValueAnimator.ofFloat(0.toFloat(),1.toFloat())
        linearInterpolator = LinearInterpolator()
        animation?.interpolator = linearInterpolator
        animation?.duration = 500
        animation?.addUpdateListener {
            val progress = it.animatedValue as Float
            pullProgress(0.toFloat(), progress)
        }
        animation?.addListener(object :Animator.AnimatorListener{
            override fun onAnimationStart(animation: Animator?) {
            }
            override fun onAnimationEnd(animation: Animator?) {
                refreshing()
            }
            override fun onAnimationCancel(animation: Animator?) {
            }
            override fun onAnimationRepeat(animation: Animator?) {
            }
        })

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOuterRing(canvas)
        drawShootLine(canvas)
    }

    private fun drawOuterRing(canvas: Canvas) {
        canvas.save()
        canvas.translate(mCenterX.toFloat(), mCenterY.toFloat())
        if (mOutRingRotateAnimator!!.isRunning) {
            canvas.rotate(START_ANGLE + mOutRingRotateAngle)
            if (mPaint.shader !== mRefreshingShader) {
                mPaint.shader = mRefreshingShader
            }
            mPaint.strokeWidth = mRadius.toFloat()/2 + (mOutRingRotateAngle * mRadius.toFloat()) / (360 * 2)
            canvas.drawCircle(
                0.0f, 0.0f, mRadius.toFloat() / 4
                        + (mOutRingRotateAngle * mRadius.toFloat()) / (360 * 4), mPaint
            )
        } else {
            mPaint.shader = null
        }
        mPaint.strokeWidth = mStrokeWidth.toFloat()
        canvas.drawCircle(0.0f, 0.0f, mRadius.toFloat(), mPaint)
        canvas.restore()
    }

    private fun drawShootLine(canvas: Canvas) {
        if (mShootLineRotateRadians <= 0.0f || mOutRingRotateAnimator!!.isRunning) {
            return
        }

        mPaint.shader = null

        canvas.save()
        canvas.translate(mCenterX.toFloat(), mCenterY.toFloat())
        canvas.rotate(mShootLineTotalRotateAngle)

        for (i in 0..5) {
            canvas.save()
            canvas.rotate((-DEGREE_60 * i).toFloat())

            if (mShootLineRotateRadians > SHOOT_LINE_ROTATE_END_RADIANS) {
                val tanRotateAngle = Math.tan(mShootLineRotateRadians.toDouble())
                val tanRotateAngleOffset60 = Math.tan((mShootLineRotateRadians + INTERVAL_RADIANS).toDouble())

                //The intersection formula of 'Kwai Line'
                val stopX =
                    ((1.0 - SQRT_3 * tanRotateAngleOffset60) / (2.0 * (tanRotateAngle - tanRotateAngleOffset60))).toFloat() * mRadius
                val stopY = ((2.0 * tanRotateAngleOffset60 - tanRotateAngle
                        - SQRT_3.toDouble() * tanRotateAngle * tanRotateAngleOffset60) / (2.0 * (tanRotateAngle - tanRotateAngleOffset60))).toFloat() * mRadius
                //Note: (0, -Radius) is Y-axis negative direction
                canvas.drawLine(0f, (-mRadius).toFloat(), stopX, stopY, mPaint)
            } else {
                val tanRotateAngle = Math.tan(mShootLineRotateRadians.toDouble())

                //The zoom formula of 'Kwai Line'
                val stopX =
                    (2.0 * tanRotateAngle * mRadius.toDouble() / (Math.pow(tanRotateAngle, 2.0) + 1.0)).toFloat()
                val stopY =
                    ((Math.pow(tanRotateAngle, 2.0) - 1.0) * mRadius / (Math.pow(tanRotateAngle, 2.0) + 1.0)).toFloat()

                canvas.drawLine(0f, (-mRadius).toFloat(), stopX, stopY, mPaint)
            }

            canvas.restore()
        }

        canvas.restore()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mBounds.set(
            (0 + paddingLeft).toFloat(), (0 + paddingTop).toFloat(), (w - paddingRight).toFloat(),
            (h - paddingBottom).toFloat()
        )
        mBounds.inset(mStrokeWidth.toFloat(), mStrokeWidth.toFloat())

        mRadius = (Math.min(mBounds.width(), mBounds.height()) / 2).toInt()
        mCenterX = mBounds.centerX().toInt()
        mCenterY = mBounds.centerY().toInt()
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    override fun reset() {
        mOutRingRotateAnimator!!.cancel()

        mShootLineRotateRadians = SHOOT_LINE_ROTATE_START_RADIANS
        mShootLineTotalRotateAngle = -(SHOOT_LINE_ROTATE_START_DEGREE / 2.0f) - 240.0f
        mOutRingRotateAngle = 0.0f
        invalidate()
    }

    fun start(){
        animation?.start()
    }

    fun isStarted():Boolean{
        return mOutRingRotateAnimator!!.isRunning
    }

    override fun refreshing() {
        mOutRingRotateAngle = 0.0f
        mShootLineTotalRotateAngle = 0.0f
        mShootLineRotateRadians = 0.0f

        if (mOutRingRotateAnimator!!.isRunning) {
            mOutRingRotateAnimator!!.cancel()
        }
        mOutRingRotateAnimator!!.start()
    }

    override fun refreshComplete() {}

    override fun pullToRefresh() {

    }

    override fun releaseToRefresh() {

    }

    override fun pullProgress(pullDistance: Float, pullProgress: Float) {
        var pullProgress = pullProgress
        pullProgress = Math.min(1.0f, Math.max(0.0f, pullProgress))

        if (pullProgress < PRE_SHOOT_LINE_TOTAL_ROTATE_END_OFFSET) {
            mPreShootLineTotalRotateAnimator!!.currentPlayTime =
                    (pullProgress / PRE_SHOOT_LINE_TOTAL_ROTATE_END_OFFSET * PRE_SHOOT_LINE_TOTAL_ROTATE_DURATION).toLong()
        } else if (pullProgress < SHOOT_LINE_ROTATE_END_OFFSET) {
            mShootLineRotateAnimator!!.currentPlayTime =
                    ((pullProgress - PRE_SHOOT_LINE_TOTAL_ROTATE_END_OFFSET) / (SHOOT_LINE_ROTATE_END_OFFSET - PRE_SHOOT_LINE_TOTAL_ROTATE_END_OFFSET) * SHOOT_LINE_ROTATE_DURATION).toLong()
        } else {
            if (pullProgress == 1.0f) {
                mShootLineStretchAnimator!!.currentPlayTime = SHOOT_LINE_STRETCH_DURATION.toLong()
            } else {
                mShootLineStretchAnimator!!.currentPlayTime =
                        ((pullProgress - SHOOT_LINE_ROTATE_END_OFFSET) / (SHOOT_LINE_STRETCH_END_OFFSET - SHOOT_LINE_ROTATE_END_OFFSET) * SHOOT_LINE_STRETCH_DURATION).toLong()
            }
        }
    }

    companion object {
        private val DEFAULT_STROKE_COLOR = Color.parseColor("#ffc6c6c6")
        private val DEFAULT_GRADIENT_START_COLOR = Color.parseColor("#ffababab")
        private val DEFAULT_GRADIENT_END_COLOR = Color.parseColor("#0dababab")

        private const val DEGREE_60 = 60.toFloat()
        private const val DEGREE_360 = 360.toFloat()
        private const val START_ANGLE = -90

        private const val PRE_SHOOT_LINE_TOTAL_ROTATE_DURATION = 10000
        private const val SHOOT_LINE_ROTATE_DURATION = 5000
        private const val SHOOT_LINE_STRETCH_DURATION = 500
        private const val OUT_RING_ROTATE_DURATION = 1000

        private const val TOTAL_DURATION = PRE_SHOOT_LINE_TOTAL_ROTATE_DURATION +
                SHOOT_LINE_ROTATE_DURATION + SHOOT_LINE_STRETCH_DURATION

        private const val PRE_SHOOT_LINE_TOTAL_ROTATE_END_OFFSET =
            PRE_SHOOT_LINE_TOTAL_ROTATE_DURATION.toFloat() / TOTAL_DURATION.toFloat()
        private const val SHOOT_LINE_ROTATE_END_OFFSET =
            PRE_SHOOT_LINE_TOTAL_ROTATE_END_OFFSET + SHOOT_LINE_ROTATE_DURATION.toFloat() / TOTAL_DURATION.toFloat()
        private const val SHOOT_LINE_STRETCH_END_OFFSET = 1.0f

        private const val SHOOT_LINE_ROTATE_END_RADIANS = (Math.PI / 6.0).toFloat()
        private const val SHOOT_LINE_ROTATE_START_RADIANS = (Math.PI / 2.5).toFloat()
        private var SHOOT_LINE_ROTATE_START_DEGREE = Math.toDegrees(SHOOT_LINE_ROTATE_END_RADIANS.toDouble()).toFloat()
        private const val INTERVAL_RADIANS = (Math.PI / 3.0).toFloat()
        private val SQRT_3 = Math.sqrt(3.0).toFloat()

        val SHOOT_LINE_ROTATE_RADIANS: Property<ShootRefreshView, Float> =
            object : Property<ShootRefreshView, Float>(Float::class.java, null) {
                override fun get(`object`: ShootRefreshView): Float? {
                    return `object`.mShootLineRotateRadians
                }

                override fun set(`object`: ShootRefreshView, value: Float?) {
                    `object`.mShootLineRotateRadians = value!!
                    `object`.invalidate()
                }
            }

        val SHOOT_LINE_TOTAL_ROTATE_DEGREE: Property<ShootRefreshView, Float> =
            object : Property<ShootRefreshView, Float>(Float::class.java, null) {
                override fun get(`object`: ShootRefreshView): Float? {
                    return `object`.mShootLineTotalRotateAngle
                }

                override fun set(`object`: ShootRefreshView, value: Float?) {
                    `object`.mShootLineTotalRotateAngle = value!!
                    `object`.invalidate()
                }
            }
    }
}
