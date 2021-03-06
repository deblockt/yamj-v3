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
package org.yamj.core.service.trailer.online;

import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.yamj.core.database.model.VideoData;
import org.yamj.core.database.model.dto.TrailerDTO;
import org.yamj.core.service.trailer.online.YouTubeTrailerScanner;

@ContextConfiguration(locations = {"classpath:spring-test.xml"})
public class YouTubeTrailerScannerTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    private YouTubeTrailerScanner youTubeTrailerScanner;

    @Test
    public void testMovieTrailers() {
        VideoData videoData = new VideoData();
        videoData.setTitle("Avatar", youTubeTrailerScanner.getScannerName());
        
        List<TrailerDTO> dtos = youTubeTrailerScanner.getTrailers(videoData);
        if (dtos != null) {
            for (TrailerDTO dto : dtos) {
                System.err.println(dto);
            }
        }
    }
}