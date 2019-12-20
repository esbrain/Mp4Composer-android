package com.daasuu.mp4compose.composer;

import android.content.Context;
import android.net.Uri;

import com.daasuu.mp4compose.logger.Logger;
import com.daasuu.mp4compose.source.DataSource;
import com.daasuu.mp4compose.source.FileDescriptorDataSource;
import com.daasuu.mp4compose.source.FilePathDataSource;
import com.daasuu.mp4compose.source.UriDataSource;

import java.io.FileDescriptor;

import androidx.annotation.NonNull;

public class VideoEntry {
    final DataSource srcDataSource;
    final long trimStartMs;
    final long trimEndMs;
    final long outputStartMs;

    VideoEntry(final DataSource srcDataSource, final long trimStartMs, long trimEndMs, long outputStartMs) {
        this.srcDataSource = srcDataSource;
        this.trimStartMs = trimStartMs;
        this.trimEndMs = trimEndMs;
        this.outputStartMs = outputStartMs;
    }

    public static VideoEntry create(@NonNull final String filePath, final long trimStartMs, final long trimEndMs, final long outputStartMs, Logger logger, DataSource.Listener listener) {
        DataSource src = new FilePathDataSource(filePath, logger, listener);
        return new VideoEntry(src, trimStartMs, trimEndMs, outputStartMs);
    }

    public static VideoEntry create(@NonNull final String filePath, final long trimStartMs, final long trimEndMs, final long outputStartMs) {
        DataSource src = new FilePathDataSource(filePath);
        return new VideoEntry(src, trimStartMs, trimEndMs, outputStartMs);
    }

    public static VideoEntry create(@NonNull final FileDescriptor fileDescriptor, final long trimStartMs, final long trimEndMs, final long outputStartMs) {
        DataSource src = new FileDescriptorDataSource(fileDescriptor);
        return new VideoEntry(src, trimStartMs, trimEndMs, outputStartMs);
    }

    public static VideoEntry create(@NonNull final Uri uri, final long trimStartMs, final long trimEndMs, final long outputStartMs, final Context context, final Logger logger, DataSource.Listener listener) {
        DataSource src = new UriDataSource(uri, context, logger, listener);
        return new VideoEntry(src, trimStartMs, trimEndMs, outputStartMs);
    }

}
