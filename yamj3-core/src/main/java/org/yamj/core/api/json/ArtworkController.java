/*
 *      Copyright (c) 2004-2013 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      The YAMJ is free software: you can redistribute it and/or modify
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
 *      along with the YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.core.api.json;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.hibernate.QueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.yamj.common.model.YamjInfo;
import org.yamj.core.api.model.ApiStatus;
import org.yamj.core.api.model.ApiWrapperList;
import org.yamj.core.api.model.ApiWrapperSingle;
import org.yamj.core.api.model.ParameterType;
import org.yamj.core.api.model.Parameters;
import org.yamj.core.api.model.dto.IndexArtworkDTO;
import org.yamj.core.api.options.OptionsIndexArtwork;
import org.yamj.core.database.model.Artwork;
import org.yamj.core.database.service.JsonApiStorageService;

@Controller
@RequestMapping("/api/artwork/**")
public class ArtworkController {

    private static final Logger LOG = LoggerFactory.getLogger(ArtworkController.class);
    private static final YamjInfo YAMJ_INFO = new YamjInfo(ArtworkController.class);
    @Autowired
    private JsonApiStorageService api;

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiWrapperSingle<Artwork> getArtworkById(@PathVariable String id) {
        LOG.info("Getting artwork with ID '{}'", id);
        ApiWrapperSingle<Artwork> wrapper = new ApiWrapperSingle<Artwork>();
        Artwork artwork = api.getEntityById(Artwork.class, Long.parseLong(id));
        wrapper.setResult(artwork);
        wrapper.setStatusCheck();
        return wrapper;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public ApiWrapperList<Artwork> getArtworkListOld(
            @RequestParam(required = false, defaultValue = "") String artwork,
            @RequestParam(required = false, defaultValue = "") String type,
            @RequestParam(required = false, defaultValue = "-1") Integer id,
            @RequestParam(required = false, defaultValue = "-1") Integer start,
            @RequestParam(required = false, defaultValue = "-1") Integer max) {

        Parameters p = new Parameters();
        p.add(ParameterType.ARTWORK_TYPE, artwork);
        p.add(ParameterType.VIDEO_TYPE, type);
        p.add(ParameterType.ID, id);
        p.add(ParameterType.START, start);
        p.add(ParameterType.MAX, max);

        LOG.info("Getting artwork list with {}", p.toString());
        ApiWrapperList<Artwork> wrapper = new ApiWrapperList<Artwork>();
        wrapper.setParameters(p);
        try {
            List<Artwork> results = api.getArtworkListOld(p);
            wrapper.setResults(results);
            wrapper.setStatusCheck();
        } catch (QueryException ex) {
            List<Artwork> results = Collections.emptyList();
            wrapper.setResults(results);
            wrapper.setStatus(new ApiStatus(400, "Error with query"));
            LOG.error("Exception: {}", ex.getMessage());
        }
        return wrapper;
    }

    @RequestMapping(value = "/test/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiWrapperSingle<IndexArtworkDTO> getArtwork(@PathVariable Long id) throws IOException {
        ApiWrapperSingle<IndexArtworkDTO> wrapper = new ApiWrapperSingle<IndexArtworkDTO>();

        LOG.info("Attempting to retrieve artwork with id '{}'", id);
        IndexArtworkDTO artwork = api.getArtworkById(id);
        LOG.info("Artwork: {}", artwork.toString());

        // Add the result to the wrapper
        wrapper.setResult(artwork);
        wrapper.setStatusCheck();
        wrapper.processYamjInfo(YAMJ_INFO);

        return wrapper;
    }

    @RequestMapping(value = "/test/list", method = RequestMethod.GET)
    @ResponseBody
    public ApiWrapperList<IndexArtworkDTO> getArtworkList(@ModelAttribute("options") OptionsIndexArtwork options) {
        LOG.info("INDEX: Artwork list - Options: {}", options.toString());
        ApiWrapperList<IndexArtworkDTO> wrapper = new ApiWrapperList<IndexArtworkDTO>();
        wrapper.setParameters(options);
        wrapper.setResults(api.getArtworkList(wrapper));
        wrapper.setStatusCheck();
        wrapper.processYamjInfo(YAMJ_INFO);

        return wrapper;
    }
}
