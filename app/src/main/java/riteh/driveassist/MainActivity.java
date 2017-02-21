package riteh.driveassist;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity {

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    // Used to load the native DriveAssist library on application startup.
    static {
        System.loadLibrary("DriveAssist");
    }

    private Size mPreviewSize;
    private String mCameraId;
    private ImageReader mImageReader;
    private ByteBuffer mYPlaneBuffer;
    private TextureView mTextureView;
    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener () {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            Image.Plane YPlane = image.getPlanes()[0];
            mYPlaneBuffer = YPlane.getBuffer();
            // TODO: Implement logic for lane detection
        }
    };

    private TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            };

    private CameraCaptureSession.CaptureCallback mSessionCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }
    };

    private void createCameraPreviewSession() {
        try {
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface textureViewSurface = new Surface(surfaceTexture);
            mPreviewCaptureRequestBuilder.addTarget(textureViewSurface);

            Surface imageReaderSurface = mImageReader.getSurface();
            mPreviewCaptureRequestBuilder.addTarget(imageReaderSurface);

            mCameraDevice.createCaptureSession(
                    Arrays.asList(textureViewSurface, imageReaderSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (mCameraDevice == null) {
                                return;
                            }
                            try {
                                mPreviewCaptureRequest = mPreviewCaptureRequestBuilder.build();
                                mCameraCaptureSession = session;
                                mCameraCaptureSession.setRepeatingRequest(
                                        mPreviewCaptureRequest,
                                        mSessionCallback,
                                        null); // TODO: Handle this on background thread
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(
                                    getApplicationContext(),
                                    "Failed creating capture session",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, null); // TODO: This should be handled by a background thread, not null
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    CameraDevice mCameraDevice;
    CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Toast.makeText(getApplicationContext(), "Camera successfully opened!", Toast.LENGTH_SHORT).show();
            mCameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Toast.makeText(getApplicationContext(), "Camera disconnected!", Toast.LENGTH_SHORT).show();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Toast.makeText(getApplicationContext(), "Error opening camera!", Toast.LENGTH_SHORT).show();
            mCameraDevice = null;
        }
    };

    /*
        Opens camera with mCameraId and mCameraStateCallback
        This should be setup before by calling setupCamera()
     */
    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraManager.openCamera(mCameraId, mCameraStateCallback, null);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = (TextureView) this.findViewById(R.id.cameraOutput);
    }

    @Override
    public void onResume() {
        super.onResume();

        setupCamera();

        if (mImageReader == null) {
            mImageReader = ImageReader.newInstance(
                    mPreviewSize.getWidth(),
                    mPreviewSize.getHeight(),
                    ImageFormat.YUV_420_888,
                    4);
            // TODO: Backgound thread should handle this, not null;
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
        }

        if (mTextureView.isAvailable()) {
            openCamera();
        }
        else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        closeCamera();
    }

    /*
        Sets up camera
         - Finds rear camera
         - Sets up
            mCameraId
            mPreviewSize
    */
    private void setupCamera() {
        CameraManager cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Find front facing camera
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

                // Make sure it's facing back
                Integer facingDirection = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facingDirection != null && facingDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                // Get available dimensions
                StreamConfigurationMap map = cameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // Find smallest image dimension for camera prewiew
                mPreviewSize = Collections.min(
                        Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                        new CompareSizesByArea());

                mCameraId = cameraId;
                return;
            }
        }
        catch (CameraAccessException exception) {
            Log.d("CAMERA", "Error accessing camera");
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String laneIntersections();
}
