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
package org.yamj.core.api.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.yamj.common.type.MetaDataType;
import org.yamj.core.api.model.CountGeneric;
import org.yamj.core.api.model.CountTimestamp;
import org.yamj.core.api.model.dto.ApiPersonDTO;
import org.yamj.core.api.model.dto.ApiVideoDTO;
import org.yamj.core.api.options.OptionsId;
import org.yamj.core.api.options.OptionsIndexVideo;
import org.yamj.core.api.wrapper.ApiWrapperList;
import org.yamj.core.database.service.JsonApiStorageService;

@Controller
@ResponseBody
@RequestMapping(value = "/api/index/**", method = RequestMethod.GET, produces = "application/json; charset=utf-8")
public class IndexController {

    private static final Logger LOG = LoggerFactory.getLogger(IndexController.class);
    @Autowired
    private JsonApiStorageService jsonApiStorageService;

    @RequestMapping("/video")
    public ApiWrapperList<ApiVideoDTO> getVideoList(
            @ModelAttribute("options") OptionsIndexVideo options) {
        LOG.debug("INDEX: Video list - Options: {}", options.toString());

        ApiWrapperList<ApiVideoDTO> wrapper = new ApiWrapperList<>();
        wrapper.setOptions(options);
        jsonApiStorageService.getVideoList(wrapper);
        wrapper.setStatusCheck();
        return wrapper;
    }

    @RequestMapping("/person")
    public ApiWrapperList<ApiPersonDTO> getPersonList(@ModelAttribute("options") OptionsId options) {
        LOG.debug("INDEX: Person list - Options: {}", options.toString());

        ApiWrapperList<ApiPersonDTO> wrapper = new ApiWrapperList<>();
        wrapper.setOptions(options);
        jsonApiStorageService.getPersonList(wrapper);
        wrapper.setStatusCheck();
        return wrapper;
    }

    @RequestMapping("/count")
    public List<CountTimestamp> getCount(@RequestParam(required = false, defaultValue = "all") String type) {
        List<CountTimestamp> results = new ArrayList<>();
        List<MetaDataType> requiredTypes = new ArrayList<>();

        if (!type.toLowerCase().contains("all")) {
            for (String stringType : StringUtils.split(type, ",")) {
                requiredTypes.add(MetaDataType.fromString(stringType));
            }
        } else {
            LOG.debug("Getting a count of all types");
            requiredTypes = Arrays.asList(MetaDataType.values());
        }

        for (MetaDataType singleType : requiredTypes) {
            if (singleType.isRealMetaData()) {
                LOG.debug("Getting a count of '{}'", singleType.toString());
                CountTimestamp result = jsonApiStorageService.getCountTimestamp(singleType);
                if (result != null) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    @RequestMapping("/jobs")
    public List<CountGeneric> getJobs(@RequestParam(required = false, defaultValue = "all") String job) {
        List<CountGeneric> results;

        if (StringUtils.isNotBlank(job) && !job.toLowerCase().contains("all")) {
            List<String> requiredJobs = Arrays.asList(StringUtils.split(job, ","));
            results = jsonApiStorageService.getJobCount(requiredJobs);
        } else {
            results = jsonApiStorageService.getJobCount(null);
        }
        return results;
    }
}
