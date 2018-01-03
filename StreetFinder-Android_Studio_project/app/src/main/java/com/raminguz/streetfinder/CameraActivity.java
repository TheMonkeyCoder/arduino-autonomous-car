package com.raminguz.streetfinder;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.Window;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2, Runnable {

    public enum State { GO_FORWARD, GO_BACKWARD, TURN_LEFT, TURN_RIGHT, TURN_LEFT_REVERSE, TURN_RIGHT_REVERSE, STOP, OVERTAKE_START, OVERTAKE_END };

    private int screenWidth, screenHeight;
    private boolean trajectoryCorrection = false, isOvertakeActive = false, isOvertakeStartActive = false, isOvertakeEndActive = false, firstLateralSensorActivation = false;

    private JavaCameraView mCameraView = null;
    private BtClient mBtClient = null;
    private State mState = State.STOP;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    mCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screenWidth =  getWindowManager().getDefaultDisplay().getWidth();
        screenHeight = getWindowManager().getDefaultDisplay().getHeight();
        setContentView(R.layout.activity_camera);

        mCameraView = (JavaCameraView) findViewById(R.id.CameraActivity);
        mCameraView.setVisibility(SurfaceView.VISIBLE);
        mCameraView.setCvCameraViewListener(this);

        mBtClient = (BtClient) getIntent().getParcelableExtra("client");
    }

    @Override
    protected void onResume() {
        super.onResume();

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);

        if (!mBtClient.isConnected()) mBtClient.openConnection();

        new Thread(this).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraView != null) mCameraView.disableView();
        if (mBtClient.isConnected()) {
            sendState(State.STOP);
            mBtClient.closeConnection();
        }
    }


    // CAMERA VIEW STATES

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat originalImage = inputFrame.rgba(),
            tmpInputFrame = inputFrame.gray().submat(screenHeight - 100, screenHeight, 0, screenWidth),
            lines = new Mat();

        boolean straightLeftLine = false, straightRightLine = false, turnLeft = false, turnRight = false;

        Imgproc.Canny(tmpInputFrame, tmpInputFrame, 40, 120, 3, true);
        Imgproc.HoughLinesP(tmpInputFrame, lines, 1, Math.PI / 180, 50, 20, 20);

        for (int i = 0; i < lines.rows(); i++) {
            double[] points = lines.get(i, 0);
            double slope = (points[3] - points[1]) / (points[2] - points[0]); // (y2 - y1) / (x2 - x1)
            double degrees = Math.toDegrees(Math.atan(slope));

            // RIGHT STRAIGHT LINE
            if ((degrees < 60 && degrees > 20)) {
                if (points[0] >= screenWidth / 2) {
                    straightRightLine = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(0, 255, 0), 6);

                } else {
                    turnLeft = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(255, 0, 0), 6);
                }

            // LEFT STRAIGHT LINE
            } else if (degrees > -60 && degrees < -20) {
                if (points[2] < screenWidth / 2) {
                    straightLeftLine = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(0, 255, 0), 6);

                } else {
                    turnRight = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(255, 0, 0), 6);
                }

            // TURN LEFT LINE
            } else if (degrees < 20 && degrees > 5) {
                if ((points[2] > screenWidth / 2 && points[3] > 80) || (isOvertakeEndActive && points[3] > 80)) {
                    turnLeft = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(255, 0, 0), 6);
                }

            // TURN RIGHT LINE
            } else if (degrees > -20 && degrees < -5) {
                if ((points[0] < screenWidth / 2 && points[1] > 80) || (isOvertakeStartActive && points[3] > 80)) {
                    turnRight = true;
                    Imgproc.line(originalImage, new Point(points[0], points[1] + screenHeight - 100), new Point(points[2], points[3] + screenHeight - 100), new Scalar(255, 0, 0), 6);
                }
            }
        }


        // OVERTAKE START CONDITION
        if (isOvertakeStartActive) {
            if (straightRightLine && straightLeftLine && mState == State.TURN_RIGHT) sendState(State.GO_FORWARD);
            else if (turnRight) sendState(State.TURN_RIGHT);
            else sendState(State.TURN_LEFT);

        // OVERTAKE END CONDITION
        } else if (isOvertakeEndActive) {
            if (straightRightLine || straightLeftLine) sendState(State.TURN_RIGHT);
            else if (turnLeft) {
                sendState(State.TURN_LEFT);
                isOvertakeEndActive = false;
            }

        // NORMAL CONDITION
        } else {
            if (turnLeft || (straightRightLine && !straightLeftLine)) sendState(State.TURN_LEFT);
            else if (turnRight || (straightLeftLine && !straightRightLine))
                sendState(State.TURN_RIGHT);
            else if (straightLeftLine && straightRightLine) sendState(State.GO_FORWARD);
            else if (mState == State.TURN_LEFT && trajectoryCorrection) sendState(State.TURN_RIGHT);
            else if (mState == State.TURN_RIGHT && trajectoryCorrection) sendState(State.TURN_LEFT);
            else sendState(State.GO_FORWARD);
        }

        trajectoryCorrection = (straightRightLine && !straightLeftLine) || (straightLeftLine && !straightRightLine);

        return originalImage;
    }

    public void sendState (State state) {
        if (mState != state) {
            mState = state;

            mBtClient.writeData(String.valueOf(mState.ordinal()));
        }
    }


    @Override
        public void run() {
        String dataReceived;

        while (mBtClient.isConnected()) {
            if (mBtClient.availableData()) {
                dataReceived = mBtClient.readChar();
                switch (dataReceived) {
                    case "N":
                        if (isOvertakeStartActive) {
                            if (firstLateralSensorActivation) {
                                isOvertakeActive = true;
                                isOvertakeStartActive = firstLateralSensorActivation = false;
                            } else firstLateralSensorActivation = true;
                        }
                        break;
                    case "Y":
                        if (isOvertakeActive) {
                            isOvertakeEndActive = true;
                            isOvertakeActive = false;
                        }
                        break;
                    default:
                        if (!isOvertakeStartActive && !isOvertakeActive && !isOvertakeEndActive && (Integer.valueOf(dataReceived) == State.OVERTAKE_START.ordinal())) {
                            isOvertakeStartActive = true;
                        }
                        break;
                }
            }
        }
    }
}
