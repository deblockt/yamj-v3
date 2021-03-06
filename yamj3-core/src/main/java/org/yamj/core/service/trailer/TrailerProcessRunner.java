/*
 *      Copyright (c) 2004-2015 YAMJ Members
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
package org.yamj.core.service.trailer;

import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.core.database.model.dto.QueueDTO;

public class TrailerProcessRunner implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(TrailerProcessRunner.class);
    private final BlockingQueue<QueueDTO> queue;
    private final TrailerProcessorService service;

    public TrailerProcessRunner(BlockingQueue<QueueDTO> queue, TrailerProcessorService service) {
        this.queue = queue;
        this.service = service;
    }

    @Override
    public void run() {
        QueueDTO queueElement = queue.poll();
        while (queueElement != null) {
            try {
                service.processTrailer(queueElement);
            } catch (Exception error) {
                LOG.error("Failed to process artwork", error);
                try {
                    service.processingError(queueElement);
                } catch (Exception ignore) {
                    // ignore this error
                }
            }
            queueElement = queue.poll();
        }
    }
}
