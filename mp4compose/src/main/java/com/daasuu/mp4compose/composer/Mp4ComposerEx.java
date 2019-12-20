package com.daasuu.mp4compose.composer;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.os.Build;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.daasuu.mp4compose.FillMode;
import com.daasuu.mp4compose.FillModeCustomItem;
import com.daasuu.mp4compose.Rotation;
import com.daasuu.mp4compose.filter.GlFilter;
import com.daasuu.mp4compose.logger.AndroidLogger;
import com.daasuu.mp4compose.logger.Logger;
import com.daasuu.mp4compose.source.AudioSource;
import com.daasuu.mp4compose.source.DataSource;
import com.daasuu.mp4compose.source.FileDescriptorDataSource;
import com.daasuu.mp4compose.source.FilePathDataSource;
import com.daasuu.mp4compose.source.UriDataSource;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by sudamasayuki on 2017/11/15.
 */

public class Mp4ComposerEx {

    private final static String TAG = Mp4Composer.class.getSimpleName();
    //private DataSource srcDataSource = null;
    private ArrayList<VideoEntry> srcDataSource = new ArrayList<VideoEntry>();
    private AudioSource srcAudioSource = null;
    private final String destPath;
    private FileDescriptor destFileDescriptor;
    private GlFilter filter;
    private Size outputResolution;
    private int bitrate = -1;
    private boolean mute = false;
    private Rotation rotation = Rotation.NORMAL;
    private Mp4Composer.Listener listener;
    private FillMode fillMode = FillMode.PRESERVE_ASPECT_FIT;
    private FillModeCustomItem fillModeCustomItem;
    private int timeScale = 1;
    private boolean flipVertical = false;
    private boolean flipHorizontal = false;
    private long trimStartMs = 0;
    private long trimEndMs = -1;
    private EGLContext shareContext;

    private ExecutorService executorService;

    private Logger logger;

    private DataSource.Listener errorDataSource = new DataSource.Listener() {
        @Override
        public void onError(Exception e) {
            notifyListenerOfFailureAndShutdown(e);
        }
    };

    public Mp4ComposerEx(@NonNull final String srcPath, @NonNull final String destPath, final long trimStartMs, long trimEndMs, long outputStartMs) {
        VideoEntry entry = VideoEntry.create(srcPath, trimStartMs, trimEndMs, outputStartMs, logger, errorDataSource);
        this.srcDataSource.add(entry);
        this.destPath = destPath;
    }

    public Mp4ComposerEx(@NonNull final FileDescriptor srcFileDescriptor, @NonNull final String destPath, final long trimStartMs, final long trimEndMs, final long outputStartMs) {
        VideoEntry entry = VideoEntry.create(srcFileDescriptor, trimStartMs, trimEndMs, outputStartMs);
        this.srcDataSource.add(entry);
        this.destPath = destPath;
    }

    public Mp4ComposerEx(@NonNull final Uri srcUri, @NonNull final String destPath, @NonNull final Context context, final long trimStartMs, final long trimEndMs, final long outputStartMs) {
        VideoEntry entry = VideoEntry.create(srcUri, trimStartMs, trimEndMs, outputStartMs, context, logger, errorDataSource);
        this.srcAudioSource = null;
        this.destPath = destPath;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public Mp4ComposerEx(@NonNull final FileDescriptor srcFileDescriptor, @NonNull final FileDescriptor destFileDescriptor, final long trimStartMs, final long trimEndMs, final long outputStartMs) {
        if (Build.VERSION.SDK_INT < 26) {
            throw new IllegalArgumentException("destFileDescriptor can not use");
        }
        VideoEntry entry = VideoEntry.create(srcFileDescriptor, trimStartMs, trimEndMs, outputStartMs);
        this.srcDataSource.add(entry);
        this.srcAudioSource = null;
        this.destPath = null;
        this.destFileDescriptor = destFileDescriptor;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public Mp4ComposerEx(@NonNull final Uri srcUri, @NonNull final FileDescriptor destFileDescriptor, @NonNull final Context context, final long trimStartMs, final long trimEndMs, final long outputStartMs) {
        if (Build.VERSION.SDK_INT < 26) {
            throw new IllegalArgumentException("destFileDescriptor can not use");
        }
        VideoEntry entry = VideoEntry.create(srcUri, trimStartMs, trimEndMs, outputStartMs, context, logger, errorDataSource);
        this.srcDataSource.add(entry);
        this.srcAudioSource = null;
        this.destPath = null;
        this.destFileDescriptor = destFileDescriptor;
    }

    public Mp4ComposerEx video(final VideoEntry entry) {
        this.srcDataSource.add(entry);
        return this;
    }

    /*
    public Mp4ComposerEx audio(@NonNull String srcAudioPath) {
        this.srcAudioSource = new FilePathDataSource(srcAudioPath, logger, errorDataSource);
        return this;
    }

    public Mp4ComposerEx audio(@NonNull final FileDescriptor srcFileDescriptor) {
        this.srcAudioSource = new FileDescriptorDataSource(srcFileDescriptor);
        return this;
    }

    public Mp4ComposerEx audio(@NonNull final Uri srcUri, @NonNull final Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            throw new IllegalArgumentException("destFileDescriptor can not use");
        }
        this.srcAudioSource = new UriDataSource(srcUri, context, logger, errorDataSource);
        return this;
    }

     */
    public Mp4ComposerEx audio(@NonNull String srcAudioPath) {
        this.srcAudioSource = new AudioSource(srcAudioPath);
        return this;
    }

    public Mp4ComposerEx filter(@NonNull GlFilter filter) {
        this.filter = filter;
        return this;
    }

    public Mp4ComposerEx size(int width, int height) {
        this.outputResolution = new Size(width, height);
        return this;
    }

    public Mp4ComposerEx videoBitrate(int bitrate) {
        this.bitrate = bitrate;
        return this;
    }

    public Mp4ComposerEx mute(boolean mute) {
        this.mute = mute;
        return this;
    }

    public Mp4ComposerEx flipVertical(boolean flipVertical) {
        this.flipVertical = flipVertical;
        return this;
    }

    public Mp4ComposerEx flipHorizontal(boolean flipHorizontal) {
        this.flipHorizontal = flipHorizontal;
        return this;
    }

    public Mp4ComposerEx rotation(@NonNull Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public Mp4ComposerEx fillMode(@NonNull FillMode fillMode) {
        this.fillMode = fillMode;
        return this;
    }

    public Mp4ComposerEx customFillMode(@NonNull FillModeCustomItem fillModeCustomItem) {
        this.fillModeCustomItem = fillModeCustomItem;
        this.fillMode = FillMode.CUSTOM;
        return this;
    }


    public Mp4ComposerEx listener(@NonNull Mp4Composer.Listener listener) {
        this.listener = listener;
        return this;
    }

    public Mp4ComposerEx timeScale(final int timeScale) {
        this.timeScale = timeScale;
        return this;
    }

    /**
     * Set the {@link Logger} that should be used. Defaults to {@link AndroidLogger} if none is set.
     *
     * @param logger The logger that should be used to log.
     * @return The composer instance.
     */
    public Mp4ComposerEx logger(@NonNull final Logger logger) {
        this.logger = logger;
        return this;
    }

    /**
     * Trim the video to the provided times. By default the video will not be trimmed.
     *
     * @param trimStartMs The start time of the trim in milliseconds.
     * @param trimEndMs   The end time of the trim in milliseconds, -1 for no end.
     * @return The composer instance.
     */
    public Mp4ComposerEx trim(final long trimStartMs, final long trimEndMs) {
        this.trimStartMs = trimStartMs;
        this.trimEndMs = trimEndMs;
        return this;
    }

    public Mp4ComposerEx shareContext(@NonNull EGLContext shareContext) {
        this.shareContext = shareContext;
        return this;
    }

    private ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }


    public Mp4ComposerEx start() {
        getExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                if (logger == null) {
                    logger = new AndroidLogger();
                }
                Mp4ComposerEngineEx engine = new Mp4ComposerEngineEx(logger);

                engine.setProgressCallback(new Mp4ComposerEngine.ProgressCallback() {
                    @Override
                    public void onProgress(final double progress) {
                        if (listener != null) {
                            listener.onProgress(progress);
                        }
                    }
                });

                final Integer videoRotate = getVideoRotation(srcDataSource.get(0).srcDataSource);
                final Size srcVideoResolution = getVideoResolution(srcDataSource.get(0).srcDataSource);

                if (srcVideoResolution == null || videoRotate == null) {
                    notifyListenerOfFailureAndShutdown(new UnsupportedOperationException("File type unsupported, path: " + srcDataSource));
                    return;
                }

                if (filter == null) {
                    filter = new GlFilter();
                }

                if (fillMode == null) {
                    fillMode = FillMode.PRESERVE_ASPECT_FIT;
                }
                if (fillMode == FillMode.CUSTOM && fillModeCustomItem == null) {
                    notifyListenerOfFailureAndShutdown(new IllegalAccessException("FillMode.CUSTOM must need fillModeCustomItem."));
                    return;
                }

                if (fillModeCustomItem != null) {
                    fillMode = FillMode.CUSTOM;
                }

                if (outputResolution == null) {
                    if (fillMode == FillMode.CUSTOM) {
                        outputResolution = srcVideoResolution;
                    } else {
                        Rotation rotate = Rotation.fromInt(rotation.getRotation() + videoRotate);
                        if (rotate == Rotation.ROTATION_90 || rotate == Rotation.ROTATION_270) {
                            outputResolution = new Size(srcVideoResolution.getHeight(), srcVideoResolution.getWidth());
                        } else {
                            outputResolution = srcVideoResolution;
                        }
                    }
                }

                if (timeScale < 2) {
                    timeScale = 1;
                }

                if (shareContext == null) {
                    shareContext = EGL14.EGL_NO_CONTEXT;
                }

                logger.debug(TAG, "rotation = " + (rotation.getRotation() + videoRotate));
                logger.debug(TAG, "rotation = " + Rotation.fromInt(rotation.getRotation() + videoRotate));
                logger.debug(TAG, "inputResolution width = " + srcVideoResolution.getWidth() + " height = " + srcVideoResolution.getHeight());
                logger.debug(TAG, "outputResolution width = " + outputResolution.getWidth() + " height = " + outputResolution.getHeight());
                logger.debug(TAG, "fillMode = " + fillMode);

                try {
                    if (bitrate < 0) {
                        bitrate = calcBitRate(outputResolution.getWidth(), outputResolution.getHeight());
                    }
                    ArrayList<VideoTrack> tracks = new ArrayList<VideoTrack>();
                    for(int i = 0; i < srcDataSource.size(); i++) {
                        VideoEntry entry = srcDataSource.get(i);
                        VideoTrack track = new VideoTrack(entry.srcDataSource, entry.trimStartMs, entry.trimEndMs, entry.outputStartMs, logger);
                        tracks.add(track);
                    }


                    engine.compose(
                            tracks.toArray(new VideoTrack[tracks.size()]),
                            srcAudioSource,
                            destPath,
                            destFileDescriptor,
                            outputResolution,
                            filter,
                            bitrate,
                            mute,
                            Rotation.fromInt(rotation.getRotation() + videoRotate),
                            srcVideoResolution,
                            fillMode,
                            fillModeCustomItem,
                            timeScale,
                            flipVertical,
                            flipHorizontal,
                            trimStartMs,
                            trimEndMs,
                            shareContext
                    );

                } catch (Exception e) {
                    if (e instanceof MediaCodec.CodecException) {
                        logger.error(TAG, "This devicel cannot codec with that setting. Check width, height, bitrate and video format.", e);
                        notifyListenerOfFailureAndShutdown(e);
                        return;
                    }

                    logger.error(TAG, "Unable to compose the engine", e);
                    notifyListenerOfFailureAndShutdown(e);
                    return;
                }

                if (listener != null) {
                    listener.onCompleted();
                }
                executorService.shutdown();
            }
        });

        return this;
    }

    private void notifyListenerOfFailureAndShutdown(final Exception failure) {
        if (listener != null) {
            listener.onFailed(failure);
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public void cancel() {
        getExecutorService().shutdownNow();
    }


    public interface Listener {
        /**
         * Called to notify progress.
         *
         * @param progress Progress in [0.0, 1.0] range, or negative value if progress is unknown.
         */
        void onProgress(double progress);

        /**
         * Called when transcode completed.
         */
        void onCompleted();

        /**
         * Called when transcode canceled.
         */
        void onCanceled();


        void onFailed(Exception exception);
    }

    @Nullable
    private Integer getVideoRotation(DataSource dataSource) {
        MediaMetadataRetriever mediaMetadataRetriever = null;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            if(dataSource.getFilePath() != null) {
                mediaMetadataRetriever.setDataSource(dataSource.getFilePath());
            } else {
                mediaMetadataRetriever.setDataSource(dataSource.getFileDescriptor());
            }
            final String orientation = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (orientation == null) {
                return null;
            }
            return Integer.valueOf(orientation);
        } catch (IllegalArgumentException e) {
            logger.error("MediaMetadataRetriever", "getVideoRotation IllegalArgumentException", e);
            return 0;
        } catch (RuntimeException e) {
            logger.error("MediaMetadataRetriever", "getVideoRotation RuntimeException", e);
            return 0;
        } catch (Exception e) {
            logger.error("MediaMetadataRetriever", "getVideoRotation Exception", e);
            return 0;
        } finally {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                }
            } catch (RuntimeException e) {
                logger.error(TAG, "Failed to release mediaMetadataRetriever.", e);
            }
        }
    }

    private int calcBitRate(int width, int height) {
        final int bitrate = (int) (0.25 * 30 * width * height);
        logger.debug(TAG, "bitrate=" + bitrate);
        return bitrate;
    }

    /**
     * Extract the resolution of the video at the provided path, or null if the format is
     * unsupported.
     */
    @Nullable
    private Size getVideoResolution(DataSource dataSource) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            if(dataSource.getFilePath() != null) {
                retriever.setDataSource(dataSource.getFilePath());
            } else {
                retriever.setDataSource(dataSource.getFileDescriptor());
            }
            final String rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            final String rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (rawWidth == null || rawHeight == null) {
                return null;
            }
            final int width = Integer.valueOf(rawWidth);
            final int height = Integer.valueOf(rawHeight);

            return new Size(width, height);
        } finally {
            try {
                if (retriever != null) {
                    retriever.release();
                }
            } catch (RuntimeException e) {
                logger.error(TAG, "Failed to release mediaMetadataRetriever.", e);
            }
        }
    }

}
