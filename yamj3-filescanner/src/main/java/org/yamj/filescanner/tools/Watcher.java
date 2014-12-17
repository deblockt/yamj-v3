/*
 *      Copyright (c) 2004-2014 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.filescanner.tools;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import name.pachler.nio.file.ClosedWatchServiceException;
import name.pachler.nio.file.FileSystems;
import name.pachler.nio.file.Path;
import name.pachler.nio.file.Paths;
import name.pachler.nio.file.StandardWatchEventKind;
import static name.pachler.nio.file.StandardWatchEventKind.ENTRY_CREATE;
import static name.pachler.nio.file.StandardWatchEventKind.ENTRY_DELETE;
import static name.pachler.nio.file.StandardWatchEventKind.ENTRY_MODIFY;
import name.pachler.nio.file.WatchEvent;
import name.pachler.nio.file.WatchKey;
import name.pachler.nio.file.WatchService;
import static name.pachler.nio.file.ext.ExtendedWatchEventModifier.FILE_TREE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Watcher {

    private static final Logger LOG = LoggerFactory.getLogger(Watcher.class);
    private final WatchService watcherService = FileSystems.getDefault().newWatchService();
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private boolean trace = Boolean.FALSE;
    @SuppressWarnings("rawtypes")
    private static final WatchEvent.Kind[] STANDARD_EVENTS = {ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE};
    private boolean watchEnabled = Boolean.TRUE;    // keep watching the directories

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    /**
     * Creates a WatchService
     */
    public Watcher() {
    }

    /**
     * Creates a WatchService and registers the given directory
     *
     * @param dir
     * @throws java.io.IOException
     */
    public Watcher(Path dir) throws IOException {
        addDirectory(dir);
    }

    /**
     * Creates a WatchService and registers the given directory
     *
     * @param dir
     * @throws java.io.IOException
     */
    public Watcher(String dir) throws IOException {
        addDirectory(dir);
    }

    /**
     * Add directory to the watch list
     *
     * @param dir
     */
    public final void addDirectory(String dir) {
        addDirectory(Paths.get(dir));
    }

    /**
     * Add directory to the watch list
     *
     * @param dir
     */
    public final void addDirectory(Path dir) {
        register(dir);

        // enable trace after initial registration
        this.trace = Boolean.TRUE;
    }

    /**
     * Register the given directory with the WatchService
     *
     * @param dir
     */
    private void register(Path dir) {
        WatchKey key = null;
        try {
            key = dir.register(watcherService, STANDARD_EVENTS, FILE_TREE);
        } catch (UnsupportedOperationException ex) {
            LOG.warn("File watching not supported: {}", ex.getMessage());
            LOG.trace("Exception:", ex);
        } catch (IOException ex) {
            LOG.error("IO Error:", ex);
        }

        if (key != null) {
            if (trace) {
                Path prev = keys.get(key);
                if (prev == null) {
                    LOG.info("Register: {}", dir);
                } else {
                    if (!dir.equals(prev)) {
                        LOG.info("Update: {} -> {}", prev, dir);
                    }
                }
            }
            keys.put(key, dir);
        }
    }

    /**
     * Process all events for keys queued to the watcher
     */
    public void processEvents() {
        while (watchEnabled) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcherService.take();
            } catch (InterruptedException ex) {
                continue;
            } catch (ClosedWatchServiceException ex) {
                LOG.info("Watch service closed, terminating.");
                LOG.trace("Exception:", ex);
                watchEnabled = Boolean.FALSE;
                break;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                LOG.warn("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                @SuppressWarnings("rawtypes")
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == StandardWatchEventKind.OVERFLOW) {
                    LOG.info("Too many watched events!");
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // print out event
                LOG.info("{}: {}", event.kind().name(), child);

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

    public void stopWatching() {
        setWatching(Boolean.FALSE);
    }

    public void startWatching() {
        setWatching(Boolean.TRUE);
    }

    public void setWatching(boolean watchFlag) {
        LOG.info("{} the watch process", (watchFlag ? "Enabling" : "Disabling"));
        this.watchEnabled = watchFlag;
    }
}
