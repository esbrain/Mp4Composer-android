package com.daasuu.mp4compose.source;

import androidx.annotation.NonNull;

import com.daasuu.mp4compose.logger.Logger;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class FilePathDataSource implements DataSource {

    private final static String TAG = FilePathDataSource.class.getSimpleName();

    private FileDescriptor fileDescriptor;

    private String filePath;

    public FilePathDataSource(@NonNull String filePath, @NonNull Logger logger, @NonNull Listener listener) {
        this.filePath = filePath;
        final File srcFile = new File(filePath);
        final FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(srcFile);
        } catch (FileNotFoundException e) {
            logger.error(TAG, "Unable to find file", e);
            listener.onError(e);
            return;
        }

        try {
            fileDescriptor = fileInputStream.getFD();
        } catch (IOException e) {
            logger.error(TAG, "Unable to read input file", e);
            listener.onError(e);
        }
    }

    // 非常用
    public FilePathDataSource(@NonNull String filePath) {
        this.filePath = filePath;
    }

    @NonNull
    @Override
    public String getFilePath() {
        return this.filePath;
    }

    @Override
    public FileDescriptor getFileDescriptor() {
        return null;
    }
}
