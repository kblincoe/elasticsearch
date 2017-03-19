/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.watcher;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.logging.Loggers;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * The file watcher checks directory and all its subdirectories for file changes and notifies its listeners accordingly
 * http://docs.oracle.com/javase/tutorial/displayCode.html?code=http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 */
public class DirectoryWatcher extends AbstractResourceWatcher<FileChangesListener> {

    private Path file;
    private final Map<WatchKey, Path> keys;

    private WatchService watchService;
    private WatchKey rootKey;

    private static final Logger logger = Loggers.getLogger(DirectoryWatcher.class);

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    public DirectoryWatcher(Path file) throws IOException {
        this.file = file;
        this.watchService = PathUtils.getDefaultFileSystem().newWatchService();
        this.keys = new HashMap<WatchKey, Path>();
    }

    @Override
    protected void doInit() throws IOException {
        rootKey = file.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (!Files.isRegularFile(file)) {
            registerAll(file, true);
        }
    }

    @Override
    protected void doCheckAndNotify() throws IOException {
        while (true) {
            // wait for key to be signalled
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            boolean isRoot = key.equals(rootKey);

            if (dir == null && !isRoot) {
                logger.error("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();

                if (isRoot) {
                    if (file.getFileName().equals(name) && kind == ENTRY_DELETE) {
                        // We only really have to worry about if this directory is deleted
                        onDirectoryDeleted(file);
                    }
                } else {
                    Path child = dir.resolve(name);

                    // if directory is created, and watching recursively, then
                    // register it and its sub-directories
                    if (kind == ENTRY_CREATE) {
                        try {
                            if (Files.isDirectory(child)) {
                                registerAll(child, false);
                                onDirectoryCreated(child);
                            } else {
                                onFileCreated(child);
                            }
                        } catch (IOException e) {
                            logger.error(e.getMessage());
                        }
                    } else if (kind == ENTRY_DELETE) {
                        if (Files.isDirectory(child)) {
                            onDirectoryDeleted(child);
                        } else {
                            onFileDeleted(child);
                        }
                    } else if (kind == ENTRY_MODIFY) {
                        onFileChanged(child);
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    public void stop() {
        try {
            watchService.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private Path[] listFiles(Path directory) throws IOException {
        final Path[] files = FileSystemUtils.files(directory);
        Arrays.sort(files);
        return files;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path directory, boolean init) throws IOException {
        WatchKey key = directory.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

        if (init) {
            for (FileChangesListener listener : listeners()) {
                listener.onDirectoryInit(directory);
            }
            for (Path child : listFiles(directory)) {
                if (Files.isRegularFile(child)) {
                    for (FileChangesListener listener : listeners()) {
                        listener.onFileInit(child);
                    }
                }
            }
        }

        keys.put(key, directory);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start, boolean init) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
                register(dir, init);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void onFileCreated(Path file) {
        for (FileChangesListener listener : listeners()) {
            try {
                listener.onFileCreated(file);
            } catch (Exception e) {
                logger.warn("cannot notify file changes listener", e);
            }
        }
    }

    private void onFileDeleted(Path file) {
        for (FileChangesListener listener : listeners()) {
            try {
                listener.onFileDeleted(file);
            } catch (Exception e) {
                logger.warn("cannot notify file changes listener", e);
            }
        }
    }

    private void onFileChanged(Path file) {
        for (FileChangesListener listener : listeners()) {
            try {
                listener.onFileChanged(file);
            } catch (Exception e) {
                logger.warn("cannot notify file changes listener", e);
            }
        }
    }

    private void onDirectoryCreated(Path dir) throws IOException {
        for (FileChangesListener listener : listeners()) {
            try {
                listener.onDirectoryCreated(dir);
            } catch (Exception e) {
                logger.warn("cannot notify file changes listener", e);
            }
        }
    }

    private void onDirectoryDeleted(Path dir) {
        for (FileChangesListener listener : listeners()) {
            try {
                listener.onDirectoryDeleted(dir);
            } catch (Exception e) {
                logger.warn("cannot notify file changes listener", e);
            }
        }
    }

}
