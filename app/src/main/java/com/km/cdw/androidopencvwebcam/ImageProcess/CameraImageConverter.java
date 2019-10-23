package com.km.cdw.androidopencvwebcam.ImageProcess;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.km.cdw.androidopencvwebcam.ImageProcess.CVProcess.OpencvProcessor;
import com.km.cdw.androidopencvwebcam.ImageProcess.FileSystem.ImageSaver;
import com.km.cdw.androidopencvwebcam.ImageProcess.GLRendering.Renderer.GLSurfaceRenderer_RgbMat;

import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;

public class CameraImageConverter {
    public static final String TAG = "CameraImageConverter";
    public static final int RENDER_DATA_FORMAT = ImageFormat.YUV_420_888;
    public static final int QUEUE_SIZE = 2;

    private ImageSaver mSaver;
    private OpencvProcessor mProcessor;
    private GLSurfaceRenderer_RgbMat mRenderer;

    private ImageReader mImgReader;
    private final ArrayDeque<YUVImgData> mProcessQueue;
    private final ArrayDeque<Mat> mRenderQueue;
    private HandlerThread mAsyncThread;
    private Handler mAsync;

    public final Semaphore mProcessQueueLock = new Semaphore(1);
    public final Semaphore mRenderQueueLock = new Semaphore(1);
    private ImageReader.OnImageAvailableListener mReaderListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            int maxcount = reader.getMaxImages();
            Log.e(TAG, "maxcount : "+maxcount);

            //Throw JPEG data to OpencvProcessor
            Image image = reader.acquireNextImage();
            if(image != null){
                if(image.getFormat() == ImageFormat.YUV_420_888) {
                    ByteBuffer yBuf = image.getPlanes()[0].getBuffer();
                    ByteBuffer uBuf = image.getPlanes()[1].getBuffer();
                    ByteBuffer vBuf = image.getPlanes()[2].getBuffer();

                    int ySize = yBuf.remaining();
                    int uSize = uBuf.remaining();
                    int vSize = vBuf.remaining();

                    ByteBuffer convertedBuf;

                    Log.e(TAG, "ySize : " + ySize + " uSize : " + uSize + " vSize : " + vSize);

//                    while(mProcessQueue.size() > 1) {
//                        mProcessQueue.removeLast();
//                    }
                    mProcessQueue.add(
                            new YUVImgData(yBuf,uBuf,vBuf, ySize,uSize,vSize, image.getWidth(),image.getHeight(),image.getFormat())
                    );
                    mProcessQueueLock.release();

                }
//                    synchronized (mProcessQueue) {
//                        if(mProcessQueue.size() >= QUEUE_SIZE) {
//                            mProcessQueue.pop();
//                        }
//                        mProcessQueue.add(
//                                new YUVImgData(yBuf,uBuf,vBuf, ySize,uSize,vSize, image.getWidth(),image.getHeight(),image.getFormat())
//                        );
//                        mProcessQueue.notifyAll();
//                    }
                //JPEG ver
//                ByteBuffer jpgBuf = image.getPlanes()[0].getBuffer();
//                int jpgSize = jpgBuf.remaining();
//                byte[] jpgBytes = new byte[jpgSize];
//                jpgBuf.get(jpgBytes);
//
//                if(mProcessQueueLock.tryAcquire()) {
//                    if(mProcessQueue.size() >= 2) {
//                        mProcessQueue.clear();
//                    }
//                    mProcessQueue.push(jpgBytes);
//                    mProcessQueueLock.release();
//                }
                image.close();
            }
        }

//        ////   TEST
//        int test_count = 3;
//        @Override
//        public void onImageAvailable(ImageReader reader) {
//            int maxcount = reader.getMaxImages();
//            Log.e(TAG, "maxcount : "+maxcount);
//
//            if(test_count < 0) {
//                return;
//            }
//            //Throw JPEG data to OpencvProcessor
//            Image image = reader.acquireNextImage();
//
//            if(image != null){
//                ByteBuffer jpgBuf = image.getPlanes()[0].getBuffer();
//                int jpgSize = jpgBuf.remaining();
//                byte[] jpgBytes = new byte[jpgSize];
//                jpgBuf.get(jpgBytes);
//
//                Log.e(TAG, "jpgBytes Size : " + jpgBytes.length + " width : " + image.getWidth() + " height : " + image.getHeight());
//
////                Bitmap bitmap = BitmapFactory.decodeByteArray(jpgBytes, 0, jpgBytes.length);
////                Bitmap outputbmp = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
////                Mat mat = new Mat();
////
////                Utils.bitmapToMat(bitmap, mat);
////                Utils.matToBitmap(mat, outputbmp);
////
////
////                ByteArrayOutputStream stream = new ByteArrayOutputStream();
////                outputbmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
////                byte[] byteArray = stream.toByteArray();
////                mImagePeeker.save(byteArray);
//
////                test_count--;
//                image.close();
//            }
//        }
    };


    public CameraImageConverter() {
        mProcessor = new OpencvProcessor();
        mRenderer = new GLSurfaceRenderer_RgbMat();
        mProcessQueue = new ArrayDeque<>(QUEUE_SIZE);
        mRenderQueue = new ArrayDeque<>(QUEUE_SIZE);
    }
    public void setCvProcessFlag(OpencvProcessor.ProcessConfig pc) {
        mProcessor.setProcessFlag(pc);
    }
    public void setSaver(Activity activity) {
        mSaver = new ImageSaver(activity);
    }
    public void capture() {
        if(mSaver != null) {
            Bitmap targetBitmap = mRenderer.getCurrentFrameBitmap();
            mSaver.saveBitmap(targetBitmap);
        }
    }


    public void clearWorkQueue() {
        mProcessQueue.clear();
        mRenderQueue.clear();

    }
    public void openImgReader(int width, int height, int fmt, int bufsize) {
        if(mAsync == null) {
            Log.e(TAG, "Async NOT SET");
            return;
        }
        mImgReader = ImageReader.newInstance(width, height, fmt, bufsize);
        mImgReader.setOnImageAvailableListener(mReaderListener, null);
    }
    public void startAsyncHandler() {
        mAsyncThread = new HandlerThread("CameraImageConverter");
        mAsyncThread.start();
        mAsync = new Handler(mAsyncThread.getLooper());
    }
    public void stopAsyncHandler() {
        mAsyncThread.quitSafely();
        mAsyncThread = null;
        mAsync = null;
    }
    public void closeImgReader() {
        mImgReader.close();
        mImgReader = null;
    }
    public void openConverter(int width, int height, int fmt, int bufsize) {
        Log.e(TAG, "openConverter START");
        startAsyncHandler();
        openImgReader(width, height, fmt, bufsize);

        mProcessQueueLock.acquireUninterruptibly();
        mRenderer.setWorkQueue(mRenderQueue, mRenderQueueLock);
        mProcessor.setQueueParam(mProcessQueue, mRenderQueue, mProcessQueueLock, mRenderQueueLock);
        mProcessor.startProcessor();
        Log.e(TAG, "openConverter STOP");
    }
    public void closeConverter() {
        Log.e(TAG, "closeConverter START");
        clearWorkQueue();
        mProcessor.stopProcessor();
        closeImgReader();
        stopAsyncHandler();
        Log.e(TAG, "closeConverter END");
    }
    public Surface getImgReaderSurface() {
        return mImgReader.getSurface();
    }
    public GLSurfaceView.Renderer getRenderer() {
        return mRenderer;
    }

}
