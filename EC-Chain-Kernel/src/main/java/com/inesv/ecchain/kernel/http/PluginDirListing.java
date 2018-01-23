package com.inesv.ecchain.kernel.http;

import com.inesv.ecchain.common.core.Constants;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;


class PluginDirListing extends SimpleFileVisitor<Path> {

    private final List<Path> directories = new ArrayList<>();

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException e) {
        if (!Constants.PLUGINS_HOME.equals(dir)) {
            directories.add(dir);
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException e) {
        return FileVisitResult.CONTINUE;
    }

    public List<Path> getDirectories() {
        return directories;
    }
}
