/**
 * Project Name:cwFaceForDev3
 * File Name:LiveActivity.java
 * Package Name:cn.cloudwalk.dev.mobilebank
 * Date:2016-5-16上午9:17:24
 * Copyright @ 2010-2016 Cloudwalk Information Technology Co.Ltd All Rights Reserved.
 */

package cn.cloudwalk.libproject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.cloudwalk.CloudwalkSDK;
import cn.cloudwalk.FaceInterface;
import cn.cloudwalk.FaceInterface.CW_LivenessCode;
import cn.cloudwalk.FaceInterface.LivessType;
import cn.cloudwalk.callback.FaceInfoCallback;
import cn.cloudwalk.callback.LivessCallBack;
import cn.cloudwalk.jni.FaceInfo;
import cn.cloudwalk.libproject.camera.CameraPreview;
import cn.cloudwalk.libproject.progressHUD.CwProgressHUD;
import cn.cloudwalk.libproject.util.CameraUtil;
import cn.cloudwalk.libproject.util.DisplayUtil;
import cn.cloudwalk.libproject.util.LogUtils;
import cn.cloudwalk.libproject.util.NullUtils;
import cn.cloudwalk.libproject.util.UIUtils;
import cn.cloudwalk.libproject.util.Util;
import cn.cloudwalk.libproject.view.CustomViewPager;
import cn.cloudwalk.libproject.view.RoundProgressBarWidthNumber;
import cn.fisc.libproject.facerecognition.FaceRecognition;

/**
 * ClassName: LiveActivity <br/>
 * Description: <br/>
 * date: 2016-5-16 上午9:17:24 <br/>
 *
 * @author 284891377
 * @since JDK 1.7
 */
public class LiveActivity extends TemplatedActivity implements LivessCallBack, FaceInfoCallback {
    private final String TAG = LogUtils.makeLogTag("LiveActivity");

    final static int NEXT_STEP = 101, UPDATE_STEP_PROCRESS = 106, SET_RESULT = 122,
            UPDATESTEPLAYOUT = 124,
            PLAYMAIN_END = 125,
            DETECT_READY = 126;
    boolean isStop;// 界面遮盖
    boolean isLivePass;// 活体是否通过
    boolean isSetResult = false;// 跳转页面
    volatile boolean isStartDetectFace;// 开始检测

    int orientation;
    // 活体声音资源初始化
    public SoundPool sndPool;
    public Map<String, Integer> poolMap;
    int currentStreamID;
    boolean isLoadmain;
    boolean isPlayMain = true;

    CameraPreview mPreview;
    int caremaId;

    ImageView mIv_top;
    RelativeLayout mRl_bottom;

    CustomViewPager mViewPager;
    ViewPagerAdapter viewPagerAdapter;

    RoundProgressBarWidthNumber mPb_step;
    ImageView mIv_step;
    TextView mTv_step;
    TimerRunnable faceTimerRunnable;

    private AnimationDrawable animationDrawable;

    // 认证步骤
    int totalStep;
    int currentStep;
    ArrayList<View> viewList;

    MainHandler mMainHandler;


    public CloudwalkSDK cloudwalkSDK;
    public int initRet;

    public static List<Integer> execLiveList;

    public CwProgressHUD processDialog;// 进度框
    // 使用广播来传递数据
    LocalBroadcastManager localBroadcastManager;
    LiveBroadcastReceiver liveBroadcastReceiver;
    LiveServerBroadcastReceiver liveServerBroadcastReceiver;
    public static final String mobileFoxC86 = "C68";

    public class LiveBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context arg0, Intent intent) {
            int faceCompareType = intent.getIntExtra("isFaceComparePass", Bulider.FACE_VERFY_FAIL);
            String faceCompareSessionId = intent.getStringExtra("faceSessionId");
            String faceCompareTipMsg = intent.getStringExtra("faceCompareTipMsg");
            double faceScore = intent.getDoubleExtra("faceCompareScore", 0d);

            setFaceResult(faceCompareType, faceScore, faceCompareSessionId, faceCompareTipMsg);
        }
    }

    public class LiveServerBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            int extInfo = intent.getIntExtra("extInfo", Bulider.FACE_LIVE_FAIL);
            int result = intent.getIntExtra("result", Bulider.FACE_LIVE_FAIL);

            setFaceLiveResult(extInfo,result);
        }
    }

    private void setFaceLiveResult(int extInfo, int result) {
        boolean isLivePass = false;
        if (result == Bulider.FACE_LIVE_PASS && extInfo == 1) {
            isLivePass = true;
        } else if (result == Bulider.FACE_LIVE_FAIL || extInfo != 1) {
            isLivePass = false;
        }
        if (processDialog != null && processDialog.isShowing()) {
            processDialog.dismiss();
        }

        mMainHandler.removeCallbacksAndMessages(null);
        if (isSetResult || isStop)
            return;
        isSetResult = true;
        Bundle bundle = getIntent().getExtras();
        Log.e("bundle",bundle.getInt("getMemberId")+"");
        Log.e("bundle",bundle.getString("getProjId")+"");
        Log.e("bundle",bundle.getString("getImgUrl")+"");
        Intent mIntent = new Intent(LiveActivity.this, LiveServerActivity.class);
        mIntent.putExtras(bundle);
        mIntent.putExtra(LiveServerActivity.FACEDECT_RESULT_ISSUCCESS, isLivePass);
        mIntent.putExtra(LiveResultActivity.CAMERAID,mPreview.getCaremaId());//摄像头id
        startActivity(mIntent);
        finish();

    }

    private void initcloudwalkSDK() {
        cloudwalkSDK = FaceRecognition.getInstance().initSDK(this);
        initRet = FaceRecognition.getInstance().getRet();
    }

    @Override
    protected boolean hasActionBar() {
        orientation = this.getResources().getConfiguration().orientation;
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cloudwalk_activity_facedect);
        setTitle(R.string.cloudwalk_live_title);
        setRightBtnIcon(R.drawable.btn_switch);
        mMainHandler = new MainHandler(this);
        initSoundPool(this);

        initView();
        initStepViews();
        // FaceRecognize单例实例化
        initcloudwalkSDK();
        initCallBack();

        if (Bulider.isServerLive) {
            processDialog = CwProgressHUD.create(this).setStyle(CwProgressHUD.Style
                    .SPIN_INDETERMINATE)
                    .setLabel(getString(R.string.cloudwalk_faceserver_live)).setCancellable(true)
                    .setAnimationSpeed(2)
                    .setDimAmount(0.5f);
        } else {
            processDialog = CwProgressHUD.create(this).setStyle(CwProgressHUD.Style
                    .SPIN_INDETERMINATE)
                    .setLabel(getString(R.string.cloudwalk_faceverifying)).setCancellable(true)
                    .setAnimationSpeed(2)
                    .setDimAmount(0.5f);
        }

    }

    private void playMain() {
        if (isPlayMain && isLoadmain) {
            isPlayMain = false;
            currentStreamID = 1;// 第一个加载的语音为1
            sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
            mMainHandler.sendEmptyMessageDelayed(PLAYMAIN_END, 3000);

        }

    }

    public void initSoundPool(Context ctx) {

        poolMap = new HashMap<String, Integer>();
        sndPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 100);
        sndPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {

            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (1 == sampleId) {
                    isLoadmain = true;
                    playMain();
                }

            }

        });

        poolMap.put("main", sndPool.load(ctx, R.raw.cloudwalk_main, 1));//
        poolMap.put("mouth_open", sndPool.load(ctx, R.raw.cloudwalk_live_mouth, 1));//
        poolMap.put("head_up", sndPool.load(ctx, R.raw.cloudwalk_live_top, 1));//
        poolMap.put("head_down", sndPool.load(ctx, R.raw.cloudwalk_live_down, 1));//
        poolMap.put("head_left", sndPool.load(ctx, R.raw.cloudwalk_live_left, 1));//
        poolMap.put("head_right", sndPool.load(ctx, R.raw.cloudwalk_live_right, 1));//
        poolMap.put("eye_blink", sndPool.load(ctx, R.raw.cloudwalk_live_eye, 1));//
        poolMap.put("good", sndPool.load(ctx, R.raw.cloudwalk_good, 1));//

    }

    public void releaseSoundPool() {
        if (sndPool != null) {
            sndPool.release();
            sndPool = null;
        }

    }

    private void initCallBack() {
        cloudwalkSDK.cwFaceInfoCallback(this);
        cloudwalkSDK.cwLivessInfoCallback(this);
    }

    private void initView() {

        // 屏幕分辨率
        DisplayMetrics dm = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height;

        // ViewPager
        mViewPager = (CustomViewPager) findViewById(R.id.viewpager);
        // 根据预览分辨率设置Preview尺寸
        mPreview = (CameraPreview) findViewById(R.id.preview);
        mPreview.setScreenOrientation(orientation);
        if (CameraUtil.isHasCamera(Camera.CameraInfo.CAMERA_FACING_FRONT)) {
            caremaId = Camera.CameraInfo.CAMERA_FACING_FRONT;
            mPreview.setCaremaId(caremaId);
        } else {
            caremaId = Camera.CameraInfo.CAMERA_FACING_BACK;
            mPreview.setCaremaId(caremaId);
        }
        mIv_top = (ImageView) findViewById(R.id.top_iv);

        mRl_bottom = (RelativeLayout) findViewById(R.id.bottom_rl);
        mPb_step = (RoundProgressBarWidthNumber) findViewById(R.id.cloudwalk_face_step_procress);
        // 屏幕方向
        int previewW = 0, previewH = 0, flTopW = 0, flTopH = 0, bottomW = 0, bottomH = 0;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {

            height = dm.heightPixels - DisplayUtil.dip2px(this, 45) - Util.getStatusBarHeight(this);
            previewW = width;
            previewH = width * Contants.PREVIEW_W / Contants.PREVIEW_H;
            flTopW = width;
            flTopH = (int) (width * 1.0 / 2047 * 1008);
            bottomW = width;
            if (height - flTopH < DisplayUtil.dip2px(this, 185)) {
                bottomH = DisplayUtil.dip2px(this, 185);
            } else {
                bottomH = height - flTopH;
            }

            // 调整布局大小
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(previewW,
					previewH);
            mPreview.setLayoutParams(params);

            params = new RelativeLayout.LayoutParams(flTopW, flTopH);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            mIv_top.setLayoutParams(params);
            mIv_top.setImageResource(R.drawable.cloudwalk_face_main_camera_mask_hor);

            params = new RelativeLayout.LayoutParams(bottomW, bottomH);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            mRl_bottom.setLayoutParams(params);

        } else if (this.getResources().getConfiguration().orientation == Configuration
				.ORIENTATION_PORTRAIT) {
            //屏幕高度-自定义titlebar高度-状态栏高度-NavigationBar高度
            int navigationBarnH = 0;
            if (UIUtils.checkDeviceHasNavigationBar(this)) {
                navigationBarnH = UIUtils.getNavigationBarHeight(this);
            }
            //对屏幕分辨率240x320进行页面适配
            if (dm.widthPixels == 240 && dm.heightPixels == 320){
                height = dm.heightPixels - DisplayUtil.dip2px(this, 95) - Util.getStatusBarHeight
                        (this) - navigationBarnH;
                previewW = width;
                previewH = (int) (width * 1.0 * Contants.PREVIEW_W / Contants.PREVIEW_H);
                flTopW = width;
                flTopH = width;
                bottomW = width;
                if (height - width < DisplayUtil.dip2px(this, 105)) {
                    bottomH = DisplayUtil.dip2px(this, 105);
                } else {
                    bottomH = height - width;
                }
            }else {
                height = dm.heightPixels - DisplayUtil.dip2px(this, 45) - Util.getStatusBarHeight
                        (this) - navigationBarnH;
                previewW = width;
                previewH = (int) (width * 1.0 * Contants.PREVIEW_W / Contants.PREVIEW_H);
                flTopW = width;
                flTopH = width;
                bottomW = width;
                if (height - width < DisplayUtil.dip2px(this, 185)) {
                    bottomH = DisplayUtil.dip2px(this, 185);
                } else {
                    bottomH = height - width;
                }
            }

            // 调整布局大小
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(previewW,
					previewH);
            mPreview.setLayoutParams(params);

            params = new RelativeLayout.LayoutParams(flTopW, flTopH);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            mIv_top.setLayoutParams(params);
            mIv_top.setImageResource(R.drawable.cloudwalk_face_main_camera_mask);

            params = new RelativeLayout.LayoutParams(bottomW, bottomH);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            mRl_bottom.setLayoutParams(params);
        }

    }

    private void initStepViews() {

        getExecLive();

        // viewList
        LayoutInflater lf = getLayoutInflater().from(this);
        viewList = new ArrayList<View>();
        View view;
        // 检测人脸item
        view = lf.inflate(R.layout.cloudwalk_layout_facedect_step_start, null);//
        addView(view);
        // 活体item
        int size = execLiveList.size();
        for (int i = 0; i < size; i++) {
            view = lf.inflate(R.layout.cloudwalk_layout_facedect_step, null);//
            addView(view);
        }

        viewPagerAdapter = new ViewPagerAdapter(viewList);
        mViewPager.setAdapter(viewPagerAdapter);

    }

    /**
     * 生成待执行的活体动作
     * 如所有动作中含眨眼,张嘴,左右转则生成的待执行活体动作中加入眨眼、眨眼中取一个，左右转取一个
     * 其余动作从其他动作中补充 ,以便活体测试
     */
    private void getExecLive() {
       /* if (1<Bulider.execLiveCount && Bulider.execLiveCount <= 4) {
            boolean isTotalHasLR = Bulider.totalLiveList.contains(LivessType.LIVESS_HEAD_LEFT)
                    || Bulider.totalLiveList.contains(LivessType.LIVESS_HEAD_RIGHT);
            boolean isTotalHasME = Bulider.totalLiveList.contains(LivessType.LIVESS_MOUTH)
                    || Bulider.totalLiveList.contains(LivessType.LIVESS_EYE);
            // 活体动作第一个 左或右 第二个张嘴或者眨眼
            if (isTotalHasLR && isTotalHasME) {
                boolean isSubHasLRME = false;
                while (!isSubHasLRME) {
                    Collections.shuffle(Bulider.totalLiveList);
                    execLiveList = Bulider.totalLiveList.subList(0, Bulider.execLiveCount);
                    isSubHasLRME = (execLiveList.contains(LivessType.LIVESS_HEAD_LEFT)
                            || execLiveList.contains(LivessType.LIVESS_HEAD_RIGHT))
                            && (execLiveList.contains(LivessType.LIVESS_MOUTH)
                            || execLiveList.contains(LivessType.LIVESS_EYE));
                }
            } else if (isTotalHasLR) {// isTotalHasLR
                boolean isSubHasLR = false;
                while (!isSubHasLR) {
                    Collections.shuffle(Bulider.totalLiveList);
                    execLiveList = Bulider.totalLiveList.subList(0, Bulider.execLiveCount);
                    isSubHasLR = (execLiveList.contains(LivessType.LIVESS_HEAD_LEFT)
                            || execLiveList.contains(LivessType.LIVESS_HEAD_RIGHT));
                }
            } else if (isTotalHasME) {// isTotalHasME
                boolean isSubHasME = false;
                while (!isSubHasME) {
                    Collections.shuffle(Bulider.totalLiveList);
                    execLiveList = Bulider.totalLiveList.subList(0, Bulider.execLiveCount);
                    isSubHasME = (execLiveList.contains(LivessType.LIVESS_MOUTH)
                            || execLiveList.contains(LivessType.LIVESS_EYE));
                }
            } else {
                Collections.shuffle(Bulider.totalLiveList);
                execLiveList = totalLiveList.subList(0, Bulider.execLiveCount);
            }
        } else {*/
            Collections.shuffle(Bulider.totalLiveList);
            execLiveList = Bulider.totalLiveList.subList(0, Bulider.execLiveCount);
        //}
    }

    private void addView(View view) {
        viewList.add(view);
        totalStep++;
    }

    /**
     * 获取最佳人脸
     */
    private void getBestFace() {
        Bulider.bestFaceData = cloudwalkSDK.cwGetOriBestFace();
        Bulider.nextFaceData = cloudwalkSDK.cwGetNextFace();
        Bulider.bestInfo = cloudwalkSDK.cwGetBestInfo();
        Bulider.nextInfo = cloudwalkSDK.cwGetNextInfo();
    }

    /**
     * 清除最佳人脸
     */
    private void clearBestFace() {
        cloudwalkSDK.cwClearBestFace();

    }

    private void doFaceSerLivess() {
        isLivePass = true;

        if (Bulider.livessCallBack == null) {
            mMainHandler.obtainMessage(SET_RESULT, CW_LivenessCode.CW_FACE_LIVENESS_FACEDEC_OK)
                    .sendToTarget();
        } else {
            localBroadcastManager = LocalBroadcastManager.getInstance(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Contants.ACTION_BROADCAST_SERVER_LIVE);
            liveServerBroadcastReceiver = new LiveServerBroadcastReceiver();
            localBroadcastManager.registerReceiver(liveServerBroadcastReceiver, filter);
            processDialog.show();
            Bulider.livessCallBack.OnLivessSerResult(Bulider.bestFaceData, Bulider.bestInfo,
                    Bulider.nextFaceData, Bulider.nextInfo, true);
        }
    }

    private void doFaceVerify() {
        isLivePass = true;

        if (Bulider.dfvCallBack == null) {

            mMainHandler.obtainMessage(SET_RESULT, CW_LivenessCode.CW_FACE_LIVENESS_FACEDEC_OK)
					.sendToTarget();

            return;
        } else {
            localBroadcastManager = LocalBroadcastManager.getInstance(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Contants.ACTION_BROADCAST_LIVE);
            liveBroadcastReceiver = new LiveBroadcastReceiver();
            localBroadcastManager.registerReceiver(liveBroadcastReceiver, filter);
            processDialog.show();
            Bulider.dfvCallBack.OnDefineFaceVerifyResult(Bulider.bestFaceData);
        }

    }

    /**
     * 返回比对结果
     *
     * @param faceCompareType 人脸比对是否通过
     * @param faceScore       比对分数
     * @param sessionId       sessionId
     * @param tipMsg          自定义提示信息
     */
    public void setFaceResult(int faceCompareType, double faceScore, String sessionId, String
			tipMsg) {
        int resultType;
        boolean isVerfyPass = false;
        if (Bulider.FACE_VERFY_PASS == faceCompareType) {
            resultType = Bulider.FACE_VERFY_PASS;
            isVerfyPass = true;
        } else if (Bulider.FACE_VERFY_FAIL == faceCompareType) {
            resultType = Bulider.FACE_VERFY_FAIL;
        } else {
            resultType = Bulider.FACE_VERFY_NETFAIL;
        }
        if (processDialog != null && processDialog.isShowing()) {
            processDialog.dismiss();
        }

        setFaceResult(isVerfyPass, faceScore, sessionId, resultType, tipMsg);

    }

    private void setFaceResult(boolean isVerfyPass, double faceScore, String sessionId, int
			resultType, String tipMsg) {
        mMainHandler.removeCallbacksAndMessages(null);
        if (isSetResult || isStop)
            return;
        isSetResult = true;

        if (Bulider.livessCallBack != null)
            Bulider.livessCallBack.OnLivessSerResult(Bulider.bestFaceData, Bulider.bestInfo,
                    Bulider.nextFaceData, Bulider.nextInfo,false);

        if (Bulider.isResultPage) {
            if (!isLivePass){
                try {
                    Bulider.bestFaceData = cloudwalkSDK.cwGetOriBestFace();
                }catch (Exception e){
                    Log.e("app",e.getLocalizedMessage());
                }
            }
            if (resultType == Bulider.FACE_VERFY_PASS || resultType == CW_LivenessCode.CW_FACE_LIVENESS_FACEDEC_OK) {
                if(Bulider.mResultCallBack!=null)Bulider.mResultCallBack.result(isLivePass, isVerfyPass, sessionId, faceScore, resultType,
                        Bulider.bestFaceData, Bulider.liveDatas,mPreview.getCaremaId());
                finish();
            }else{
                Bundle bundle = getIntent().getExtras();
                Log.e("bundle",bundle.getInt("getMemberId")+"");
                Log.e("bundle",bundle.getString("getProjId")+"");
                Log.e("bundle",bundle.getString("getImgUrl")+"");
                Intent mIntent = new Intent(this, LiveResultActivity.class);
                mIntent.putExtras(bundle);
                mIntent.putExtra(LiveResultActivity.FACEDECT_RESULT_TYPE, resultType);// 结果类型

                if (NullUtils.isNotEmpty(tipMsg))
                    mIntent.putExtra(LiveResultActivity.FACEDECT_RESULT_MSG, tipMsg);// 自定义提示语句
                mIntent.putExtra(LiveResultActivity.ISLIVEPASS, isLivePass);// 活体是否通过
                mIntent.putExtra(LiveResultActivity.ISVERFYPASS, isVerfyPass);// 比对是否通过
                mIntent.putExtra(LiveResultActivity.FACESCORE, faceScore);// 比对分数
                mIntent.putExtra(LiveResultActivity.SESSIONID, sessionId);// 比对日志id
                mIntent.putExtra(LiveResultActivity.CAMERAID,mPreview.getCaremaId());//摄像头id
                startActivity(mIntent);
                finish();
            }

        } else {

            if (Bulider.mResultCallBack != null)
                Bulider.mResultCallBack.result(isLivePass, isVerfyPass, sessionId, faceScore,
						resultType,
                        Bulider.bestFaceData, Bulider.liveDatas,mPreview.getCaremaId());
            finish();
        }

    }

    private void doNextStep() {

        int nextDelayTime = 10;
        if (currentStep == 1) {// 进入第一个动作
            nextDelayTime = 500; //delay时间稍微长一点,播放提示音
            mMainHandler.sendEmptyMessageDelayed(UPDATESTEPLAYOUT, nextDelayTime / 2);
        } else if (totalStep == currentStep) {// 活体最后一步
            isLivePass = true;
            currentStreamID = poolMap.get("good");
            if (sndPool != null) {
                sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
            }
            nextDelayTime = 500;
        } else {
            currentStreamID = poolMap.get("good");
            if (sndPool != null) {
                sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
            }
            nextDelayTime = 1000;
            // 在播放下一步时间中间切换页面
            mMainHandler.sendEmptyMessageDelayed(UPDATESTEPLAYOUT, nextDelayTime / 2);
        }

        mMainHandler.sendEmptyMessageDelayed(NEXT_STEP, nextDelayTime);
    }

    void resetLive() {
        // 重置页面状态
        mMainHandler.removeCallbacksAndMessages(null);
        isPlayMain = true;// 控制欢迎语音播放
        Bulider.bestFaceData = null;// 重置最佳人脸
        if (Bulider.isLivesPicReturn)
            Bulider.liveDatas = new HashMap<Integer, byte[]>();// 重置活体证据图片
        isLivePass = false;// 活体是否通过标志位
        playMain();// 重新播放欢迎语音
        isSetResult = false;
        // 重置开始检测人脸
        currentStep = 0;
        mViewPager.setCurrentItem(currentStep);
        mPb_step.setVisibility(View.GONE);
        isStartDetectFace = false;// 是否检测到人脸标志位
        if (initRet == 0) {// 开始人脸检测
            //doNextStep();
            cloudwalkSDK.setWorkType(CloudwalkSDK.DetectType.LIVE_DETECT);//设置检测模式为活体检测
            cloudwalkSDK.setTotalStep(execLiveList.size());//设置活体动作个数
            cloudwalkSDK.setStageflag(FaceInterface.LivessFlag.LIVE_PREPARE);//进入准备阶段
        } else {

            mMainHandler.obtainMessage(SET_RESULT, CW_LivenessCode.CW_FACE_LIVENESS_AUTH_ERROR)
					.sendToTarget();

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isStop = false;
        resetLive();

        mPreview.cwStartCamera();

    }

    @Override
    protected void onRestart() {
        super.onRestart();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mMainHandler.removeCallbacksAndMessages(null);
        cloudwalkSDK.cwDestory();
        releaseSoundPool();
        if (processDialog != null && processDialog.isShowing()) {
            processDialog.dismiss();
        }
        if (localBroadcastManager != null)
            if (liveBroadcastReceiver != null) {
                localBroadcastManager.unregisterReceiver(liveBroadcastReceiver);
            } else {
                localBroadcastManager.unregisterReceiver(liveServerBroadcastReceiver);
            }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPreview.cwStopCamera();
        stopTimerRunnable();
        isStop = true;
        mMainHandler.removeCallbacksAndMessages(null);
        sndPool.stop(currentStreamID);

        getExecLive();
    }

    /**
     * startLivessDetect:活体检测 <br/>
     *
     * @param livessType
     * @author:284891377 Date: 2016-5-20 上午10:18:55
     * @since JDK 1.7
     */
    private void startLivessDetect(final int livessType) {
        switch (livessType) {
            case LivessType.LIVESS_HEAD_LEFT:

                currentStreamID = poolMap.get("head_left");
                sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
                //待语音播报完成再进行动作,交互更友好,避免动作间影响
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startTimerRunnable(Bulider.timerCount);
                        cloudwalkSDK.cwStartLivess(livessType);
                    }
                }, 100);
                break;
            case LivessType.LIVESS_HEAD_RIGHT://

                currentStreamID = poolMap.get("head_right");
                sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
                //待语音播报完成再进行动作,交互更友好,避免动作间影响
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startTimerRunnable(Bulider.timerCount);
                        cloudwalkSDK.cwStartLivess(livessType);
                    }
                }, 100);
                break;
            case LivessType.LIVESS_HEAD_UP:

                currentStreamID = poolMap.get("head_up");
                sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
                //待语音播报完成再进行动作,交互更友好,避免动作间影响
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startTimerRunnable(Bulider.timerCount);
                        cloudwalkSDK.cwStartLivess(livessType);
                    }
                }, 100);
                break;
            case LivessType.LIVESS_HEAD_DOWN://

                currentStreamID = poolMap.get("head_down");
                sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
                //待语音播报完成再进行动作,交互更友好,避免动作间影响
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startTimerRunnable(Bulider.timerCount);
                        cloudwalkSDK.cwStartLivess(livessType);
                    }
                }, 100);
                break;
            case LivessType.LIVESS_MOUTH://
                // mIv_step.setImageResource(R.drawable.biyan);//

                currentStreamID = poolMap.get("mouth_open");
                sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
                //待语音播报完成再进行动作,交互更友好,避免动作间影响
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startTimerRunnable(Bulider.timerCount);
                        cloudwalkSDK.cwStartLivess(livessType);
                    }
                }, 100);
                break;
            case LivessType.LIVESS_EYE://

                currentStreamID = poolMap.get("eye_blink");
                sndPool.play(currentStreamID, 1.0f, 1.0f, 0, 0, 1.0f);
                //待语音播报完成再进行动作,交互更友好,避免动作间影响
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startTimerRunnable(Bulider.timerCount);
                        cloudwalkSDK.cwStartLivess(livessType);
                    }
                }, 100);

                break;

        }

    }


    private void updateStepLayout(int livessType) {

        View view = viewList.get(currentStep);

        mTv_step = (TextView) view.findViewById(R.id.cloudwalk_face_step_tv);
        mIv_step = (ImageView) view.findViewById(R.id.cloudwalk_face_step_img);
        mPb_step.setVisibility(View.VISIBLE);
        mPb_step.setMax(Bulider.timerCount);
        mPb_step.setProgress(Bulider.timerCount);

        switch (livessType) {
            case LivessType.LIVESS_HEAD_LEFT:
                if (caremaId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mIv_step.setImageResource(R.drawable.cloudwalk_left_anim);
                } else {
                    mIv_step.setImageResource(R.drawable.cloudwalk_right_anim);
                }

                mTv_step.setText(R.string.cloudwalk_live_headleft);
                animationDrawable = (AnimationDrawable) mIv_step.getDrawable();
                animationDrawable.start();

                break;
            case LivessType.LIVESS_HEAD_RIGHT:
                if (caremaId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mIv_step.setImageResource(R.drawable.cloudwalk_right_anim);
                } else {
                    mIv_step.setImageResource(R.drawable.cloudwalk_left_anim);
                }

                mTv_step.setText(R.string.cloudwalk_live_headright);
                animationDrawable = (AnimationDrawable) mIv_step.getDrawable();
                animationDrawable.start();

                break;
            case LivessType.LIVESS_HEAD_UP://

                mIv_step.setImageResource(R.drawable.cloudwalk_up_anim);
                mTv_step.setText(R.string.cloudwalk_live_headup);
                animationDrawable = (AnimationDrawable) mIv_step.getDrawable();
                animationDrawable.start();
                break;
            case LivessType.LIVESS_HEAD_DOWN://

                mIv_step.setImageResource(R.drawable.cloudwalk_down_anim);
                mTv_step.setText(R.string.cloudwalk_live_headdown);
                animationDrawable = (AnimationDrawable) mIv_step.getDrawable();
                animationDrawable.start();

                break;
            case LivessType.LIVESS_MOUTH://
                mIv_step.setImageResource(R.drawable.cloudwalk_mouth_anim);
                mTv_step.setText(R.string.cloudwalk_live_mouth);
                animationDrawable = (AnimationDrawable) mIv_step.getDrawable();
                animationDrawable.start();

                break;
            case LivessType.LIVESS_EYE://
                mIv_step.setImageResource(R.drawable.cloudwalk_eye_anim);
                mTv_step.setText(R.string.cloudwalk_live_eye);

                animationDrawable = (AnimationDrawable) mIv_step.getDrawable();
                animationDrawable.start();

                break;

        }

        mViewPager.setCurrentItem(currentStep, true);
    }

    private class ViewPagerAdapter extends PagerAdapter {
        private List<View> mListViews;

        public ViewPagerAdapter(List<View> mListViews) {
            this.mListViews = mListViews;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(mListViews.get(position));
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            container.addView(mListViews.get(position), 0);
            return mListViews.get(position);
        }

        @Override
        public int getCount() {
            return mListViews.size();
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == arg1;
        }
    }

    @Override
    public void detectFaceInfo(FaceInfo[] faceInfos, int faceNum) {
        if (faceNum > 0) {// 检测到脸, 画脸框
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {// 横屏

            } else {
                boolean isX = faceInfos[0].x > Contants.PREVIEW_H * 0.15;
                boolean isY = faceInfos[0].y > Contants.PREVIEW_H * 0.05;
                boolean isW = (faceInfos[0].x + faceInfos[0].width) < Contants.PREVIEW_H * 0.85;
                boolean isH = (faceInfos[0].y + faceInfos[0].height) < Contants.PREVIEW_W * 0.87;

            }

        } else {// 未检测到脸,清除脸框

        }

    }



    @Override
    public void detectLivess(int livessType, byte[] imageData) {
        cloudwalkSDK.cwStopLivess();
        stopTimerRunnable();
        if (Bulider.isLivesPicReturn) {
            Bulider.liveDatas.put(livessType, imageData);
        }
        if (isSetResult || isStop)
            return;
        switch (livessType) {

            case CW_LivenessCode.CW_FACE_LIVENESS_HEADLEFT:
                currentStep++;
                doNextStep();

                break;
            case CW_LivenessCode.CW_FACE_LIVENESS_HEADRIGHT:
                currentStep++;
                doNextStep();
                break;
            case CW_LivenessCode.CW_FACE_LIVENESS_BLINK:
                currentStep++;
                doNextStep();
                break;
            case CW_LivenessCode.CW_FACE_LIVENESS_OPENMOUTH:
                currentStep++;
                doNextStep();
                break;
            case CW_LivenessCode.CW_FACE_LIVENESS_HEADPITCH:
                currentStep++;
                doNextStep();
                break;
            case CW_LivenessCode.CW_FACE_LIVENESS_HEADDOWN:
                currentStep++;
                doNextStep();
                break;

        }

    }

    /**
     * 准备阶段提示信息
     * @param info
     */
    @Override
    public void detectInfo(final int info) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (viewList!=null && viewList.get(0)!=null) {
                    TextView tv_Info = (TextView)viewList.get(0).findViewById(R.id.cloudwalk_face_info_txt);
                    if (tv_Info!=null) {
                        switch (info){
                            case FaceInterface.CW_LivenessCode.CW_FACE_INFO_TOO_FAR://too far
                                tv_Info.setText(R.string.cloudwalk_tip_too_far);
                                break;
                            case FaceInterface.CW_LivenessCode.CW_FACE_INFO_TOO_CLOSE://too close
                                tv_Info.setText(R.string.cloudwalk_tip_too_close);
                                break;
                            case FaceInterface.CW_LivenessCode.CW_FACE_INFO_NOT_FRONTAL://not frontal
                                tv_Info.setText(R.string.cloudwalk_tip_not_frontal);
                                break;
                            case FaceInterface.CW_LivenessCode.CW_FACE_INFO_NOT_STABLE://not stable
                                tv_Info.setText(R.string.cloudwalk_tip_not_stable);
                                break;
                            case FaceInterface.CW_LivenessCode.CW_FACE_INFO_TOO_DARK://too dark
                                tv_Info.setText(R.string.cloudwalk_tip_too_dark);
//                                if (mPreview!=null) {
//                                    mPreview.increaseExposure();//增加曝光值
//                                }
                                break;
                            case FaceInterface.CW_LivenessCode.CW_FACE_INFO_TOO_BRIGHT://too bright
                                tv_Info.setText(R.string.cloudwalk_tip_too_bright);
//                                if (mPreview!=null) {
//                                    mPreview.decreaseExposure();//减少曝光值
//                                }
                                break;
                            case FaceInterface.CW_LivenessCode.CW_FACE_INFO_NOT_CENTER://not center
                                tv_Info.setText(R.string.cloudwalk_tip_not_center);
                                break;
                            case FaceInterface.CW_FaceDETCode.CW_FACE_NO_FACE:
                                tv_Info.setText(R.string.cloudwalk_tip_no_face);
                                break;

                            //句柄提示信息
                            case FaceInterface.CW_FaceDETCode.CW_FACE_UNAUTHORIZED_ERR:
                                tv_Info.setText(R.string.facedectfail_appid);
                                break;
                        }
                    }
                }

            }
        });
    }

    @Override
    public void detectReady() {
        currentStep ++;
        cloudwalkSDK.cwStopLivess();//暂停处理视频帧
        if (isStartDetectFace) {//提示语播放完成,直接进入检测
            doNextStep();
        } else {
            mMainHandler.sendEmptyMessageDelayed(DETECT_READY,400);
        }
    }

    @Override
    public void detectFinished() {
    }

    @Override
    public void OnActionNotStandard(int notStandardType) {

        if (currentStep != 0 && !isLivePass) {

            mMainHandler.obtainMessage(SET_RESULT, notStandardType).sendToTarget();
        }

    }

    public static class MainHandler extends Handler {
        private final WeakReference<LiveActivity> mActivity;

        public MainHandler(LiveActivity activity) {
            mActivity = new WeakReference<LiveActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            LiveActivity activity = mActivity.get();
            if (activity == null)
                return;
            switch (msg.what) {

                case SET_RESULT:
                    Integer resultCode = (Integer) msg.obj;
                    activity.setFaceResult(false, 0d, "", resultCode, null);

                    break;

                case UPDATE_STEP_PROCRESS:

                    Integer progress = (Integer) msg.obj;
                    activity.mPb_step.setProgress(progress);
                    break;

                case UPDATESTEPLAYOUT:
                    activity.updateStepLayout(execLiveList.get(activity.currentStep - 1));
                    break;
                case PLAYMAIN_END:
                    activity.isStartDetectFace = true;//
                    break;

                case NEXT_STEP:
                    if (activity.totalStep == activity.currentStep) {// 人脸比对页面
                        activity.getBestFace();
                        if (Bulider.isServerLive) {
                            activity.doFaceSerLivess();
                        }else {
                            activity.doFaceVerify();
                        }
                    } else {
                        if (activity.currentStep == 1){
//                            activity.clearBestFace();
                        }
                        activity.startLivessDetect(execLiveList.get(activity.currentStep - 1));
                    }
                    break;
                case DETECT_READY:
                    if (activity.isStartDetectFace) {
                       activity.doNextStep();
                    } else {
                       sendEmptyMessageDelayed(DETECT_READY,400);
                    }
                    break;
            }
            super.handleMessage(msg);
        }

    }

    @Override
    public void onRightClick(View v) {

        caremaId = mPreview.switchCarema();
        stopTimerRunnable();
        resetLive();
        super.onRightClick(v);
    }

    private void startTimerRunnable(int count) {

        faceTimerRunnable = new TimerRunnable(count, this);

        mMainHandler.postDelayed(faceTimerRunnable, 0);

    }

    void stopTimerRunnable() {
        if (faceTimerRunnable != null)
            faceTimerRunnable.setFlag(false);
    }

    static class TimerRunnable implements Runnable {

        private final WeakReference<LiveActivity> mActivity;

        int djsCount;
        boolean flag = true;

        public boolean isFlag() {
            return flag;
        }

        public void setFlag(boolean flag) {
            this.flag = flag;
        }

        public TimerRunnable(int djsCount, LiveActivity activity) {
            super();
            this.djsCount = djsCount;
            mActivity = new WeakReference<LiveActivity>(activity);
        }

        public void run() {
            LiveActivity act = mActivity.get();
            if (!flag || act == null)
                return;

            act.mMainHandler.obtainMessage(UPDATE_STEP_PROCRESS, djsCount).sendToTarget();
            djsCount--;

            if (djsCount >= 0) {
                act.mMainHandler.postDelayed(act.faceTimerRunnable, 1000);
            } else {

                act.mMainHandler.obtainMessage(SET_RESULT, CW_LivenessCode.CW_FACE_LIVENESS_OVERTIME).sendToTarget();

            }

        }
    }
}