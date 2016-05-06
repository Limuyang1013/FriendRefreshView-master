package com.muyang.friendrefreshview;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;

/**
 * 仿微信朋友圈列表页下拉刷新控件
 * Created by 李牧羊 on 15/12/19.
 */
public class FriendRefreshView extends ViewGroup implements OnDetectScrollListener {
    /**
     * 圆形指示器
     */
    private ImageView mRainbowView;
    /**
     * 内容布局
     */
    private ListView mContentView;

    /**
     * 控件宽和高
     */
    private int sWidth;
    private int sHeight;

    /**
     * 处理拖动
     */
    private ViewDragHelper mDragHelper;
    /**
     * contentView的当前top属性
     */
    private int currentTop;
    /**
     * listView首个item
     */
    private int firstItem;
    /**
     * 是否正在向下滑动
     */
    private boolean bScrollDown = false;

    /**
     * 是否在拖动控件
     */
    private boolean bDraging = false;

    /**
     * 圆形加载指示器最大top 80
     */
    private int rainbowMaxTop = 80;
    /**
     * 圆形加载指示器刷新时的top 80
     */
    private int rainbowStickyTop = 80;
    /**
     * 圆形加载指示器初始top -120
     */
    private int rainbowStartTop =-120;
    /**
     * 圆形加载指示器的半径
     */
    private int rainbowRadius = 100;
    /**
     * -120
     */
    private int rainbowTop = -120;
    /**
     * 圆形加载指示器旋转的角度
     */
    private int rainbowRotateAngle = 0;

    private boolean bViewHelperSettling = false;
    /**
     * 刷新接口listener
     */
    private OnRefreshListener mRefreshLisenter;

    private AbsListView.OnScrollListener onScrollListener;
    private OnDetectScrollListener onDetectScrollListener;

    /**
     * 把正常情况、正在刷新、正在拖动集合到一个枚举类里面
     */
    public enum State {
        NORMAL,
        REFRESHING,
        DRAGING
    }

    /**
     * 控件当前状态
     */
    private State mState = State.NORMAL;

    public FriendRefreshView(Context context) {
        this(context, null);
    }

    public FriendRefreshView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FriendRefreshView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initHandler();
        initDragHelper();
        initListView();
        initRainbowView();
        setBackgroundColor(Color.parseColor("#000000"));
        onDetectScrollListener = this;
    }

    /**
     * 初始化handler，当ViewDragHelper释放了mContentView时，
     * 我们通过循环发送消息刷新mRainbowView的位置和角度
     */
    private void initHandler() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 0:
                        if (rainbowTop > rainbowStartTop) {
                            rainbowTop -= 10;
                            /**
                             * 当view确定自身已经不再适合现有的区域时，该view本身调用这个方法要求parent
                             * view重新调用他的onMeasure onLayout来对重新设置自己位置
                             * 需要在UI线程调用
                             */
                            requestLayout();
                            mHandler.sendEmptyMessageDelayed(0, 15);
                        }
                        break;
                    case 1:
                        if (rainbowTop <= rainbowStickyTop) {
                            if (rainbowTop < rainbowStickyTop) {
                                rainbowTop += 10;
                                if (rainbowTop > rainbowStickyTop) {
                                    rainbowTop = rainbowStickyTop;
                                }
                            }
                            /**
                             * 绕Z轴旋转，参数为正则顺时针
                             */
                            mRainbowView.setRotation(rainbowRotateAngle -= 20);
                        } else {
                            mRainbowView.setRotation(rainbowRotateAngle += 20);
                        }

                        requestLayout();

                        mHandler.sendEmptyMessageDelayed(1, 15);
                        break;
                }
            }
        };
    }

    /**
     * 初始化mDragHelper，我们处理拖动的核心类
     */
    private void initDragHelper() {
        mDragHelper = ViewDragHelper.create(this, new ViewDragHelper.Callback() {
            /**
             * view，这里一定要返回true， 返回true表示允许
             * tryCaptureView()里会传递当前触摸区域下的子View实例作为参数，如果需要对当前触摸的子View进行拖拽移动就返回true，
             * 否则返回false。
             */
            @Override
            public boolean tryCaptureView(View view, int i) {
                return view == mContentView && !bViewHelperSettling;
            }
            /**
             * @return 返回view的left值
             */
            @Override
            public int clampViewPositionHorizontal(View child, int left, int dx) {
                return 0;
            }
            /**
             * 决定了要拖拽的子View在垂直方向上应该移动到的位置，该方法会传递三个参数：
             * @param child 要拖拽的子View实例
             * @param top 期望的移动后位置子View的top值
             * @param dy 移动的距离
             * @return 返回值为子View在最终位置时的top值，一般直接返回第二个参数即可
             */
            @Override
            public int clampViewPositionVertical(View child, int top, int dy) {
                return top;
            }

            /**
             *
             * @param changedView
             * @param left
             * @param top 移动后的top值
             * @param dx
             * @param dy
             */
            @Override
            public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
                super.onViewPositionChanged(changedView, left, top, dx, dy);
                if (changedView == mContentView) {
                    int lastContentTop = currentTop;
                    if (top >= 0) {
                        currentTop = top;
                    } else {
                        top = 0;
                    }
                    int lastTop = rainbowTop;
                    int rTop = top + rainbowStartTop;
                    if (rTop >= rainbowMaxTop) {
                        if (!isRefreshing()) {
                            rainbowRotateAngle += (currentTop - lastContentTop) * 2;
                            rTop = rainbowMaxTop;
                            rainbowTop = rTop;
                            mRainbowView.setRotation(rainbowRotateAngle);
                        } else {
                            rTop = rainbowMaxTop;
                            rainbowTop = rTop;
                        }

                    } else {
                        if (isRefreshing()) {
                            rainbowTop = rainbowStickyTop;
                        } else {
                            rainbowTop = rTop;
                            rainbowRotateAngle += (rainbowTop - lastTop) * 3;
                            mRainbowView.setRotation(rainbowRotateAngle);
                        }
                    }

                    requestLayout();

                }
            }

            @Override
            public void onViewReleased(View releasedChild, float xvel, float yvel) {
                super.onViewReleased(releasedChild, xvel, yvel);
                mDragHelper.settleCapturedViewAt(0, 0);
                ViewCompat.postInvalidateOnAnimation(FriendRefreshView.this);
                //如果手势释放时，拖动的距离大于rainbowStickyTop，开始刷新
                if (currentTop >= rainbowStickyTop) {
                    startRefresh();
                }

            }
        });


    }

    /**
     * 实现松手之后的平缓停止滑动效果
     */
    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mDragHelper.continueSettling(true)) {
            //方法用来实现动画效果,重新绘制
            ViewCompat.postInvalidateOnAnimation(this);
            bViewHelperSettling = true;
        } else {
            bViewHelperSettling = false;
        }
    }

    /**
     * 我们invoke方法shouldIntercept来判断是否需要拦截事件，
     * 拦截事件是为了将事件传递给mDragHelper来处理，我们这里只有当mContentView滑动到顶部
     * 且mContentView没有处于滑动状态时才触发拦截。
     * @param ev
     * @return
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mDragHelper.shouldInterceptTouchEvent(ev);
        return shouldIntercept();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //处理拦截到的事件，这个方法会在返回前分发事件；return true 表示消费了事件
        mDragHelper.processTouchEvent(event);
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_UP:
                mLastMotionY = 0;
                bDraging = false;
                bScrollDown = false;
                rainbowRotateAngle = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                int index = MotionEventCompat.getActionIndex(event);
                int pointerId = MotionEventCompat.getPointerId(event, index);
                if (shouldIntercept()) {
                    mDragHelper.captureChildView(mContentView, pointerId);
                }
                break;
        }
        return true;
    }

    /**
     * 判断是否需要拦截触摸事件
     *
     * @return
     */
    private boolean shouldIntercept() {
        if (bDraging) return true;
        //getChildCount返回的是当前可见的 Item 个数
        int childCount = mContentView.getChildCount();
        if (childCount > 0) {
            View firstChild = mContentView.getChildAt(0);
            if (firstChild.getTop() >= 0
                    && firstItem == 0 && currentTop == 0
                    && bScrollDown) {
                return true;
            } else return false;
        } else {
            return true;
        }
    }

    /**
     * 判断mContentView是否处于顶部
     *
     * @return
     */
    private boolean checkIsTop() {
        int childCount = mContentView.getChildCount();
        if (childCount > 0) {
            View firstChild = mContentView.getChildAt(0);
            if (firstChild.getTop() >= 0
                    && firstItem == 0 && currentTop == 0) {
                return true;
            } else return false;
        } else {
            return false;
        }
    }

    private void initRainbowView() {
        mRainbowView = new ImageView(getContext());
        mRainbowView.setImageResource(R.drawable.rainbow_ic);
        addView(mRainbowView);
    }

    /**
     * 初始化listView，我们创建了istView for you，所有你要做的
     * 就是调用setAdapter，绑定你自定义的adapter
     */
    private void initListView() {
        mContentView = new FriendRefreshListView(getContext());
        View mHeadViw = LayoutInflater.from(getContext()).inflate(R.layout.list_head_layout, null);
        mContentView.addHeaderView(mHeadViw);

        this.addView(mContentView);
        mContentView.setOnScrollListener(new AbsListView.OnScrollListener() {

            private int oldTop;
            private int oldFirstVisibleItem;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (onScrollListener != null) {
                    onScrollListener.onScrollStateChanged(view, scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                firstItem = firstVisibleItem;
                if (onScrollListener != null) {
                    onScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                }

                if (onDetectScrollListener != null) {
                    onDetectedListScroll(view, firstVisibleItem);
                }
            }

            private void onDetectedListScroll(AbsListView absListView, int firstVisibleItem) {
                View view = absListView.getChildAt(0);
                int top = (view == null) ? 0 : view.getTop();

                if (firstVisibleItem == oldFirstVisibleItem) {
                    if (top > oldTop) {
                        onDetectScrollListener.onUpScrolling();
                    } else if (top < oldTop) {
                        onDetectScrollListener.onDownScrolling();
                    }
                } else {
                    if (firstVisibleItem < oldFirstVisibleItem) {
                        onDetectScrollListener.onUpScrolling();
                    } else {
                        onDetectScrollListener.onDownScrolling();
                    }
                }

                oldTop = top;
                oldFirstVisibleItem = firstVisibleItem;
            }

        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        sWidth = MeasureSpec.getSize(widthMeasureSpec);
        sHeight = MeasureSpec.getSize(heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        LayoutParams contentParams = (LayoutParams) mContentView.getLayoutParams();
        contentParams.left = 0;
        contentParams.top = 0;
    }


    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LayoutParams contentParams = (LayoutParams) mContentView.getLayoutParams();
        mContentView.layout(contentParams.left, currentTop,
                contentParams.left + sWidth, currentTop + sHeight);

        mRainbowView.layout(rainbowRadius, rainbowTop,
                rainbowRadius * 2, rainbowTop + rainbowRadius);
    }

    @Override
    public void onUpScrolling() {
        bScrollDown = false;
    }

    @Override
    public void onDownScrolling() {
        bScrollDown = true;
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {

        public int left = 0;
        public int top = 0;

        public LayoutParams(Context arg0, AttributeSet arg1) {
            super(arg0, arg1);
        }

        public LayoutParams(int arg0, int arg1) {
            super(arg0, arg1);
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams arg0) {
            super(arg0);
        }

    }

    @Override
    public android.view.ViewGroup.LayoutParams generateLayoutParams(
            AttributeSet attrs) {
        return new FriendRefreshView.LayoutParams(getContext(), attrs);
    }

    @Override
    protected android.view.ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected android.view.ViewGroup.LayoutParams generateLayoutParams(
            android.view.ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return p instanceof FriendRefreshView.LayoutParams;
    }

    private float mLastMotionX;
    private float mLastMotionY;

    /**
     * 对ListView的触摸事件进行判断，是否处于滑动状态
     */
    private class FriendRefreshListView extends ListView {


        public FriendRefreshListView(Context context) {
            this(context, null);
        }

        public FriendRefreshListView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public FriendRefreshListView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            setBackgroundColor(Color.parseColor("#ffffff"));
        }

        /*当前活动的点Id,有效的点的Id*/
        protected int mActivePointerId = INVALID_POINTER;

        /*无效的点*/
        private static final int INVALID_POINTER = -1;

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            final int action = ev.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    int index = MotionEventCompat.getActionIndex(ev);
                    mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                    if (mActivePointerId == INVALID_POINTER)
                        break;
                    mLastMotionX = ev.getX();
                    mLastMotionY = ev.getY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    int indexMove = MotionEventCompat.getActionIndex(ev);
                    mActivePointerId = MotionEventCompat.getPointerId(ev, indexMove);

                    if (mActivePointerId == INVALID_POINTER) {

                    } else {
                        final float y = ev.getY();
                        float dy = y - mLastMotionY;
                        if (checkIsTop() && dy >= 1.0f) {
                            bScrollDown = true;
                            bDraging = true;
                        } else {
                            bScrollDown = false;
                            bDraging = false;
                        }
                        mLastMotionX = y;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    mLastMotionY = 0;
                    break;
            }
            return super.onTouchEvent(ev);
        }
    }

    public void setAdapter(BaseAdapter adapter) {
        if (mContentView != null) {
            mContentView.setAdapter(adapter);
        }
    }

    public boolean isRefreshing() {
        return mState == State.REFRESHING;
    }


    Handler mHandler;

    public void startRefresh() {
        if (!isRefreshing()) {
            mHandler.removeMessages(0);
            mHandler.removeMessages(1);
            mHandler.sendEmptyMessage(1);
            mState = State.REFRESHING;
            invokeListner();
        }

    }

    private void invokeListner() {
        if (mRefreshLisenter != null) {
            mRefreshLisenter.onRefresh();
        }
    }

    public void stopRefresh() {
        mHandler.removeMessages(1);
        mHandler.sendEmptyMessage(0);
        mState = State.NORMAL;
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.mRefreshLisenter = listener;
    }

    public interface OnRefreshListener {
        public void onRefresh();
    }
}
