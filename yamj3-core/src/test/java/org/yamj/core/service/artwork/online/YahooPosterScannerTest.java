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
package org.yamj.core.service.artwork.online;

import java.util.List;
import javax.annotation.Resource;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.yamj.core.database.model.VideoData;
import org.yamj.core.service.artwork.ArtworkDetailDTO;
import org.yamj.core.service.artwork.online.YahooPosterScanner;

@ContextConfiguration(locations = {"classpath:spring-test.xml"})
public class YahooPosterScannerTest extends AbstractJUnit4SpringContextTests {

    @Resource(name = "yahooPosterScanner")
    private YahooPosterScanner yahooPosterScanner;

    @Test
    public void testPosterUrl() {
        VideoData videoData = new VideoData();
        videoData.setTitle("Avatar", yahooPosterScanner.getScannerName());
        videoData.setPublicationYear(2009, yahooPosterScanner.getScannerName());
        
        List<ArtworkDetailDTO> dtos = yahooPosterScanner.getPosters(videoData);
        if (dtos != null) {
            for (ArtworkDetailDTO dto : dtos) {
                System.err.println(dto);
            }
        }
    }
}